package jmri.jmrix.nce.simulator.simulatedtrains;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nonnull;

import jmri.InstanceManager;
import jmri.InvokeOnAnyThread;
import jmri.SystemConnectionMemo;
import jmri.jmrit.simulation.SimulatedDetectorOutput;
import jmri.jmrit.simulation.SimulatedSpeedSource;
import jmri.jmrit.simulation.SimulationAdapter;
import jmri.jmrix.ConnectionConfig;
import jmri.jmrix.ConnectionConfigManager;
import jmri.jmrix.nce.simulator.SimulatorAdapter;

import org.openide.util.lookup.ServiceProvider;

/**
 * NCE's part in simulated trains: for each NCE Simulator connection, the
 * speed commands its simulated command station receives, and the AIU inputs
 * for its sensors. A connection to a real command station is never offered,
 * nor is a simulator whose Simulated trains setting leaves it out.
 *
 * @author Keith Bruce Copyright (C) 2026
 * @since 5.19.2
 */
@ServiceProvider(service = SimulationAdapter.class)
public class NceSimulationAdapter implements SimulationAdapter {

    /** The speed source and sensor output of each simulator, made once, when first offered. */
    private static final Map<SimulatorAdapter, Parts> PARTS = new WeakHashMap<>();

    private static final class Parts {

        final NceSimulatedSpeedSource speedSource = new NceSimulatedSpeedSource();
        final NceSimulatedSensorOutput sensorOutput;

        Parts(SimulatorAdapter simulator) {
            sensorOutput = new NceSimulatedSensorOutput(simulator);
            simulator.addMessageListener(speedSource);
        }
    }

    private static Parts parts(SimulatorAdapter simulator) {
        synchronized (PARTS) {
            return PARTS.computeIfAbsent(simulator, Parts::new);
        }
    }

    /**
     * @param simulator an NCE Simulator connection's simulated command
     *                  station
     * @return the speed source following it
     */
    @Nonnull
    public static NceSimulatedSpeedSource speedSource(@Nonnull SimulatorAdapter simulator) {
        return parts(simulator).speedSource;
    }

    /**
     * @param simulator an NCE Simulator connection's simulated command
     *                  station
     * @return the output reporting on its sensors
     */
    @Nonnull
    public static NceSimulatedSensorOutput sensorOutput(@Nonnull SimulatorAdapter simulator) {
        return parts(simulator).sensorOutput;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    @InvokeOnAnyThread
    public List<SimulatedSpeedSource> getSpeedSources() {
        List<SimulatedSpeedSource> list = new ArrayList<>();
        for (SimulatorAdapter s : simulators()) {
            list.add(speedSource(s));
        }
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    @InvokeOnAnyThread
    public List<SimulatedDetectorOutput> getDetectorOutputs() {
        List<SimulatedDetectorOutput> list = new ArrayList<>();
        for (SimulatorAdapter s : simulators()) {
            list.add(sensorOutput(s));
        }
        return list;
    }

    private static List<SimulatorAdapter> simulators() {
        List<SimulatorAdapter> list = new ArrayList<>();
        ConnectionConfigManager ccm = InstanceManager.getNullableDefault(ConnectionConfigManager.class);
        if (ccm == null) {
            return list;
        }
        for (ConnectionConfig c : ccm.getConnections()) {
            if (c.getAdapter() instanceof SimulatorAdapter) {
                SimulatorAdapter s = (SimulatorAdapter) c.getAdapter();
                SystemConnectionMemo memo = s.getSystemConnectionMemo();
                if (s.isForSimulatedTrains() && memo != null
                        && InstanceManager.getList(SystemConnectionMemo.class).contains(memo)) {
                    list.add(s);
                }
            }
        }
        return list;
    }
}
