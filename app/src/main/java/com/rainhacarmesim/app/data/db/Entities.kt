package com.rainhacarmesim.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Dispositivo conhecido numa rede. A identidade é ([networkKey], [deviceKey]):
 * [deviceKey] é "mac:AA:BB:..." quando o MAC foi obtido, senão "ip:192.168.0.10".
 */
@Entity(tableName = "devices", primaryKeys = ["networkKey", "deviceKey"])
data class DeviceEntity(
    val networkKey: String,
    val deviceKey: String,
    val ip: String,
    val mac: String?,
    val hostname: String?,
    val vendor: String?,
    val firstSeen: Long,
    val lastSeen: Long,
    val online: Boolean,
    /** Varreduras consecutivas em que o dispositivo não respondeu. */
    val missedScans: Int,
    /** Detectado como novo e ainda não visto pelo usuário. */
    val isNew: Boolean,
    val isSelf: Boolean,
    val isGateway: Boolean,
    val lastLatencyMs: Int?,
    /** Como foi detectado na última vez (ICMP, TCP, ...). */
    val detectedBy: String?,
)

@Entity(tableName = "scans", indices = [Index("networkKey")])
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val networkKey: String,
    val startedAt: Long,
    val finishedAt: Long,
    val hostsProbed: Int,
    val devicesFound: Int,
    val newDevices: Int,
    val automatic: Boolean,
)

@Entity(tableName = "alerts", indices = [Index("createdAt"), Index("networkKey", "deviceKey")])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val networkKey: String,
    val deviceKey: String,
    /** Nome de [com.rainhacarmesim.app.domain.AlertType]. */
    val type: String,
    val deviceLabel: String,
    val ip: String,
    val createdAt: Long,
    val read: Boolean = false,
)

/** Tráfego do próprio celular agregado por minuto (amostrado enquanto o app está aberto). */
@Entity(tableName = "traffic_minutes")
data class TrafficMinuteEntity(
    @PrimaryKey val minuteStart: Long,
    val rxBytes: Long,
    val txBytes: Long,
)

@Entity(tableName = "speed_tests", indices = [Index("timestamp")])
data class SpeedTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val latencyMs: Double,
    val jitterMs: Double,
    val server: String,
    val connection: String,
)
