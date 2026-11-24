package jmri.jmrix.can.cbus.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jmri.jmrix.can.CanMessage;
import jmri.jmrix.can.CanSystemConnectionMemo;
import jmri.jmrix.can.cbus.node.CbusNodeEvent;
import jmri.jmrix.can.cbus.node.CbusNodeTestBus;
import jmri.jmrix.can.cbus.simulator.CbusDummyVlcbNode.ReplyStyle;
import jmri.jmrix.can.cbus.simulator.moduletypes.MergVlcb;
import jmri.util.JUnitUtil;

import org.junit.jupiter.api.*;

/**
 * The replies of a simulated VLCB node, node 1234 (04 D2), to the frames a
 * configuration tool sends it.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class CbusDummyVlcbNodeTest {

    private static final String NVSET_1 = "96 04 D2 01 05";
    private static final String NVSET_11 = "96 04 D2 0B 05";
    private static final String EVLRN_3 = "D2 00 00 00 64 03 2A";
    private static final String EVLRN_9 = "D2 00 00 00 64 09 2A";

    @Test
    public void testIsVlcb() {
        assertEquals("MERG VLCB", new MergVlcb().getModuleType(), "module type");
        assertTrue((node.getNodeParamManager().getParameter(8) & 0x40) != 0, "VLCB flag set");
        assertEquals(ReplyStyle.PIC_LIBRARY, node.getReplyStyle(), "copies VLCBlib_PIC by default");
    }

    @Test
    public void testNvsetPic() {
        assertEquals(List.of("RX 59 04 D2", "RX AF 04 D2 96 01 00"), send(NVSET_1), "WRACK then GRSP");
        assertEquals(5, node.getNodeNvManager().getNV(1), "NV1 set");
        assertEquals(List.of("RX 6F 04 D2 0A"), send(NVSET_11), "CMDERR only");
    }

    @Test
    public void testNvsetArduino() {
        node.setReplyStyle(ReplyStyle.ARDUINO_LIBRARY);
        assertEquals(List.of("RX 59 04 D2"), send(NVSET_1), "WRACK only");
        assertEquals(5, node.getNodeNvManager().getNV(1), "NV1 set");
        assertEquals(List.of("RX AF 04 D2 96 02 0A", "RX 6F 04 D2 0A"), send(NVSET_11), "GRSP then CMDERR");
    }

    @Test
    public void testNvsetGrspOnly() {
        node.setReplyStyle(ReplyStyle.GRSP_ONLY);
        assertEquals(List.of("RX AF 04 D2 96 02 00"), send(NVSET_1), "GRSP OK");
        assertEquals(List.of("RX AF 04 D2 96 02 0A"), send(NVSET_11), "GRSP error");
    }

    @Test
    public void testNvsetRejected() {
        node.setFailWritesWith(12);
        assertEquals(List.of("RX 6F 04 D2 0C"), send(NVSET_1), "CMDERR");
        assertEquals(0, node.getNodeNvManager().getNV(1), "NV1 not set");
        node.setFailWritesWith(0);
        assertEquals(List.of("RX 59 04 D2", "RX AF 04 D2 96 01 00"), send(NVSET_1), "accepted again");
    }

    @Test
    public void testNextWriteRejected() {
        node.setFailNextWrites(12, 1);
        assertEquals(List.of("RX 6F 04 D2 0C"), send(NVSET_1), "first fails");
        assertEquals(List.of("RX 59 04 D2", "RX AF 04 D2 96 01 00"), send(NVSET_1), "second accepted");
    }

    @Test
    public void testNvsetForAnotherNode() {
        assertEquals(List.of(), send("96 04 D3 01 05"), "no reply");
        assertEquals(0, node.getNodeNvManager().getNV(1), "NV1 not set");
    }

    @Test
    public void testEvlrn() {
        assertEquals(List.of(), send(EVLRN_3), "ignored out of learn mode");
        send("53 04 D2"); // NNLRN
        assertEquals(List.of("RX 59 04 D2", "RX AF 04 D2 D2 04 00"), send(EVLRN_3), "WRACK then GRSP");
        assertEquals(42, taught().getEvVar(3), "EV3 taught");
        assertEquals(List.of("RX 6F 04 D2 06", "RX AF 04 D2 D2 04 06"), send(EVLRN_9), "CMDERR then GRSP");

        node.setReplyStyle(ReplyStyle.ARDUINO_LIBRARY);
        assertEquals(List.of("RX 59 04 D2", "RX AF 04 D2 D2 04 00"), send(EVLRN_3), "Arduino: as PIC");

        node.setReplyStyle(ReplyStyle.GRSP_ONLY);
        assertEquals(List.of("RX AF 04 D2 D2 04 00"), send(EVLRN_3), "GRSP OK");
        assertEquals(List.of("RX AF 04 D2 D2 04 06"), send(EVLRN_9), "GRSP error");
    }

    @Test
    public void testEvlrnRejected() {
        send("53 04 D2"); // NNLRN
        node.setFailWritesWith(11);
        assertEquals(List.of("RX 6F 04 D2 0B", "RX AF 04 D2 D2 04 0B"), send(EVLRN_3), "CMDERR then GRSP");
        assertNull(node.getNodeEventManager().getNodeEvent(0, 100), "nothing taught");
    }

    @Test
    public void testReqev() {
        send("53 04 D2"); // NNLRN
        send(EVLRN_3);
        assertEquals(List.of("RX D3 00 00 00 64 03 2A"), send("B2 00 00 00 64 03"), "EV3");
        assertEquals(List.of(
            "RX D3 00 00 00 64 00 08",
            "RX D3 00 00 00 64 01 00",
            "RX D3 00 00 00 64 02 00",
            "RX D3 00 00 00 64 03 2A",
            "RX D3 00 00 00 64 04 00",
            "RX D3 00 00 00 64 05 00",
            "RX D3 00 00 00 64 06 00",
            "RX D3 00 00 00 64 07 00",
            "RX D3 00 00 00 64 08 00"), send("B2 00 00 00 64 00"), "EV0: the count, then every EV");
        node.setFcuCompatible(true);
        assertEquals(List.of("RX D3 00 00 00 64 00 08"), send("B2 00 00 00 64 00"), "FCU mode: the count only");
        assertEquals(List.of("RX 6F 04 D2 06", "RX AF 04 D2 B2 04 06"), send("B2 00 00 00 64 09"),
            "EV out of range");
        assertEquals(List.of("RX 6F 04 D2 07", "RX AF 04 D2 B2 04 07"), send("B2 00 00 00 65 01"),
            "no such event");
        send("54 04 D2"); // NNULN
        assertEquals(List.of(), send("B2 00 00 00 64 03"), "ignored out of learn mode");
    }

    @Test
    public void testNvrdAll() {
        send(NVSET_1);
        List<String> frames = send("71 04 D2 00");
        assertEquals(11, frames.size(), "the count, then each of 10");
        assertEquals("RX 97 04 D2 00 0A", frames.get(0), "NVANS with the count");
        assertEquals("RX 97 04 D2 01 05", frames.get(1), "NV1");
        node.setFcuCompatible(true);
        assertEquals(List.of("RX 97 04 D2 00 0A"), send("71 04 D2 00"), "FCU mode: the count only");
        assertEquals(List.of("RX 97 04 D2 01 05"), send("71 04 D2 01"), "one NV, as for CBUS");
    }

    @Test
    public void testReval() {
        send("53 04 D2"); // NNLRN
        send(EVLRN_3);
        send("54 04 D2"); // NNULN
        int index = taught().getIndex();
        String reval = String.format("9C 04 D2 %02X 03", index);
        assertEquals(List.of(String.format("RX B5 04 D2 %02X 03 2A", index)), send(reval), "NEVAL, as the libraries do");
        node.setRefusesReval(true);
        assertEquals(List.of("RX 6F 04 D2 01", "RX AF 04 D2 9C 04 01"), send(reval), "refused, as the specification says");
    }

    // event 0:100
    private CbusNodeEvent taught() {
        CbusNodeEvent ev = node.getNodeEventManager().getNodeEvent(0, 100);
        assertNotNull(ev, "event 0:100 taught");
        return ev;
    }

    // send a frame from the configuration tool, and return the node's replies
    private List<String> send(String hex) {
        String[] bytes = hex.split(" ");
        int[] data = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = Integer.parseInt(bytes[i], 16);
        }
        bus.clearFrames();
        bus.sendCanMessage(new CanMessage(data, 0x12), null);
        // replies come in order within a few ms; wait for the bus to go quiet
        int[] last = {-1};
        JUnitUtil.waitFor(() -> {
            JUnitUtil.waitFor(100);
            int now = bus.getFrames().size();
            boolean quiet = now == last[0];
            last[0] = now;
            return quiet;
        }, "bus quiet");
        List<String> frames = bus.getFrames();
        frames.remove(0); // the frame sent
        return frames;
    }

    private CanSystemConnectionMemo memo;
    private CbusNodeTestBus bus;
    private CbusDummyVlcbNode node;

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
        memo = new CanSystemConnectionMemo();
        bus = new CbusNodeTestBus();
        memo.setTrafficController(bus);
        memo.setProtocol(jmri.jmrix.can.CanConfigurationManager.MERGCBUS);
        node = (CbusDummyVlcbNode) new MergVlcb().getNewDummyNode(memo, 1234);
        ((CbusSimCanListener) node.getCanListener()).setDelay(0);
        node.setNodeInFLiMMode(true);
    }

    @AfterEach
    public void tearDown() {
        node.dispose();
        bus.terminateThreads();
        memo.dispose();
        JUnitUtil.tearDown();
    }

}
