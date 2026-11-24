package jmri.jmrix.can.cbus.simulator;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jmri.jmrix.can.CanReply;
import jmri.jmrix.can.CanSystemConnectionMemo;
import jmri.jmrix.can.cbus.CbusConstants;
import jmri.jmrix.can.cbus.node.CbusNodeEvent;

/**
 * Simulating a VLCB Node.
 * <p>
 * A VLCB node answers a write with a generic response, GRSP, which names the
 * request it answers and gives a result, 0 for success.
 * The VLCB libraries also send the CBUS replies, WRACK and CMDERR, so that
 * CBUS configuration tools still work, and they differ in which they send.
 * {@link ReplyStyle} picks which library to copy.
 * <p>
 * In learn mode the node also answers REQEV with EVANS.
 * For event variable 0 it answers with the number of event variables,
 * then, unless in FCU compatibility mode, with each event variable in turn.
 * It answers NVRD for NV 0 in the same way, with NVANS.
 *
 * @author Keith Bruce Copyright (C) 2026
 * @see <a href="https://github.com/SvenRosvall/VLCB-Arduino">VLCB-Arduino</a>
 * @see <a href="https://github.com/spikyian/VLCBlib_PIC">VLCBlib_PIC</a>
 */
public class CbusDummyVlcbNode extends CbusDummyNode {

    /**
     * Which replies the node sends to NVSET and EVLRN.
     */
    public enum ReplyStyle {
        /**
         * As VLCBlib_PIC: WRACK then GRSP on success. A failed NVSET sends
         * CMDERR only; a failed EVLRN sends CMDERR then GRSP.
         * The GRSP for NVSET gives the minimum node service type, 1.
         */
        PIC_LIBRARY,
        /**
         * As VLCB-Arduino: a successful NVSET sends WRACK only, a failed one
         * GRSP then CMDERR. EVLRN sends WRACK then GRSP on success, and
         * CMDERR then GRSP on failure.
         */
        ARDUINO_LIBRARY,
        /**
         * As the VLCB specification intends once CBUS tools no longer need
         * them: GRSP only, no WRACK or CMDERR.
         */
        GRSP_ONLY
    }

    /** GRSP result for success. */
    public static final int GRSP_OK = 0;
    /** GRSP and CMDERR code for a command the node does not support. */
    public static final int ERR_INVALID_COMMAND = 1;
    /** GRSP and CMDERR code for an event variable index out of range. */
    public static final int ERR_INVALID_EV_INDEX = 6;
    /** GRSP and CMDERR code for an event the node does not hold. */
    public static final int ERR_INVALID_EVENT = 7;
    /** GRSP and CMDERR code for a node variable index out of range. */
    public static final int ERR_INVALID_NV_INDEX = 10;

    /** GRSP service type of the minimum node service. */
    public static final int SERVICE_MNS = 1;
    /** GRSP service type of the node variable service. */
    public static final int SERVICE_NV = 2;
    /** GRSP service type of the event teach service. */
    public static final int SERVICE_TEACH = 4;

    // the order of two frames sent together
    private static final int SECOND_FRAME = 2;
    // the gap between event variables sent for EV#0
    private static final int STREAM_GAP = 5;

    private volatile ReplyStyle replyStyle = ReplyStyle.PIC_LIBRARY;
    private volatile boolean fcuCompatible = false;
    private volatile boolean refusesReval = false;
    private int failWritesWith = 0;
    private int failWritesLeft = -1;

    /**
     * Create a simulated VLCB node.
     *
     * @param sysmemo the connection, may be null
     * @param nodenumber the node number
     */
    public CbusDummyVlcbNode( @CheckForNull CanSystemConnectionMemo sysmemo, int nodenumber ){
        super( sysmemo, nodenumber );
    }

    @Override
    protected CbusDummyNodeCanListener createDummyCanListener(){
        return new CbusDummyVlcbNodeCanListener(_memo,this);
    }

    /**
     * Set which replies the node sends to NVSET and EVLRN.
     *
     * @param style the library to copy
     */
    public void setReplyStyle(@Nonnull ReplyStyle style) {
        replyStyle = style;
    }

    /**
     * Get which replies the node sends to NVSET and EVLRN.
     *
     * @return the library copied
     */
    @Nonnull
    public ReplyStyle getReplyStyle() {
        return replyStyle;
    }

