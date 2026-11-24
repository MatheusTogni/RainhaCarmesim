package com.rainhacarmesim.app.network

import android.net.TrafficStats
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Taxa instantânea do PRÓPRIO celular (todas as interfaces), em bytes por segundo. */
data class TrafficRate(val rxBytesPerSec: Double, val txBytesPerSec: Double, val timestamp: Long)

data class TrafficDelta(val rxBytes: Long, val txBytes: Long, val timestamp: Long)

/**
 * Lê os contadores do sistema ([TrafficStats]). Não enxerga o tráfego de outros aparelhos da rede:
 * o Android só expõe os contadores do próprio dispositivo.
 */
class TrafficSampler(private val historySize: Int = HISTORY_SIZE) {
    companion object {
        /** Amostras (uma por segundo) mantidas para o gráfico ao vivo. */
        const val HISTORY_SIZE = 60
    }

    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L

    private val _current = MutableStateFlow<TrafficRate?>(null)
    val current: StateFlow<TrafficRate?> = _current.asStateFlow()

    private val _history = MutableStateFlow<List<TrafficRate>>(emptyList())
    val history: StateFlow<List<TrafficRate>> = _history.asStateFlow()

    private val _supported = MutableStateFlow(true)
    val supported: StateFlow<Boolean> = _supported.asStateFlow()

    /** Faz uma leitura. Retorna o delta de bytes desde a última leitura (para persistência). */
    fun sample(): TrafficDelta? {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            _supported.value = false
            return null
        }
        _supported.value = true
        val delta = if (lastRx >= 0 && lastAt > 0) {
            val seconds = (now - lastAt) / 1000.0
            val dRx = (rx - lastRx).coerceAtLeast(0)
            val dTx = (tx - lastTx).coerceAtLeast(0)
            if (seconds > 0) {
                val rate = TrafficRate(dRx / seconds, dTx / seconds, System.currentTimeMillis())
                _current.value = rate
                _history.value = (_history.value + rate).takeLast(historySize)
            }
            TrafficDelta(dRx, dTx, System.currentTimeMillis())
        } else null
        lastRx = rx
        lastTx = tx
        lastAt = now
        return delta
    }

    /** Ao pausar a amostragem, a próxima leitura não deve gerar uma taxa com o intervalo parado. */
    fun reset() {
        lastRx = -1L
        lastTx = -1L
        lastAt = 0L
        _current.value = null
    }
}
