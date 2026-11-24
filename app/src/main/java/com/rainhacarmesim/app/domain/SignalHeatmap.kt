package com.rainhacarmesim.app.domain

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Ponto no plano do mapa, em metros. X cresce para a direita e Y para "frente" (topo do mapa). */
data class MapPoint(val x: Float, val y: Float)

/** Medição de sinal: média do RSSI (dBm) de uma célula do mapa, centrada em (x, y). */
data class SignalSample(val x: Float, val y: Float, val rssi: Float, val count: Int)

/** Retângulo do mapa em metros. */
data class MapBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
}

/**
 * Agrupa as leituras numa grade de [cellSize] metros e guarda a média de cada célula.
 * Assim, ficar parado no mesmo lugar não "pesa" mais na interpolação do que andar.
 */
class SignalGrid(private val cellSize: Float = 0.5f) {
    private val sum = HashMap<Long, Double>()
    private val count = HashMap<Long, Int>()

    private fun key(ix: Int, iy: Int): Long = (ix.toLong() shl 32) or (iy.toLong() and 0xFFFFFFFFL)

    fun add(x: Float, y: Float, rssi: Int) {
        val k = key(floor(x / cellSize).toInt(), floor(y / cellSize).toInt())
        sum[k] = (sum[k] ?: 0.0) + rssi
        count[k] = (count[k] ?: 0) + 1
    }

    fun clear() {
        sum.clear()
        count.clear()
    }

    val isEmpty: Boolean get() = count.isEmpty()

    fun samples(): List<SignalSample> = count.map { (k, n) ->
        val ix = (k shr 32).toInt()
        val iy = k.toInt()
        SignalSample(
            x = (ix + 0.5f) * cellSize,
            y = (iy + 0.5f) * cellSize,
            rssi = (sum.getValue(k) / n).toFloat(),
            count = n,
        )
    }
}

object SignalHeatmap {
    /** Além desta distância (m) de qualquer medição, o mapa fica transparente: não há dado. */
    const val MAX_RADIUS_M = 3f
    /** Até esta distância (m) a cor é opaca; depois esmaece até [MAX_RADIUS_M]. */
    private const val SOLID_RADIUS_M = 1f

    /** Escala de cores: do sinal fraco (vermelho) ao excelente (verde). */
    const val SCALE_MIN_DBM = -90f
    const val SCALE_MAX_DBM = -50f
    private val stops = listOf(
        -90f to 0xFFFF3B3B.toInt(),
        -80f to 0xFFFF8A00.toInt(),
        -70f to 0xFFFFD60A.toInt(),
        -60f to 0xFF9BE15D.toInt(),
        -50f to 0xFF3DDC84.toInt(),
    )