    /**
     * Set FCU compatibility mode, where REQEV for event variable 0 answers
     * with the number of event variables only, and NVRD for NV 0 with the
     * number of NVs only.
     *
     * @param compatible true for FCU compatibility mode
     */
    public void setFcuCompatible(boolean compatible) {
        fcuCompatible = compatible;
    }

    /**
     * Refuse REVAL, as the VLCB specification says a node should.
     * By default the node answers REVAL as the VLCB libraries do.
     *
     * @param refuse true to answer REVAL with an error
     */
    public void setRefusesReval(boolean refuse) {
        refusesReval = refuse;
    }

    /**
     * Get whether the node refuses REVAL.
     *
     * @return true if REVAL is answered with an error
     */
    public boolean getRefusesReval() {
        return refusesReval;
    }

    /**
     * Make every NVSET and EVLRN fail with an error code, as a node does when
     * it rejects a value.
     *
     * @param errorCode the error code, or 0 for writes to succeed
     */
    public void setFailWritesWith(int errorCode) {
        setFailNextWrites(errorCode, -1);
    }

    /**
     * Make the next NVSET or EVLRN requests fail with an error code; later
     * ones succeed.
     *
     * @param errorCode the error code
     * @param count how many fail, or -1 for every one
     */
    public synchronized void setFailNextWrites(int errorCode, int count) {
        failWritesWith = errorCode & 0xff;
        failWritesLeft = count;
    }

    // the error code for this write, 0 for success
    private synchronized int writeError() {
        if ( failWritesLeft == 0 ) {
            return GRSP_OK;
        }
        if ( failWritesLeft > 0 ) {
            failWritesLeft--;
        }
        return failWritesWith;
    }

    /**
     * Set a node variable from an NVSET request.
     *
     * @param index the node variable index
     * @param newval the new value
     */
    @Override
    protected void setDummyNV(int index, int newval) {
        int error = writeError();
        if ( index < 1 || index > getNodeNvManager().getTotalNVs() ) {
            error = ERR_INVALID_NV_INDEX;
        }
        if ( error == GRSP_OK ) {
            getNodeNvManager().setNV(index,newval);
        }
        switch (replyStyle) {
            case PIC_LIBRARY:
                if ( error == GRSP_OK ) {
                    sendWRACK();
                    sendGRSP(CbusConstants.CBUS_NVSET, SERVICE_MNS, GRSP_OK, SECOND_FRAME);
                } else {
                    sendCMDERR(error);
                }
                break;
            case ARDUINO_LIBRARY:
                if ( error == GRSP_OK ) {
                    sendWRACK();
                } else {
                    sendGRSP(CbusConstants.CBUS_NVSET, SERVICE_NV, error, 0);
                    sendCmdErr(error, SECOND_FRAME);
                }
                break;
            default:
                sendGRSP(CbusConstants.CBUS_NVSET, SERVICE_NV, error, 0);
                break;
        }
    }

    /**
     * Teach an event variable from an EVLRN request, made in learn mode.
     *
     * @param nn the event node number
     * @param en the event number
     * @param evIndex the event variable index
     * @param val the event variable value
     */
    protected void learnEvent(int nn, int en, int evIndex, int val) {
        int error = writeError();
        if ( evIndex < 1 || evIndex > getNodeParamManager().getParameter(5) ) {
            error = ERR_INVALID_EV_INDEX;
        }
        if ( error == GRSP_OK ) {
            CbusNodeEvent ev = getNodeEventManager().getNodeEvent(nn,en);
            if ( ev == null ) {
                // a new event's variables start at 0, as in a module
                ev = getNodeEventManager().provideNodeEvent(nn,en);
                ev.setEvArr(new int[getNodeParamManager().getParameter(5)]);
            }
            ev.setEvVar(evIndex,val);
            if ( ev.getIndex() < 0 ) {
                ev.setIndex( getNodeEventManager().getNextFreeIndex() );
            }
        }
        if ( replyStyle == ReplyStyle.GRSP_ONLY ) {
            sendGRSP(CbusConstants.CBUS_EVLRN, SERVICE_TEACH, error, 0);
        } else if ( error == GRSP_OK ) {
            sendWRACK();
            sendGRSP(CbusConstants.CBUS_EVLRN, SERVICE_TEACH, GRSP_OK, SECOND_FRAME);
        } else {
            sendCMDERR(error);
            sendGRSP(CbusConstants.CBUS_EVLRN, SERVICE_TEACH, error, SECOND_FRAME);
        }
    }

