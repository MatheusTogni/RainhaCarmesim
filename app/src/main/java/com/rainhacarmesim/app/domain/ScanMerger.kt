package com.rainhacarmesim.app.domain

import com.rainhacarmesim.app.data.db.DeviceEntity

/** Host que respondeu numa varredura. Campos opcionais podem não estar disponíveis no Android. */
data class DiscoveredHost(
    val ip: String,
    val mac: String? = null,
    val hostname: String? = null,
    val vendor: String? = null,
    val latencyMs: Int? = null,
    val isSelf: Boolean = false,
    val isGateway: Boolean = false,
    val detectedBy: String? = null,
)

/** Evento de mudança de estado que pode virar alerta. */
data class DeviceTransition(val type: AlertType, val device: DeviceEntity)

data class MergeResult(
    val devices: List<DeviceEntity>,
    val transitions: List<DeviceTransition>,
    val newCount: Int,
)

/**
 * Compara a varredura atual com os dispositivos conhecidos.
 *
 * Regras:
 * - Dispositivo não conhecido → novo (alerta NEW_DEVICE), exceto na primeira varredura da rede,
 *   que serve de referência e não gera alertas.
 * - Dispositivo conhecido que estava offline e respondeu → RETURNED.
 * - Dispositivo online que não respondeu só vira offline após [offlineThreshold] varreduras
 *   consecutivas sem resposta → DISCONNECTED. Uma falha isolada não muda o status.
 */
object ScanMerger {
    const val DEFAULT_OFFLINE_THRESHOLD = 2

    fun keyFor(host: DiscoveredHost): String =
        if (host.mac != null) "mac:${host.mac.uppercase()}" else "ip:${host.ip}"

    fun merge(
        networkKey: String,
        known: List<DeviceEntity>,
        found: List<DiscoveredHost>,
        now: Long,
        offlineThreshold: Int = DEFAULT_OFFLINE_THRESHOLD,
    ): MergeResult {
        val baseline = known.isEmpty()
        val byKey = known.associateBy { it.deviceKey }.toMutableMap()
        val matchedKeys = mutableSetOf<String>()
        val result = mutableListOf<DeviceEntity>()
        val transitions = mutableListOf<DeviceTransition>()
        var newCount = 0

        for (host in found) {
            val key = keyFor(host)
            val existing = byKey[key]
                ?: if (host.mac == null) {
                    // Sem MAC, o IP é a única identidade possível.
                    known.firstOrNull { it.ip == host.ip && it.deviceKey !in matchedKeys }
                } else {
                    // MAC passou a ser legível para um host antes identificado só por IP.
                    known.firstOrNull { it.deviceKey == "ip:${host.ip}" && it.deviceKey !in matchedKeys }
                }

            if (existing != null && existing.deviceKey !in matchedKeys) {
                matchedKeys += existing.deviceKey
                val updated = existing.copy(
                    ip = host.ip,
                    mac = host.mac ?: existing.mac,
                    hostname = host.hostname ?: existing.hostname,
                    vendor = host.vendor ?: existing.vendor,
                    lastSeen = now,
                    online = true,
                    missedScans = 0,
                    isSelf = host.isSelf,
                    isGateway = host.isGateway,
                    lastLatencyMs = host.latencyMs,
                    detectedBy = host.detectedBy ?: existing.detectedBy,
                )
                if (!existing.online) transitions += DeviceTransition(AlertType.RETURNED, updated)
                result += updated
            } else {
                val created = DeviceEntity(
                    networkKey = networkKey,
                    deviceKey = key,
                    ip = host.ip,
                    mac = host.mac,
                    hostname = host.hostname,
                    vendor = host.vendor,
                    firstSeen = now,
                    lastSeen = now,
                    online = true,
                    missedScans = 0,
                    isNew = !baseline && !host.isSelf,
                    isSelf = host.isSelf,
                    isGateway = host.isGateway,
                    lastLatencyMs = host.latencyMs,
                    detectedBy = host.detectedBy,
                )
                matchedKeys += key
                byKey[key] = created
                if (!baseline && !host.isSelf) {
                    newCount++
                    transitions += DeviceTransition(AlertType.NEW_DEVICE, created)
                }
                result += created
            }
        }

        for (device in known) {
            if (device.deviceKey in matchedKeys) continue
            if (!device.online) {
                result += device
                continue
            }
            val missed = device.missedScans + 1
            if (missed >= offlineThreshold) {
                val offline = device.copy(online = false, missedScans = missed)
                transitions += DeviceTransition(AlertType.DISCONNECTED, offline)
                result += offline
            } else {
                result += device.copy(missedScans = missed)
            }
        }

        return MergeResult(result, transitions, newCount)
    }

    /** Nome amigável para exibir em listas e alertas. */
    fun label(device: DeviceEntity): String {
        if (device.isSelf) return "Este celular"
        device.hostname?.takeIf { it.isNotBlank() }?.let { return it }
        if (device.isGateway) return "Roteador (gateway)"
        device.vendor?.takeIf { it.isNotBlank() }?.let { return it }
        return device.ip
    }
}