    /** Cor ARGB (opaca) para um RSSI em dBm. */
    fun colorFor(rssi: Float): Int {
        val v = rssi.coerceIn(SCALE_MIN_DBM, SCALE_MAX_DBM)
        for (i in 0 until stops.size - 1) {
            val (a, ca) = stops[i]
            val (b, cb) = stops[i + 1]
            if (v <= b) return lerpColor(ca, cb, (v - a) / (b - a))
        }
        return stops.last().second
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun mix(shift: Int) = (ch(a, shift) + (ch(b, shift) - ch(a, shift)) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /**
     * Interpolação por inverso do quadrado da distância (IDW) em (x, y).
     * Retorna o RSSI estimado e a distância até a medição mais próxima, ou null sem medições no raio.
     */
    fun interpolate(samples: List<SignalSample>, x: Float, y: Float, maxRadius: Float = MAX_RADIUS_M): Pair<Float, Float>? {
        val r2 = maxRadius * maxRadius
        var wSum = 0.0
        var vSum = 0.0
        var nearest2 = Float.MAX_VALUE
        for (s in samples) {
            val dx = s.x - x
            val dy = s.y - y
            val d2 = dx * dx + dy * dy
            if (d2 > r2) continue
            if (d2 < nearest2) nearest2 = d2
            val w = 1.0 / max(d2, 0.01f)
            wSum += w
            vSum += w * s.rssi
        }
        if (wSum == 0.0) return null
        return (vSum / wSum).toFloat() to sqrt(nearest2)
    }

    /**
     * Gera os pixels ARGB (largura [w] x altura [h]) do mapa de calor cobrindo [bounds].
     * A linha 0 é o topo do mapa (maior Y).
     */
    fun render(samples: List<SignalSample>, bounds: MapBounds, w: Int, h: Int): IntArray {
        val pixels = IntArray(w * h)
        if (samples.isEmpty() || w <= 0 || h <= 0) return pixels
        for (j in 0 until h) {
            val y = bounds.maxY - (j + 0.5f) / h * bounds.height
            for (i in 0 until w) {
                val x = bounds.minX + (i + 0.5f) / w * bounds.width
                val (rssi, dist) = interpolate(samples, x, y) ?: continue
                val fade = if (dist <= SOLID_RADIUS_M) 1f
                else 1f - (dist - SOLID_RADIUS_M) / (MAX_RADIUS_M - SOLID_RADIUS_M)
                val alpha = (fade.coerceIn(0f, 1f) * 0.85f * 255).roundToInt()
                pixels[j * w + i] = (alpha shl 24) or (colorFor(rssi) and 0x00FFFFFF)
            }
        }
        return pixels
    }

    /**
     * Área a mostrar: envolve todos os pontos com folga, tem pelo menos [minSpan] metros
     * e segue a proporção [aspect] (largura / altura) da tela, centralizada no conteúdo.
     */
    fun boundsFor(points: List<MapPoint>, aspect: Float, padding: Float = 1.5f, minSpan: Float = 8f): MapBounds {
        var minX = 0f
        var maxX = 0f
        var minY = 0f
        var maxY = 0f
        if (points.isNotEmpty()) {
            minX = points.minOf { it.x }; maxX = points.maxOf { it.x }
            minY = points.minOf { it.y }; maxY = points.maxOf { it.y }
        }
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        var spanX = max(maxX - minX + 2 * padding, minSpan)
        var spanY = max(maxY - minY + 2 * padding, minSpan)
        val a = if (aspect > 0f) aspect else 1f
        if (spanX / spanY < a) spanX = spanY * a else spanY = spanX / a
        return MapBounds(cx - spanX / 2f, cy - spanY / 2f, cx + spanX / 2f, cy + spanY / 2f)
    }

    /** Resumo das medições: (pior, média, melhor) em dBm. */
    fun summary(samples: List<SignalSample>): Triple<Float, Float, Float>? {
        if (samples.isEmpty()) return null
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        var sum = 0.0
        for (s in samples) {
            lo = min(lo, s.rssi)
            hi = max(hi, s.rssi)
            sum += s.rssi
        }
        return Triple(lo, (sum / samples.size).toFloat(), hi)
    }
}

/**
 * Detector de passos pelo acelerômetro (não exige permissão de atividade física).
 * Separa a gravidade com um filtro lento e conta um passo a cada pico da aceleração
 * dinâmica acima de [highThreshold], exigindo que ela volte abaixo de [lowThreshold]
 * e um intervalo mínimo entre passos.
 */
class StepDetector(
    private val highThreshold: Float = 1.2f,
    private val lowThreshold: Float = 0.3f,
    private val minIntervalMs: Long = 280,
) {
    private var gravity = Float.NaN
    private var smooth = 0f
    private var armed = true
    private var lastStepMs = Long.MIN_VALUE / 2

    /** Processa uma leitura do acelerômetro (m/s²). Retorna true quando detecta um passo. */
    fun onSample(ax: Float, ay: Float, az: Float, timeMs: Long): Boolean {
        val mag = sqrt(ax * ax + ay * ay + az * az)
        gravity = if (gravity.isNaN()) mag else gravity * 0.95f + mag * 0.05f
        smooth = smooth * 0.6f + (mag - gravity) * 0.4f
        if (!armed) {
            if (smooth < lowThreshold) armed = true
            return false
        }
        if (smooth > highThreshold && timeMs - lastStepMs >= minIntervalMs) {
            armed = false
            lastStepMs = timeMs
            return true
        }
        return false
    }

    fun reset() {
        gravity = Float.NaN
        smooth = 0f
        armed = true
        lastStepMs = Long.MIN_VALUE / 2
    }
}
