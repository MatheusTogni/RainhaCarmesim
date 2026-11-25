package com.rainhacarmesim.app.feature.dashboard

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainhacarmesim.app.R
import com.rainhacarmesim.app.data.db.AlertEntity
import com.rainhacarmesim.app.data.repo.ScanState
import com.rainhacarmesim.app.domain.Formatters
import com.rainhacarmesim.app.feature.alerts.AlertRow
import com.rainhacarmesim.app.network.NetworkSnapshot
import com.rainhacarmesim.app.network.SsidStatus
import com.rainhacarmesim.app.network.Transport
import com.rainhacarmesim.app.ui.appViewModel
import com.rainhacarmesim.app.ui.components.ErrorState
import com.rainhacarmesim.app.ui.components.InfoRow
import com.rainhacarmesim.app.ui.components.LoadingState
import com.rainhacarmesim.app.ui.components.Metric
import com.rainhacarmesim.app.ui.components.Notice
import com.rainhacarmesim.app.ui.components.RcCard
import com.rainhacarmesim.app.ui.components.StatusDot
import com.rainhacarmesim.app.ui.theme.MonoValue
import com.rainhacarmesim.app.ui.theme.RcColors
import com.rainhacarmesim.app.update.UpdateState

@Composable
fun DashboardScreen(
    onOpenDevices: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel { DashboardViewModel(it) }
    val network by vm.network.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val lastScan by vm.lastScan.collectAsStateWithLifecycle()
    val alerts by vm.recentAlerts.collectAsStateWithLifecycle()
    val unread by vm.unreadAlerts.collectAsStateWithLifecycle()
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val trafficSupported by vm.trafficSupported.collectAsStateWithLifecycle()
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val update by vm.updateState.collectAsStateWithLifecycle()
    val updateDismissed by vm.updateBannerDismissed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(R.drawable.crown_logo),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape).border(1.dp, RcColors.Crimson, CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rainha Carmesim", style = MaterialTheme.typography.headlineSmall, color = RcColors.Text)
                    Text("Diagnóstico da rede local", style = MaterialTheme.typography.bodySmall, color = RcColors.TextDim)
                }
                IconButton(onClick = onOpenAlerts) {
                    BadgedBox(badge = {
                        if (unread > 0) Badge(containerColor = RcColors.Crimson) { Text(if (unread > 99) "99+" else "$unread") }
                    }) {
                        Icon(Icons.Rounded.Notifications, contentDescription = "Alertas", tint = RcColors.Text)
                    }
                }
            }
        }

        val available = update as? UpdateState.Available
        if (available != null && !updateDismissed) {
            item {
                RcCard(title = "Atualização disponível", icon = Icons.Rounded.SystemUpdate, accent = RcColors.Positive) {
                    Text(
                        "Versão ${available.info.versionName} disponível no GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RcColors.Text,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = vm::dismissUpdate) { Text("Depois") }
                        TextButton(onClick = onOpenSettings) { Text("Ver detalhes") }
                    }
                }
            }
        }

        when (val state = network) {
            SnapshotState.Loading -> item { LoadingState("Lendo informações da rede…") }
            is SnapshotState.Error -> item { ErrorState(state.message, onRetry = vm::refresh) }
            is SnapshotState.Ready -> {
                val snap = state.snapshot
                item {
                    ConnectionCard(
                        snap = snap,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        },
                        onOpenLocationSettings = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                    )
                }
                if (snap.connected) {
                    item { AddressCard(snap) }
                }
                item {
                    // IntrinsicSize.Min + fillMaxHeight: os dois cards sempre com a mesma altura (a do mais alto).
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        RcCard(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            title = "Dispositivos",
                            icon = Icons.Rounded.Devices,
                            onClick = onOpenDevices,
                        ) {
                            if (lastScan == null) {
                                Text("—", style = MonoValue, color = RcColors.TextDim)
                                Text("sem varredura nesta rede", style = MaterialTheme.typography.bodySmall, color = RcColors.TextFaint)
                            } else {
                                val online = devices.count { it.online }
                                Metric(label = "online agora", value = "$online")
                                Text("${devices.size} conhecidos", style = MaterialTheme.typography.bodySmall, color = RcColors.TextDim)
                            }
                        }
                        RcCard(modifier = Modifier.weight(1f).fillMaxHeight(), title = "Tráfego", icon = Icons.Rounded.Radar) {
                            val t = traffic
                            if (!trafficSupported) {
                                Text("Contadores indisponíveis neste aparelho", style = MaterialTheme.typography.bodySmall, color = RcColors.TextFaint)
                            } else if (t == null) {
                                Text("medindo…", style = MaterialTheme.typography.bodySmall, color = RcColors.TextDim)
                            } else {
                                val down = Formatters.bitRate(t.rxBytesPerSec)
                                val up = Formatters.bitRate(t.txBytesPerSec)
                                Metric("download", down.first, unit = down.second, icon = Icons.Rounded.ArrowDownward)
                                Spacer(Modifier.height(6.dp))
                                Metric(
                                    "upload", up.first, unit = up.second,
                                    icon = Icons.Rounded.ArrowUpward, iconTint = RcColors.Upload,
                                )
                            }
                        }
                    }
                }
                item { ScanCard(snap, scan, lastScan?.finishedAt, onScan = vm::scan, onOpenDevices = onOpenDevices) }
            }
        }

        if (alerts.isNotEmpty()) {
            item {
                RcCard(
                    title = "Alertas recentes",
                    icon = Icons.Rounded.Notifications,
                    trailing = { TextButton(onClick = onOpenAlerts) { Text("Ver todos") } },
                ) {
                    alerts.forEach { alert: AlertEntity ->
                        AlertRow(alert, onClick = { onOpenDevice(alert.deviceKey) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    snap: NetworkSnapshot,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val (color, label) = when {
        !snap.connected -> RcColors.Danger to "Sem conexão"
        snap.validated -> RcColors.Positive to "Conectado à internet"
        else -> RcColors.Warning to "Conectado, sem acesso verificado à internet"
    }
    RcCard(
        title = "Conexão",
        icon = if (snap.connected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, pulsing = snap.connected)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = RcColors.Text)
        }
        Spacer(Modifier.height(10.dp))
        if (!snap.connected) {
            Notice("Ative o Wi-Fi ou os dados móveis para ver as informações da rede.")
            return@RcCard
        }
        InfoRow("Tipo", transportLabel(snap.transport), valueStyle = MaterialTheme.typography.bodyMedium)
        if (snap.transport == Transport.WIFI) {
            when (snap.ssidStatus) {
                SsidStatus.OK -> InfoRow("Rede Wi-Fi", snap.ssid)
                SsidStatus.NEED_PERMISSION -> {
                    InfoRow("Rede Wi-Fi", null, unavailableText = "requer permissão")
                    Notice("O Android só revela o nome da rede Wi-Fi a apps com permissão de localização. Ela é usada apenas para isso.")
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.Bordeaux),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Permitir") }
                }
                SsidStatus.LOCATION_OFF -> {
                    InfoRow("Rede Wi-Fi", null, unavailableText = "localização desativada")
                    TextButton(onClick = onOpenLocationSettings) { Text("Ativar localização") }
                }
                else -> InfoRow("Rede Wi-Fi", null)
            }
            InfoRow("Taxa do link", snap.linkSpeedMbps?.let { "$it Mbps" })
            InfoRow("Sinal", snap.rssi?.let { "$it dBm (${signalLabel(it)})" })
        }
    }
}

@Composable
private fun AddressCard(snap: NetworkSnapshot) {
    RcCard(title = "Endereçamento", icon = Icons.Rounded.Dns) {
        InfoRow("IP local", snap.localIp)
        InfoRow("Máscara", snap.subnetMask?.let { "$it (/${snap.prefixLength})" })
        InfoRow("Gateway", snap.gateway)
        if (snap.dns.isEmpty()) {
            InfoRow("DNS", null)
        } else {
            snap.dns.forEachIndexed { i, d -> InfoRow(if (i == 0) "DNS" else "", d) }
        }
        InfoRow("Interface", snap.interfaceName, valueStyle = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScanCard(
    snap: NetworkSnapshot,
    scan: ScanState,
    lastScanAt: Long?,
    onScan: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    RcCard(title = "Última varredura", icon = Icons.Rounded.History, onClick = onOpenDevices) {
        if (lastScanAt != null) {
            Text(Formatters.dateTime(lastScanAt), style = MonoValue, color = RcColors.Text)
            Text(Formatters.relative(lastScanAt), style = MaterialTheme.typography.bodySmall, color = RcColors.TextDim)
        } else {
            Text("Nenhuma varredura nesta rede ainda", style = MaterialTheme.typography.bodyMedium, color = RcColors.TextDim)
        }
        Spacer(Modifier.height(10.dp))
        when (scan) {
            is ScanState.Running -> {
                Text(
                    "${scan.stage}… ${scan.done}/${scan.total}" + if (scan.automatic) " (automática)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = RcColors.TextDim,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { scan.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = RcColors.Crimson,
                    trackColor = RcColors.SurfaceHigh,
                )
            }
            else -> {
                if (scan is ScanState.Failed) {
                    Notice(scan.message, color = RcColors.Warning)
                    Spacer(Modifier.height(6.dp))
                }
                Button(
                    onClick = onScan,
                    enabled = snap.canScanLan,
                    colors = ButtonDefaults.buttonColors(containerColor = RcColors.Crimson),
                ) {
                    Icon(Icons.Rounded.Radar, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Varrer agora")
                }
                if (!snap.canScanLan) {
                    Spacer(Modifier.height(6.dp))
                    Notice("A varredura exige conexão Wi-Fi ou cabeada com IPv4.")
                }
            }
        }
    }
}

fun transportLabel(t: Transport): String = when (t) {
    Transport.WIFI -> "Wi-Fi"
    Transport.ETHERNET -> "Cabo (Ethernet)"
    Transport.CELLULAR -> "Dados móveis"
    Transport.VPN -> "VPN"
    Transport.OTHER -> "Outra"
    Transport.NONE -> "Nenhuma"
}

fun signalLabel(rssi: Int): String = when {
    rssi >= -55 -> "excelente"
    rssi >= -67 -> "bom"
    rssi >= -75 -> "regular"
    else -> "fraco"
}
