package jmri.jmrix.can.cbus.node;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import jmri.jmrix.can.CanListener;
import jmri.jmrix.can.CanMessage;
import jmri.jmrix.can.CanReply;
import jmri.jmrix.can.TrafficControllerScaffold;
import jmri.util.ThreadingUtil;

/**
 * A CAN bus for Node Manager tests, joining JMRI's nodes to simulated modules
 * as a real connection does.
 * <p>
 * A frame JMRI sends is passed to every listener's {@code message()}, so the
 * simulated module hears it and JMRI's node sees its own frame; a frame a
 * simulated module sends is passed to every listener's {@code reply()}. Both
 * are passed on the layout thread (Swing thread), one at a time in the order
 * sent, as the traffic controller passes them.
 * <p>
 * Every frame is kept, in order, as "TX" (sent by JMRI) or "RX" (received
 * from a module) followed by its opcode and data in hex, so that a test can
 * compare the whole exchange.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class CbusNodeTestBus extends TrafficControllerScaffold {

    private final List<String> frames = new ArrayList<>();

    /** {@inheritDoc} */
    @Override
    public void sendCanMessage(CanMessage m, CanListener l) {
        super.sendCanMessage(m, l);
        CanMessage copy = new CanMessage(m);
        record("TX", copy.toString());
        ThreadingUtil.runOnLayoutEventually(() -> sendToListeners(copy));
    }

    /** {@inheritDoc} */
    @Override
    public void sendCanReply(CanReply r, CanListener l) {
        super.sendCanReply(r, l);
        CanReply copy = new CanReply(r);
        record("RX", copy.toString());
        ThreadingUtil.runOnLayoutEventually(() -> sendToListeners(copy));
    }

    private void record(String direction, String frame) {
        // "[5f8] 97 04 D2 00 01": leave out the CAN ID, which is the sender's
        int start = frame.indexOf(']');
        synchronized (frames) {
            frames.add(direction + " " + frame.substring(start + 1).trim());
        }
    }

    /**
     * The frames so far, in order.
     *
     * @return a copy
     */
    @Nonnull
    public List<String> getFrames() {
        synchronized (frames) {
            return new ArrayList<>(frames);
        }
    }

    /**
     * Forget the frames so far.
     */
    public void clearFrames() {
        synchronized (frames) {
            frames.clear();
        }
    }

}
