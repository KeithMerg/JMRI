package jmri.jmrit.beantable.block;

import static org.junit.jupiter.api.Assertions.*;

import jmri.BeanSetting;
import jmri.Block;
import jmri.BlockManager;
import jmri.InstanceManager;
import jmri.Path;
import jmri.Section;
import jmri.SectionManager;
import jmri.Sensor;
import jmri.SensorManager;
import jmri.Transit;
import jmri.TransitManager;
import jmri.TransitSection;
import jmri.Turnout;
import jmri.TurnoutManager;
import jmri.util.JUnitUtil;
import jmri.util.ThreadingUtil;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Tests for BlockDetailsFrame.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class BlockDetailsFrameTest {

    private Block points;

    @Test
    public void testDetails() {
        String text = BlockDetailsFrame.details(points);
        assertTrue(text.startsWith("Block: Points (IBPoints)\nState: Occupied\nSensor: Points sensor (Active)\n"
                + "Value: Goods\nLength: 250 mm\nGhost: Yes\n"), text);
        assertTrue(text.contains("Paths: 1\n  To Main\n    Points turnout Thrown\n"), text);
        assertTrue(text.contains("Sections:\n  Throat\n"), text);
        assertTrue(text.contains("Transits:\n  Round, Section 1: Throat\n"), text);
        assertTrue(text.contains("Points (IBPoints)"), "with where it is used: " + text);
    }

    @Test
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
    public void testWindow() {
        BlockDetailsFrame[] f = new BlockDetailsFrame[1];
        ThreadingUtil.runOnGUI(() -> {
            f[0] = new BlockDetailsFrame();
            f[0].initComponents();
            f[0].show(points);
        });
        assertEquals(BlockDetailsFrame.details(points), f[0].getText());
        JUnitUtil.dispose(f[0]);
    }

    @BeforeEach
    public void setUp() throws Exception {
        JUnitUtil.setUp();
        JUnitUtil.initInternalSensorManager();
        JUnitUtil.initInternalTurnoutManager();
        JUnitUtil.initSectionManager();
        BlockManager bm = InstanceManager.getDefault(BlockManager.class);
        points = bm.createNewBlock("IBPoints", "Points");
        Block main = bm.createNewBlock("IBMain", "Main");
        Sensor s = InstanceManager.getDefault(SensorManager.class).provideSensor("IS1");
        s.setUserName("Points sensor");
        points.setSensor("IS1");
        s.setState(Sensor.ACTIVE);
        points.setValue("Goods");
        points.setLength(250);
        points.setIsGhost(true);
        Turnout t = InstanceManager.getDefault(TurnoutManager.class).provideTurnout("IT1");
        t.setUserName("Points turnout");
        Path p = new Path(main, Path.NONE, Path.NONE);
        p.addSetting(new BeanSetting(t, "IT1", Turnout.THROWN));
        points.addPath(p);
        Section sec = InstanceManager.getDefault(SectionManager.class).createNewSection("Throat");
        sec.addBlock(points);
        Transit tr = InstanceManager.getDefault(TransitManager.class).createNewTransit("Round");
        tr.addTransitSection(new TransitSection(sec, 1, Section.FORWARD));
    }

    @AfterEach
    public void tearDown() {
        JUnitUtil.deregisterBlockManagerShutdownTask();
        JUnitUtil.tearDown();
    }
}
