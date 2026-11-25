package jmri.jmrix.can.cbus.simulator;

import java.util.List;

import jmri.jmrit.simulation.SimulationTestUtil;
import jmri.jmrix.can.CanSystemConnectionMemo;
import jmri.jmrix.can.TrafficControllerScaffold;
import jmri.util.JUnitUtil;

import org.junit.jupiter.api.*;

/**
 * Tests for CbusSimulationAdapter.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class CbusSimulationAdapterTest {

    @Test
    public void testOffersEachCanSimulationConnectionTheSameObjects() {
        CanSystemConnectionMemo a = CbusSimulationTestUtil.addCanSimulation("M");
        CanSystemConnectionMemo b = CbusSimulationTestUtil.addCanSimulation("M2");
        try {
            CbusSimulationAdapter adapter = new CbusSimulationAdapter();
            CbusSimulator sa = a.get(CbusSimulator.class);
            CbusSimulator sb = b.get(CbusSimulator.class);
            Assertions.assertEquals(List.of(sa.getSimulatedSpeedSource(), sb.getSimulatedSpeedSource()),
                    adapter.getSpeedSources());
            Assertions.assertEquals(List.of(sa.getSimulatedSensorOutput(), sb.getSimulatedSensorOutput()),
                    adapter.getDetectorOutputs());
            Assertions.assertEquals(adapter.getSpeedSources(), new CbusSimulationAdapter().getSpeedSources(),
                    "the same objects each time");
        } finally {
            CbusSimulationTestUtil.dispose(b);
            CbusSimulationTestUtil.dispose(a);
        }
        Assertions.assertTrue(new CbusSimulationAdapter().getSpeedSources().isEmpty(), "none once disposed");
        Assertions.assertTrue(new CbusSimulationAdapter().getDetectorOutputs().isEmpty());
    }

    /**
     * @param memo a connection's memo
     * @return the connection's port
     */
    private static jmri.jmrix.PortAdapter portOf(CanSystemConnectionMemo memo) {
        for (jmri.jmrix.ConnectionConfig c : jmri.InstanceManager.getDefault(
                jmri.jmrix.ConnectionConfigManager.class).getConnections()) {
            jmri.jmrix.PortAdapter p = c.getAdapter();
            if (p != null && p.getSystemConnectionMemo() == memo) {
                return p;
            }
        }
        throw new IllegalStateException("no connection for " + memo.getUserName());
    }

    @Test
    public void testNotOfferedWhenTheSettingLeavesItOut() {
        CanSystemConnectionMemo a = CbusSimulationTestUtil.addCanSimulation("M");
        try {
            jmri.jmrix.PortAdapter port = portOf(a);
            CbusSimulator sa = a.get(CbusSimulator.class);
            Assertions.assertFalse(sa.isLeftOutOfSimulatedTrains(), "used by default");
            Assertions.assertEquals(1, new CbusSimulationAdapter().getSpeedSources().size());
            String[] choices = port.getOptionChoices(jmri.jmrix.AbstractPortController.SIMULATED_TRAINS_OPTION);
            port.setOptionState(jmri.jmrix.AbstractPortController.SIMULATED_TRAINS_OPTION, choices[1]);
            Assertions.assertTrue(sa.isLeftOutOfSimulatedTrains());
            Assertions.assertTrue(new CbusSimulationAdapter().getSpeedSources().isEmpty(), "no speed source");
            Assertions.assertTrue(new CbusSimulationAdapter().getDetectorOutputs().isEmpty(), "no sensor output");
            Assertions.assertTrue(new CbusSimulationAdapter().getReporterOutputs().isEmpty(), "no reporter output");
        } finally {
            CbusSimulationTestUtil.dispose(a);
        }
    }

    @Test
    public void testNeverOffersAConnectionToARealBus() {
        CanSystemConnectionMemo memo = new CanSystemConnectionMemo();
        TrafficControllerScaffold tc = new TrafficControllerScaffold();
        memo.setTrafficController(tc);
        CbusSimulator sim = new CbusSimulator(memo);
        jmri.InstanceManager.store(sim, CbusSimulator.class);
        try {
            Assertions.assertFalse(sim.isOnCanSimulation());
            Assertions.assertTrue(new CbusSimulationAdapter().getSpeedSources().isEmpty());
            Assertions.assertTrue(new CbusSimulationAdapter().getDetectorOutputs().isEmpty());
            Assertions.assertThrows(IllegalArgumentException.class, () -> new CbusSimulatedCab(memo),
                    "no simulated cab on a real bus");
        } finally {
            jmri.InstanceManager.deregister(sim, CbusSimulator.class);
            sim.dispose();
            tc.terminateThreads();
            memo.dispose();
        }
    }

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
        JUnitUtil.resetProfileManager();
        SimulationTestUtil.setUpSimulatedProfile();
    }

    @AfterEach
    public void tearDown() {
        JUnitUtil.deregisterBlockManagerShutdownTask();
        JUnitUtil.tearDown();
    }
}
