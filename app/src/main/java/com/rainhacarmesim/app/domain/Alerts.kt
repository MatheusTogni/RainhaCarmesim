package com.rainhacarmesim.app.domain

enum class AlertType(val title: String) {
    NEW_DEVICE("Novo dispositivo na rede"),
    RETURNED("Dispositivo voltou à rede"),
    DISCONNECTED("Dispositivo se desconectou");

    companion object {
        fun from(name: String): AlertType? = entries.firstOrNull { it.name == name }
    }
}

/** Regras anti-repetição de alertas. */
object AlertPolicy {
    /** Intervalo mínimo entre dois alertas do mesmo tipo para o mesmo dispositivo. */
    const val COOLDOWN_MS = 10 * 60 * 1000L

    fun shouldEmit(lastSameAlertAt: Long?, now: Long): Boolean =
        lastSameAlertAt == null || now - lastSameAlertAt >= COOLDOWN_MS
}
