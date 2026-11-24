package jmri.jmrix.can.cbus.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jmri.jmrix.can.CanSystemConnectionMemo;
import jmri.jmrix.can.cbus.CbusConfigurationManager;
import jmri.jmrix.can.cbus.CbusPreferences;
import jmri.jmrix.can.cbus.simulator.CbusDummyNode;
import jmri.jmrix.can.cbus.simulator.CbusSimCanListener;
import jmri.jmrix.can.cbus.simulator.CbusSimulatedModuleProvider;
import jmri.jmrix.can.cbus.simulator.moduletypes.MergCanmiosvo;
import jmri.jmrix.can.cbus.simulator.moduletypes.MergCanpan;
import jmri.util.JUnitUtil;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * The frames the Node Manager exchanges with a CBUS module, every one in
 * order, as it reads the module, writes its NVs, teaches it events and reads
 * them back.
 * <p>
 * Each module is a simulated one on a {@link CbusNodeTestBus}, answering as
 * the real module does. These are the exchanges as JMRI makes them today;
 * a change made for other kinds of node must leave every one of them as it
 * is.
 * <ul>
 * <li>CANPAN: NV writes are not acknowledged; events are taught with a WRACK
 * for each event variable.
 * <li>CANMIO-SVO: each NV write is made in learn mode and acknowledged with
 * WRACK.
 * </ul>
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class CbusNodeFramesTest {

    @Test
    public void testCanpan() {
        CbusNode node = moduleOnBus(new MergCanpan());
        assertEquals(PAN_FETCH, fetch(node), "parameters, events and NVs read");
        assertEquals(PAN_NVWRITE, writeNvs(node), "NV written, no acknowledgement");
        assertEquals(PAN_TEACH, teach(node, 13), "two events taught, a WRACK for each variable");
        assertEquals(PAN_READ, readEvents(node), "events read back");
    }

    @Test
    public void testCanmioSvo() {
        CbusNode node = moduleOnBus(new MergCanmiosvo());
        assertEquals(SVO_FETCH, fetch(node), "parameters, events and NVs read");
        assertEquals(SVO_NVWRITE, writeNvs(node), "each NV written in learn mode, WRACK for each");
        assertEquals(SVO_TEACH, teach(node, 4), "two events taught, a WRACK for each variable");
        assertEquals(SVO_READ, readEvents(node), "events read back");
    }

    private CbusNode moduleOnBus(CbusSimulatedModuleProvider type) {
        module = type.getNewDummyNode(memo, 1234);
        ((CbusSimCanListener) module.getCanListener()).setDelay(0);
        module.setNodeInFLiMMode(true);
        return nodeModel.provideNodeByNodeNum(1234);
    }

    private List<String> fetch(CbusNode node) {
        nodeModel.startUrgentFetch();
        nodeModel.triggerUrgentFetch();
        JUnitUtil.waitFor(() -> node.getNodeStats().totalRemainingNodeBytes() == 0, "node read");
        return framesWhenQuiet();
    }

    // change NV1, and NV2 if there is one
    private List<String> writeNvs(CbusNode node) {
        int[] nvs = node.getNodeNvManager().getNvArray().clone();
        nvs[1] = (nvs[1] + 1) % 256;
        if (nvs.length > 2) {
            nvs[2] = (nvs[2] + 7) % 256;
        }
        node.getNodeNvManager().sendNvsToNode(nvs);
        JUnitUtil.waitFor(() -> !node.getNodeNvManager().teachOutstandingNvs(), "NVs written");
        return framesWhenQuiet();
    }

    // events 0:100 and 0:101, with variables 0, 1, 2, 0 ... and 1, 2, 0, 1 ...
    private List<String> teach(CbusNode node, int evVars) {
        ArrayList<CbusNodeEvent> events = new ArrayList<>();
        for (int e = 0; e < 2; e++) {
            CbusNodeEvent ev = new CbusNodeEvent(memo, 0, 100 + e, 1234, -1, evVars);
            int[] values = new int[evVars];
            for (int i = 0; i < evVars; i++) {
                values[i] = (i + e) % 3;
            }
            ev.setEvArr(values);
            events.add(ev);
        }
        node.getNodeEventManager().sendNewEvSToNode(events);
        JUnitUtil.waitFor(() -> !node.getNodeEventManager().TEACH_OUTSTANDING_EVS, "events taught");
        return framesWhenQuiet();
    }

    private List<String> readEvents(CbusNode node) {
        node.getNodeEventManager().resetNodeEvents();
        return fetch(node);
    }

    // the frames so far, once nothing more has been sent for a while
    private List<String> framesWhenQuiet() {
        int[] last = {-1};
        JUnitUtil.waitFor(() -> {
            int now = bus.getFrames().size();
            boolean quiet = now == last[0];
            last[0] = now;
            if (!quiet) {
                JUnitUtil.waitFor(300);
            }
            return quiet;
        }, "bus quiet");
        List<String> frames = bus.getFrames();
        bus.clearFrames();
        return frames;
    }

    private static final List<String> PAN_FETCH = List.of(
        "TX 73 04 D2 00", "RX 9B 04 D2 00 14",
        "TX 73 04 D2 01", "RX 9B 04 D2 01 A5",
        "TX 73 04 D2 03", "RX 9B 04 D2 03 1D",
        "TX 73 04 D2 06", "RX 9B 04 D2 06 01",
        "TX 73 04 D2 05", "RX 9B 04 D2 05 0D",
        "TX 73 04 D2 07", "RX 9B 04 D2 07 01",
        "TX 73 04 D2 02", "RX 9B 04 D2 02 59",
        "TX 58 04 D2", "RX 74 04 D2 00",
        "TX 73 04 D2 04", "RX 9B 04 D2 04 80",
        "TX 73 04 D2 08", "RX 9B 04 D2 08 0D",
        "TX 73 04 D2 09", "RX 9B 04 D2 09 0D",
        "TX 73 04 D2 0A", "RX 9B 04 D2 0A 01",
        "TX 73 04 D2 0B", "RX 9B 04 D2 0B 00",
        "TX 73 04 D2 0C", "RX 9B 04 D2 0C 08",
        "TX 73 04 D2 0D", "RX 9B 04 D2 0D 00",
        "TX 73 04 D2 0E", "RX 9B 04 D2 0E 00",
        "TX 73 04 D2 0F", "RX 9B 04 D2 0F 00",
        "TX 73 04 D2 10", "RX 9B 04 D2 10 00",
        "TX 73 04 D2 11", "RX 9B 04 D2 11 00",
        "TX 73 04 D2 12", "RX 9B 04 D2 12 00",
        "TX 73 04 D2 13", "RX 9B 04 D2 13 01",
        "TX 73 04 D2 14", "RX 9B 04 D2 14 01",
        "TX 71 04 D2 01", "RX 97 04 D2 01 00");

    private static final List<String> PAN_NVWRITE = List.of(
        "TX 96 04 D2 01 01");

    private static final List<String> PAN_READ = List.of(
        "TX 58 04 D2", "RX 74 04 D2 02",
        "TX 57 04 D2", "RX F2 04 D2 00 00 00 64 02",
        "RX F2 04 D2 00 00 00 65 01", "TX 9C 04 D2 02 01",
        "RX B5 04 D2 02 01 00", "TX 9C 04 D2 02 02",
        "RX B5 04 D2 02 02 01", "TX 9C 04 D2 02 03",
        "RX B5 04 D2 02 03 02", "TX 9C 04 D2 02 04",
        "RX B5 04 D2 02 04 00", "TX 9C 04 D2 02 05",
        "RX B5 04 D2 02 05 01", "TX 9C 04 D2 02 06",
        "RX B5 04 D2 02 06 02", "TX 9C 04 D2 02 07",
        "RX B5 04 D2 02 07 00", "TX 9C 04 D2 02 08",
        "RX B5 04 D2 02 08 01", "TX 9C 04 D2 02 09",
        "RX B5 04 D2 02 09 02", "TX 9C 04 D2 02 0A",
        "RX B5 04 D2 02 0A 00", "TX 9C 04 D2 02 0B",
        "RX B5 04 D2 02 0B 01", "TX 9C 04 D2 02 0C",
        "RX B5 04 D2 02 0C 02", "TX 9C 04 D2 02 0D",
        "RX B5 04 D2 02 0D 00", "TX 9C 04 D2 01 01",
        "RX B5 04 D2 01 01 01", "TX 9C 04 D2 01 02",
        "RX B5 04 D2 01 02 02", "TX 9C 04 D2 01 03",
        "RX B5 04 D2 01 03 00", "TX 9C 04 D2 01 04",
        "RX B5 04 D2 01 04 01", "TX 9C 04 D2 01 05",
        "RX B5 04 D2 01 05 02", "TX 9C 04 D2 01 06",
        "RX B5 04 D2 01 06 00", "TX 9C 04 D2 01 07",
        "RX B5 04 D2 01 07 01", "TX 9C 04 D2 01 08",
        "RX B5 04 D2 01 08 02", "TX 9C 04 D2 01 09",
        "RX B5 04 D2 01 09 00", "TX 9C 04 D2 01 0A",
        "RX B5 04 D2 01 0A 01", "TX 9C 04 D2 01 0B",
        "RX B5 04 D2 01 0B 02", "TX 9C 04 D2 01 0C",
        "RX B5 04 D2 01 0C 00", "TX 9C 04 D2 01 0D",
        "RX B5 04 D2 01 0D 01");

    private static final List<String> PAN_TEACH = List.of(
        "TX 53 04 D2", "TX D2 00 00 00 64 01 00",
        "RX 59 04 D2", "TX D2 00 00 00 64 02 01",
        "RX 59 04 D2", "TX D2 00 00 00 64 03 02",
        "RX 59 04 D2", "TX D2 00 00 00 64 04 00",
        "RX 59 04 D2", "TX D2 00 00 00 64 05 01",
        "RX 59 04 D2", "TX D2 00 00 00 64 06 02",
        "RX 59 04 D2", "TX D2 00 00 00 64 07 00",
        "RX 59 04 D2", "TX D2 00 00 00 64 08 01",
        "RX 59 04 D2", "TX D2 00 00 00 64 09 02",
        "RX 59 04 D2", "TX D2 00 00 00 64 0A 00",
        "RX 59 04 D2", "TX D2 00 00 00 64 0B 01",
        "RX 59 04 D2", "TX D2 00 00 00 64 0C 02",
        "RX 59 04 D2", "TX D2 00 00 00 64 0D 00",
        "RX 59 04 D2", "TX D2 00 00 00 65 01 01",
        "RX 59 04 D2", "TX D2 00 00 00 65 02 02",
        "RX 59 04 D2", "TX D2 00 00 00 65 03 00",
        "RX 59 04 D2", "TX D2 00 00 00 65 04 01",
        "RX 59 04 D2", "TX D2 00 00 00 65 05 02",
        "RX 59 04 D2", "TX D2 00 00 00 65 06 00",
        "RX 59 04 D2", "TX D2 00 00 00 65 07 01",
        "RX 59 04 D2", "TX D2 00 00 00 65 08 02",
        "RX 59 04 D2", "TX D2 00 00 00 65 09 00",
        "RX 59 04 D2", "TX D2 00 00 00 65 0A 01",
        "RX 59 04 D2", "TX D2 00 00 00 65 0B 02",
        "RX 59 04 D2", "TX D2 00 00 00 65 0C 00",
        "RX 59 04 D2", "TX D2 00 00 00 65 0D 01",
        "RX 59 04 D2", "TX 54 04 D2");

    private static final List<String> SVO_FETCH = List.of(
        "TX 73 04 D2 00", "RX 9B 04 D2 00 14",
        "TX 73 04 D2 01", "RX 9B 04 D2 01 A5",
        "TX 73 04 D2 03", "RX 9B 04 D2 03 32",
        "TX 73 04 D2 06", "RX 9B 04 D2 06 25",
        "TX 73 04 D2 05", "RX 9B 04 D2 05 04",
        "TX 73 04 D2 07", "RX 9B 04 D2 07 01",
        "TX 73 04 D2 02", "RX 9B 04 D2 02 01",
        "TX 58 04 D2", "RX 74 04 D2 00",
        "TX 73 04 D2 04", "RX 9B 04 D2 04 80",
        "TX 73 04 D2 08", "RX 9B 04 D2 08 0D",
        "TX 73 04 D2 09", "RX 9B 04 D2 09 0D",
        "TX 73 04 D2 0A", "RX 9B 04 D2 0A 01",
        "TX 73 04 D2 0B", "RX 9B 04 D2 0B 00",
        "TX 73 04 D2 0C", "RX 9B 04 D2 0C 08",
        "TX 73 04 D2 0D", "RX 9B 04 D2 0D 00",
        "TX 73 04 D2 0E", "RX 9B 04 D2 0E 00",
        "TX 73 04 D2 0F", "RX 9B 04 D2 0F 00",
        "TX 73 04 D2 10", "RX 9B 04 D2 10 00",
        "TX 73 04 D2 11", "RX 9B 04 D2 11 00",
        "TX 73 04 D2 12", "RX 9B 04 D2 12 00",
        "TX 73 04 D2 13", "RX 9B 04 D2 13 01",
        "TX 73 04 D2 14", "RX 9B 04 D2 14 01",
        "TX 71 04 D2 01", "RX 97 04 D2 01 FF",
        "TX 71 04 D2 02", "RX 97 04 D2 02 00",
        "TX 71 04 D2 03", "RX 97 04 D2 03 FF",
        "TX 71 04 D2 04", "RX 97 04 D2 04 FF",
        "TX 71 04 D2 05", "RX 97 04 D2 05 FF",
        "TX 71 04 D2 06", "RX 97 04 D2 06 FF",
        "TX 71 04 D2 07", "RX 97 04 D2 07 00",
        "TX 71 04 D2 08", "RX 97 04 D2 08 00",
        "TX 71 04 D2 09", "RX 97 04 D2 09 FF",
        "TX 71 04 D2 0A", "RX 97 04 D2 0A FF",
        "TX 71 04 D2 0B", "RX 97 04 D2 0B 00",
        "TX 71 04 D2 0C", "RX 97 04 D2 0C 00",
        "TX 71 04 D2 0D", "RX 97 04 D2 0D FF",
        "TX 71 04 D2 0E", "RX 97 04 D2 0E FF",
        "TX 71 04 D2 0F", "RX 97 04 D2 0F 00",
        "TX 71 04 D2 10", "RX 97 04 D2 10 00",
        "TX 71 04 D2 11", "RX 97 04 D2 11 FF",
        "TX 71 04 D2 12", "RX 97 04 D2 12 FF",
        "TX 71 04 D2 13", "RX 97 04 D2 13 00",
        "TX 71 04 D2 14", "RX 97 04 D2 14 00",
        "TX 71 04 D2 15", "RX 97 04 D2 15 FF",
        "TX 71 04 D2 16", "RX 97 04 D2 16 FF",
        "TX 71 04 D2 17", "RX 97 04 D2 17 00",
        "TX 71 04 D2 18", "RX 97 04 D2 18 00",
        "TX 71 04 D2 19", "RX 97 04 D2 19 FF",
        "TX 71 04 D2 1A", "RX 97 04 D2 1A FF",
        "TX 71 04 D2 1B", "RX 97 04 D2 1B 00",
        "TX 71 04 D2 1C", "RX 97 04 D2 1C 00",
        "TX 71 04 D2 1D", "RX 97 04 D2 1D FF",
        "TX 71 04 D2 1E", "RX 97 04 D2 1E FF",
        "TX 71 04 D2 1F", "RX 97 04 D2 1F 00",
        "TX 71 04 D2 20", "RX 97 04 D2 20 00",
        "TX 71 04 D2 21", "RX 97 04 D2 21 FF",
        "TX 71 04 D2 22", "RX 97 04 D2 22 FF",
        "TX 71 04 D2 23", "RX 97 04 D2 23 00",
        "TX 71 04 D2 24", "RX 97 04 D2 24 00",
        "TX 71 04 D2 25", "RX 97 04 D2 25 00");

    private static final List<String> SVO_NVWRITE = List.of(
        "TX 53 04 D2", "TX 96 04 D2 01 00",
        "TX 54 04 D2", "RX 59 04 D2",
        "TX 53 04 D2", "TX 96 04 D2 02 07",
        "TX 54 04 D2", "RX 59 04 D2");

    private static final List<String> SVO_READ = List.of(
        "TX 58 04 D2", "RX 74 04 D2 02",
        "TX 57 04 D2", "RX F2 04 D2 00 00 00 64 02",
        "RX F2 04 D2 00 00 00 65 01", "TX 9C 04 D2 02 01",
        "RX B5 04 D2 02 01 00", "TX 9C 04 D2 02 02",
        "RX B5 04 D2 02 02 01", "TX 9C 04 D2 02 03",
        "RX B5 04 D2 02 03 02", "TX 9C 04 D2 02 04",
        "RX B5 04 D2 02 04 00", "TX 9C 04 D2 01 01",
        "RX B5 04 D2 01 01 01", "TX 9C 04 D2 01 02",
        "RX B5 04 D2 01 02 02", "TX 9C 04 D2 01 03",
        "RX B5 04 D2 01 03 00", "TX 9C 04 D2 01 04",
        "RX B5 04 D2 01 04 01");

    private static final List<String> SVO_TEACH = List.of(
        "TX 53 04 D2", "TX D2 00 00 00 64 01 00",
        "RX 59 04 D2", "TX D2 00 00 00 64 02 01",
        "RX 59 04 D2", "TX D2 00 00 00 64 03 02",
        "RX 59 04 D2", "TX D2 00 00 00 64 04 00",
        "RX 59 04 D2", "TX D2 00 00 00 65 01 01",
        "RX 59 04 D2", "TX D2 00 00 00 65 02 02",
        "RX 59 04 D2", "TX D2 00 00 00 65 03 00",
        "RX 59 04 D2", "TX D2 00 00 00 65 04 01",
        "RX 59 04 D2", "TX 54 04 D2");

    private CanSystemConnectionMemo memo;
    private CbusNodeTestBus bus;
    private CbusNodeTableDataModel nodeModel;
    private CbusDummyNode module;

    @BeforeEach
    public void setUp(@TempDir File tempDir) throws java.io.IOException {
        JUnitUtil.setUp();
        JUnitUtil.resetProfileManager(new jmri.profile.NullProfile(tempDir));
        memo = new CanSystemConnectionMemo();
        bus = new CbusNodeTestBus();
        memo.setTrafficController(bus);
        memo.setProtocol(jmri.jmrix.can.CanConfigurationManager.MERGCBUS);
        // read in the background as by default, so that a read runs to the end
        memo.get(CbusPreferences.class).setNodeBackgroundFetchDelay(100);
        nodeModel = memo.get(CbusConfigurationManager.class).provide(CbusNodeTableDataModel.class);
    }

    @AfterEach
    public void tearDown() {
        if (module != null) {
            module.dispose();
        }
        nodeModel.dispose();
        bus.terminateThreads();
        memo.dispose();
        JUnitUtil.tearDown();
    }

}
