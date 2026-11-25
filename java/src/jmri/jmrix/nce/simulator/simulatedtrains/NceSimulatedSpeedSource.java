package jmri.jmrix.nce.simulator.simulatedtrains;

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
import jmri.jmrix.nce.simulator.SimulatorAdapter;

/**
 * Speed commands for simulated trains from an NCE Simulator connection: what
 * each loco is told by the locomotive control commands the simulated command
 * station is sent, read here as NCE's binary command documentation gives
 * them, not with JMRI's NCE code, so a fault there shows as a train moving
 * differently from what was asked.
 * <p>
 * A locomotive control command is 0xA2, the address high and low bytes (the
 * high byte with bits 6 and 7 set for a long address), a sub-command and its
 * data: 1 and 2 set the speed in 28 steps, reverse and forward; 3 and 4 in
 * 128 steps; 5 and 6 are an emergency stop, reverse and forward. The data of
 * a speed command is 0 to 127 in either mode; 0 is stop. In 128 steps it is
 * taken as the step, at most 126; in 28 steps it is taken to be scaled to
 * the 28 steps, as the documentation gives the same range for both.
 * Functions and other sub-commands move no loco.
 * <p>
 * A loco keeps its last command when it is sent nothing more, as a decoder
 * keeps running.
 *
 * @author Keith Bruce Copyright (C) 2026
 * @since 5.19.2
 */
@javax.annotation.concurrent.ThreadSafe
public class NceSimulatedSpeedSource implements SimulatedSpeedSource, SimulatorAdapter.MessageListener {

    private final Map<DccLocoAddress, Command> commands = new ConcurrentHashMap<>();
    private final List<CommandListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    @InvokeOnAnyThread
    public Command getCommand(@Nonnull DccLocoAddress address) {
        return commands.get(address);
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
        if (b[0] != 0xA2 || b.length < 5) {
            return;
        }
        DccLocoAddress address = (b[1] & 0xC0) == 0xC0
                ? new DccLocoAddress(((b[1] & 0x3F) << 8) | b[2], true)
                : new DccLocoAddress(b[2], false);
        int data = b[4] & 0x7F;
        switch (b[3]) {
            case 1:
            case 2:
                received(address, new Command((int) Math.round(data * 28.0 / 126), SpeedStepMode.NMRA_DCC_28,
                        b[3] == 2, false, timeNanos));
                break;
            case 3:
            case 4:
                received(address, new Command(Math.min(data, 126), SpeedStepMode.NMRA_DCC_128,
                        b[3] == 4, false, timeNanos));
                break;
            case 5:
            case 6:
                Command old = commands.get(address);
                received(address, new Command(0, old == null ? SpeedStepMode.NMRA_DCC_128 : old.getMode(),
                        b[3] == 6, true, timeNanos));
                break;
            default:
                break; // functions and other sub-commands
        }
    }

    private void received(DccLocoAddress address, Command now) {
        Command old = commands.get(address);
        if (old != null && old.getSpeedStep() == now.getSpeedStep() && old.getMode() == now.getMode()
                && old.isForward() == now.isForward() && old.isEmergencyStop() == now.isEmergencyStop()) {
            return; // the same command again: nothing changes for the decoder
        }
        commands.put(address, now);
        for (CommandListener l : listeners) {
            l.commandChanged(address);
        }
    }
}
