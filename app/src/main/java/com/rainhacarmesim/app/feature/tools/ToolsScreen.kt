package com.rainhacarmesim.app.feature.tools

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainhacarmesim.app.data.db.SpeedTestEntity
import com.rainhacarmesim.app.domain.Formatters
import com.rainhacarmesim.app.network.PingResult
import com.rainhacarmesim.app.network.SpeedTestClient
import com.rainhacarmesim.app.ui.appViewModel
import com.rainhacarmesim.app.ui.components.ChartLegend
import com.rainhacarmesim.app.ui.components.ChartSeries
import com.rainhacarmesim.app.ui.components.Gauge
import com.rainhacarmesim.app.ui.components.InfoRow
import com.rainhacarmesim.app.ui.components.LineChart
import com.rainhacarmesim.app.ui.components.Metric
import com.rainhacarmesim.app.ui.components.Notice
import com.rainhacarmesim.app.ui.components.RcCard
import com.rainhacarmesim.app.ui.components.ScreenHeader
import com.rainhacarmesim.app.ui.theme.MonoFamily
import com.rainhacarmesim.app.ui.theme.RcColors
import kotlin.math.log10

@Composable
fun ToolsScreen(initialTarget: String?) {
    val vm = appViewModel { ToolsViewModel(it) }
    val ping by vm.ping.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val history by vm.speedHistory.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Ferramentas", "Ping e teste de velocidade") }
        item { PingCard(vm, ping, initialTarget) }
        item { SpeedTestCard(vm, speed) }
        if (history.isNotEmpty()) {
            item { SpeedHistoryCard(history) }
        }
    }
}

@Composable
private fun PingCard(vm: ToolsViewModel, state: PingUiState, initialTarget: String?) {
    var target by rememberSaveable(initialTarget) { mutableStateOf(initialTarget ?: "") }
    var count by rememberSaveable { mutableIntStateOf(4) }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    fun start() {
        inputError = vm.validate(target)
        if (inputError == null) {
            focus.clearFocus()
            vm.startPing(target, count)
        }
    }

    RcCard(title = "Ping", icon = Icons.Rounded.NetworkCheck) {
        OutlinedTextField(
            value = target,
            onValueChange = { target = it.take(253); inputError = null },
            label = { Text("IP ou hostname") },
            placeholder = { Text("ex.: 192.168.0.1 ou google.com") },
            singleLine = true,
            isError = inputError != null,
            supportingText = { inputError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { start() }),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RcColors.Crimson,
                focusedLabelColor = RcColors.Crimson,
                cursorColor = RcColors.Crimson,
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pacotes:", style = MaterialTheme.typography.bodyMedium, color = RcColors.TextDim)
            listOf(4, 10, 20).forEach { n ->
                FilterChip(
                    selected = count == n,
                    onClick = { count = n },
                    enabled = !state.running,
                    label = { Text("$n") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RcColors.Bordeaux),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.running) {
            OutlinedButton(onClick = vm::stopPing, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Stop, null)
                Spacer(Modifier.width(6.dp))
                Text("Parar")
            }
        } else {
            Button(
                onClick = { start() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = RcColors.Crimson),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Executar ping")
            }
        }

        state.error?.let {
            Spacer(Modifier.height(10.dp))
            Notice(it, icon = Icons.Rounded.Warning, color = RcColors.Warning)
        }

        if (state.replies.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val times = state.replies.map { it.timeMs?.toFloat() ?: 0f }
            if (times.size >= 2) {
                LineChart(
                    series = listOf(ChartSeries("latência", RcColors.Crimson, times)),
                    maxLabel = { "${Formatters.number(it.toDouble(), 0)} ms" },
                )
                Spacer(Modifier.height(8.dp))
            }
            state.replies.takeLast(6).forEach { r ->
                Text(
                    if (r.timeMs != null) "seq=${r.seq}  tempo=${Formatters.millis(r.timeMs)}" else "seq=${r.seq}  sem resposta",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                    color = if (r.timeMs != null) RcColors.TextDim else RcColors.Danger,
                )
            }
        }

        state.result?.let { PingSummary(it) }
    }
}

@Composable
private fun PingSummary(r: PingResult) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = RcColors.Outline)
    Spacer(Modifier.height(8.dp))
    val lossColor = when {
        r.lossPercent <= 0.0 -> RcColors.Positive
        r.lossPercent < 20.0 -> RcColors.Warning
        else -> RcColors.Danger
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Metric("enviados", "${r.transmitted}")
        Metric("recebidos", "${r.received}")
        Metric("perda", Formatters.number(r.lossPercent, 0), unit = "%", color = lossColor)
    }
    Spacer(Modifier.height(8.dp))
    InfoRow("Endereço", r.resolvedAddress)
    InfoRow("Latência mín.", r.minMs?.let { Formatters.millis(it) })
    InfoRow("Latência média", r.avgMs?.let { Formatters.millis(it) })
    InfoRow("Latência máx.", r.maxMs?.let { Formatters.millis(it) })
    InfoRow("Método", r.method, valueStyle = MaterialTheme.typography.bodyMedium)
}

