package com.rainhacarmesim.app.network

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * Identifica o fabricante a partir do prefixo OUI do MAC, usando a base embarcada
 * (assets/oui.txt.gz, derivada da lista pública da IEEE mantida pelo projeto Wireshark).
 */
class VendorLookup(private val context: Context) {
    @Volatile
    private var table: Map<String, String>? = null

    private fun load(): Map<String, String> {
        table?.let { return it }
        synchronized(this) {
            table?.let { return it }
            val map = HashMap<String, String>(48_000)
            runCatching {
                context.assets.open("oui.txt.gz").use { raw ->
                    GZIPInputStream(raw).bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val tab = line.indexOf('\t')
                            if (tab == 6) map[line.substring(0, 6)] = line.substring(7)
                        }
                    }
                }
            }
            table = map
            return map
        }
    }

    /** Retorna o fabricante, "MAC privado (aleatório)" para endereços locais, ou null. */
    fun lookup(mac: String?): String? {
        if (mac == null) return null
        val hex = mac.replace(":", "").replace("-", "").uppercase()
        if (hex.length < 6) return null
        val firstOctet = hex.substring(0, 2).toIntOrNull(16) ?: return null
        if ((firstOctet and 0x02) != 0) return "MAC privado (aleatório)"
        return load()[hex.substring(0, 6)]
    }
}
