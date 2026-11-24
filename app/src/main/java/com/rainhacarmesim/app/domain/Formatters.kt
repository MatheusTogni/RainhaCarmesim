package com.rainhacarmesim.app.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Formatação de unidades e datas exibidas na interface. */
object Formatters {
    private val ptBR = Locale("pt", "BR")

    /** Taxa em bytes/s formatada em bits (padrão de redes): bps, Kbps, Mbps, Gbps. */
    fun bitRate(bytesPerSecond: Double): Pair<String, String> {
        val bits = bytesPerSecond * 8.0
        return when {
            bits >= 1e9 -> number(bits / 1e9) to "Gbps"
            bits >= 1e6 -> number(bits / 1e6) to "Mbps"
            bits >= 1e3 -> number(bits / 1e3) to "Kbps"
            else -> number(bits, decimals = 0) to "bps"
        }
    }

    fun bitRateText(bytesPerSecond: Double): String = bitRate(bytesPerSecond).let { "${it.first} ${it.second}" }

    /** Mbps (já em megabits) para texto. */
    fun mbps(value: Double): String = number(value)

    /** Quantidade de dados em bytes: B, KB, MB, GB, TB (base 1024). */
    fun bytes(value: Long): String {
        val v = abs(value.toDouble())
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var scaled = v
        var i = 0
        while (scaled >= 1024.0 && i < units.lastIndex) {
            scaled /= 1024.0
            i++
        }
        val text = if (i == 0) number(scaled, 0) else number(scaled)
        return "$text ${units[i]}"
    }

    /** Até 3 algarismos significativos, com vírgula decimal. */
    fun number(value: Double, decimals: Int = -1): String {
        val d = if (decimals >= 0) decimals else when {
            value >= 100 -> 0
            value >= 10 -> 1
            else -> 2
        }
        return String.format(ptBR, "%.${d}f", value)
    }

    fun millis(ms: Double): String = "${number(ms, if (ms >= 100) 0 else 1)} ms"

    fun dateTime(epochMs: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", ptBR).format(Date(epochMs))

    fun shortDateTime(epochMs: Long): String =
        SimpleDateFormat("dd/MM HH:mm", ptBR).format(Date(epochMs))

    fun time(epochMs: Long): String = SimpleDateFormat("HH:mm", ptBR).format(Date(epochMs))

    fun dayLabel(epochMs: Long): String = SimpleDateFormat("dd/MM", ptBR).format(Date(epochMs))

    /** "há 3 min", "há 2 h", ... */
    fun relative(epochMs: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - epochMs).coerceAtLeast(0) / 1000
        return when {
            diff < 60 -> "agora há pouco"
            diff < 3600 -> "há ${diff / 60} min"
            diff < 86400 -> "há ${diff / 3600} h"
            else -> "há ${diff / 86400} d"
        }
    }
}
