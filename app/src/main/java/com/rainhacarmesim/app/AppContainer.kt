package com.rainhacarmesim.app

import android.content.Context
import com.rainhacarmesim.app.data.db.AppDatabase
import com.rainhacarmesim.app.data.repo.DeviceRepository
import com.rainhacarmesim.app.data.repo.ScanCoordinator
import com.rainhacarmesim.app.data.repo.SettingsStore
import com.rainhacarmesim.app.data.repo.TrafficMonitor
import com.rainhacarmesim.app.data.repo.TrafficRepository
import com.rainhacarmesim.app.network.DeviceScanner
import com.rainhacarmesim.app.network.HostProbe
import com.rainhacarmesim.app.network.HostnameResolver
import com.rainhacarmesim.app.network.NetworkInfoProvider
import com.rainhacarmesim.app.network.PingRunner
import com.rainhacarmesim.app.network.SpeedTestClient
import com.rainhacarmesim.app.network.SystemUsageHistory
import com.rainhacarmesim.app.network.TrafficSampler
import com.rainhacarmesim.app.network.VendorLookup
import com.rainhacarmesim.app.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Injeção de dependências manual: um único lugar cria e compartilha os serviços do app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Escopo do processo, para tarefas que não devem morrer ao trocar de tela. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val userAgent = "RainhaCarmesim/${BuildConfig.VERSION_NAME} (Android)"

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val settings = SettingsStore(appContext)

    val networkInfo = NetworkInfoProvider(appContext)
    private val vendorLookup = VendorLookup(appContext)
    private val scanner = DeviceScanner(HostProbe(), HostnameResolver(), vendorLookup)

    val deviceRepository by lazy { DeviceRepository(database) }
    val trafficRepository by lazy { TrafficRepository(database) }

    val scanCoordinator by lazy { ScanCoordinator(networkInfo, scanner, deviceRepository, appScope) }
    val trafficMonitor by lazy { TrafficMonitor(TrafficSampler(), trafficRepository) }
    val usageHistory = SystemUsageHistory(appContext)

    val pingRunner = PingRunner()
    val speedTestClient = SpeedTestClient(userAgent)

    val updateManager by lazy { UpdateManager(appContext, settings, appScope) }
}
