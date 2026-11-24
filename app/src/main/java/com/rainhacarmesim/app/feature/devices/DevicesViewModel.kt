package com.rainhacarmesim.app.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainhacarmesim.app.AppContainer
import com.rainhacarmesim.app.data.db.DeviceEntity
import com.rainhacarmesim.app.data.db.ScanEntity
import com.rainhacarmesim.app.network.NetworkSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DeviceFilter(val label: String) { ALL("Todos"), ONLINE("Online"), OFFLINE("Offline"), NEW("Novos") }

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModel(private val c: AppContainer) : ViewModel() {
    val network: StateFlow<NetworkSnapshot?> = c.networkInfo.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val networkKey = network.map { it?.networkKey }

    /** null = ainda carregando. */
    private val allDevices: StateFlow<List<DeviceEntity>?> = networkKey
        .flatMapLatest { key -> if (key == null) flowOf(emptyList()) else c.deviceRepository.observeDevices(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lastScan: StateFlow<ScanEntity?> = networkKey
        .flatMapLatest { key -> if (key == null) flowOf(null) else c.deviceRepository.observeLastScan(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val filter = MutableStateFlow(DeviceFilter.ALL)

    val devices: StateFlow<List<DeviceEntity>?> = combine(allDevices, filter) { list, f ->
        list?.filter {
            when (f) {
                DeviceFilter.ALL -> true
                DeviceFilter.ONLINE -> it.online
                DeviceFilter.OFFLINE -> !it.online
                DeviceFilter.NEW -> it.isNew
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val counts: StateFlow<Map<DeviceFilter, Int>> = allDevices.map { list ->
        val l = list.orEmpty()
        mapOf(
            DeviceFilter.ALL to l.size,
            DeviceFilter.ONLINE to l.count { it.online },
            DeviceFilter.OFFLINE to l.count { !it.online },
            DeviceFilter.NEW to l.count { it.isNew },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val scanState = c.scanCoordinator.state

    fun find(deviceKey: String): DeviceEntity? = allDevices.value?.firstOrNull { it.deviceKey == deviceKey }

    fun scan() = c.scanCoordinator.startManualScan()
    fun cancelScan() = c.scanCoordinator.cancel()

    fun acknowledgeNew() {
        val key = network.value?.networkKey ?: return
        viewModelScope.launch { c.deviceRepository.acknowledgeNew(key) }
    }

    fun forget(device: DeviceEntity) {
        viewModelScope.launch { c.deviceRepository.forgetDevice(device.networkKey, device.deviceKey) }
    }
}
