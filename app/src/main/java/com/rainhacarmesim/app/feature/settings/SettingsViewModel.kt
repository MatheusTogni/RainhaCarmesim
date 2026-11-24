package com.rainhacarmesim.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainhacarmesim.app.AppContainer
import com.rainhacarmesim.app.data.repo.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ClearTarget(val label: String, val description: String) {
    DEVICES("Dispositivos conhecidos", "Apaga dispositivos e varreduras. A próxima varredura vira a nova referência."),
    ALERTS("Alertas", "Apaga o histórico de alertas."),
    TRAFFIC("Histórico de tráfego e testes", "Apaga as amostras de tráfego e os resultados de speed test."),
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val autoScan: StateFlow<Boolean> = c.settings.autoScanEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val interval: StateFlow<Int> = c.settings.autoScanMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.DEFAULT_INTERVAL)

    val update = c.updateManager.state
    val installedVersion: String = c.updateManager.installedVersionName
    val installedCode: Long = c.updateManager.installedVersionCode

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun hasLocationPermission() = c.networkInfo.hasLocationPermission()
    fun hasUsageAccess() = c.usageHistory.hasAccess()
    fun canInstallPackages() = c.updateManager.installer.canRequestInstalls()

    fun setAutoScan(enabled: Boolean) = viewModelScope.launch { c.settings.setAutoScanEnabled(enabled) }
    fun setInterval(minutes: Int) = viewModelScope.launch { c.settings.setAutoScanMinutes(minutes) }

    fun checkUpdate() = c.updateManager.check()
    fun downloadUpdate() = c.updateManager.download()
    fun cancelDownload() = c.updateManager.cancelDownload()
    fun installUpdate() = c.updateManager.install()
    fun onResume() = c.updateManager.onReturnedFromSettings()

    fun clear(target: ClearTarget) = viewModelScope.launch {
        when (target) {
            ClearTarget.DEVICES -> c.deviceRepository.clearDevices()
            ClearTarget.ALERTS -> c.deviceRepository.clearAlerts()
            ClearTarget.TRAFFIC -> {
                c.trafficRepository.clearTraffic()
                c.trafficRepository.clearSpeedTests()
            }
        }
        _message.value = "${target.label}: dados apagados"
    }

    fun consumeMessage() {
        _message.value = null
    }
}
