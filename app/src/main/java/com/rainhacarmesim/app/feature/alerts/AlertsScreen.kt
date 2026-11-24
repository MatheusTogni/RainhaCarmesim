package com.rainhacarmesim.app.feature.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rainhacarmesim.app.AppContainer
import com.rainhacarmesim.app.data.db.AlertEntity
import com.rainhacarmesim.app.domain.AlertType
import com.rainhacarmesim.app.domain.Formatters
import com.rainhacarmesim.app.ui.appViewModel
import com.rainhacarmesim.app.ui.components.EmptyState
import com.rainhacarmesim.app.ui.components.Notice
import com.rainhacarmesim.app.ui.components.RcCard
import com.rainhacarmesim.app.ui.components.ScreenHeader
import com.rainhacarmesim.app.ui.components.StatusDot
import com.rainhacarmesim.app.ui.theme.MonoFamily
import com.rainhacarmesim.app.ui.theme.RcColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertsViewModel(private val c: AppContainer) : ViewModel() {
    val alerts: StateFlow<List<AlertEntity>?> = c.deviceRepository.observeAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markRead(id: Long) = viewModelScope.launch { c.deviceRepository.markAlertRead(id) }
    fun markAllRead() = viewModelScope.launch { c.deviceRepository.markAllAlertsRead() }
    fun clear() = viewModelScope.launch { c.deviceRepository.clearAlerts() }
}

fun alertVisual(type: AlertType?): Pair<ImageVector, Color> = when (type) {
    AlertType.NEW_DEVICE -> Icons.Rounded.NewReleases to RcColors.Warning
    AlertType.RETURNED -> Icons.Rounded.Link to RcColors.Positive
    AlertType.DISCONNECTED -> Icons.Rounded.LinkOff to RcColors.Danger
    null -> Icons.Rounded.NotificationsNone to RcColors.TextDim
}

/** Linha de alerta reutilizada na Home e na central de alertas. */
@Composable
fun AlertRow(alert: AlertEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val type = AlertType.from(alert.type)
    val (icon, color) = alertVisual(type)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(type?.title ?: alert.type, style = MaterialTheme.typography.labelMedium, color = color)
            Text(
                alert.deviceLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = RcColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${alert.ip} · ${Formatters.shortDateTime(alert.createdAt)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                color = RcColors.TextDim,
            )
        }
        if (!alert.read) StatusDot(RcColors.Crimson, size = 8)
    }
}

@Composable
fun AlertsScreen(onBack: () -> Unit, onOpenDevice: (String) -> Unit) {
    val vm = appViewModel { AlertsViewModel(it) }
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = RcColors.Text)
                }
                ScreenHeader(
                    title = "Alertas",
                    subtitle = "Gerados enquanto o app está aberto",
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(onClick = { vm.markAllRead() }, enabled = !alerts.isNullOrEmpty()) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = "Marcar todos como lidos", tint = RcColors.TextDim)
                    }
                    IconButton(onClick = { confirmClear = true }, enabled = !alerts.isNullOrEmpty()) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Limpar alertas", tint = RcColors.TextDim)
                    }
                }
            }
        }
        item {
            Notice(
                "Os alertas comparam varreduras sucessivas da rede. Um dispositivo só é considerado " +
                    "desconectado após duas varreduras seguidas sem resposta."
            )
        }
        val list = alerts
        if (list != null && list.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.NotificationsNone,
                    title = "Nenhum alerta",
                    message = "Quando um dispositivo novo aparecer, voltar ou sair da rede, o aviso surge aqui.",
                )
            }
        } else if (list != null) {
            item {
                RcCard {
                    list.forEach { alert ->
                        AlertRow(alert, onClick = {
                            vm.markRead(alert.id)
                            onOpenDevice(alert.deviceKey)
                        })
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar alertas?") },
            text = { Text("O histórico de alertas será apagado. Os dispositivos conhecidos são mantidos.") },
            confirmButton = {
                TextButton(onClick = { vm.clear(); confirmClear = false }) { Text("Limpar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
            containerColor = RcColors.Surface,
        )
    }
}
