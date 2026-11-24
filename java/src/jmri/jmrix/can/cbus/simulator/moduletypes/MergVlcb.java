package jmri.jmrix.can.cbus.simulator.moduletypes;

import static jmri.jmrix.can.cbus.CbusConstants.MANU_MERG;

import jmri.jmrix.can.CanSystemConnectionMemo;
import jmri.jmrix.can.cbus.node.CbusNode;
import jmri.jmrix.can.cbus.simulator.CbusDummyNode;
import jmri.jmrix.can.cbus.simulator.CbusDummyVlcbNode;
import jmri.jmrix.can.cbus.simulator.CbusSimulatedModuleProvider;

import org.openide.util.lookup.ServiceProvider;

/**
 * Simulation of a VLCB module: 8 event variables per event, 10 node
 * variables, and the VLCB flag, bit 6, set in the node flags.
 *
 * @author Keith Bruce Copyright (C) 2026
 */
@ServiceProvider(service = CbusSimulatedModuleProvider.class)
public class MergVlcb extends CbusSimulatedModuleProvider {

    /** Node flags: consumer, producer, FLiM, bootloader and VLCB. */
    public static final int NODE_FLAGS = 0x4F;

    @Override
    public int getManufacturerId() {
        return MANU_MERG;
    }

    @Override
    public int getModuleId() {
        return 252;
    }

    @Override
    public String getToolTipText() {
        return "Simulation of a VLCB module";
    }

    @Override
    protected CbusDummyNode createNewDummyNode(CanSystemConnectionMemo memo, int nodeNumber ){
        return new CbusDummyVlcbNode(memo, nodeNumber);
    }

    @Override
    public void configureDummyNode(CbusNode node) {
        int[] _params = new int[]{
            20, /* 0 num parameters   */
            MANU_MERG, /* 1 manufacturer ID   */
            97, /* 2 Minor code version, a   */
            252, /* 3 Manufacturer module identifier   */
            64, /* 4 Number of supported events   */
            8, /* 5 Number of Event Variables per event   */
            10, /* 6 Number of Node Variables   */
            4, /* 7 Major version   */
            NODE_FLAGS, /* 8 Node flags   */
            13, /* 9 Processor type   */
            1, /* 10 Bus type   */
            0, /* 11 load address, 1/4 bytes   */
            8, /* 12 load address, 2/4 bytes   */
            0, /* 13 load address, 3/4 bytes   */
            0, /* 14 load address, 4/4 bytes   */
            0, /* 15 CPU manufacturer's id 1/4  */
            0, /* 16 CPU manufacturer's id 2/4  */
            0, /* 17 CPU manufacturer's id 3/4  */
            0, /* 18 CPU manufacturer's id 4/4  */
            1, /* 19 CPU manufacturer code   */
            0, /* 20 Beta revision   */
        };
        node.getNodeParamManager().setParameters(_params);
        node.getNodeNvManager().setNVs( new int[]{ 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } );
        node.setNodeNameFromName("VLCB");
    }

}
