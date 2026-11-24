package com.rainhacarmesim.app.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainhacarmesim.app.AppContainer
import com.rainhacarmesim.app.data.db.SpeedTestEntity
import com.rainhacarmesim.app.network.InputValidator
import com.rainhacarmesim.app.network.PingResult
import com.rainhacarmesim.app.network.SpeedTestClient
import com.rainhacarmesim.app.network.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.UnknownHostException

data class PingReplyUi(val seq: Int, val timeMs: Double?)

data class PingUiState(
    val running: Boolean = false,
    val replies: List<PingReplyUi> = emptyList(),
    val result: PingResult? = null,
    val error: String? = null,
)

sealed interface SpeedUiState {
    data object Idle : SpeedUiState
    data class Running(
        val stage: SpeedTestClient.Stage,
        val currentMbps: Double?,
        val fraction: Float,
        val downloadSeries: List<Float>,
        val uploadSeries: List<Float>,
    ) : SpeedUiState
    data class Done(val result: SpeedTestClient.Result) : SpeedUiState
    data class Error(val message: String) : SpeedUiState
}

class ToolsViewModel(private val c: AppContainer) : ViewModel() {
    private val _ping = MutableStateFlow(PingUiState())
    val ping: StateFlow<PingUiState> = _ping.asStateFlow()
    private var pingJob: Job? = null

    private val _speed = MutableStateFlow<SpeedUiState>(SpeedUiState.Idle)
    val speed: StateFlow<SpeedUiState> = _speed.asStateFlow()
    private var speedJob: Job? = null

    val speedHistory: StateFlow<List<SpeedTestEntity>> = c.trafficRepository.observeSpeedTests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun isOnMobileData(): Boolean = c.networkInfo.current().transport == Transport.CELLULAR

    /** Valida a entrada; retorna mensagem de erro ou null. */
    fun validate(target: String): String? =
        if (InputValidator.normalizeTarget(target) == null) "Informe um IP (ex.: 192.168.0.1) ou hostname válido" else null

    fun startPing(input: String, count: Int) {
        val target = InputValidator.normalizeTarget(input)
        if (target == null) {
            _ping.value = PingUiState(error = validate(input))
            return
        }
        pingJob?.cancel()
        _ping.value = PingUiState(running = true)
        pingJob = viewModelScope.launch {
            try {
                val result = c.pingRunner.run(target, count) { seq, time ->
                    _ping.update { it.copy(replies = (it.replies + PingReplyUi(seq, time)).takeLast(100)) }
                }
                _ping.update { it.copy(running = false, result = result) }
            } catch (e: CancellationException) {
                _ping.update { it.copy(running = false) }
                throw e
            } catch (e: UnknownHostException) {
                _ping.update { it.copy(running = false, error = "Endereço não encontrado: $target") }
            } catch (e: Exception) {
                _ping.update { it.copy(running = false, error = e.message ?: "Falha ao executar o ping") }
            }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
    }

    fun startSpeedTest() {
        if (speedJob?.isActive == true) return
        val connection = c.networkInfo.current()
        if (!connection.connected) {
            _speed.value = SpeedUiState.Error("Sem conexão com a internet")
            return
        }
        _speed.value = SpeedUiState.Running(SpeedTestClient.Stage.LATENCY, null, 0f, emptyList(), emptyList())
        speedJob = viewModelScope.launch {
            try {
                val result = c.speedTestClient.run { p ->
                    _speed.update { prev ->
                        val r = prev as? SpeedUiState.Running
                        val down = r?.downloadSeries.orEmpty()
                        val up = r?.uploadSeries.orEmpty()
                        val v = p.currentMbps?.toFloat()
                        SpeedUiState.Running(
                            stage = p.stage,
                            currentMbps = p.currentMbps,
                            fraction = p.fraction,
                            downloadSeries = if (p.stage == SpeedTestClient.Stage.DOWNLOAD && v != null) down + v else down,
                            uploadSeries = if (p.stage == SpeedTestClient.Stage.UPLOAD && v != null) up + v else up,
                        )
                    }
                }
                c.trafficRepository.saveSpeedTest(
                    SpeedTestEntity(
                        timestamp = System.currentTimeMillis(),
                        downloadMbps = result.downloadMbps,
                        uploadMbps = result.uploadMbps,
                        latencyMs = result.latencyMs,
                        jitterMs = result.jitterMs,
                        server = SpeedTestClient.SERVER,
                        connection = when (connection.transport) {
                            Transport.WIFI -> "Wi-Fi"
                            Transport.CELLULAR -> "Dados móveis"
                            Transport.ETHERNET -> "Cabo"
                            else -> "Outra"
                        },
                    )
                )
                _speed.value = SpeedUiState.Done(result)
            } catch (e: CancellationException) {
                _speed.value = SpeedUiState.Idle
                throw e
            } catch (e: UnknownHostException) {
                _speed.value = SpeedUiState.Error("Sem acesso à internet (não foi possível localizar o servidor de teste)")
            } catch (e: Exception) {
                _speed.value = SpeedUiState.Error("Teste interrompido: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun cancelSpeedTest() {
        speedJob?.cancel()
    }
}
