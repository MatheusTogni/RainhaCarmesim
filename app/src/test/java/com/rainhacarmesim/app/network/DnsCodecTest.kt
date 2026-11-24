package com.rainhacarmesim.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsCodecTest {
    @Test fun reverseName() {
        assertEquals("10.0.168.192.in-addr.arpa", DnsCodec.reverseName("192.168.0.10"))
    }

    /** Monta uma resposta a partir da consulta, com um registro PTR usando ponteiro de compressão. */
    private fun ptrResponse(id: Int, target: String): ByteArray {
        val query = DnsCodec.buildQuery(id, DnsCodec.reverseName("192.168.0.10"), DnsCodec.TYPE_PTR)
        val out = ByteArrayOutputStream()
        out.write(query, 0, 2)
        out.write(0x81); out.write(0x80) // resposta, RD+RA, RCODE 0
        out.write(0); out.write(1) // QD
        out.write(0); out.write(1) // AN
        out.write(0); out.write(0); out.write(0); out.write(0)
        out.write(query, 12, query.size - 12)
        out.write(0xC0); out.write(12) // nome = ponteiro para a pergunta
        out.write(0); out.write(12) // PTR
        out.write(0); out.write(1)
        out.write(0); out.write(0); out.write(0x0E); out.write(0x10)
        val rdata = ByteArrayOutputStream()
        target.split('.').forEach { label ->
            rdata.write(label.length)
            rdata.write(label.toByteArray())
        }
        rdata.write(0)
        val bytes = rdata.toByteArray()
        out.write(0); out.write(bytes.size)
        out.write(bytes)
        return out.toByteArray()
    }

    @Test fun parsesPtrAnswer() {
        val response = ptrResponse(0x1234, "notebook.lan")
        assertEquals("notebook.lan", DnsCodec.parsePtrAnswer(response, response.size, 0x1234))
    }

    @Test fun rejectsWrongId() {
        val response = ptrResponse(0x1234, "notebook.lan")
        assertNull(DnsCodec.parsePtrAnswer(response, response.size, 0x9999))
    }

    @Test fun netBiosNameParsing() {
        val out = ByteArrayOutputStream()
        out.write(ByteArray(6)) // id, flags, qd
        out.write(0); out.write(1) // AN
        out.write(ByteArray(4))
        out.write(0x20); out.write(ByteArray(32) { 'A'.code.toByte() }); out.write(0)
        out.write(ByteArray(10)) // type, class, ttl, rdlength
        out.write(2) // nomes
        fun name(n: String, suffix: Int, flags: Int) {
            out.write(n.padEnd(15, ' ').toByteArray(Charsets.US_ASCII))
            out.write(suffix)
            out.write(flags shr 8); out.write(flags and 0xFF)
        }
        name("WORKGROUP", 0x00, 0x8400) // grupo
        name("DESKTOP-ABC", 0x00, 0x0400)
        val bytes = out.toByteArray()
        assertEquals("DESKTOP-ABC", DnsCodec.parseNetBiosName(bytes, bytes.size))
    }
}
