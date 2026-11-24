package jmri.jmrit.display.controlPanelEditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;

import jmri.InstanceManager;
import jmri.NamedBeanHandle;
import jmri.NamedBeanHandleManager;
import jmri.Sensor;
import jmri.SensorManager;
import jmri.jmrit.display.IndicatorTrackIcon;
import jmri.jmrit.display.LocoIcon;
import jmri.jmrit.display.Positionable;
import jmri.jmrit.logix.OBlock;
import jmri.jmrit.logix.OBlockManager;
import jmri.jmrit.logix.OPath;
import jmri.jmrit.logix.Portal;
import jmri.jmrit.logix.PortalManager;
import jmri.jmrit.logix.TrackerTableAction;
import jmri.jmrit.simulation.SimulationTestUtil;
import jmri.jmrit.simulation.VirtualTrain;
import jmri.util.JUnitUtil;
import jmri.util.ThreadingUtil;
import jmri.util.junit.annotations.DisabledIfHeadless;
import jmri.util.swing.JmriMouseEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netbeans.jemmy.operators.JButtonOperator;
import org.netbeans.jemmy.operators.JCheckBoxOperator;
import org.netbeans.jemmy.operators.JDialogOperator;

/**
 * A loco marker dropped on an OBlock's track on a Control Panel Editor panel,
 * in a profile whose connections are all simulators, places a simulated train
 * there, and starts a Tracker for it too if the box is left ticked.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
@DisabledIfHeadless
public class ControlPanelEditorSimulatedTrainTest {

    private static final String TITLE = "Place Simulated Train";

    private ControlPanelEditor cpe;
    private OBlock[] blocks;
    private IndicatorTrackIcon track;

    /*
     * Three OBlocks in a line, joined by Portals: A - B - C, each 1000 mm,
     * each with a sensor, and an OPath through each.
     */
    private void layout() {
        OBlockManager obm = InstanceManager.getDefault(OBlockManager.class);
        PortalManager pm = InstanceManager.getDefault(PortalManager.class);
        blocks = new OBlock[3];
        String[] names = {"A", "B", "C"};
        for (int i = 0; i < 3; i++) {
            blocks[i] = Objects.requireNonNull(obm.createNewOBlock("OB" + (i + 1), names[i]), "OBlock created");
            blocks[i].setLength(1000);
            Sensor s = InstanceManager.getDefault(SensorManager.class).provideSensor("IS" + (i + 1));
            ThreadingUtil.runOnLayout(() -> s.setCommandedState(Sensor.INACTIVE));
            blocks[i].setSensor("IS" + (i + 1));
        }
        Portal ab = pm.createNewPortal("A-B");
        ab.setFromBlock(blocks[0], false);
        ab.setToBlock(blocks[1], false);
        Portal bc = pm.createNewPortal("B-C");
        bc.setFromBlock(blocks[1], false);
        bc.setToBlock(blocks[2], false);
        blocks[0].addPath(new OPath("a", blocks[0], null, ab, null));
        blocks[1].addPath(new OPath("b", blocks[1], ab, bc, null));
        blocks[2].addPath(new OPath("c", blocks[2], bc, null, null));
    }

    private LocoIcon marker(String text) {
        LocoIcon m = new LocoIcon(cpe);
        m.setText(text);
        return m;
    }

    /** Answer the placing dialog on another thread, as it is modal. */
    private Thread answer(boolean alsoTracker, String button) {
        Thread t = new Thread(() -> {
            JDialogOperator dialog = new JDialogOperator(TITLE);
            if (!alsoTracker) {
                new JCheckBoxOperator(dialog, "Also start a Tracker for it here").changeSelection(false);
            }
            new JButtonOperator(dialog, button).doClick();
        }, "answer placing dialog");
        t.start();
        return t;
    }

    /** Drop a marker on B's track. */
    private void drop(LocoIcon m, Thread answering) {
        JmriMouseEvent release = new JmriMouseEvent(cpe.getTargetPanel(), MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(), 0, track.getX() + 5, track.getY() + 5, 1, false);
        ThreadingUtil.runOnGUI(() -> m.doMouseReleased(release));
        JUnitUtil.waitFor(() -> !answering.isAlive(), "dialog answered");
    }

    @Test
    public void testDropPlacesATrainAndStartsATracker() {
        drop(marker("5 Goods"), answer(true, "OK"));
        VirtualTrain t = Objects.requireNonNull(jmri.jmrit.simulation.SimulatedLayout.getDefault().getTrains()
                .stream().filter(x -> x.getName().equals("5 Goods")).findFirst().orElse(null), "placed");
        assertEquals(List.of(blocks[1]), SimulationTestUtil.truth(t).getBlocks());
        JUnitUtil.waitFor(() -> blocks[1].isOccupied(), "B's sensor sees the train");
        JUnitUtil.waitFor(() -> InstanceManager.getDefault(TrackerTableAction.class).findTrackerIn(blocks[1]) != null,
                "a Tracker started in B");
    }

    @Test
    public void testUntickedPlacesTheTrainOnly() {
        drop(marker("5 Goods"), answer(false, "OK"));
        JUnitUtil.waitFor(() -> blocks[1].isOccupied(), "B's sensor sees the train");
        assertNull(InstanceManager.getDefault(TrackerTableAction.class).findTrackerIn(blocks[1]), "no Tracker");
    }

    @Test
    public void testCancelDoesNothing() {
        drop(marker("5 Goods"), answer(true, "Cancel"));
        assertEquals(0, jmri.jmrit.simulation.SimulatedLayout.getDefault().getTrains().size());
        assertNull(InstanceManager.getDefault(TrackerTableAction.class).findTrackerIn(blocks[1]));
        assertEquals(Sensor.INACTIVE, Objects.requireNonNull(blocks[1].getSensor()).getKnownState());
    }

    @BeforeEach
    public void setUp() throws Positionable.DuplicateIdException {
        JUnitUtil.setUp();
        JUnitUtil.resetProfileManager();
        SimulationTestUtil.setUpSimulatedProfile();
        layout();
        cpe = ThreadingUtil.runOnGUIwithReturn(() -> new ControlPanelEditor("Simulated trains on OBlocks"));
        ThreadingUtil.runOnGUI(() -> {
            track = new IndicatorTrackIcon(cpe);
            // an image for each state, so the icon has a size to drop on
            for (String state : new String[]{"ClearTrack", "OccupiedTrack", "AllocatedTrack", "PositionTrack",
                    "DontUseTrack", "ErrorTrack"}) {
                track.setIcon(state, new jmri.jmrit.catalog.NamedIcon(
                        "resources/icons/smallschematics/tracksegments/Diamond_Left_Closed.gif",
                        "resources/icons/smallschematics/tracksegments/Diamond_Left_Closed.gif"));
            }
            NamedBeanHandle<OBlock> handle = InstanceManager.getDefault(NamedBeanHandleManager.class)
                    .getNamedBeanHandle("B", blocks[1]);
            track.setOccBlockHandle(handle);
            track.setLocation(100, 100);
            track.setDisplayLevel(jmri.jmrit.display.Editor.TURNOUTS);  // as track icons are, above the background
            try {
                cpe.putItem(track);
            } catch (Positionable.DuplicateIdException e) {
                throw new IllegalStateException(e);
            }
        });
        assertNotNull(track.getOccBlock());
        ThreadingUtil.runOnGUI(() -> track.displayState("ClearTrack"));
        assertEquals(true, track.getWidth() > 0 && track.getHeight() > 0, "the track icon has a size to drop on");
    }

    @AfterEach
    public void tearDown() {
        SimulationTestUtil.removeTrains();
        if (cpe != null) {
            JUnitUtil.dispose(cpe);
        }
        JUnitUtil.deregisterBlockManagerShutdownTask();
        JUnitUtil.tearDown();
    }
}