private fun gaugeFraction(mbps: Double): Float = (log10(mbps + 1.0) / log10(1001.0)).toFloat().coerceIn(0f, 1f)

@Composable
private fun SpeedTestCard(vm: ToolsViewModel, state: SpeedUiState) {
    var confirmMobile by rememberSaveable { mutableStateOf(false) }

    RcCard(title = "Teste de velocidade", icon = Icons.Rounded.Speed) {
        Notice(
            "O teste usa a internet (servidor ${SpeedTestClient.SERVER}, via HTTPS) e pode transferir " +
                "centenas de MB. Em dados móveis, isso consome sua franquia.",
            icon = Icons.Rounded.Warning,
            color = RcColors.Warning,
        )
        Spacer(Modifier.height(12.dp))

        when (state) {
            is SpeedUiState.Running -> {
                val (caption, color) = when (state.stage) {
                    SpeedTestClient.Stage.LATENCY -> "Medindo latência…" to RcColors.Crimson
                    SpeedTestClient.Stage.DOWNLOAD -> "Download" to RcColors.Crimson
                    SpeedTestClient.Stage.UPLOAD -> "Upload" to RcColors.Upload
                    SpeedTestClient.Stage.DONE -> "Concluindo…" to RcColors.Crimson
                }
                val mbps = state.currentMbps ?: 0.0
                Gauge(
                    fraction = gaugeFraction(mbps),
                    valueText = if (state.currentMbps == null) "—" else Formatters.mbps(mbps),
                    unitText = "Mbps",
                    caption = caption,
                    color = color,
                )
                if (state.downloadSeries.size >= 2 || state.uploadSeries.size >= 2) {
                    val series = buildList {
                        if (state.downloadSeries.size >= 2) add(ChartSeries("download", RcColors.Crimson, state.downloadSeries))
                        if (state.uploadSeries.size >= 2) add(ChartSeries("upload", RcColors.Upload, state.uploadSeries))
                    }
                    LineChart(series, maxLabel = { "${Formatters.number(it.toDouble())} Mbps" })
                    ChartLegend(series)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::cancelSpeedTest, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Cancelar")
                }
            }
            else -> {
                if (state is SpeedUiState.Done) {
                    val r = state.result
                    Gauge(
                        fraction = gaugeFraction(r.downloadMbps),
                        valueText = Formatters.mbps(r.downloadMbps),
                        unitText = "Mbps",
                        caption = "download",
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("upload", Formatters.mbps(r.uploadMbps), unit = "Mbps", color = RcColors.Upload)
                        Metric("latência", Formatters.number(r.latencyMs, 0), unit = "ms")
                        Metric("jitter", Formatters.number(r.jitterMs, 1), unit = "ms")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Dados usados: ${Formatters.bytes(r.downloadedBytes + r.uploadedBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = RcColors.TextFaint,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (state is SpeedUiState.Error) {
                    Notice(state.message, icon = Icons.Rounded.Warning, color = RcColors.Danger)
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = { if (vm.isOnMobileData()) confirmMobile = true else vm.startSpeedTest() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RcColors.Crimson),
                ) {
                    Icon(Icons.Rounded.Speed, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state is SpeedUiState.Done) "Testar novamente" else "Iniciar teste")
                }
            }
        }
    }

    if (confirmMobile) {
        AlertDialog(
            onDismissRequest = { confirmMobile = false },
            title = { Text("Usar dados móveis?") },
            text = { Text("Você está em dados móveis. O teste pode consumir até cerca de 370 MB da sua franquia.") },
            confirmButton = {
                TextButton(onClick = { confirmMobile = false; vm.startSpeedTest() }) { Text("Testar mesmo assim") }
            },
            dismissButton = { TextButton(onClick = { confirmMobile = false }) { Text("Cancelar") } },
            containerColor = RcColors.Surface,
        )
    }
}

@Composable
private fun SpeedHistoryCard(history: List<SpeedTestEntity>) {
    RcCard(title = "Histórico de testes", icon = Icons.Rounded.History) {
        history.take(10).forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Formatters.shortDateTime(t.timestamp), style = MaterialTheme.typography.bodyMedium, color = RcColors.Text)
                    Text(t.connection, style = MaterialTheme.typography.bodySmall, color = RcColors.TextFaint)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "↓ ${Formatters.mbps(t.downloadMbps)}  ↑ ${Formatters.mbps(t.uploadMbps)} Mbps",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                        color = RcColors.Text,
                    )
                    Text(
                        "${Formatters.number(t.latencyMs, 0)} ms",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                        color = RcColors.TextDim,
                    )
                }
            }
        }
    }
}
