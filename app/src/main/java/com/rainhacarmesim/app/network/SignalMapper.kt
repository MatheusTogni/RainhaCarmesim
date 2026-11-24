package com.rainhacarmesim.app.network

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.rainhacarmesim.app.domain.MapPoint
import com.rainhacarmesim.app.domain.SignalGrid
import com.rainhacarmesim.app.domain.SignalSample
import com.rainhacarmesim.app.domain.StepDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mapeador de sinal Wi-Fi: estima onde o usuário está andando (contagem de passos pelo
 * acelerômetro + direção pelo sensor de rotação) e associa o RSSI do Wi-Fi conectado a
 * cada posição. A posição é relativa ao ponto de partida, com a direção inicial para cima.
 *
 * Os callbacks de sensores rodam na thread principal, a mesma do [CoroutineScope] usado em
 * [start] (viewModelScope), então o estado interno não precisa de sincronização.
 */
class SignalMapper(context: Context, private val networkInfo: NetworkInfoProvider) {

    data class State(
        val running: Boolean = false,
        val position: MapPoint = MapPoint(0f, 0f),
        /** Trechos do caminho percorrido (uma correção manual de posição inicia um novo trecho). */
        val path: List<List<MapPoint>> = listOf(listOf(MapPoint(0f, 0f))),
        val samples: List<SignalSample> = emptyList(),
        /** RSSI mais recente, ou null quando não está no Wi-Fi. */
        val rssi: Int? = null,
        val onWifi: Boolean = true,
        val steps: Int = 0,
        val distanceM: Float = 0f,
        val stepLengthM: Float = DEFAULT_STEP_M,
    )

    companion object {
        const val DEFAULT_STEP_M = 0.7f
        private const val RSSI_INTERVAL_MS = 700L
        /** Uma leitura de RSSI vale para os passos dados até este tempo depois dela. */
        private const val RSSI_FRESH_MS = 2_500L
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    // O vetor de rotação "de jogo" ignora o magnetômetro, que é muito distorcido dentro de casa.
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** Sem estes sensores, a posição só muda tocando no mapa. */
    val hasMotionSensors: Boolean = accelerometer != null && rotationSensor != null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Direção atual relativa à inicial, em graus (0 = para cima no mapa, sentido horário). */
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    private val grid = SignalGrid()
    private val stepDetector = StepDetector()
    private val segments = mutableListOf(mutableListOf(MapPoint(0f, 0f)))
    private var x = 0f
    private var y = 0f
    private var steps = 0
    private var distance = 0f
    private var stepLength = DEFAULT_STEP_M

    private val rotation = FloatArray(9)
    private var headX = 0f
    private var headY = 0f
    private var headingRad = Float.NaN
    private var heading0 = Float.NaN

    private var lastRssi: Int? = null
    private var lastRssiAt = 0L
    private var onWifi = true
    private var pollJob: Job? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER ->
                    if (stepDetector.onSample(e.values[0], e.values[1], e.values[2], e.timestamp / 1_000_000)) onStep()
                else -> onRotation(e.values)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(scope: CoroutineScope) {
        if (pollJob?.isActive == true) return
        stepDetector.reset()
        accelerometer?.let { sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        rotationSensor?.let { sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        pollJob = scope.launch {
            while (isActive) {
                sampleRssi()
                delay(RSSI_INTERVAL_MS)
            }
        }
        publish()
    }

    fun stop() {
        sensorManager?.unregisterListener(listener)
        pollJob?.cancel()
        pollJob = null
        publish()
    }

    /** Apaga o mapa. A direção atual do celular passa a ser "para cima". */
    fun clear() {
        grid.clear()
        segments.clear()
        segments += mutableListOf(MapPoint(0f, 0f))
        x = 0f; y = 0f; steps = 0; distance = 0f
        heading0 = headingRad
        _heading.value = 0f
        publish()
    }

    fun setStepLength(meters: Float) {
        stepLength = meters
        publish()
    }

    /** Correção manual: o usuário indica no mapa onde está de verdade. */
    fun moveTo(nx: Float, ny: Float) {
        x = nx; y = ny
        val last = segments.last()
        if (last.size <= 1) last.clear()
        if (last.isNotEmpty()) segments += mutableListOf()
        segments.last() += MapPoint(x, y)
        recordIfFresh()
        publish()
    }

    private fun onRotation(values: FloatArray) {
        SensorManager.getRotationMatrixFromVector(rotation, values)
        // Para onde o usuário está virado: a projeção horizontal do topo do celular (segurando
        // deitado) somada à da traseira (segurando em pé). Funciona em qualquer inclinação.
        val hx = rotation[1] - rotation[2]
        val hy = rotation[4] - rotation[5]
        if (hx * hx + hy * hy < 1e-4f) return
        // Suaviza como vetor (evita o salto de -180° para 180°).
        headX = headX * 0.7f + hx * 0.3f
        headY = headY * 0.7f + hy * 0.3f
        headingRad = atan2(headX, headY)
        if (heading0.isNaN()) heading0 = headingRad
        _heading.value = Math.toDegrees((headingRad - heading0).toDouble()).toFloat()
    }

    private fun onStep() {
        if (headingRad.isNaN()) return
        val rel = headingRad - heading0
        x += stepLength * sin(rel)
        y += stepLength * cos(rel)
        steps++
        distance += stepLength
        segments.last() += MapPoint(x, y)
        recordIfFresh()
        publish()
    }

    private fun sampleRssi() {
        val rssi = networkInfo.currentWifiRssi()
        onWifi = rssi != null
        lastRssi = rssi
        if (rssi != null) {
            lastRssiAt = SystemClock.elapsedRealtime()
            grid.add(x, y, rssi)
        }
        publish()
    }

    private fun recordIfFresh() {
        val rssi = lastRssi ?: return
        if (SystemClock.elapsedRealtime() - lastRssiAt <= RSSI_FRESH_MS) grid.add(x, y, rssi)
    }

    private fun publish() {
        _state.value = State(
            running = pollJob?.isActive == true,
            position = MapPoint(x, y),
            path = segments.map { it.toList() },
            samples = grid.samples(),
            rssi = lastRssi,
            onWifi = onWifi,
            steps = steps,
            distanceM = distance,
            stepLengthM = stepLength,
        )
    }
}
