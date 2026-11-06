package com.netscan.app

import com.netscan.app.scan.ChannelUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelUtilsTest {

    @Test fun canal_24ghz() {
        assertEquals(1, ChannelUtils.channelFor(2412))
        assertEquals(6, ChannelUtils.channelFor(2437))
        assertEquals(11, ChannelUtils.channelFor(2462))
        assertEquals(14, ChannelUtils.channelFor(2484))
    }

    @Test fun canal_5ghz() {
        assertEquals(36, ChannelUtils.channelFor(5180))
        assertEquals(40, ChannelUtils.channelFor(5200))
    }

    @Test fun banda() {
        assertEquals("2.4 GHz", ChannelUtils.bandFor(2412))
        assertEquals("5 GHz", ChannelUtils.bandFor(5180))
        assertEquals("6 GHz", ChannelUtils.bandFor(5955))
    }
}
