package jmri.jmrix.lenz.xnetsimulator.simulatedtrains;

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
 * Tests for {@link XNetSimulatedSpeedSource}: the messages a simulated
 * XpressNet command station is sent, written out here byte by byte from the
 * XpressNet specification, read as a decoder receives their speed.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class XNetSimulatedSpeedSourceTest {

    private final DccLocoAddress three = new DccLocoAddress(3, false);
    private final DccLocoAddress longOne = new DccLocoAddress(1234, true);
    private XNetSimulatedSpeedSource s;
    private final List<DccLocoAddress> told = new ArrayList<>();

    @Test
    public void test128Steps() {
        assertNull(s.getCommand(three), "nothing sent yet");
        s.messageReceived("E4 13 00 03 C0 34", 1); // short 3, forward, 0x40: step 63
        Command c = s.getCommand(three);
        assertEquals(63, c.getSpeedStep());
        assertEquals(SpeedStepMode.NMRA_DCC_128, c.getMode());
        assertTrue(c.isForward());
        s.messageReceived("E4 13 C4 D2 05 E4", 2); // 1234 + 0xC000, reverse, step 4
        assertEquals(4, s.getCommand(longOne).getSpeedStep());
        assertFalse(s.getCommand(longOne).isForward());
        s.messageReceived("E4 13 00 03 81 75", 3); // emergency stop
        assertTrue(s.getCommand(three).isEmergencyStop());
        assertEquals(List.of(three, longOne, three), told);
    }

    @Test
    public void testFoundByNumberAlone() {
        s.messageReceived("E4 13 00 03 C0 34", 1);
        assertEquals(63, s.getCommand(new DccLocoAddress(3, true)).getSpeedStep(),
                "XpressNet sends 3 as a short address, whatever JMRI calls it");
    }

    @Test
    public void test28And27Steps() {
        s.messageReceived("E4 12 00 03 94 61", 1); // 1 00 1 0100: forward, value 01001 (9), step 6
        Command c = s.getCommand(three);
        assertEquals(SpeedStepMode.NMRA_DCC_28, c.getMode());
        assertEquals(6, c.getSpeedStep());
        assertTrue(c.isForward());
        s.messageReceived("E4 11 00 03 0F 19", 2); // reverse, value 11110 (30): step 27 of 27
        assertEquals(SpeedStepMode.NMRA_DCC_27, s.getCommand(three).getMode());
        assertEquals(27, s.getCommand(three).getSpeedStep());
        assertFalse(s.getCommand(three).isForward());
        s.messageReceived("E4 12 00 03 01 F6", 3); // value 00010: emergency stop
        assertTrue(s.getCommand(three).isEmergencyStop());
    }

    @Test
    public void test14Steps() {
        s.messageReceived("E4 10 00 03 06 F1", 1); // reverse, value 6: step 5
        Command c = s.getCommand(three);
        assertEquals(SpeedStepMode.NMRA_DCC_14, c.getMode());
        assertEquals(5, c.getSpeedStep());
        assertFalse(c.isForward());
    }

    @Test
    public void testEmergencyStops() {
        s.messageReceived("E4 13 00 03 C0 34", 1);
        s.messageReceived("E4 13 C4 D2 85 64", 1);
        s.messageReceived("92 00 03 91", 2); // emergency stop for 3
        assertTrue(s.getCommand(three).isEmergencyStop());
        assertTrue(s.getCommand(three).isForward(), "direction kept");
        assertFalse(s.getCommand(longOne).isEmergencyStop());
        s.messageReceived("E4 13 00 03 C0 34", 3);
        s.messageReceived("91 03 92", 4); // XpressNet 1 and 2 form
        assertTrue(s.getCommand(three).isEmergencyStop());
        s.messageReceived("E4 13 00 03 C0 34", 5);
        s.messageReceived("80 80", 6); // emergency stop for all
        assertTrue(s.getCommand(three).isEmergencyStop());
        assertTrue(s.getCommand(longOne).isEmergencyStop());
    }

    @Test
    public void testOtherMessagesIgnored() {
        s.messageReceived("E4 20 00 03 01 C6", 1); // function group 1
        s.messageReceived("21 80 A1", 1); // track power off: not a loco command
        s.messageReceived("E3 00 00 03 E0", 1); // locomotive information request
        s.messageReceived("E4 13 00", 1); // too short
        s.messageReceived("", 1);
        s.messageReceived("XY", 1);
        assertTrue(told.isEmpty());
    }

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
        s = new XNetSimulatedSpeedSource();
        s.addCommandListener(told::add);
    }

    @AfterEach
    public void tearDown() {
        JUnitUtil.tearDown();
    }
}
