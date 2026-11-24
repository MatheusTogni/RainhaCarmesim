package com.rainhacarmesim.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rainhacarmesim.app.ui.theme.MonoBig
import com.rainhacarmesim.app.ui.theme.RcColors

data class ChartSeries(val label: String, val color: Color, val values: List<Float>)

/** Legenda simples de séries. */
@Composable
fun ChartLegend(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        series.forEachIndexed { i, s ->
            if (i > 0) Spacer(Modifier.width(16.dp))
            Box(Modifier.size(10.dp).background(s.color, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(6.dp))
            Text(s.label, style = MaterialTheme.typography.labelMedium, color = RcColors.TextDim)
        }
    }
}

/**
 * Gráfico de linhas com preenchimento suave. Todas as séries compartilham a escala vertical.
 * [maxLabel] recebe o valor máximo da escala e devolve o texto do topo do eixo.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    maxLabel: ((Float) -> String)? = null,
) {
    val maxValue = series.flatMap { it.values }.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val scaleMax = maxValue * 1.15f
    Column(modifier) {
        if (maxLabel != null) {
            Text(maxLabel(scaleMax), style = MaterialTheme.typography.labelSmall, color = RcColors.TextFaint)
            Spacer(Modifier.height(4.dp))
        }
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width
            val h = size.height
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(RcColors.Outline, Offset(0f, y), Offset(w, y), strokeWidth = 1f, pathEffect = dash)
            }
            series.forEach { s ->
                val n = s.values.size
                if (n < 2) return@forEach
                val step = w / (n - 1)
                val line = Path()
                s.values.forEachIndexed { i, v ->
                    val x = i * step
                    val y = h - (v / scaleMax).coerceIn(0f, 1f) * h
                    if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), Color.Transparent)))
                drawPath(line, s.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

/** Gráfico de barras agrupadas (ex.: download/upload por período). */
@Composable
fun BarChart(
    labels: List<String>,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    maxLabel: ((Float) -> String)? = null,
) {
    val maxValue = series.flatMap { it.values }.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Column(modifier) {
        if (maxLabel != null) {
            Text(maxLabel(maxValue), style = MaterialTheme.typography.labelSmall, color = RcColors.TextFaint)
            Spacer(Modifier.height(4.dp))
        }
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val groups = labels.size
            if (groups == 0 || series.isEmpty()) return@Canvas
            val w = size.width
            val h = size.height
            drawLine(RcColors.Outline, Offset(0f, h), Offset(w, h), strokeWidth = 1f)
            val groupWidth = w / groups
            val inner = groupWidth * 0.78f
            val barWidth = inner / series.size
            for (g in 0 until groups) {
                series.forEachIndexed { si, s ->
                    val v = s.values.getOrElse(g) { 0f }
                    val bh = (v / maxValue).coerceIn(0f, 1f) * (h - 2f)
                    if (bh <= 0f) return@forEachIndexed
                    val x = g * groupWidth + (groupWidth - inner) / 2f + si * barWidth
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(s.color, s.color.copy(alpha = 0.55f)), startY = h - bh, endY = h),
                        topLeft = Offset(x + 1f, h - bh),
                        size = Size((barWidth - 2f).coerceAtLeast(1f), bh),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            val stride = when {
                labels.size > 20 -> 5
                labels.size > 10 -> 2
                else -> 1
            }
            labels.forEachIndexed { i, label ->
                Text(
                    if (i % stride == 0) label else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = RcColors.TextFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * Medidor semicircular. [fraction] em 0..1 (escala logarítmica é responsabilidade de quem chama).
 */
@Composable
fun Gauge(
    fraction: Float,
    valueText: String,
    unitText: String,
    caption: String,
    modifier: Modifier = Modifier,
    color: Color = RcColors.Crimson,
) {
    Box(modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 24.dp, vertical = 8.dp)) {
            val stroke = 14.dp.toPx()
            val diameter = minOf(size.width, size.height * 2f) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, stroke / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(RcColors.SurfaceHigh, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val sweep = 180f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.horizontalGradient(listOf(RcColors.Bordeaux, color, RcColors.CrimsonBright)),
                    startAngle = 180f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 12.dp)) {
            Text(valueText, style = MonoBig, color = RcColors.Text)
            Text(unitText, style = MaterialTheme.typography.labelMedium, color = RcColors.TextDim)
            Spacer(Modifier.height(2.dp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = RcColors.TextFaint)
        }
    }
}
