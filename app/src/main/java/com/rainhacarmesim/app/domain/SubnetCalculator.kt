package com.rainhacarmesim.app.domain

/** Cálculos de sub-rede IPv4 (puros, sem dependência do Android). */
object SubnetCalculator {
    /** Maior quantidade de hosts que a varredura aceita (equivale a uma /22). */
    const val MAX_SCAN_PREFIX = 22

    fun ipToInt(ip: String): Int? {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return null
        var result = 0
        for (p in parts) {
            val n = p.toIntOrNull() ?: return null
            if (n !in 0..255 || p.isEmpty() || (p.length > 1 && p.startsWith('0'))) return null
            result = (result shl 8) or n
        }
        return result
    }

    fun intToIp(value: Int): String =
        "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}.${(value ushr 8) and 0xFF}.${value and 0xFF}"

    fun prefixToMaskInt(prefix: Int): Int {
        require(prefix in 0..32) { "prefixo inválido: $prefix" }
        return if (prefix == 0) 0 else (-1 shl (32 - prefix))
    }

    fun maskFromPrefix(prefix: Int): String = intToIp(prefixToMaskInt(prefix))

    fun networkAddress(ip: String, prefix: Int): String? =
        ipToInt(ip)?.let { intToIp(it and prefixToMaskInt(prefix)) }

    data class ScanRange(
        /** Prefixo real da rede. */
        val networkPrefix: Int,
        /** Prefixo efetivamente varrido (pode ser menor que a rede, por limite de tamanho). */
        val scannedPrefix: Int,
        val networkAddress: String,
        val hosts: List<String>,
    ) {
        val limited: Boolean get() = scannedPrefix != networkPrefix
    }

    /**
     * Lista os endereços de host a sondar em torno de [ip]. Redes maiores que /22 são reduzidas
     * à /22 que contém o próprio IP, para manter a varredura rápida e leve.
     */
    fun scanRange(ip: String, prefix: Int): ScanRange? {
        val ipInt = ipToInt(ip) ?: return null
        if (prefix !in 0..32) return null
        val effective = maxOf(prefix, MAX_SCAN_PREFIX)
        val mask = prefixToMaskInt(effective)
        val network = ipInt and mask
        val hosts = mutableListOf<String>()
        if (effective >= 31) {
            // /31 e /32: não há endereços de rede/broadcast reservados.
            val count = 1 shl (32 - effective)
            for (i in 0 until count) hosts += intToIp(network + i)
        } else {
            val count = 1 shl (32 - effective)
            for (i in 1 until count - 1) hosts += intToIp(network + i)
        }
        return ScanRange(prefix, effective, intToIp(network), hosts)
    }

    fun isValidIpv4(text: String): Boolean = ipToInt(text) != null
}