    /**
     * Answer a REQEV request, made in learn mode.
     *
     * @param nn the event node number
     * @param en the event number
     * @param evIndex the event variable index, 0 for all
     */
    protected void answerReqev(int nn, int en, int evIndex) {
        CbusNodeEvent ev = getNodeEventManager().getNodeEvent(nn,en);
        int numEvs = getNodeParamManager().getParameter(5);
        if ( ev == null ) {
            sendError(CbusConstants.CBUS_REQEV, ERR_INVALID_EVENT);
            return;
        }
        if ( evIndex > numEvs ) {
            sendError(CbusConstants.CBUS_REQEV, ERR_INVALID_EV_INDEX);
            return;
        }
        if ( evIndex == 0 ) {
            sendEVANS(nn, en, 0, numEvs, 0);
            if ( !fcuCompatible ) {
                for ( int i = 1; i <= numEvs; i++ ) {
                    sendEVANS(nn, en, i, ev.getEvVar(i), STREAM_GAP * i);
                }
            }
        } else {
            sendEVANS(nn, en, evIndex, ev.getEvVar(evIndex), 0);
        }
    }

    /**
     * Answer an NVRD request for NV 0 with the number of NVs, then, unless in
     * FCU compatibility mode, each NV in turn.
     */
    protected void answerNvrdAll() {
        int numNvs = getNodeNvManager().getTotalNVs();
        sendNvans(0, numNvs, 0);
        if ( !fcuCompatible ) {
            for ( int i = 1; i <= numNvs; i++ ) {
                sendNvans(i, getNodeNvManager().getNV(i), STREAM_GAP * i);
            }
        }
    }

    private void sendNvans(int index, int value, int extraDelay) {
        CanReply r = new CanReply(5);
        r.setElement(0, CbusConstants.CBUS_NVANS);
        r.setElement(1, getNodeNumber() >> 8);
        r.setElement(2, getNodeNumber() & 0xff);
        r.setElement(3, index & 0xff);
        r.setElement(4, value & 0xff);
        sendFrame(r, extraDelay);
    }

    /**
     * Refuse a REVAL request, as the VLCB specification says a node should.
     */
    protected void refuseReval() {
        sendError(CbusConstants.CBUS_REVAL, ERR_INVALID_COMMAND);
    }

    // CMDERR then GRSP, or GRSP only
    private void sendError(int requestOpc, int error) {
        if ( replyStyle == ReplyStyle.GRSP_ONLY ) {
            sendGRSP(requestOpc, SERVICE_TEACH, error, 0);
        } else {
            sendCMDERR(error);
            sendGRSP(requestOpc, SERVICE_TEACH, error, SECOND_FRAME);
        }
    }

    private void sendEVANS(int nn, int en, int evIndex, int value, int extraDelay) {
        CanReply r = new CanReply(7);
        r.setElement(0, CbusConstants.CBUS_EVANS);
        r.setElement(1, nn >> 8);
        r.setElement(2, nn & 0xff);
        r.setElement(3, en >> 8);
        r.setElement(4, en & 0xff);
        r.setElement(5, evIndex & 0xff);
        r.setElement(6, value & 0xff);
        sendFrame(r, extraDelay);
    }

    private void sendCmdErr(int error, int extraDelay) {
        CanReply r = new CanReply(4);
        r.setElement(0, CbusConstants.CBUS_CMDERR);
        r.setElement(1, getNodeNumber() >> 8);
        r.setElement(2, getNodeNumber() & 0xff);
        r.setElement(3, error & 0xff);
        sendFrame(r, extraDelay);
    }

    /**
     * Send a generic response.
     *
     * @param requestOpc the opcode of the request answered
     * @param serviceType the service type
     * @param result the result, 0 for success
     * @param extraDelay delay in milliseconds after the node's response delay
     */
    protected void sendGRSP(int requestOpc, int serviceType, int result, int extraDelay) {
        CanReply r = new CanReply(6);
        r.setElement(0, CbusConstants.VLCB_GRSP);
        r.setElement(1, getNodeNumber() >> 8);
        r.setElement(2, getNodeNumber() & 0xff);
        r.setElement(3, requestOpc & 0xff);
        r.setElement(4, serviceType & 0xff);
        r.setElement(5, result & 0xff);
        sendFrame(r, extraDelay);
    }

}
