package com.rainhacarmesim.app.data.repo

import com.rainhacarmesim.app.data.db.AlertEntity
import com.rainhacarmesim.app.network.DeviceScanner
import com.rainhacarmesim.app.network.NetworkInfoProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(
        val stage: String,
        val done: Int,
        val total: Int,
        val found: Int,
        val automatic: Boolean,
    ) : ScanState {
        val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
    }
    data class Finished(val summary: ScanSummary) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * Ponto único para iniciar varreduras (manuais ou automáticas), evitando execuções simultâneas,
 * e para publicar os alertas gerados enquanto o app está aberto.
 */
class ScanCoordinator(
    private val networkInfo: NetworkInfoProvider,
    private val scanner: DeviceScanner,
    private val repository: DeviceRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var manualJob: Job? = null

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _alerts = MutableSharedFlow<List<AlertEntity>>(extraBufferCapacity = 8)
    /** Alertas recém-gerados, para exibição imediata (snackbar). */
    val newAlerts: SharedFlow<List<AlertEntity>> = _alerts.asSharedFlow()

    @Volatile
    private var lastFinishedAt = 0L

    val isRunning: Boolean get() = _state.value is ScanState.Running

    fun startManualScan() {
        if (isRunning) return
        manualJob = scope.launch { runScan(automatic = false) }
    }

    fun cancel() {
        manualJob?.cancel()
    }

    /**
     * Laço de varredura automática. Deve ser chamado de dentro de um escopo atrelado ao ciclo de vida
     * da tela (app em primeiro plano): ao sair do app, o laço é cancelado. Nada roda em segundo plano.
     */
    suspend fun runAutoScanLoop(settings: SettingsStore) {
        combine(settings.autoScanEnabled, settings.autoScanMinutes) { enabled, minutes -> enabled to minutes }
            .collectLatest { (enabled, minutes) ->
                if (!enabled) return@collectLatest
                val intervalMs = minutes.coerceAtLeast(1) * 60_000L
                while (true) {
                    val wait = lastFinishedAt + intervalMs - System.currentTimeMillis()
                    if (wait > 0) delay(wait)
                    if (networkInfo.current().canScanLan && !isRunning) {
                        runScan(automatic = true)
                        if (_state.value is ScanState.Failed) lastFinishedAt = System.currentTimeMillis()
                    } else {
                        delay(intervalMs)
                    }
                }
            }
    }

    private suspend fun runScan(automatic: Boolean) {
        if (!mutex.tryLock()) return
        try {
            val snapshot = networkInfo.current()
            val key = snapshot.networkKey
            if (!snapshot.canScanLan || key == null) {
                _state.value = ScanState.Failed("Conecte-se a uma rede Wi-Fi (ou cabeada) para varrer a rede local.")
                return
            }
            _state.value = ScanState.Running("Sondando endereços", 0, 0, 0, automatic)
            val outcome = scanner.scan(snapshot) { phase ->
                _state.value = when (phase) {
                    is DeviceScanner.Phase.Probing ->
                        ScanState.Running("Sondando endereços", phase.done, phase.total, phase.found, automatic)
                    is DeviceScanner.Phase.Resolving -> {
                        val prev = _state.value as? ScanState.Running
                        ScanState.Running("Identificando nomes", phase.done, phase.total, prev?.found ?: 0, automatic)
                    }
                }
            }
            val summary = repository.saveScan(key, outcome, automatic)
            lastFinishedAt = summary.finishedAt
            _state.value = ScanState.Finished(summary)
            if (summary.alerts.isNotEmpty()) _alerts.tryEmit(summary.alerts)
        } catch (e: CancellationException) {
            _state.value = ScanState.Idle
            throw e
        } catch (e: Exception) {
            _state.value = ScanState.Failed("Falha na varredura: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            mutex.unlock()
        }
    }
}
