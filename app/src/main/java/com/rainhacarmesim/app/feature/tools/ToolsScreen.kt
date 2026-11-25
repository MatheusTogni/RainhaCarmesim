package com.rainhacarmesim.app.feature.tools

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.rainhacarmesim.app.ui.components.Notice
import com.rainhacarmesim.app.ui.components.RcCard
import com.rainhacarmesim.app.ui.components.ScreenHeader
import com.rainhacarmesim.app.ui.theme.MonoFamily
import com.rainhacarmesim.app.ui.theme.MonoValue
import com.rainhacarmesim.app.ui.theme.RcColors
import kotlin.math.log10

/** Cantos dos controles dentro dos cards (o card tem 18 dp; 14 dp evita a "pílula dentro de pílula"). */
private val ControlShape = RoundedCornerShape(14.dp)

/** Altura mínima de toque dos botões principais. */
private val ControlHeight = 48.dp

/** Intervalo entre pacotes do ping (o PingRunner envia 1 por segundo, em ICMP e em TCP). */
private const val PING_INTERVAL_MS = 1_000

@Composable
fun ToolsScreen(initialTarget: String?, onOpenSignalMap: () -> Unit) {
    val vm = appViewModel { ToolsViewModel(it) }
    val ping by vm.ping.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val history by vm.speedHistory.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Ferramentas", "Ping, velocidade e mapa de sinal") }
        item { PingCard(vm, ping, initialTarget) }
        item { SpeedTestCard(vm, speed, last = history.firstOrNull()) }
        item { SignalMapEntryCard(onOpenSignalMap) }
        if (history.isNotEmpty()) {
            item { SpeedHistoryCard(history) }
        }
    }
}

// ───────────────────────────── Componentes locais ─────────────────────────────

private class Stat(val label: String, val value: String, val unit: String? = null, val color: Color = RcColors.Text)

