package com.rainhacarmesim.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.random.Random

/**
 * Descobre o nome de um host por três caminhos, na ordem:
 * 1. DNS reverso (PTR) no gateway/DNS da rede — roteadores costumam conhecer os nomes do DHCP;
 * 2. mDNS unicast (porta 5353) direto no host — Apple, Linux/Avahi, impressoras, Chromecast...;
 * 3. NetBIOS (porta 137) — Windows e Samba.
 * Qualquer um pode falhar; nesse caso o hostname fica indisponível.
 */
class HostnameResolver(private val timeoutMs: Int = 700) {

    suspend fun resolve(ip: String, dnsServers: List<String>): String? = withContext(Dispatchers.IO) {
        for (server in dnsServers.distinct().take(3)) {
            queryPtr(server, 53, ip, recursion = true)?.let { return@withContext clean(it) }
        }
        queryPtr(ip, 5353, ip, recursion = false)?.let { return@withContext clean(it) }
        netBios(ip)
    }

    private fun clean(name: String): String = name.removeSuffix(".local").removeSuffix(".lan").removeSuffix(".home")

    private fun queryPtr(server: String, port: Int, ip: String, recursion: Boolean): String? {
        val id = Random.nextInt(0, 0xFFFF)
        val query = DnsCodec.buildQuery(id, DnsCodec.reverseName(ip), DnsCodec.TYPE_PTR, recursion)
        return exchange(server, port, query) { buf, len -> DnsCodec.parsePtrAnswer(buf, len, id) }
    }

    private fun netBios(ip: String): String? {
        val id = Random.nextInt(0, 0xFFFF)
        return exchange(ip, 137, DnsCodec.buildNetBiosStatusQuery(id)) { buf, len -> DnsCodec.parseNetBiosName(buf, len) }
    }

    private fun exchange(host: String, port: Int, payload: ByteArray, parse: (ByteArray, Int) -> String?): String? =
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val address = InetAddress.getByName(host)
                socket.send(DatagramPacket(payload, payload.size, address, port))
                val buf = ByteArray(1500)
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                parse(buf, packet.length)
            }
        } catch (e: SocketTimeoutException) {
            null
        } catch (e: Exception) {
            null
        }
}
