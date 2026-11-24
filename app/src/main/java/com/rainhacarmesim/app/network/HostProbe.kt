package com.rainhacarmesim.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class ProbeResult(val latencyMs: Int, val method: String)

/**
 * Verifica se um IP responde. Usa ICMP (InetAddress.isReachable) e, em paralelo, tentativas TCP
 * em portas comuns. Uma conexão recusada (RST) também prova que o host existe.
 */
class HostProbe(
    private val icmpTimeoutMs: Int = 600,
    private val tcpTimeoutMs: Int = 450,
) {
    private val ports = intArrayOf(80, 443, 22, 445, 139, 53, 8080, 554, 62078)

    suspend fun probe(ip: String): ProbeResult? = coroutineScope {
        val address = withContext(Dispatchers.IO) { InetAddress.getByName(ip) }
        val icmp = async(Dispatchers.IO) { icmp(address) }
        val tcp = ports.map { port -> async(Dispatchers.IO) { tcp(ip, port) } }
        val icmpResult = icmp.await()
        val tcpResults = tcp.awaitAll().filterNotNull()
        when {
            icmpResult != null -> icmpResult
            tcpResults.isNotEmpty() -> tcpResults.minByOrNull { it.latencyMs }
            else -> null
        }
    }

    private fun icmp(address: InetAddress): ProbeResult? {
        val start = System.nanoTime()
        return try {
            if (address.isReachable(icmpTimeoutMs)) ProbeResult(elapsedMs(start), "ICMP") else null
        } catch (e: Exception) {
            null
        }
    }

    private fun tcp(ip: String, port: Int): ProbeResult? {
        val start = System.nanoTime()
        return try {
            Socket().use { s -> s.connect(InetSocketAddress(ip, port), tcpTimeoutMs) }
            ProbeResult(elapsedMs(start), "TCP $port")
        } catch (e: ConnectException) {
            // ECONNREFUSED: o host respondeu com RST, logo está ativo. Outros erros (sem rota) não contam.
            val msg = e.message.orEmpty()
            if (msg.contains("ECONNREFUSED") || msg.contains("refused", ignoreCase = true)) {
                ProbeResult(elapsedMs(start), "TCP $port (recusada)")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun elapsedMs(start: Long): Int = ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(0)
}
