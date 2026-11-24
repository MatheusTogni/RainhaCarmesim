package com.rainhacarmesim.app.network

import java.io.File

/**
 * Tenta ler a tabela de vizinhos (IP → MAC).
 * A partir do Android 10 o acesso a /proc/net/arp e ao netlink é bloqueado para apps comuns,
 * então em aparelhos atuais o resultado costuma vir vazio. Nunca inventamos MACs.
 */
object ArpTable {
    private val macRegex = Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")

    fun read(): Map<String, String> {
        val fromProc = runCatching { readProc() }.getOrDefault(emptyMap())
        if (fromProc.isNotEmpty()) return fromProc
        return runCatching { readIpNeigh() }.getOrDefault(emptyMap())
    }

    private fun readProc(): Map<String, String> {
        val file = File("/proc/net/arp")
        if (!file.canRead()) return emptyMap()
        return file.readLines().drop(1).mapNotNull { line ->
            val cols = line.trim().split(Regex("""\s+"""))
            if (cols.size < 4) return@mapNotNull null
            normalize(cols[3])?.let { cols[0] to it }
        }.toMap()
    }

    private fun readIpNeigh(): Map<String, String> {
        val process = ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        return output.lines().mapNotNull { line ->
            val cols = line.trim().split(Regex("""\s+"""))
            val idx = cols.indexOf("lladdr")
            if (idx < 0 || idx + 1 >= cols.size) return@mapNotNull null
            normalize(cols[idx + 1])?.let { cols[0] to it }
        }.toMap()
    }

    fun normalize(mac: String): String? {
        val m = mac.trim().uppercase()
        if (!macRegex.matches(m) || m == "00:00:00:00:00:00" || m == "FF:FF:FF:FF:FF:FF") return null
        return m
    }
}
