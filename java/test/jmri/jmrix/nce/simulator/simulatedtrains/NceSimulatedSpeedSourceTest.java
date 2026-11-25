package jmri.jmrix.nce.simulator.simulatedtrains;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import jmri.DccLocoAddress;
import jmri.SpeedStepMode;
import jmri.jmrit.simulation.SimulatedSpeedSource.Command;
import jmri.util.JUnitUtil;

import org.junit.jupiter.api.*;

/**
 * Tests for {@link NceSimulatedSpeedSource}: the locomotive control commands
 * a simulated NCE command station is sent, written out here byte by byte from
 * NCE's binary command documentation, read as a decoder receives their speed.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class NceSimulatedSpeedSourceTest {

    private final DccLocoAddress three = new DccLocoAddress(3, false);
    private final DccLocoAddress longThree = new DccLocoAddress(3, true);
    private final DccLocoAddress longOne = new DccLocoAddress(1234, true);
    private NceSimulatedSpeedSource s;
    private final List<DccLocoAddress> told = new ArrayList<>();

    @Test
    public void test128Steps() {
        assertNull(s.getCommand(three), "nothing sent yet");
        s.messageReceived("A2 00 03 04 3F", 1); // short 3, forward 128 steps, 63
        Command c = s.getCommand(three);
        assertEquals(63, c.getSpeedStep());
        assertEquals(SpeedStepMode.NMRA_DCC_128, c.getMode());
        assertTrue(c.isForward());
        s.messageReceived("A2 C4 D2 03 04", 2); // long 1234, reverse, 4
        assertEquals(4, s.getCommand(longOne).getSpeedStep());
        assertFalse(s.getCommand(longOne).isForward());
        s.messageReceived("A2 C0 03 04 7F", 3); // long 3: top step
        assertEquals(126, s.getCommand(longThree).getSpeedStep());
        assertEquals(63, s.getCommand(three).getSpeedStep(), "short 3 is another loco");
        assertEquals(List.of(three, longOne, longThree), told);
    }

    @Test
    public void test28Steps() {
        s.messageReceived("A2 00 03 02 3F", 1); // forward 28 steps, 63 of 126
        Command c = s.getCommand(three);
        assertEquals(SpeedStepMode.NMRA_DCC_28, c.getMode());
        assertEquals(14, c.getSpeedStep());
        assertTrue(c.isForward());
        s.messageReceived("A2 00 03 01 7E", 2); // reverse, top
        assertEquals(28, s.getCommand(three).getSpeedStep());
        assertFalse(s.getCommand(three).isForward());
    }

    @Test
    public void testEmergencyStop() {
        s.messageReceived("A2 00 03 04 3F", 1);
        s.messageReceived("A2 00 03 06 00", 2); // emergency stop forward
        assertTrue(s.getCommand(three).isEmergencyStop());
        assertTrue(s.getCommand(three).isForward());
        s.messageReceived("A2 00 03 05 00", 3); // emergency stop reverse
        assertFalse(s.getCommand(three).isForward());
        s.messageReceived("A2 00 03 04 3F", 4);
        assertFalse(s.getCommand(three).isEmergencyStop());
    }

    @Test
    public void testOtherMessagesIgnored() {
        s.messageReceived("A2 00 03 07 10", 1); // function group 1
        s.messageReceived("8A 01", 1); // AIU poll
        s.messageReceived("A2 00 03", 1); // too short
        s.messageReceived("", 1);
        s.messageReceived("XY", 1);
        assertTrue(told.isEmpty());
    }

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
        s = new NceSimulatedSpeedSource();
        s.addCommandListener(told::add);
    }

    @AfterEach
    public void tearDown() {
        JUnitUtil.tearDown();
    }
}