/** Faixa com 2–3 valores lado a lado, centralizados e separados por filetes (cabe em telas de 360 dp). */
@Composable
private fun StatStrip(stats: List<Stat>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(RcColors.Background.copy(alpha = 0.45f))
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { i, s ->
            if (i > 0) Box(Modifier.width(1.dp).height(30.dp).background(RcColors.Outline))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.label, style = MaterialTheme.typography.labelMedium, color = RcColors.TextDim, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(s.value, style = MonoValue.copy(fontSize = 16.sp), color = s.color, maxLines = 1)
                    if (s.unit != null) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            s.unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = RcColors.TextDim,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Ação principal do card: carmesim cheio, largura total, alvo de toque de 48 dp. */
@Composable
private fun PrimaryAction(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(ControlHeight),
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(containerColor = RcColors.Crimson, contentColor = Color.White),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Ação secundária (cancelar/parar): contornada, mesmo tamanho da principal. */
@Composable
private fun SecondaryAction(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(ControlHeight),
        shape = ControlShape,
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ───────────────────────────────── Ping ─────────────────────────────────

@Composable
private fun PingCard(vm: ToolsViewModel, state: PingUiState, initialTarget: String?) {
    var target by rememberSaveable(initialTarget) { mutableStateOf(initialTarget ?: "") }
    var count by rememberSaveable { mutableIntStateOf(4) }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val error = inputError

    fun start() {
        inputError = vm.validate(target)
        if (inputError == null) {
            focus.clearFocus()
            vm.startPing(target, count)
        }
    }

    RcCard(title = "Ping", icon = Icons.Rounded.NetworkCheck) {
        // Campo + botão de executar na mesma linha: o ▶ fica ao alcance do polegar e economiza uma linha inteira.
        Row(verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.take(253); inputError = null },
                label = { Text("IP ou hostname") },
                placeholder = { Text("ex.: 192.168.0.1") },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) { { Text(error) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { start() }),
                modifier = Modifier.weight(1f),
                shape = ControlShape,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RcColors.Crimson,
                    focusedLabelColor = RcColors.Crimson,
                    cursorColor = RcColors.Crimson,
                    unfocusedBorderColor = RcColors.Outline,
                ),
            )
            Spacer(Modifier.width(10.dp))
            FilledIconButton(
                onClick = { if (state.running) vm.stopPing() else start() },
                modifier = Modifier.size(56.dp),
                shape = ControlShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (state.running) RcColors.Bordeaux else RcColors.Crimson,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    if (state.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.running) "Parar ping" else "Executar ping",
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pacotes", style = MaterialTheme.typography.bodyMedium, color = RcColors.TextDim)
            listOf(4, 10, 20).forEach { n ->
                FilterChip(
                    selected = count == n,
                    onClick = { count = n },
                    enabled = !state.running,
                    label = { Text("$n") },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RcColors.Bordeaux,
                        selectedLabelColor = RcColors.Text,
                    ),
                )
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
                    // Modo ao vivo (igual ao do teste de velocidade): eixo fixo no nº de pacotes, a ponta desliza
                    // até cada resposta (o ping envia 1 por segundo) e a escala vertical é animada.
                    capacity = state.count.coerceAtLeast(2),
                    revealMillis = PING_INTERVAL_MS,
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
    val lossColor = when {
        r.lossPercent <= 0.0 -> RcColors.Positive
        r.lossPercent < 20.0 -> RcColors.Warning
        else -> RcColors.Danger
    }
    StatStrip(
        listOf(
            Stat("enviados", "${r.transmitted}"),
            Stat("recebidos", "${r.received}"),
            Stat("perda", Formatters.number(r.lossPercent, 0), unit = "%", color = lossColor),
        )
    )
    Spacer(Modifier.height(6.dp))
    InfoRow("Endereço", r.resolvedAddress)
    InfoRow("Latência mín.", r.minMs?.let { Formatters.millis(it) })
    InfoRow("Latência média", r.avgMs?.let { Formatters.millis(it) })
    InfoRow("Latência máx.", r.maxMs?.let { Formatters.millis(it) })
    InfoRow("Método", r.method, valueStyle = MaterialTheme.typography.bodyMedium)
}

// ───────────────────────────── Teste de velocidade ─────────────────────────────

private fun gaugeFraction(mbps: Double): Float = (log10(mbps + 1.0) / log10(1001.0)).toFloat().coerceIn(0f, 1f)

/** Resultado exibido com o medidor quando não há teste em andamento. */
private class SpeedSummary(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val latencyMs: Double,
    val jitterMs: Double,
    val caption: String,
    val footnote: String,
)

@Composable
private fun SpeedTestCard(vm: ToolsViewModel, state: SpeedUiState, last: SpeedTestEntity?) {
    var confirmMobile by rememberSaveable { mutableStateOf(false) }

    RcCard(title = "Teste de velocidade", icon = Icons.Rounded.Speed) {
        when (state) {
            is SpeedUiState.Running -> {
                val (caption, color) = when (state.stage) {
                    SpeedTestClient.Stage.LATENCY -> "Medindo latência…" to RcColors.Crimson
                    SpeedTestClient.Stage.DOWNLOAD -> "Download" to RcColors.Crimson
                    SpeedTestClient.Stage.UPLOAD -> "Upload" to RcColors.Upload
                    SpeedTestClient.Stage.DONE -> "Concluindo…" to RcColors.Crimson
                }
                // Valor e ponteiro animados entre as amostras, para o medidor deslizar em vez de pular.
                val mbps by animateFloatAsState(
                    (state.currentMbps ?: 0.0).toFloat(),
                    tween(350, easing = FastOutSlowInEasing),
                    label = "mbps",
                )
                Gauge(
                    fraction = gaugeFraction(mbps.toDouble()),
                    valueText = if (state.currentMbps == null) "—" else Formatters.mbps(mbps.toDouble()),
                    unitText = "Mbps",
                    caption = caption,
                    color = color,
                )
                if (state.downloadSeries.size >= 2 || state.uploadSeries.size >= 2) {
                    val series = buildList {
                        if (state.downloadSeries.size >= 2) add(ChartSeries("download", RcColors.Crimson, state.downloadSeries))
                        if (state.uploadSeries.size >= 2) add(ChartSeries("upload", RcColors.Upload, state.uploadSeries))
                    }
                    LineChart(
                        series,
                        maxLabel = { "${Formatters.number(it.toDouble())} Mbps" },
                        capacity = SpeedTestClient.SAMPLES_PER_STAGE,
                        revealMillis = SpeedTestClient.REPORT_INTERVAL_MS.toInt(),
                    )
                    ChartLegend(series)
                }
                Spacer(Modifier.height(12.dp))
                SecondaryAction("Cancelar", Icons.Rounded.Stop, vm::cancelSpeedTest)
            }

            else -> {
                // Em repouso o card mostra o último resultado (ou um medidor vazio), em vez de ficar só com um botão.
                val summary = when {
                    state is SpeedUiState.Done -> state.result.let {
                        SpeedSummary(
                            it.downloadMbps, it.uploadMbps, it.latencyMs, it.jitterMs,
                            caption = "download",
                            footnote = "Dados usados: ${Formatters.bytes(it.downloadedBytes + it.uploadedBytes)}",
                        )
                    }
                    last != null -> SpeedSummary(
                        last.downloadMbps, last.uploadMbps, last.latencyMs, last.jitterMs,
                        caption = "último download",
                        footnote = "Último teste · ${Formatters.shortDateTime(last.timestamp)} · ${last.connection}",
                    )
                    else -> null
                }

                if (summary != null) {
                    Gauge(
                        fraction = gaugeFraction(summary.downloadMbps),
                        valueText = Formatters.mbps(summary.downloadMbps),
                        unitText = "Mbps",
                        caption = summary.caption,
                    )
                    Spacer(Modifier.height(4.dp))
                    StatStrip(
                        listOf(
                            Stat("upload", Formatters.mbps(summary.uploadMbps), "Mbps", RcColors.Upload),
                            Stat("latência", Formatters.number(summary.latencyMs, 0), "ms"),
                            Stat("jitter", Formatters.number(summary.jitterMs, 1), "ms"),
                        )
                    )
                } else {
                    Gauge(fraction = 0f, valueText = "—", unitText = "Mbps", caption = "pronto para testar")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    summary?.footnote ?: "Mede latência, download e upload.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RcColors.TextFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                if (state is SpeedUiState.Error) {
                    Notice(state.message, icon = Icons.Rounded.Warning, color = RcColors.Danger)
                    Spacer(Modifier.height(12.dp))
                }
                PrimaryAction(
                    text = if (summary != null) "Testar novamente" else "Iniciar teste",
                    icon = Icons.Rounded.Speed,
                    onClick = { if (vm.isOnMobileData()) confirmMobile = true else vm.startSpeedTest() },
                )
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

// ───────────────────────────── Mapa de sinal ─────────────────────────────

/** Atalho de navegação: o card inteiro é tocável (sem botão, pois só abre outra tela). */
@Composable
private fun SignalMapEntryCard(onOpen: () -> Unit) {
    RcCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(RcColors.BordeauxDeep),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Map, null, tint = RcColors.Crimson)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mapa de sinal Wi-Fi", style = MaterialTheme.typography.titleMedium, color = RcColors.Text)
                Text(
                    "Veja num mapa de calor onde o sinal é forte ou fraco",
                    style = MaterialTheme.typography.bodySmall,
                    color = RcColors.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = RcColors.TextFaint)
        }
    }
}

// ───────────────────────────────── Histórico ─────────────────────────────────

@Composable
private fun SpeedHistoryCard(history: List<SpeedTestEntity>) {
    RcCard(title = "Histórico de testes", icon = Icons.Rounded.History) {
        history.take(10).forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = RcColors.Outline.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Formatters.shortDateTime(t.timestamp), style = MaterialTheme.typography.bodyMedium, color = RcColors.Text)
                    Text(t.connection, style = MaterialTheme.typography.bodySmall, color = RcColors.TextFaint)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = RcColors.Crimson)) { append("↓ ") }
                            append(Formatters.mbps(t.downloadMbps))
                            withStyle(SpanStyle(color = RcColors.Upload)) { append("   ↑ ") }
                            append(Formatters.mbps(t.uploadMbps))
                            withStyle(SpanStyle(color = RcColors.TextDim)) { append(" Mbps") }
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                        color = RcColors.Text,
                        maxLines = 1,
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
