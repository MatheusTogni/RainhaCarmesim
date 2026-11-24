package com.rainhacarmesim.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class PingResult(
    val target: String,
    val resolvedAddress: String?,
    val method: String,
    val transmitted: Int,
    val received: Int,
    val lossPercent: Double,
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?,
)

/**
 * Executa ping ICMP usando o binário do sistema (/system/bin/ping). Se ele não existir
 * ou não puder ser executado, recorre a medições de conexão TCP (portas 443/80) e informa o método.
 */
class PingRunner {

    suspend fun run(
        target: String,
        count: Int,
        onReply: (seq: Int, timeMs: Double?) -> Unit,
    ): PingResult = withContext(Dispatchers.IO) {
        val binary = listOf("/system/bin/ping", "/system/xbin/ping").firstOrNull { File(it).exists() }
        if (binary != null) {
            try {
                return@withContext icmp(binary, target, count, onReply)
            } catch (e: IOException) {
                // binário indisponível ou sem permissão: segue para o modo TCP
            }
        }
        tcp(target, count, onReply)
    }

    private suspend fun icmp(binary: String, target: String, count: Int, onReply: (Int, Double?) -> Unit): PingResult {
        val isV6 = target.contains(':')
        val exe = if (isV6 && File("/system/bin/ping6").exists()) "/system/bin/ping6" else binary
        val cmd = listOf(exe, "-c", count.toString(), "-W", "2", "-i", "1", target)
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val lines = mutableListOf<String>()
        var resolved: String? = null
        try {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    lines += line
                    if (resolved == null) resolved = PingOutputParser.parseResolvedAddress(line)
                    val reply = PingOutputParser.parseReply(line)
                    if (reply != null) onReply(reply.seq, reply.timeMs)
                }
            }
            process.waitFor()
        } finally {
            process.destroy()
        }
        val summary = PingOutputParser.parseSummary(lines)
        if (summary == null) {
            val last = lines.lastOrNull { it.isNotBlank() }
            if (last != null && (last.contains("unknown host", true) || last.contains("Name or service", true))) {
                throw UnknownHostError(target)
            }
            throw IOException(last ?: "ping não retornou resultado")
        }
        return PingResult(
            target = target,
            resolvedAddress = resolved,
            method = "ICMP",
            transmitted = summary.transmitted,
            received = summary.received,
            lossPercent = summary.lossPercent,
            minMs = summary.minMs,
            avgMs = summary.avgMs,
            maxMs = summary.maxMs,
        )
    }

    private suspend fun tcp(target: String, count: Int, onReply: (Int, Double?) -> Unit): PingResult {
        val address = try {
            InetAddress.getByName(target)
        } catch (e: Exception) {
            throw UnknownHostError(target)
        }
        val times = mutableListOf<Double>()
        var sent = 0
        for (seq in 1..count) {
            if (!currentCoroutineContext().isActive) break
            sent++
            val t = connectTime(address, 443) ?: connectTime(address, 80)
            if (t != null) times += t
            onReply(seq, t)
            if (seq < count) delay(1000)
        }
        return PingResult(
            target = target,
            resolvedAddress = address.hostAddress,
            method = "TCP (ICMP indisponível)",
            transmitted = sent,
            received = times.size,
            lossPercent = if (sent == 0) 0.0 else (sent - times.size) * 100.0 / sent,
            minMs = times.minOrNull(),
            avgMs = if (times.isEmpty()) null else times.average(),
            maxMs = times.maxOrNull(),
        )
    }

    private fun connectTime(address: InetAddress, port: Int): Double? {
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(address, port), 2000) }
            (System.nanoTime() - start) / 1_000_000.0
        } catch (e: Exception) {
            null
        }
    }
}

class UnknownHostError(host: String) : IOException("Não foi possível resolver o endereço \"$host\"")
