package com.rainhacarmesim.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.random.Random

/**
 * Teste de velocidade real contra a infraestrutura pública da Cloudflare (speed.cloudflare.com),
 * via HTTPS. Mede latência (mediana de requisições vazias), download e upload (vazão agregada
 * de conexões paralelas por tempo limitado e com teto de dados).
 */
class SpeedTestClient(private val userAgent: String) {

    companion object {
        const val SERVER = "speed.cloudflare.com"
        private const val BASE = "https://speed.cloudflare.com"
        private const val TEST_DURATION_MS = 10_000L
        private const val WARMUP_MS = 1_000L
        private const val MAX_DOWNLOAD_BYTES = 250L * 1024 * 1024
        private const val MAX_UPLOAD_BYTES = 120L * 1024 * 1024
        private const val CONNECTIONS = 4
    }

    enum class Stage { LATENCY, DOWNLOAD, UPLOAD, DONE }

    data class Progress(val stage: Stage, val currentMbps: Double?, val fraction: Float)

    data class Result(
        val latencyMs: Double,
        val jitterMs: Double,
        val downloadMbps: Double,
        val uploadMbps: Double,
        val downloadedBytes: Long,
        val uploadedBytes: Long,
    )

    suspend fun run(onProgress: (Progress) -> Unit): Result = withContext(Dispatchers.IO) {
        onProgress(Progress(Stage.LATENCY, null, 0f))
        val (latency, jitter) = measureLatency()
        val (down, downBytes) = measureThroughput(Stage.DOWNLOAD, MAX_DOWNLOAD_BYTES, onProgress) { counter, stop ->
            downloadLoop(counter, stop)
        }
        val (up, upBytes) = measureThroughput(Stage.UPLOAD, MAX_UPLOAD_BYTES, onProgress) { counter, stop ->
            uploadLoop(counter, stop)
        }
        onProgress(Progress(Stage.DONE, null, 1f))
        Result(latency, jitter, down, up, downBytes, upBytes)
    }

    private fun open(path: String): HttpURLConnection {
        val c = URL("$BASE$path").openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        c.useCaches = false
        c.setRequestProperty("User-Agent", userAgent)
        c.setRequestProperty("Accept-Encoding", "identity")
        return c
    }

    private suspend fun measureLatency(): Pair<Double, Double> {
        // Primeira requisição abre TLS e não entra na conta.
        singleEmptyRequest()
        val samples = mutableListOf<Double>()
        repeat(10) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            runCatching { singleEmptyRequest() }.getOrNull()?.let { samples += it }
        }
        if (samples.isEmpty()) throw IOException("Sem resposta do servidor de teste")
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        val jitter = if (samples.size < 2) 0.0 else samples.zipWithNext { a, b -> abs(a - b) }.average()
        return median to jitter
    }

    /** Mede o tempo até o primeiro byte de uma resposta vazia (aproximação de RTT HTTP). */
    private fun singleEmptyRequest(): Double {
        val c = open("/__down?bytes=0")
        try {
            val start = System.nanoTime()
            val code = c.responseCode
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            if (code !in 200..299) throw IOException("Servidor respondeu HTTP $code")
            c.inputStream.use { it.readBytes() }
            return elapsed
        } finally {
            c.disconnect()
        }
    }

    private suspend fun measureThroughput(
        stage: Stage,
        maxBytes: Long,
        onProgress: (Progress) -> Unit,
        worker: suspend (AtomicLong, () -> Boolean) -> Unit,
    ): Pair<Double, Long> = coroutineScope {
        val counter = AtomicLong(0)
        val error = AtomicReference<Throwable?>(null)
        val start = System.nanoTime()
        fun elapsedMs() = (System.nanoTime() - start) / 1_000_000
        val stop = { elapsedMs() >= TEST_DURATION_MS || counter.get() >= maxBytes || error.get() != null }

        val workers = (1..CONNECTIONS).map {
            async {
                try {
                    worker(counter, stop)
                } catch (e: IOException) {
                    error.compareAndSet(null, e)
                }
            }
        }

        var warmupBytes = 0L
        var warmupAt = -1L
        val reporter = launch {
            var lastBytes = 0L
            var lastTime = System.nanoTime()
            while (isActive && !stop()) {
                delay(250)
                val now = System.nanoTime()
                val bytes = counter.get()
                val instant = (bytes - lastBytes) * 8.0 / ((now - lastTime) / 1e9) / 1e6
                lastBytes = bytes
                lastTime = now
                if (warmupAt < 0 && elapsedMs() >= WARMUP_MS) {
                    warmupAt = elapsedMs()
                    warmupBytes = bytes
                }
                onProgress(Progress(stage, instant, (elapsedMs().toFloat() / TEST_DURATION_MS).coerceIn(0f, 1f)))
            }
        }
        workers.awaitAll()
        reporter.cancel()

        val total = counter.get()
        val elapsed = elapsedMs()
        error.get()?.let { if (total == 0L) throw it }
        if (total == 0L || elapsed <= 0) throw IOException("Nenhum dado transferido")
        // Descarta o primeiro segundo (abertura de conexões / slow start) quando possível.
        val mbps = if (warmupAt in 1 until elapsed && total > warmupBytes) {
            (total - warmupBytes) * 8.0 / ((elapsed - warmupAt) / 1000.0) / 1e6
        } else {
            total * 8.0 / (elapsed / 1000.0) / 1e6
        }
        mbps to total
    }

    private suspend fun downloadLoop(counter: AtomicLong, stop: () -> Boolean) {
        val buffer = ByteArray(64 * 1024)
        while (!stop()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val c = open("/__down?bytes=${25 * 1024 * 1024}")
            try {
                val code = c.responseCode
                if (code !in 200..299) throw IOException("Servidor respondeu HTTP $code")
                c.inputStream.use { input ->
                    while (!stop()) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        counter.addAndGet(n.toLong())
                    }
                }
            } finally {
                c.disconnect()
            }
        }
    }

    private suspend fun uploadLoop(counter: AtomicLong, stop: () -> Boolean) {
        val chunk = ByteArray(64 * 1024).also { Random.nextBytes(it) }
        val bodySize = 8 * 1024 * 1024
        while (!stop()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val c = open("/__up")
            try {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setFixedLengthStreamingMode(bodySize)
                c.setRequestProperty("Content-Type", "application/octet-stream")
                var sent = 0
                var aborted = false
                c.outputStream.use { out ->
                    while (sent < bodySize) {
                        if (stop()) {
                            aborted = true
                            break
                        }
                        val n = minOf(chunk.size, bodySize - sent)
                        out.write(chunk, 0, n)
                        sent += n
                        counter.addAndGet(n.toLong())
                    }
                }
                if (aborted) return
                val code = c.responseCode
                if (code !in 200..299) throw IOException("Servidor respondeu HTTP $code")
                c.inputStream.use { it.readBytes() }
            } catch (e: IOException) {
                // Encerrar o corpo antes do fim gera erro esperado ao interromper o teste.
                if (!stop()) throw e
                return
            } finally {
                c.disconnect()
            }
        }
    }
}
