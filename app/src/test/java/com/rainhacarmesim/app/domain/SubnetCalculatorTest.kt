package com.rainhacarmesim.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubnetCalculatorTest {
    @Test fun ipRoundTrip() {
        val v = SubnetCalculator.ipToInt("192.168.1.42")!!
        assertEquals("192.168.1.42", SubnetCalculator.intToIp(v))
    }

    @Test fun rejectsInvalidIps() {
        assertNull(SubnetCalculator.ipToInt("256.1.1.1"))
        assertNull(SubnetCalculator.ipToInt("1.2.3"))
        assertNull(SubnetCalculator.ipToInt("a.b.c.d"))
        assertNull(SubnetCalculator.ipToInt("01.2.3.4"))
        assertFalse(SubnetCalculator.isValidIpv4(""))
    }

    @Test fun masks() {
        assertEquals("255.255.255.0", SubnetCalculator.maskFromPrefix(24))
        assertEquals("255.255.252.0", SubnetCalculator.maskFromPrefix(22))
        assertEquals("255.255.255.255", SubnetCalculator.maskFromPrefix(32))
        assertEquals("0.0.0.0", SubnetCalculator.maskFromPrefix(0))
    }

    @Test fun slash24Range() {
        val r = SubnetCalculator.scanRange("192.168.0.37", 24)!!
        assertEquals(254, r.hosts.size)
        assertEquals("192.168.0.1", r.hosts.first())
        assertEquals("192.168.0.254", r.hosts.last())
        assertEquals("192.168.0.0", r.networkAddress)
        assertFalse(r.limited)
    }

    @Test fun largeNetworksAreLimited() {
        val r = SubnetCalculator.scanRange("10.20.30.40", 16)!!
        assertTrue(r.limited)
        assertEquals(22, r.scannedPrefix)
        assertEquals(1022, r.hosts.size)
        assertTrue(r.hosts.contains("10.20.30.40"))
    }

    @Test fun networkAddress() {
        assertEquals("172.16.4.0", SubnetCalculator.networkAddress("172.16.5.9", 23))
    }
}
