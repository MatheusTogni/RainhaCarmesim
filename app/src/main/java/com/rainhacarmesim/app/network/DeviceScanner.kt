package com.rainhacarmesim.app.network

import android.os.Build
import com.rainhacarmesim.app.domain.DiscoveredHost
import com.rainhacarmesim.app.domain.SubnetCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

data class ScanOutcome(
    val hosts: List<DiscoveredHost>,
    val probed: Int,
    val range: SubnetCalculator.ScanRange,
    val startedAt: Long,
    val finishedAt: Long,
)

/**
 * Varre a sub-rede local. Todo o trabalho roda fora da thread principal e respeita cancelamento.
 */
class DeviceScanner(
    private val probe: HostProbe,
    private val resolver: HostnameResolver,
    private val vendors: VendorLookup,
    private val parallelism: Int = 32,
) {
    sealed interface Phase {
        data class Probing(val done: Int, val total: Int, val found: Int) : Phase
        data class Resolving(val done: Int, val total: Int) : Phase
    }

    suspend fun scan(network: NetworkSnapshot, onPhase: (Phase) -> Unit): ScanOutcome {
        val ip = requireNotNull(network.localIp) { "Sem endereço IPv4" }
        val prefix = requireNotNull(network.prefixLength) { "Sem máscara de sub-rede" }
        val range = requireNotNull(SubnetCalculator.scanRange(ip, prefix)) { "Sub-rede inválida" }
        val startedAt = System.currentTimeMillis()

        val targets = range.hosts.filter { it != ip }
        val total = targets.size
        val done = AtomicInteger(0)
        val foundCount = AtomicInteger(0)
        val semaphore = Semaphore(parallelism)
        onPhase(Phase.Probing(0, total, 0))

        val alive: List<Pair<String, ProbeResult>> = coroutineScope {
            targets.map { target ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        coroutineContext.ensureActive()
                        val r = runCatching { probe.probe(target) }.getOrNull()
                        if (r != null) foundCount.incrementAndGet()
                        onPhase(Phase.Probing(done.incrementAndGet(), total, foundCount.get()))
                        r?.let { target to it }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        // Após as sondagens a tabela ARP do sistema está populada (quando o Android permite lê-la).
        val arp = withContext(Dispatchers.IO) { ArpTable.read() }

        val resolveTotal = alive.size + 1
        val resolved = AtomicInteger(0)
        onPhase(Phase.Resolving(0, resolveTotal))
        val resolveSemaphore = Semaphore(8)
        val hosts = coroutineScope {
            alive.map { (address, result) ->
                async(Dispatchers.IO) {
                    resolveSemaphore.withPermit {
                        coroutineContext.ensureActive()
                        val mac = arp[address]
                        val name = resolver.resolve(address, network.dns + listOfNotNull(network.gateway))
                        onPhase(Phase.Resolving(resolved.incrementAndGet(), resolveTotal))
                        DiscoveredHost(
                            ip = address,
                            mac = mac,
                            hostname = name,
                            vendor = vendors.lookup(mac),
                            latencyMs = result.latencyMs,
                            isGateway = address == network.gateway,
                            detectedBy = result.method,
                        )
                    }
                }
            }.awaitAll()
        }

        // O próprio aparelho: dados reais do sistema (o MAC próprio é oculto pelo Android).
        val self = DiscoveredHost(
            ip = ip,
            hostname = listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
                .filter { it.isNotBlank() }.distinct().joinToString(" "),
            vendor = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            latencyMs = 0,
            isSelf = true,
            detectedBy = "local",
        )
        onPhase(Phase.Resolving(resolveTotal, resolveTotal))

        val sorted = (hosts + self).sortedBy { SubnetCalculator.ipToInt(it.ip)?.toLong()?.and(0xFFFFFFFFL) ?: 0L }
        return ScanOutcome(sorted, total, range, startedAt, System.currentTimeMillis())
    }
}
