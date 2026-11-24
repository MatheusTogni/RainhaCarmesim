package com.rainhacarmesim.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PingOutputParserTest {
    private val output = """
        PING google.com (142.250.79.46) 56(84) bytes of data.
        64 bytes from 142.250.79.46: icmp_seq=1 ttl=117 time=12.3 ms
        64 bytes from 142.250.79.46: icmp_seq=2 ttl=117 time=14.9 ms
        64 bytes from 142.250.79.46: icmp_seq=4 ttl=117 time=11.0 ms

        --- google.com ping statistics ---
        4 packets transmitted, 3 received, 25% packet loss, time 3005ms
        rtt min/avg/max/mdev = 11.016/12.733/14.912/1.612 ms
    """.trimIndent().lines()

    @Test fun parsesReplies() {
        val r = PingOutputParser.parseReply(output[1])!!
        assertEquals(1, r.seq)
        assertEquals(12.3, r.timeMs, 0.001)
        assertNull(PingOutputParser.parseReply(output[0]))
    }

    @Test fun parsesHeader() {
        assertEquals("142.250.79.46", PingOutputParser.parseResolvedAddress(output[0]))
    }

    @Test fun parsesSummary() {
        val s = PingOutputParser.parseSummary(output)!!
        assertEquals(4, s.transmitted)
        assertEquals(3, s.received)
        assertEquals(25.0, s.lossPercent, 0.001)
        assertEquals(11.016, s.minMs!!, 0.001)
        assertEquals(12.733, s.avgMs!!, 0.001)
        assertEquals(14.912, s.maxMs!!, 0.001)
    }

    @Test fun totalLossHasNoRtt() {
        val s = PingOutputParser.parseSummary(listOf("3 packets transmitted, 0 received, 100% packet loss, time 2041ms"))
        assertNotNull(s)
        assertEquals(100.0, s!!.lossPercent, 0.001)
        assertNull(s.avgMs)
    }
}
