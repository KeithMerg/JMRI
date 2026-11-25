package jmri.jmrix.lenz.xnetsimulator.simulatedtrains;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jmri.DccLocoAddress;
import jmri.InvokeOnAnyThread;
import jmri.SpeedStepMode;
import jmri.jmrit.simulation.SimulatedSpeedSource;
import jmri.jmrix.lenz.xnetsimulator.XNetSimulatorAdapter;

/**
 * Speed commands for simulated trains from an XpressNet Simulator connection:
 * what each loco is told by the messages the simulated command station is
 * sent, read here as the XpressNet specification gives them, not with JMRI's
 * XpressNet code, so a fault there shows as a train moving differently from
 * what was asked.
 * <ul>
 * <li>Locomotive speed and direction (0xE4, then 0x10, 0x11, 0x12 or 0x13 for
 * 14, 27, 28 or 128 speed steps, the address high and low bytes, then
 * RVVVVVVV). An address below 100 is sent with a high byte of 0; a higher one
 * as the address plus 0xC000. R is 1 for forward. In 128 steps V is 0 stop, 1
 * emergency stop, 2 to 127 steps 1 to 126; in 27 and 28 steps bits 0 to 3
 * are the speed and bit 4 its least significant bit, as in the DCC
 * instruction (0 and 1 stop, 2 and 3 emergency stop, 4 up steps 1 up); in 14
 * steps bits 0 to 3 are 0 stop, 1 emergency stop, 2 to 15 steps 1 to 14.
 * <li>Emergency stop for a locomotive (0x92 with the address high and low
 * bytes; 0x91 with the low byte for XpressNet 1 and 2).
 * <li>Emergency stop for all locomotives (0x80): every loco sent a command so
 * far.
 * </ul>
 * XpressNet has no long and short addresses of the same number: a loco is
 * found by its number alone. A loco keeps its last command when it is sent
 * nothing more, as a decoder keeps running.
 *
 * @author Keith Bruce Copyright (C) 2026
 * @since 5.19.2
 */
@javax.annotation.concurrent.ThreadSafe
public class XNetSimulatedSpeedSource implements SimulatedSpeedSource, XNetSimulatorAdapter.MessageListener {

    private final Map<Integer, Command> commands = new ConcurrentHashMap<>();
    private final List<CommandListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    @InvokeOnAnyThread
    public Command getCommand(@Nonnull DccLocoAddress address) {
        return commands.get(address.getNumber());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @InvokeOnAnyThread
    public void addCommandListener(@Nonnull CommandListener listener) {
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @InvokeOnAnyThread
    public void removeCommandListener(@Nonnull CommandListener listener) {
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @InvokeOnAnyThread
    public synchronized void messageReceived(String message, long timeNanos) {
        String text = message.trim();
        if (text.isEmpty()) {
            return;
        }
        String[] w = text.split("\\s+");
        int[] b = new int[w.length];
        try {
            for (int i = 0; i < w.length; i++) {
                b[i] = Integer.parseInt(w[i], 16) & 0xFF;
            }
        } catch (NumberFormatException e) {
            return; // not bytes
        }
        if (b[0] == 0xE4 && b.length >= 5) {
            SpeedStepMode mode;
            switch (b[1]) {
                case 0x10:
                    mode = SpeedStepMode.NMRA_DCC_14;
                    break;
                case 0x11:
                    mode = SpeedStepMode.NMRA_DCC_27;
                    break;
                case 0x12:
                    mode = SpeedStepMode.NMRA_DCC_28;
                    break;
                case 0x13:
                    mode = SpeedStepMode.NMRA_DCC_128;
                    break;
                default:
                    return; // functions and other locomotive operations
            }
            received(address(b[2], b[3]), speed(b[4], mode, timeNanos));
        } else if (b[0] == 0x92 && b.length >= 3) {
            estop(address(b[1], b[2]), timeNanos);
        } else if (b[0] == 0x91 && b.length >= 2) {
            estop(b[1], timeNanos);
        } else if (b[0] == 0x80 && b.length >= 1) {
            for (Integer a : commands.keySet()) {
                estop(a, timeNanos);
            }
        }
    }

    private static int address(int high, int low) {
        return high == 0 ? low : ((high << 8) | low) - 0xC000;
    }

    /**
     * @param value     the speed and direction byte, RVVVVVVV
     * @param mode      the speed step mode
     * @param timeNanos when it was received
     * @return the command, as a decoder receives it
     */
    @Nonnull
    static Command speed(int value, @Nonnull SpeedStepMode mode, long timeNanos) {
        boolean forward = (value & 0x80) != 0;
        boolean estop;
        int step;
        if (mode == SpeedStepMode.NMRA_DCC_128) {
            int v = value & 0x7F;
            estop = v == 1;
            step = v < 2 ? 0 : v - 1;
        } else if (mode == SpeedStepMode.NMRA_DCC_14) {
            int v = value & 0x0F;
            estop = v == 1;
            step = v < 2 ? 0 : v - 1;
        } else {
            int v = ((value & 0x0F) << 1) | ((value >> 4) & 1);
            estop = v == 2 || v == 3;
            step = v < 4 ? 0 : v - 3;
        }
        return new Command(Math.min(step, mode.numSteps), mode, forward, estop, timeNanos);
    }

    private void estop(int address, long timeNanos) {
        Command old = commands.get(address);
        received(address, old == null
                ? new Command(0, SpeedStepMode.NMRA_DCC_128, true, true, timeNanos)
                : new Command(0, old.getMode(), old.isForward(), true, timeNanos));
    }

    private void received(int address, Command now) {
        Command old = commands.get(address);
        if (old != null && old.getSpeedStep() == now.getSpeedStep() && old.getMode() == now.getMode()
                && old.isForward() == now.isForward() && old.isEmergencyStop() == now.isEmergencyStop()) {
            return; // the same command again: nothing changes for the decoder
        }
        commands.put(address, now);
        DccLocoAddress a = new DccLocoAddress(address, address >= 100);
        for (CommandListener l : listeners) {
            l.commandChanged(a);
        }
    }
}
