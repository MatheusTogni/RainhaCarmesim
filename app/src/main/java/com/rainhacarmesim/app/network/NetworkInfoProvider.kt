package com.rainhacarmesim.app.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.rainhacarmesim.app.domain.SubnetCalculator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.Inet4Address

enum class Transport { WIFI, ETHERNET, CELLULAR, VPN, OTHER, NONE }

enum class SsidStatus { OK, NEED_PERMISSION, LOCATION_OFF, UNAVAILABLE, NOT_WIFI }

/** Retrato da conexão atual. Campos nulos = o Android não forneceu a informação. */
data class NetworkSnapshot(
    val connected: Boolean,
    val transport: Transport,
    val validated: Boolean = false,
    val ssid: String? = null,
    val ssidStatus: SsidStatus = SsidStatus.NOT_WIFI,
    val localIp: String? = null,
    val prefixLength: Int? = null,
    val gateway: String? = null,
    val dns: List<String> = emptyList(),
    val interfaceName: String? = null,
    val linkSpeedMbps: Int? = null,
    val rssi: Int? = null,
) {
    val subnetMask: String? get() = prefixLength?.let { SubnetCalculator.maskFromPrefix(it) }

    /** A varredura só faz sentido em rede local (Wi-Fi ou cabo) com IPv4. */
    val canScanLan: Boolean
        get() = connected && (transport == Transport.WIFI || transport == Transport.ETHERNET) &&
            localIp != null && prefixLength != null

    /**
     * Identificador estável da rede para separar os dispositivos conhecidos por rede.
     * Usa endereço de rede + gateway (o SSID nem sempre está disponível por causa de permissões).
     */
    val networkKey: String?
        get() {
            val ip = localIp ?: return null
            val prefix = prefixLength ?: return null
            val net = SubnetCalculator.networkAddress(ip, prefix) ?: return null
            return "$net/$prefix@${gateway ?: "-"}"
        }

    companion object {
        val Disconnected = NetworkSnapshot(connected = false, transport = Transport.NONE)
    }
}

/** Lê IP, máscara, gateway, DNS e SSID usando apenas APIs públicas do Android. */
class NetworkInfoProvider(private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun current(): NetworkSnapshot {
        val network = cm.activeNetwork ?: return NetworkSnapshot.Disconnected
        return build(network, cm.getNetworkCapabilities(network), cm.getLinkProperties(network))
    }

    /** Emite um novo retrato a cada mudança de rede. */
    fun observe(): Flow<NetworkSnapshot> = callbackFlow {
        var caps: NetworkCapabilities? = null
        var link: LinkProperties? = null
        var net: Network? = null

        fun emitCurrent() {
            val n = net
            trySend(if (n == null) NetworkSnapshot.Disconnected else build(n, caps, link))
        }

        val listener = object : CallbackListener {
            override fun onAvailable(network: Network) {
                net = network
                caps = cm.getNetworkCapabilities(network)
                link = cm.getLinkProperties(network)
                emitCurrent()
            }

            override fun onCapabilities(network: Network, c: NetworkCapabilities) {
                net = network; caps = c; emitCurrent()
            }

            override fun onLink(network: Network, l: LinkProperties) {
                net = network; link = l; emitCurrent()
            }

            override fun onLost(network: Network) {
                if (net == network) {
                    net = null; caps = null; link = null
                }
                emitCurrent()
            }
        }
        val callback = makeCallback(listener)
        trySend(current())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.conflate().distinctUntilChanged()

    private interface CallbackListener {
        fun onAvailable(network: Network)
        fun onCapabilities(network: Network, c: NetworkCapabilities)
        fun onLink(network: Network, l: LinkProperties)
        fun onLost(network: Network)
    }

    private fun makeCallback(l: CallbackListener): ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // FLAG_INCLUDE_LOCATION_INFO é necessário para o Android 12+ entregar o SSID no WifiInfo.
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onAvailable(network: Network) = l.onAvailable(network)
                override fun onCapabilitiesChanged(network: Network, c: NetworkCapabilities) = l.onCapabilities(network, c)
                override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = l.onLink(network, lp)
                override fun onLost(network: Network) = l.onLost(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = l.onAvailable(network)
                override fun onCapabilitiesChanged(network: Network, c: NetworkCapabilities) = l.onCapabilities(network, c)
                override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = l.onLink(network, lp)
                override fun onLost(network: Network) = l.onLost(network)
            }
        }

    private fun build(network: Network, caps: NetworkCapabilities?, link: LinkProperties?): NetworkSnapshot {
        val transport = when {
            caps == null -> Transport.OTHER
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Transport.VPN
            else -> Transport.OTHER
        }
        val ipv4 = link?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
        val gateway = link?.routes
            ?.firstOrNull { r -> r.isDefaultRoute && (r.gateway as? Inet4Address)?.isAnyLocalAddress == false }
            ?.gateway?.hostAddress
        val dns = link?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        var ssid: String? = null
        var ssidStatus = SsidStatus.NOT_WIFI
        var linkSpeed: Int? = null
        var rssi: Int? = null
        if (transport == Transport.WIFI) {
            val info = wifiInfo(caps)
            if (info != null) {
                linkSpeed = info.linkSpeed.takeIf { it > 0 }
                rssi = info.rssi.takeIf { it in -127..0 && it != -127 }
            }
            val raw = info?.ssid?.removeSurrounding("\"")
            ssid = raw?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            ssidStatus = when {
                ssid != null -> SsidStatus.OK
                !hasLocationPermission() -> SsidStatus.NEED_PERMISSION
                !isLocationEnabled() -> SsidStatus.LOCATION_OFF
                else -> SsidStatus.UNAVAILABLE
            }
        }

        return NetworkSnapshot(
            connected = true,
            transport = transport,
            validated = validated,
            ssid = ssid,
            ssidStatus = ssidStatus,
            localIp = ipv4?.address?.hostAddress,
            prefixLength = ipv4?.prefixLength,
            gateway = gateway,
            dns = dns,
            interfaceName = link?.interfaceName,
            linkSpeedMbps = linkSpeed,
            rssi = rssi,
        )
    }

    private fun hasRealSsid(info: WifiInfo): Boolean {
        val ssid = info.ssid?.removeSurrounding("\"") ?: return false
        return ssid.isNotBlank() && ssid != "<unknown ssid>"
    }

    @Suppress("DEPRECATION")
    private fun wifiInfo(caps: NetworkCapabilities?): WifiInfo? {
        val fromCaps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) caps?.transportInfo as? WifiInfo else null
        if (fromCaps != null && hasRealSsid(fromCaps)) return fromCaps
        // Fallback: o WifiInfo das capacidades pode vir sem SSID (redigido); o WifiManager ainda o entrega
        // quando há permissão de localização.
        val legacy = runCatching { wifi?.connectionInfo }.getOrNull()
        return if (legacy != null && (fromCaps == null || hasRealSsid(legacy))) legacy else fromCaps
    }
}
