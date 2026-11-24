package com.rainhacarmesim.app.network

import com.rainhacarmesim.app.domain.SubnetCalculator

/** Validação de alvo digitado pelo usuário (IP ou hostname). */
object InputValidator {
    private val labelRegex = Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
    private val ipv6Regex = Regex("^[0-9A-Fa-f:]+$")

    /** Devolve o alvo normalizado ou null se inválido. Nunca aceita algo que comece com '-'. */
    fun normalizeTarget(input: String): String? {
        val t = input.trim().trimEnd('.')
        if (t.isEmpty() || t.startsWith("-") || t.length > 253) return null
        if (SubnetCalculator.isValidIpv4(t)) return t
        if (t.count { it == ':' } >= 2) return if (ipv6Regex.matches(t)) t else null
        if (t.all { it.isDigit() || it == '.' }) return null // IPv4 malformado
        val labels = t.split('.')
        return if (labels.all { labelRegex.matches(it) }) t else null
    }
}
