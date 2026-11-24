package com.rainhacarmesim.app.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rainhacarmesim.app.data.db.DeviceEntity
import com.rainhacarmesim.app.domain.Formatters
import com.rainhacarmesim.app.domain.ScanMerger
import com.rainhacarmesim.app.ui.components.InfoRow
import com.rainhacarmesim.app.ui.components.Notice
import com.rainhacarmesim.app.ui.components.Tag
import com.rainhacarmesim.app.ui.theme.RcColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailSheet(
    device: DeviceEntity,
    onDismiss: () -> Unit,
    onForget: () -> Unit,
    onPing: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmForget by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RcColors.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Text(ScanMerger.label(device), style = MaterialTheme.typography.headlineSmall, color = RcColors.Text)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(if (device.online) "online" else "offline", if (device.online) RcColors.Positive else RcColors.TextFaint)
                if (device.isNew) Tag("novo", RcColors.Warning)
                if (device.isGateway) Tag("gateway", RcColors.Crimson)
                if (device.isSelf) Tag("este celular", RcColors.Crimson)
            }
            Spacer(Modifier.height(16.dp))
            InfoRow("Endereço IP", device.ip)
            InfoRow("Endereço MAC", device.mac)
            InfoRow("Hostname", device.hostname)
            InfoRow("Fabricante", device.vendor)
            InfoRow("Latência", device.lastLatencyMs?.takeIf { device.online && !device.isSelf }?.let { "$it ms" })
            InfoRow("Detectado via", device.detectedBy, valueStyle = MaterialTheme.typography.bodyMedium)
            InfoRow("Visto pela 1ª vez", Formatters.dateTime(device.firstSeen))
            InfoRow("Visto por último", Formatters.dateTime(device.lastSeen))
            if (device.online && device.missedScans > 0) {
                InfoRow("Sem resposta", "${device.missedScans} varredura(s)", valueColor = RcColors.Warning)
            }
            Spacer(Modifier.height(10.dp))
            if (device.mac == null && !device.isSelf) {
                Notice("MAC indisponível: o Android bloqueia a leitura da tabela ARP para apps. O dispositivo é identificado pelo IP.")
                Spacer(Modifier.height(6.dp))
            }
            if (device.isSelf) {
                Notice("O Android oculta o MAC real deste aparelho para apps; nome e fabricante vêm do sistema.")
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onPing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RcColors.Crimson),
                ) {
                    Icon(Icons.Rounded.NetworkCheck, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ping")
                }
                if (!device.isSelf) {
                    OutlinedButton(onClick = { confirmForget = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Delete, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Esquecer")
                    }
                }
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Esquecer dispositivo?") },
            text = { Text("Ele deixará de ser conhecido. Se aparecer de novo, será tratado como novo dispositivo.") },
            confirmButton = { TextButton(onClick = { confirmForget = false; onForget() }) { Text("Esquecer") } },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancelar") } },
            containerColor = RcColors.Surface,
        )
    }
}
