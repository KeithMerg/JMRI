package jmri.jmrix.can.cbus.simulator.moduletypes;

import org.junit.jupiter.api.BeforeEach;

/**
 * Tests for MergVlcb.
 * @author Keith Bruce Copyright (C) 2026
 */
public class MergVlcbTest extends SimModuleProviderTestBase {

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        provider = new MergVlcb();
    }

}
