package jmri.jmrix.nce.simulator.simulatedtrains;

import javax.annotation.Nonnull;

import jmri.InvokeOnAnyThread;
import jmri.InvokeOnLayoutThread;
import jmri.Manager;
import jmri.Sensor;
import jmri.jmrit.simulation.SimulatedDetectorOutput;
import jmri.jmrix.nce.NceSensor;
import jmri.jmrix.nce.simulator.SimulatorAdapter;

/**
 * Reports simulated detection on an NCE Simulator connection's sensors, as a
 * detector wired to an AIU input does: the simulated command station gives
 * the input's state when JMRI next polls that AIU.
 * <p>
 * An inverted sensor's input is reported the other way round, so that the
 * sensor ends up in the state the simulated train gives it. A change shows
 * when the AIU is next polled, so its time is the poll's.
 *
 * @author Keith Bruce Copyright (C) 2026
 * @since 5.19.2
 */
@javax.annotation.concurrent.ThreadSafe
public class NceSimulatedSensorOutput implements SimulatedDetectorOutput {

    private final SimulatorAdapter adapter;

    /**
     * @param adapter the NCE Simulator connection's simulated command station
     */
    public NceSimulatedSensorOutput(@Nonnull SimulatorAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * {@inheritDoc}
     * <p>
     * True for a sensor of this connection, by its system name's prefix, on
     * an AIU input. Its input is set to match the sensor now, unreported: a
     * poll gives all fourteen inputs of an AIU together, so an inverted
     * sensor's input must start active for the sensor to stay inactive.
     */
    @Override
    @InvokeOnLayoutThread
    public boolean reports(@Nonnull Sensor sensor) {
        int input = input(sensor);
        if (input < 0) {
            return false;
        }
        adapter.setAiuInput(input, (sensor.getKnownState() == Sensor.ACTIVE) != sensor.getInverted());
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if the sensor is not one this output
     *                                  {@link #reports}
     */
    @Override
    @InvokeOnAnyThread
    public void report(@Nonnull Sensor sensor, int state, long timeNanos) {
        int input = input(sensor);
        if (input < 0) {
            throw new IllegalArgumentException("Not an NCE Simulator AIU input: " + sensor.getSystemName());
        }
        adapter.setAiuInput(input, (state == Sensor.ACTIVE) != sensor.getInverted());
    }

    /**
     * @param sensor a sensor
     * @return its number as an NCE sensor numbers it, 16 per AIU from 0, or
     *         -1 if it is not on an AIU input of this connection
     */
    private int input(Sensor sensor) {
        if (!(sensor instanceof NceSensor)) {
            return -1;
        }
        String name = sensor.getSystemName();
        if (!adapter.getSystemConnectionMemo().getSystemPrefix().equals(Manager.getSystemPrefix(name))) {
            return -1;
        }
        String address = name.substring(Manager.getSystemPrefixLength(name) + 1);
        int number;
        try {
            int colon = address.indexOf(':');
            number = colon < 0 ? Integer.parseInt(address)
                    : (Integer.parseInt(address.substring(0, colon)) - 1) * 16
                            + Integer.parseInt(address.substring(colon + 1)) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
        int aiu = number / 16 + 1;
        return number < 0 || aiu > 63 || number % 16 > 13 ? -1 : number;
    }
}
