package jmri.jmrix.lenz.xnetsimulator.simulatedtrains;

import javax.annotation.Nonnull;

import jmri.InvokeOnAnyThread;
import jmri.InvokeOnLayoutThread;
import jmri.Manager;
import jmri.Sensor;
import jmri.jmrit.simulation.SimulatedDetectorOutput;
import jmri.jmrix.lenz.XNetAddress;
import jmri.jmrix.lenz.XNetSensor;
import jmri.jmrix.lenz.xnetsimulator.XNetSimulatorAdapter;
import jmri.util.ThreadingUtil;

/**
 * Reports simulated detection on an XpressNet Simulator connection's sensors,
 * as a feedback module does: the simulated command station sends a feedback
 * broadcast with the four inputs of that half of the module.
 * <p>
 * An inverted sensor's input is reported the other way round, so that the
 * sensor ends up in the state the simulated train gives it. The broadcast's
 * received time is when the connection reads it, a moment after the change.
 *
 * @author Keith Bruce Copyright (C) 2026
 * @since 5.19.2
 */
@javax.annotation.concurrent.ThreadSafe
public class XNetSimulatedSensorOutput implements SimulatedDetectorOutput {

    private final XNetSimulatorAdapter adapter;

    /**
     * @param adapter the XpressNet Simulator connection's simulated command
     *                station
     */
    public XNetSimulatedSensorOutput(@Nonnull XNetSimulatorAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * {@inheritDoc}
     * <p>
     * True for a sensor of this connection, by its system name's prefix,
     * with a feedback input number. Its input is set to match the sensor
     * now, unreported: a feedback broadcast reports four inputs together, so
     * an inverted sensor's input must start active for the sensor to stay
     * inactive when another of the four changes.
     */
    @Override
    @InvokeOnLayoutThread
    public boolean reports(@Nonnull Sensor sensor) {
        int input = input(sensor);
        if (input <= 0) {
            return false;
        }
        adapter.setFeedback(input, (sensor.getKnownState() == Sensor.ACTIVE) != sensor.getInverted());
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The broadcast is sent on the layout thread (Swing thread), later, so
     * that the command station's replies are always written by long-lived
     * threads.
     *
     * @throws IllegalArgumentException if the sensor is not one this output
     *                                  {@link #reports}
     */
    @Override
    @InvokeOnAnyThread
    public void report(@Nonnull Sensor sensor, int state, long timeNanos) {
        int input = input(sensor);
        if (input <= 0) {
            throw new IllegalArgumentException("Not an XpressNet Simulator feedback input: " + sensor.getSystemName());
        }
        boolean active = (state == Sensor.ACTIVE) != sensor.getInverted();
        ThreadingUtil.runOnLayoutEventually(() -> adapter.reportFeedback(input, active));
    }

    /**
     * @param sensor a sensor
     * @return its feedback input number, 1 or more, from its system name, or
     *         -1 if it is not an XpressNet sensor of this connection
     */
    private int input(Sensor sensor) {
        if (!(sensor instanceof XNetSensor)) {
            return -1;
        }
        String prefix = adapter.getSystemConnectionMemo().getSystemPrefix();
        String name = sensor.getSystemName();
        if (!prefix.equals(Manager.getSystemPrefix(name))) {
            return -1;
        }
        return XNetAddress.getBitFromSystemName(name, prefix);
    }
}
