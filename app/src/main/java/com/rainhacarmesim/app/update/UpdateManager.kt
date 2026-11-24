package com.rainhacarmesim.app.update

import android.content.Context
import com.rainhacarmesim.app.BuildConfig
import com.rainhacarmesim.app.data.repo.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val downloaded: Long, val total: Long?) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class NeedsPermission(val info: UpdateInfo, val file: File) : UpdateState
    data class Installing(val info: UpdateInfo, val file: File) : UpdateState
    data class Error(val message: String, val info: UpdateInfo? = null, val file: File? = null) : UpdateState
}

/**
 * Orquestra o fluxo de atualização: consultar → baixar → verificar SHA-256 → instalar.
 * A atualização nunca é obrigatória.
 */
class UpdateManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }

    private val userAgent = "RainhaCarmesim/${BuildConfig.VERSION_NAME} (Android)"
    private val downloader = ApkDownloader(userAgent)
    val installer = ApkInstaller(context)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Banner da Home escondido pelo usuário ("Depois") nesta sessão. */
    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    private var job: Job? = null

    val installedVersionCode: Long get() = BuildConfig.VERSION_CODE.toLong()
    val installedVersionName: String get() = BuildConfig.VERSION_NAME

    private val updatesDir: File get() = File(context.cacheDir, "updates")

    fun checkIfDue() {
        scope.launch {
            val last = settings.lastUpdateCheck.first()
            if (System.currentTimeMillis() - last >= AUTO_CHECK_INTERVAL_MS) check()
        }
    }

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val info = fetchMetadata()
                settings.setLastUpdateCheck(System.currentTimeMillis())
                if (VersionComparator.isNewer(info.versionCode, installedVersionCode)) {
                    _bannerDismissed.value = false
                    UpdateState.Available(info)
                } else {
                    cleanup()
                    UpdateState.UpToDate(System.currentTimeMillis())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Error(friendly(e))
            }
        }
    }

    fun download() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Error -> s.info ?: return
            else -> return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            val target = File(updatesDir, "RainhaCarmesim-${info.versionName}.apk")
            _state.value = UpdateState.Downloading(info, 0, null)
            _state.value = try {
                downloader.download(info.apkUrl, info.sha256, target) { done, total ->
                    _state.value = UpdateState.Downloading(info, done, total)
                }
                val problem = installer.validateArchive(target, installedVersionCode)
                if (problem != null) {
                    target.delete()
                    UpdateState.Error(problem, info)
                } else {
                    UpdateState.ReadyToInstall(info, target)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Error(friendly(e), info)
            }
        }
    }

    fun cancelDownload() {
        val s = _state.value
        if (s is UpdateState.Downloading) {
            job?.cancel()
            _state.value = UpdateState.Available(s.info)
        }
    }

    /** Inicia a instalação; pede a permissão de "fontes desconhecidas" somente se necessário. */
    fun install() {
        val (info, file) = when (val s = _state.value) {
            is UpdateState.ReadyToInstall -> s.info to s.file
            is UpdateState.NeedsPermission -> s.info to s.file
            is UpdateState.Installing -> s.info to s.file
            is UpdateState.Error -> (s.info ?: return) to (s.file ?: return)
            else -> return
        }
        if (!installer.canRequestInstalls()) {
            _state.value = UpdateState.NeedsPermission(info, file)
            context.startActivity(installer.unknownSourcesSettingsIntent())
            return
        }
        scope.launch {
            _state.value = UpdateState.Installing(info, file)
            try {
                withContext(Dispatchers.IO) { installer.installWithSession(file) }
            } catch (e: Exception) {
                try {
                    installer.installWithViewIntent(file)
                } catch (e2: Exception) {
                    _state.value = UpdateState.Error("Não foi possível iniciar a instalação: ${e2.message}", info, file)
                }
            }
        }
    }

    /** Chamado ao voltar das configurações de permissão. */
    fun onReturnedFromSettings() {
        val s = _state.value
        if (s is UpdateState.NeedsPermission && installer.canRequestInstalls()) {
            _state.value = UpdateState.ReadyToInstall(s.info, s.file)
        }
    }

    fun dismissBanner() {
        _bannerDismissed.value = true
    }

    fun onInstallerShown() { /* o estado Installing permanece até o resultado */ }

    fun onInstallSucceeded() {
        cleanup()
    }

    fun onInstallFailed(message: String) {
        val s = _state.value
        _state.value = when (s) {
            is UpdateState.Installing -> UpdateState.Error(message, s.info, s.file)
            else -> UpdateState.Error(message)
        }
    }

    private fun cleanup() {
        runCatching { updatesDir.listFiles()?.forEach { it.delete() } }
    }

    private suspend fun fetchMetadata(): UpdateInfo = withContext(Dispatchers.IO) {
        val c = URL(BuildConfig.UPDATE_METADATA_URL).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = true
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.useCaches = false
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Cache-Control", "no-cache")
            val code = c.responseCode
            if (code == 404) throw IOException("Nenhuma versão publicada ainda no GitHub Releases")
            if (code !in 200..299) throw IOException("GitHub respondeu HTTP $code")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            UpdateMetadata.parse(body, BuildConfig.UPDATE_REPO)
        } finally {
            c.disconnect()
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> "Sem conexão com a internet"
        is ApkDownloader.IntegrityException -> e.message ?: "Falha na verificação de integridade"
        is java.net.SocketTimeoutException -> "Tempo de conexão esgotado. Tente novamente."
        is IOException -> e.message ?: "Falha de rede"
        is IllegalArgumentException -> e.message ?: "Metadados inválidos"
        else -> e.message ?: e.javaClass.simpleName
    }
}
