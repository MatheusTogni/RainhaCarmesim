package com.rainhacarmesim.app.feature.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainhacarmesim.app.data.db.DeviceEntity
import com.rainhacarmesim.app.data.repo.ScanState
import com.rainhacarmesim.app.domain.Formatters
import com.rainhacarmesim.app.domain.ScanMerger
import com.rainhacarmesim.app.ui.appViewModel
import com.rainhacarmesim.app.ui.components.EmptyState
import com.rainhacarmesim.app.ui.components.LoadingState
import com.rainhacarmesim.app.ui.components.Notice
import com.rainhacarmesim.app.ui.components.RcCard
import com.rainhacarmesim.app.ui.components.ScreenHeader
import com.rainhacarmesim.app.ui.components.StatusDot
import com.rainhacarmesim.app.ui.components.Tag
import com.rainhacarmesim.app.ui.theme.MonoFamily
import com.rainhacarmesim.app.ui.theme.RcColors

@Composable
fun DevicesScreen(focusDeviceKey: String?, onPing: (String) -> Unit) {
    val vm = appViewModel { DevicesViewModel(it) }
    val network by vm.network.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val lastScan by vm.lastScan.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<DeviceEntity?>(null) }
    var focusHandled by remember { mutableStateOf(false) }

    // Abre o detalhe do dispositivo vindo de um alerta.
    LaunchedEffect(focusDeviceKey, devices) {
        if (!focusHandled && focusDeviceKey != null && devices != null) {
            vm.find(focusDeviceKey)?.let { selected = it }
            focusHandled = true
        }
    }

    val running = scan as? ScanState.Running
    val canScan = network?.canScanLan == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(
                title = "Dispositivos",
                subtitle = lastScan?.let { "Última varredura: ${Formatters.dateTime(it.finishedAt)}" }
                    ?: "Nenhuma varredura nesta rede",
            ) {
                if ((counts[DeviceFilter.NEW] ?: 0) > 0) {
                    IconButton(onClick = vm::acknowledgeNew) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = "Marcar novos como vistos", tint = RcColors.TextDim)
                    }
                }
            }
        }

        item {
            RcCard {
                if (running != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(running.stage, style = MaterialTheme.typography.titleMedium, color = RcColors.Text)
                            Text(
                                "${running.done} de ${running.total} · ${running.found} respondendo",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                                color = RcColors.TextDim,
                            )
                        }
                        if (!running.automatic) {
                            IconButton(onClick = vm::cancelScan) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancelar varredura", tint = RcColors.TextDim)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { running.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = RcColors.Crimson,
                        trackColor = RcColors.SurfaceHigh,
                    )
                } else {
                    Button(
                        onClick = vm::scan,
                        enabled = canScan,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.Crimson),
                    ) {
                        Icon(Icons.Rounded.Radar, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Iniciar varredura")
                    }
                    val s = scan
                    if (s is ScanState.Failed) {
                        Spacer(Modifier.height(8.dp))
                        Notice(s.message, color = RcColors.Warning)
                    }
                    if (s is ScanState.Finished && s.summary.limitedRange) {
                        Spacer(Modifier.height(8.dp))
                        Notice("Rede grande: a varredura cobriu apenas a /${s.summary.scannedPrefix} que contém este celular.")
                    }
                }
            }
        }

        if (!canScan && network != null) {
            item {
                EmptyState(
                    icon = Icons.Rounded.WifiOff,
                    title = "Sem rede local",
                    message = "Conecte-se a uma rede Wi-Fi ou cabeada para descobrir os dispositivos dela.",
                )
            }
            return@LazyColumn
        }

        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { vm.filter.value = f },
                        label = { Text("${f.label} (${counts[f] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RcColors.Bordeaux,
                            selectedLabelColor = RcColors.Text,
                        ),
                    )
                }
            }
        }

        val list = devices
        when {
            list == null -> item { LoadingState("Carregando dispositivos…") }
            list.isEmpty() && lastScan == null -> item {
                EmptyState(
                    icon = Icons.Rounded.Devices,
                    title = "Nenhum dispositivo ainda",
                    message = "Inicie uma varredura. A primeira varredura de uma rede vira a referência para detectar novos dispositivos.",
                )
            }
            list.isEmpty() -> item {
                EmptyState(icon = Icons.Rounded.Devices, title = "Nada neste filtro", message = "Nenhum dispositivo corresponde ao filtro selecionado.")
            }
            else -> {
                items(list, key = { it.deviceKey }) { device ->
                    DeviceRow(device, onClick = { selected = device })
                }
                if (list.none { it.mac != null && !it.isSelf }) {
                    item {
                        Notice(
                            "O Android 10+ não permite que apps leiam o endereço MAC de outros aparelhos, " +
                                "por isso MAC e fabricante geralmente aparecem como indisponíveis."
                        )
                    }
                }
            }
        }
    }

    selected?.let { device ->
        DeviceDetailSheet(
            device = device,
            onDismiss = { selected = null },
            onForget = {
                vm.forget(device)
                selected = null
            },
            onPing = {
                selected = null
                onPing(device.ip)
            },
        )
    }
}

@Composable
private fun DeviceRow(device: DeviceEntity, onClick: () -> Unit) {
    val icon = when {
        device.isSelf -> Icons.Rounded.PhoneAndroid
        device.isGateway -> Icons.Rounded.Router
        else -> Icons.Rounded.Devices
    }
    val statusColor = if (device.online) RcColors.Positive else RcColors.TextFaint
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RcColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(RcColors.BordeauxDeep),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (device.online) RcColors.Crimson else RcColors.TextFaint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ScanMerger.label(device),
                    style = MaterialTheme.typography.titleMedium,
                    color = RcColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (device.isNew) {
                    Spacer(Modifier.width(6.dp))
                    Tag("novo", RcColors.Warning)
                }
            }
            Text(device.ip, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily), color = RcColors.TextDim)
            val extra = listOfNotNull(device.vendor?.takeIf { !device.isSelf }, device.mac).joinToString(" · ")
            if (extra.isNotEmpty()) {
                Text(extra, style = MaterialTheme.typography.bodySmall, color = RcColors.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(statusColor, size = 8)
                Spacer(Modifier.width(6.dp))
                Text(if (device.online) "online" else "offline", style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
            if (device.online && device.lastLatencyMs != null && !device.isSelf) {
                Text("${device.lastLatencyMs} ms", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily), color = RcColors.TextFaint)
            } else if (!device.online) {
                Text(Formatters.relative(device.lastSeen), style = MaterialTheme.typography.labelSmall, color = RcColors.TextFaint)
            }
        }
    }
}
