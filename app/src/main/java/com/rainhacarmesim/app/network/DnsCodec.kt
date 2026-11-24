package com.rainhacarmesim.app.network

import java.io.ByteArrayOutputStream

/**
 * Codificação mínima de mensagens DNS, suficiente para consultas PTR (DNS reverso e mDNS unicast)
 * e para a consulta de status NetBIOS. Pura, testável sem Android.
 */
object DnsCodec {
    const val TYPE_PTR = 12

    fun reverseName(ipv4: String): String = ipv4.split('.').reversed().joinToString(".") + ".in-addr.arpa"

    fun buildQuery(id: Int, name: String, type: Int, recursionDesired: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.write16(id and 0xFFFF)
        out.write16(if (recursionDesired) 0x0100 else 0x0000)
        out.write16(1) // QDCOUNT
        out.write16(0)
        out.write16(0)
        out.write16(0)
        for (label in name.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.size in 1..63) { "rótulo DNS inválido" }
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        out.write16(type)
        out.write16(1) // IN
        return out.toByteArray()
    }

    /** Retorna o primeiro nome PTR da resposta, ou null. */
    fun parsePtrAnswer(data: ByteArray, length: Int = data.size, expectedId: Int? = null): String? {
        if (length < 12) return null
        val id = u16(data, 0)
        if (expectedId != null && id != (expectedId and 0xFFFF)) return null
        val flags = u16(data, 2)
        if ((flags and 0x8000) == 0) return null // não é resposta
        if ((flags and 0x000F) != 0) return null // RCODE de erro
        val qd = u16(data, 4)
        val an = u16(data, 6)
        var pos = 12
        repeat(qd) {
            pos = skipName(data, pos, length) ?: return null
            pos += 4
        }
        repeat(an) {
            pos = skipName(data, pos, length) ?: return null
            if (pos + 10 > length) return null
            val type = u16(data, pos)
            val rdLength = u16(data, pos + 8)
            val rdStart = pos + 10
            if (rdStart + rdLength > length) return null
            if (type == TYPE_PTR) {
                return readName(data, rdStart, length)?.trimEnd('.')?.takeIf { it.isNotBlank() }
            }
            pos = rdStart + rdLength
        }
        return null
    }

    internal fun skipName(data: ByteArray, start: Int, length: Int): Int? {
        var pos = start
        while (pos < length) {
            val len = data[pos].toInt() and 0xFF
            when {
                len == 0 -> return pos + 1
                (len and 0xC0) == 0xC0 -> return pos + 2
                else -> pos += len + 1
            }
        }
        return null
    }

    internal fun readName(data: ByteArray, start: Int, length: Int): String? {
        val labels = mutableListOf<String>()
        var pos = start
        var jumps = 0
        while (pos < length) {
            val len = data[pos].toInt() and 0xFF
            when {
                len == 0 -> return labels.joinToString(".")
                (len and 0xC0) == 0xC0 -> {
                    if (pos + 1 >= length || ++jumps > 16) return null
                    pos = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                }
                else -> {
                    if (pos + 1 + len > length) return null
                    labels += String(data, pos + 1, len, Charsets.UTF_8)
                    pos += len + 1
                }
            }
        }
        return null
    }

    internal fun u16(data: ByteArray, pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)

    private fun ByteArrayOutputStream.write16(v: Int) {
        write((v shr 8) and 0xFF)
        write(v and 0xFF)
    }

    // ---- NetBIOS (RFC 1002) ----

    /** Consulta "node status" (NBSTAT) para o nome curinga "*". */
    fun buildNetBiosStatusQuery(id: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write16(id and 0xFFFF)
        out.write16(0x0000)
        out.write16(1)
        out.write16(0)
        out.write16(0)
        out.write16(0)
        val raw = ByteArray(16)
        raw[0] = '*'.code.toByte()
        out.write(0x20)
        for (b in raw) {
            val v = b.toInt() and 0xFF
            out.write('A'.code + (v shr 4))
            out.write('A'.code + (v and 0x0F))
        }
        out.write(0)
        out.write16(0x0021) // NBSTAT
        out.write16(0x0001) // IN
        return out.toByteArray()
    }

    /** Extrai o nome da máquina (sufixo 0x00, nome único) de uma resposta NBSTAT. */
    fun parseNetBiosName(data: ByteArray, length: Int = data.size): String? {
        if (length < 12) return null
        val an = u16(data, 6)
        if (an < 1) return null
        var pos = skipName(data, 12, length) ?: return null
        pos += 10 // type, class, ttl, rdlength
        if (pos >= length) return null
        val count = data[pos].toInt() and 0xFF
        pos++
        var fallback: String? = null
        repeat(count) {
            if (pos + 18 > length) return fallback
            val name = String(data, pos, 15, Charsets.US_ASCII).trim { it <= ' ' }
            val suffix = data[pos + 15].toInt() and 0xFF
            val flags = u16(data, pos + 16)
            val group = (flags and 0x8000) != 0
            if (suffix == 0x00 && !group && name.isNotEmpty()) return name
            if (fallback == null && !group && name.isNotEmpty()) fallback = name
            pos += 18
        }
        return fallback
    }
}
