package com.rainhacarmesim.app.domain

import com.rainhacarmesim.app.data.db.DeviceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanMergerTest {
    private val net = "192.168.0.0/24@192.168.0.1"

    private fun device(ip: String, online: Boolean = true, missed: Int = 0, mac: String? = null) = DeviceEntity(
        networkKey = net,
        deviceKey = if (mac != null) "mac:$mac" else "ip:$ip",
        ip = ip, mac = mac, hostname = null, vendor = null,
        firstSeen = 0, lastSeen = 0, online = online, missedScans = missed,
        isNew = false, isSelf = false, isGateway = false, lastLatencyMs = null, detectedBy = null,
    )

    @Test fun firstScanIsBaselineWithoutAlerts() {
        val r = ScanMerger.merge(net, emptyList(), listOf(DiscoveredHost("192.168.0.10"), DiscoveredHost("192.168.0.11")), 1000)
        assertEquals(2, r.devices.size)
        assertTrue(r.transitions.isEmpty())
        assertTrue(r.devices.none { it.isNew })
    }

    @Test fun unknownHostIsNew() {
        val r = ScanMerger.merge(net, listOf(device("192.168.0.10")), listOf(DiscoveredHost("192.168.0.10"), DiscoveredHost("192.168.0.99")), 1000)
        assertEquals(1, r.newCount)
        assertEquals(AlertType.NEW_DEVICE, r.transitions.single().type)
        assertTrue(r.devices.first { it.ip == "192.168.0.99" }.isNew)
    }

    @Test fun singleMissDoesNotMarkOffline() {
        val r = ScanMerger.merge(net, listOf(device("192.168.0.10")), emptyList(), 1000)
        val d = r.devices.single()
        assertTrue(d.online)
        assertEquals(1, d.missedScans)
        assertTrue(r.transitions.isEmpty())
    }

    @Test fun secondConsecutiveMissMarksOffline() {
        val r = ScanMerger.merge(net, listOf(device("192.168.0.10", missed = 1)), emptyList(), 1000)
        val d = r.devices.single()
        assertFalse(d.online)
        assertEquals(AlertType.DISCONNECTED, r.transitions.single().type)
    }

    @Test fun offlineDeviceReturning() {
        val r = ScanMerger.merge(net, listOf(device("192.168.0.10", online = false, missed = 3)), listOf(DiscoveredHost("192.168.0.10")), 1000)
        val d = r.devices.single()
        assertTrue(d.online)
        assertEquals(0, d.missedScans)
        assertEquals(AlertType.RETURNED, r.transitions.single().type)
    }

    @Test fun alreadyOfflineDeviceDoesNotRealert() {
        val r = ScanMerger.merge(net, listOf(device("192.168.0.10", online = false, missed = 2)), emptyList(), 1000)
        assertTrue(r.transitions.isEmpty())
    }

    @Test fun macIdentityFollowsIpChange() {
        val known = device("192.168.0.10", mac = "AA:BB:CC:00:11:22")
        val r = ScanMerger.merge(net, listOf(known), listOf(DiscoveredHost("192.168.0.50", mac = "aa:bb:cc:00:11:22")), 1000)
        assertEquals(1, r.devices.size)
        assertEquals("192.168.0.50", r.devices.single().ip)
        assertTrue(r.transitions.isEmpty())
    }

    @Test fun keepsPreviousHostnameWhenMissing() {
        val known = device("192.168.0.10").copy(hostname = "impressora")
        val r = ScanMerger.merge(net, listOf(known), listOf(DiscoveredHost("192.168.0.10")), 1000)
        assertEquals("impressora", r.devices.single().hostname)
    }

    @Test fun alertCooldown() {
        assertTrue(AlertPolicy.shouldEmit(null, 1000))
        assertFalse(AlertPolicy.shouldEmit(1000, 1000 + 60_000))
        assertTrue(AlertPolicy.shouldEmit(1000, 1000 + AlertPolicy.COOLDOWN_MS))
    }
}
