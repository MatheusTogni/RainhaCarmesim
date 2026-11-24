package com.rainhacarmesim.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test fun bitRates() {
        assertEquals("0" to "bps", Formatters.bitRate(0.0))
        assertEquals("8,00" to "Kbps", Formatters.bitRate(1000.0))
        assertEquals("100" to "Mbps", Formatters.bitRate(12_500_000.0))
        assertEquals("1,00" to "Gbps", Formatters.bitRate(125_000_000.0))
    }

    @Test fun bytes() {
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1,00 KB", Formatters.bytes(1024))
        assertEquals("1,50 MB", Formatters.bytes(1572864))
        assertEquals("10,0 GB", Formatters.bytes(10L * 1024 * 1024 * 1024))
    }
}
