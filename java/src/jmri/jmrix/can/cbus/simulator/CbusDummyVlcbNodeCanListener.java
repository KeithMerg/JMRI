package jmri.jmrix.can.cbus.simulator;

import jmri.jmrix.AbstractMessage;
import jmri.jmrix.can.CanSystemConnectionMemo;
import jmri.jmrix.can.cbus.CbusConstants;

/**
 * Processing of CAN Frames for a CbusDummyVlcbNode.
 * <p>
 * NVSET, EVLRN, REQEV, NVRD for NV 0, and REVAL when refused, are answered as a VLCB node
 * answers them; every other frame as a CBUS node answers it.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
public class CbusDummyVlcbNodeCanListener extends CbusDummyNodeCanListener {

    private final CbusDummyVlcbNode _vlcbNode;

    /**
     * Create a new CbusDummyVlcbNodeCanListener.
     *
     * @param connmemo The CAN Connection to listen to.
     * @param node The Node
     */
    public CbusDummyVlcbNodeCanListener ( CanSystemConnectionMemo connmemo, CbusDummyVlcbNode node ){
        super(connmemo,node);
        _vlcbNode = node;
    }

    @Override
    protected void startProcessFrame(AbstractMessage m) {
        if ( _vlcbNode.getNodeParamManager().getParameter(3) != 0
            && !_vlcbNode.getNodeInSetupMode()
            && _vlcbNode.getNodeInFLiMMode()
            && processVlcbFrame(m) ) {
            return;
        }
        super.startProcessFrame(m);
    }

    // true if the frame is answered here
    private boolean processVlcbFrame(AbstractMessage m) {
        int len = m.getNumDataElements();
        boolean toThisNode = len > 2 && _vlcbNode.getNodeNumber() == ( m.getElement(1) * 256 ) + m.getElement(2);
        switch (m.getElement(0)) {
            case CbusConstants.CBUS_NVSET:
                if ( toThisNode && len > 4 ) {
                    _vlcbNode.setDummyNV(m.getElement(3),m.getElement(4));
                }
                return true;
            case CbusConstants.CBUS_NVRD:
                if ( toThisNode && len > 3 && m.getElement(3) == 0 ) {
                    _vlcbNode.answerNvrdAll();
                    return true;
                }
                return false;
            case CbusConstants.CBUS_REVAL:
                if ( toThisNode && _vlcbNode.getRefusesReval() ) {
                    _vlcbNode.refuseReval();
                    return true;
                }
                return false;
            case CbusConstants.CBUS_EVLRN:
                if ( _vlcbNode.getNodeInLearnMode() && len > 6 ) {
                    _vlcbNode.learnEvent(
                        ( m.getElement(1) * 256 ) + m.getElement(2),
                        ( m.getElement(3) * 256 ) + m.getElement(4),
                        m.getElement(5),
                        m.getElement(6) );
                }
                return true;
            case CbusConstants.CBUS_REQEV:
                if ( _vlcbNode.getNodeInLearnMode() && len > 5 ) {
                    _vlcbNode.answerReqev(
                        ( m.getElement(1) * 256 ) + m.getElement(2),
                        ( m.getElement(3) * 256 ) + m.getElement(4),
                        m.getElement(5) );
                }
                return true;
            default:
                return false;
        }
    }

}
