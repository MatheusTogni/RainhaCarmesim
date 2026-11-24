package com.rainhacarmesim.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainhacarmesim.app.AppContainer
import com.rainhacarmesim.app.data.db.AlertEntity
import com.rainhacarmesim.app.data.db.DeviceEntity
import com.rainhacarmesim.app.data.db.ScanEntity
import com.rainhacarmesim.app.network.NetworkSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SnapshotState {
    data object Loading : SnapshotState
    data class Ready(val snapshot: NetworkSnapshot) : SnapshotState
    data class Error(val message: String) : SnapshotState
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val c: AppContainer) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    val network: StateFlow<SnapshotState> = refresh
        .flatMapLatest { c.networkInfo.observe() }
        .map<NetworkSnapshot, SnapshotState> { SnapshotState.Ready(it) }
        .catch { emit(SnapshotState.Error(it.message ?: "Não foi possível ler a rede")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SnapshotState.Loading)

    private val networkKey = network.map { (it as? SnapshotState.Ready)?.snapshot?.networkKey }

    val devices: StateFlow<List<DeviceEntity>> = networkKey
        .flatMapLatest { key -> if (key == null) flowOf(emptyList()) else c.deviceRepository.observeDevices(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastScan: StateFlow<ScanEntity?> = networkKey
        .flatMapLatest { key -> if (key == null) flowOf(null) else c.deviceRepository.observeLastScan(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentAlerts: StateFlow<List<AlertEntity>> = c.deviceRepository.observeAlerts()
        .map { list -> list.filter { !it.read }.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadAlerts: StateFlow<Int> = c.deviceRepository.observeUnreadAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val traffic = c.trafficMonitor.sampler.current
    val trafficSupported = c.trafficMonitor.sampler.supported
    val scanState = c.scanCoordinator.state
    val updateState = c.updateManager.state
    val updateBannerDismissed = c.updateManager.bannerDismissed

    fun refresh() {
        refresh.value++
    }

    fun scan() = c.scanCoordinator.startManualScan()

    fun startUpdateDownload() = c.updateManager.download()
    fun dismissUpdate() = c.updateManager.dismissBanner()
}
