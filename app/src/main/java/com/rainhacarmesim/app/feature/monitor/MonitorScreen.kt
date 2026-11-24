package com.rainhacarmesim.app.feature.monitor

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainhacarmesim.app.domain.Formatters
import com.rainhacarmesim.app.network.TrafficSampler
import com.rainhacarmesim.app.ui.appViewModel
import com.rainhacarmesim.app.ui.components.BarChart
import com.rainhacarmesim.app.ui.components.ChartLegend
import com.rainhacarmesim.app.ui.components.ChartSeries
import com.rainhacarmesim.app.ui.components.EmptyState
import com.rainhacarmesim.app.ui.components.ErrorState
import com.rainhacarmesim.app.ui.components.InfoRow
import com.rainhacarmesim.app.ui.components.LineChart
import com.rainhacarmesim.app.ui.components.LoadingState
import com.rainhacarmesim.app.ui.components.Metric
import com.rainhacarmesim.app.ui.components.Notice
import com.rainhacarmesim.app.ui.components.RcCard
import com.rainhacarmesim.app.ui.components.ScreenHeader
import com.rainhacarmesim.app.ui.theme.RcColors

@Composable
fun MonitorScreen() {
    val vm = appViewModel { MonitorViewModel(it) }
    val current by vm.current.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val supported by vm.supported.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Recarrega ao voltar (ex.: após conceder "Acesso de uso" nas configurações).
    LifecycleResumeEffect(Unit) {
        vm.reload()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Monitor", "Tráfego deste celular") }

        item {
            RcCard(title = "Agora", icon = Icons.Rounded.ShowChart) {
                if (!supported) {
                    Notice("Este aparelho não disponibiliza contadores de tráfego (TrafficStats).", color = RcColors.Warning)
                    return@RcCard
                }
                val c = current
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val down = c?.let { Formatters.bitRate(it.rxBytesPerSec) }
                    val up = c?.let { Formatters.bitRate(it.txBytesPerSec) }
                    Metric("download", down?.first ?: "—", unit = down?.second, icon = Icons.Rounded.ArrowDownward)
                    Metric(
                        "upload", up?.first ?: "—", unit = up?.second,
                        icon = Icons.Rounded.ArrowUpward, iconTint = RcColors.Upload,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (live.size >= 2) {
                    val series = listOf(
                        ChartSeries("download", RcColors.Crimson, live.map { (it.rxBytesPerSec * 8 / 1e6).toFloat() }),
                        ChartSeries("upload", RcColors.Upload, live.map { (it.txBytesPerSec * 8 / 1e6).toFloat() }),
                    )
                    LineChart(
                        series,
                        maxLabel = { "${Formatters.number(it.toDouble())} Mbps" },
                        capacity = TrafficSampler.HISTORY_SIZE,
                        scrollKey = live.last().timestamp,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChartLegend(series)
                    Text("Últimos ${live.size} segundos", style = MaterialTheme.typography.labelSmall, color = RcColors.TextFaint)
                } else {
                    Text("Coletando amostras…", style = MaterialTheme.typography.bodySmall, color = RcColors.TextDim)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.entries.forEach { p ->
                    FilterChip(
                        selected = history.period == p,
                        onClick = { vm.selectPeriod(p) },
                        label = { Text(p.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RcColors.Bordeaux),
                    )
                }
            }
        }

        item {
            RcCard(title = "Consumo de dados", icon = Icons.Rounded.BarChart) {
                val h = history
                when {
                    h.loading -> LoadingState("Carregando histórico…")
                    h.error != null -> ErrorState(h.error, onRetry = vm::reload)
                    h.totalRx == 0L && h.totalTx == 0L -> EmptyState(
                        icon = Icons.Rounded.BarChart,
                        title = "Sem dados no período",
                        message = if (h.source == HistorySource.APP_SAMPLES)
                            "O histórico é registrado enquanto o app está aberto. Volte mais tarde ou conceda o Acesso de uso."
                        else "O Android não registrou tráfego neste período.",
                    )
                    else -> {
                        val series = listOf(
                            ChartSeries("download", RcColors.Crimson, h.bars.map { it.rx.toFloat() }),
                            ChartSeries("upload", RcColors.Upload, h.bars.map { it.tx.toFloat() }),
                        )
                        BarChart(
                            labels = h.bars.map { it.label },
                            series = series,
                            maxLabel = { Formatters.bytes(it.toLong()) },
                        )
                        Spacer(Modifier.height(6.dp))
                        ChartLegend(series)
                        Spacer(Modifier.height(10.dp))
                        InfoRow("Recebido", Formatters.bytes(h.totalRx))
                        InfoRow("Enviado", Formatters.bytes(h.totalTx))
                        if (h.source == HistorySource.SYSTEM) {
                            InfoRow("Via Wi-Fi", h.wifiTotal?.let { Formatters.bytes(it) })
                            InfoRow("Via dados móveis", h.mobileTotal?.let { Formatters.bytes(it) })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (h.source == HistorySource.SYSTEM) {
                    Notice("Fonte: registros do Android (NetworkStatsManager), inclusive com o app fechado.")
                } else {
                    Notice("Fonte: amostras do próprio app — cobre apenas os períodos em que ele esteve aberto.")
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) { Text("Conceder Acesso de uso (opcional)") }
                }
            }
        }

        item {
            RcCard(title = "Sobre o tráfego de outros aparelhos", icon = Icons.Rounded.Info, accent = RcColors.TextDim) {
                Text(
                    "Os valores acima são apenas do tráfego deste celular. O Android não permite que um app " +
                        "veja o tráfego de outros dispositivos conectados ao mesmo Wi-Fi; para isso seria preciso " +
                        "consultar o roteador.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RcColors.TextDim,
                )
            }
        }
    }
}
