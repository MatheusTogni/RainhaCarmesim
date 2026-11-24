package com.rainhacarmesim.app.data.repo

import com.rainhacarmesim.app.network.TrafficSampler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Amostra o tráfego do próprio celular a cada segundo enquanto o app está aberto e grava
 * agregados por minuto no banco local.
 */
class TrafficMonitor(
    val sampler: TrafficSampler,
    private val repository: TrafficRepository,
) {
    suspend fun run() {
        sampler.reset()
        runCatching { repository.prune(System.currentTimeMillis()) }
        var minute = -1L
        var rx = 0L
        var tx = 0L
        try {
            while (true) {
                val delta = sampler.sample()
                if (delta != null) {
                    val m = delta.timestamp - delta.timestamp % 60_000L
                    if (minute >= 0 && m != minute) {
                        runCatching { repository.addSample(minute, rx, tx) }
                        rx = 0L
                        tx = 0L
                    }
                    minute = m
                    rx += delta.rxBytes
                    tx += delta.txBytes
                }
                delay(1_000)
            }
        } finally {
            if (minute >= 0) {
                withContext(NonCancellable) { runCatching { repository.addSample(minute, rx, tx) } }
            }
            sampler.reset()
        }
    }
}
