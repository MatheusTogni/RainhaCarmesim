package com.rainhacarmesim.app.data.repo

import androidx.room.withTransaction
import com.rainhacarmesim.app.data.db.AlertEntity
import com.rainhacarmesim.app.data.db.AppDatabase
import com.rainhacarmesim.app.data.db.DeviceEntity
import com.rainhacarmesim.app.data.db.ScanEntity
import com.rainhacarmesim.app.domain.AlertPolicy
import com.rainhacarmesim.app.domain.ScanMerger
import com.rainhacarmesim.app.network.ScanOutcome
import kotlinx.coroutines.flow.Flow

data class ScanSummary(
    val found: Int,
    val newDevices: Int,
    val alerts: List<AlertEntity>,
    val finishedAt: Long,
    val limitedRange: Boolean,
    val scannedPrefix: Int,
)

/** Persistência de dispositivos, varreduras e alertas, e a comparação entre varreduras. */
class DeviceRepository(private val db: AppDatabase) {
    private val devices = db.deviceDao()
    private val scans = db.scanDao()
    private val alerts = db.alertDao()

    fun observeDevices(networkKey: String): Flow<List<DeviceEntity>> = devices.observe(networkKey)
    fun observeLastScan(networkKey: String): Flow<ScanEntity?> = scans.observeLast(networkKey)
    fun observeAlerts(): Flow<List<AlertEntity>> = alerts.observeAll()
    fun observeUnreadAlerts(): Flow<Int> = alerts.observeUnreadCount()

    /** Salva o resultado de uma varredura e devolve os alertas gerados (já filtrados contra repetição). */
    suspend fun saveScan(networkKey: String, outcome: ScanOutcome, automatic: Boolean): ScanSummary {
        val now = outcome.finishedAt
        return db.withTransaction {
            val known = devices.getAll(networkKey)
            val merge = ScanMerger.merge(networkKey, known, outcome.hosts, now)
            devices.upsertAll(merge.devices)

            val newAlerts = merge.transitions.mapNotNull { t ->
                val last = alerts.lastAlertTime(networkKey, t.device.deviceKey, t.type.name)
                if (!AlertPolicy.shouldEmit(last, now)) return@mapNotNull null
                AlertEntity(
                    networkKey = networkKey,
                    deviceKey = t.device.deviceKey,
                    type = t.type.name,
                    deviceLabel = ScanMerger.label(t.device),
                    ip = t.device.ip,
                    createdAt = now,
                )
            }
            val ids = if (newAlerts.isEmpty()) emptyList() else alerts.insertAll(newAlerts)
            scans.insert(
                ScanEntity(
                    networkKey = networkKey,
                    startedAt = outcome.startedAt,
                    finishedAt = now,
                    hostsProbed = outcome.probed,
                    devicesFound = outcome.hosts.size,
                    newDevices = merge.newCount,
                    automatic = automatic,
                )
            )
            ScanSummary(
                found = outcome.hosts.size,
                newDevices = merge.newCount,
                alerts = newAlerts.zip(ids) { a, id -> a.copy(id = id) },
                finishedAt = now,
                limitedRange = outcome.range.limited,
                scannedPrefix = outcome.range.scannedPrefix,
            )
        }
    }

    suspend fun acknowledgeNew(networkKey: String) = devices.acknowledgeNew(networkKey)
    suspend fun forgetDevice(networkKey: String, deviceKey: String) = devices.delete(networkKey, deviceKey)
    suspend fun markAlertRead(id: Long) = alerts.markRead(id)
    suspend fun markAllAlertsRead() = alerts.markAllRead()
    suspend fun clearAlerts() = alerts.deleteAll()

    suspend fun clearDevices() {
        db.withTransaction {
            devices.deleteAll()
            scans.deleteAll()
        }
    }
}
