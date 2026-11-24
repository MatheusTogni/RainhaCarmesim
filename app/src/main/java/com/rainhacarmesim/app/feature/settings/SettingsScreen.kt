package com.rainhacarmesim.app.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainhacarmesim.app.BuildConfig
import com.rainhacarmesim.app.data.repo.SettingsStore
import com.rainhacarmesim.app.ui.appViewModel
import com.rainhacarmesim.app.ui.components.InfoRow
import com.rainhacarmesim.app.ui.components.Notice
import com.rainhacarmesim.app.ui.components.RcCard
import com.rainhacarmesim.app.ui.components.ScreenHeader
import com.rainhacarmesim.app.ui.components.StatusDot
import com.rainhacarmesim.app.ui.theme.RcColors

@Composable
fun SettingsScreen(showMessage: (String) -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val autoScan by vm.autoScan.collectAsStateWithLifecycle()
    val interval by vm.interval.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Estado das permissões é relido ao voltar das telas de configuração do sistema.
    var permissionsVersion by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        permissionsVersion++
        onPauseOrDispose { }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsVersion++
    }
    var clearTarget by remember { mutableStateOf<ClearTarget?>(null) }

    LaunchedEffect(message) {
        message?.let {
            showMessage(it)
            vm.consumeMessage()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Ajustes", "Monitoramento, permissões e atualizações") }

        item {
            UpdateCard(
                state = update,
                installedVersion = vm.installedVersion,
                installedCode = vm.installedCode,
                onCheck = vm::checkUpdate,
                onDownload = vm::downloadUpdate,
                onCancelDownload = vm::cancelDownload,
                onInstall = vm::installUpdate,
            )
        }

        item {
            RcCard(title = "Varredura automática", icon = Icons.Rounded.Radar) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Varrer periodicamente", style = MaterialTheme.typography.titleMedium, color = RcColors.Text)
                        Text(
                            "Só enquanto o app está aberto. Gera os alertas de novos dispositivos e desconexões.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RcColors.TextDim,
                        )
                    }
                    Switch(
                        checked = autoScan,
                        onCheckedChange = { vm.setAutoScan(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = RcColors.Crimson),
                    )
                }
                if (autoScan) {
                    Spacer(Modifier.height(10.dp))
                    Text("Intervalo", style = MaterialTheme.typography.labelMedium, color = RcColors.TextDim)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsStore.INTERVAL_OPTIONS.forEach { m ->
                            FilterChip(
                                selected = interval == m,
                                onClick = { vm.setInterval(m) },
                                label = { Text("$m min") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RcColors.Bordeaux),
                            )
                        }
                    }
                }
            }
        }

        item {
            // permissionsVersion força a releitura do estado após mudanças.
            val location = remember(permissionsVersion) { vm.hasLocationPermission() }
            val usage = remember(permissionsVersion) { vm.hasUsageAccess() }
            val install = remember(permissionsVersion) { vm.canInstallPackages() }
            RcCard(title = "Permissões", icon = Icons.Rounded.Security) {
                PermissionRow(
                    title = "Localização",
                    description = "Exigida pelo Android apenas para mostrar o nome (SSID) da rede Wi-Fi.",
                    granted = location,
                    actionLabel = "Permitir",
                    onAction = {
                        locationLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                )
                PermissionRow(
                    title = "Acesso de uso (opcional)",
                    description = "Permite ler o histórico de dados registrado pelo Android, inclusive com o app fechado.",
                    granted = usage,
                    actionLabel = "Abrir",
                    onAction = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                )
                PermissionRow(
                    title = "Instalar atualizações",
                    description = "Solicitada apenas no momento de instalar uma nova versão baixada.",
                    granted = install,
                    actionLabel = null,
                    onAction = null,
                )
            }
        }

        item {
            RcCard(title = "Dados locais", icon = Icons.Rounded.DeleteForever) {
                Notice("Tudo fica salvo apenas neste aparelho. Nada é enviado a servidores.")
                Spacer(Modifier.height(6.dp))
                ClearTarget.entries.forEach { t ->
                    TextButton(onClick = { clearTarget = t }) { Text("Limpar ${t.label.lowercase()}") }
                }
            }
        }

        item {
            RcCard(title = "Sobre", icon = Icons.Rounded.Info) {
                InfoRow("Aplicativo", "Rainha Carmesim", valueStyle = MaterialTheme.typography.bodyMedium)
                InfoRow("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Pacote", BuildConfig.APPLICATION_ID, valueStyle = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.UPDATE_REPO}"))
                    )
                }) { Text("Abrir repositório no GitHub") }
            }
        }
    }

    clearTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { clearTarget = null },
            title = { Text("Limpar ${t.label.lowercase()}?") },
            text = { Text(t.description) },
            confirmButton = { TextButton(onClick = { vm.clear(t); clearTarget = null }) { Text("Limpar") } },
            dismissButton = { TextButton(onClick = { clearTarget = null }) { Text("Cancelar") } },
            containerColor = RcColors.Surface,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (granted) RcColors.Positive else RcColors.TextFaint)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = RcColors.Text)
            Text(description, style = MaterialTheme.typography.bodySmall, color = RcColors.TextDim)
        }
        if (!granted && actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        } else {
            Text(if (granted) "concedida" else "não concedida", style = MaterialTheme.typography.labelSmall, color = RcColors.TextFaint)
        }
    }
}
