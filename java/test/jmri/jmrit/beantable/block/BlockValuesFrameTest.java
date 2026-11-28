package jmri.jmrit.beantable.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jmri.Block;
import jmri.BlockManager;
import jmri.InstanceManager;
import jmri.JmriException;
import jmri.Sensor;
import jmri.SensorManager;
import jmri.jmrit.beantable.BlockTableAction;
import jmri.jmrit.roster.Roster;
import jmri.jmrit.roster.RosterEntry;
import jmri.util.JUnitUtil;
import jmri.util.ThreadingUtil;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Tests for BlockValuesFrame, and clearing Block values.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class BlockValuesFrameTest {

    private BlockValuesFrame frame;
    private Block east;
    private Block west;
    private Block yard;
    private Sensor eastSensor;
    private Sensor yardSensor;

    private Block block(String name, String sensor) throws JmriException {
        Block b = InstanceManager.getDefault(BlockManager.class).createNewBlock("IB" + name, name);
        if (sensor != null) {
            b.setSensor(sensor);
        }
        return b;
    }

    @Test
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    public void testListsOccupiedBlocksWithNoValueAndNamesThem() throws JmriException {
        ThreadingUtil.runOnGUI(() -> {
            frame = new BlockValuesFrame();
            frame.initComponents();
        });
        assertEquals(List.of(east), frame.getListed(), "occupied, no value; not West (empty), not Yard (has a value)");

        RosterEntry re = new RosterEntry();
        re.setId("Class 37");
        Roster.getDefault().addEntry(re);
        ThreadingUtil.runOnGUI(() -> {
            frame.setNewValue(east, "Class 37");
            assertEquals(1, frame.apply());
        });
        assertSame(re, east.getValue(), "a Roster entry is put in the Block as itself");
        assertTrue(frame.getListed().isEmpty(), "named, so no longer listed");

        westSensorActive();
        JUnitUtil.waitFor(() -> frame.getListed().contains(west), "listed once it becomes occupied");
        ThreadingUtil.runOnGUI(() -> {
            frame.setNewValue(west, "Goods 2");
            frame.apply();
        });
        assertEquals("Goods 2", west.getValue(), "anything else as text");
    }

    private void westSensorActive() throws JmriException {
        InstanceManager.getDefault(SensorManager.class).provideSensor("IS2").setState(Sensor.ACTIVE);
    }

    @Test
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    public void testIncludeBlocksWithAValueAndCopy() {
        ThreadingUtil.runOnGUI(() -> {
            frame = new BlockValuesFrame();
            frame.initComponents();
        });
        assertEquals("East: (none)\n", frame.text());
        ThreadingUtil.runOnGUI(() -> {
            javax.swing.JCheckBox include = findCheckBox(frame.getContentPane());
            include.doClick();
        });
        assertEquals(2, frame.getListed().size());
        assertTrue(frame.text().contains("Yard: Train 1\n"), frame.text());
    }

    private static javax.swing.JCheckBox findCheckBox(java.awt.Container c) {
        for (java.awt.Component x : c.getComponents()) {
            if (x instanceof javax.swing.JCheckBox) {
                return (javax.swing.JCheckBox) x;
            }
            if (x instanceof java.awt.Container) {
                javax.swing.JCheckBox found = findCheckBox((java.awt.Container) x);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void testClearValues() {
        west.setValue("Stale");
        assertEquals(1, BlockTableAction.clearValues(true), "the unoccupied West only");
        assertNull(west.getValue());
        assertEquals("Train 1", yard.getValue(), "an occupied Block keeps its value");
        assertEquals(1, BlockTableAction.clearValues(false), "then every Block");
        assertNull(yard.getValue());
    }

    @BeforeEach
    public void setUp() throws JmriException {
        JUnitUtil.setUp();
        JUnitUtil.resetProfileManager();
        JUnitUtil.initInternalSensorManager();
        JUnitUtil.initRosterConfigManager();
        east = block("East", "IS1");
        west = block("West", "IS2");
        yard = block("Yard", "IS3");
        eastSensor = InstanceManager.getDefault(SensorManager.class).provideSensor("IS1");
        yardSensor = InstanceManager.getDefault(SensorManager.class).provideSensor("IS3");
        eastSensor.setState(Sensor.ACTIVE);
        InstanceManager.getDefault(SensorManager.class).provideSensor("IS2").setState(Sensor.INACTIVE);
        yardSensor.setState(Sensor.ACTIVE);
        yard.setValue("Train 1");
    }

    @AfterEach
    public void tearDown() {
        if (frame != null) {
            JUnitUtil.dispose(frame);
        }
        JUnitUtil.deregisterBlockManagerShutdownTask();
        JUnitUtil.tearDown();
    }
}
