package com.rainhacarmesim.app.feature.tools

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rainhacarmesim.app.AppContainer
import com.rainhacarmesim.app.domain.Formatters
import com.rainhacarmesim.app.domain.MapBounds
import com.rainhacarmesim.app.domain.MapPoint
import com.rainhacarmesim.app.domain.SignalHeatmap
import com.rainhacarmesim.app.domain.SignalSample
import com.rainhacarmesim.app.feature.dashboard.signalLabel
import com.rainhacarmesim.app.network.SignalMapper
import com.rainhacarmesim.app.ui.appViewModel
import com.rainhacarmesim.app.ui.components.Metric
import com.rainhacarmesim.app.ui.components.Notice
import com.rainhacarmesim.app.ui.components.RcCard
import com.rainhacarmesim.app.ui.components.ScreenHeader
import com.rainhacarmesim.app.ui.theme.MonoBig
import com.rainhacarmesim.app.ui.theme.RcColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class SignalMapViewModel(c: AppContainer) : ViewModel() {
    private val mapper = c.newSignalMapper()
    val state = mapper.state
    val heading = mapper.heading
    val hasMotionSensors = mapper.hasMotionSensors

    fun start() = mapper.start(viewModelScope)
    fun stop() = mapper.stop()
    fun clear() = mapper.clear()
    fun moveTo(p: MapPoint) = mapper.moveTo(p.x, p.y)

    override fun onCleared() = mapper.stop()
}

@Composable
fun SignalMapScreen(onBack: () -> Unit) {
    val vm = appViewModel { SignalMapViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val heading by vm.heading.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    // Tela acesa enquanto mapeia; sensores desligados ao sair da tela ou do app.
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) { onDispose { vm.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.stop() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = RcColors.Text)
                }
                ScreenHeader(
                    title = "Mapa de sinal Wi-Fi",
                    subtitle = "Ande pelo local para medir o sinal",
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(onClick = { confirmClear = true }, enabled = state.samples.isNotEmpty() || state.steps > 0) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Limpar mapa", tint = RcColors.TextDim)
                    }
                }
            }
        }
        item { CurrentSignalCard(state) }
        item {
            RcCard(title = "Mapa", icon = Icons.Rounded.Map) {
                SignalMapCanvas(state, heading, onTap = vm::moveTo)
                Spacer(Modifier.height(10.dp))
                SignalLegend()
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { if (state.running) vm.stop() else vm.start() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RcColors.Crimson, contentColor = Color.White),
                ) {
                    Icon(if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            state.running -> "Pausar"
                            state.samples.isEmpty() && state.steps == 0 -> "Iniciar mapeamento"
                            else -> "Continuar"
                        }
                    )
                }
            }
        }
        item {
            RcCard(title = "Como usar", icon = Icons.AutoMirrored.Rounded.DirectionsWalk) {
                Notice(
                    "Segure o celular à sua frente, apontando para onde você anda, e caminhe devagar pelo local. " +
                        "A direção em que você está ao iniciar fica para cima no mapa.",
                )
                Spacer(Modifier.height(8.dp))
                Notice(
                    "A posição é estimada pelos seus passos e pode desviar com o tempo. Toque no mapa para " +
                        "corrigir onde você está.",
                )
                if (!vm.hasMotionSensors) {
                    Spacer(Modifier.height(8.dp))
                    Notice(
                        "Este celular não tem os sensores de movimento necessários. Toque no mapa para marcar " +
                            "onde você está a cada parada.",
                        icon = Icons.Rounded.Warning,
                        color = RcColors.Warning,
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar o mapa?") },
            text = { Text("As medições serão apagadas e o ponto atual vira o novo ponto de partida.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clear() }) { Text("Limpar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CurrentSignalCard(state: SignalMapper.State) {
    RcCard(title = "Sinal agora", icon = Icons.Rounded.Wifi) {
        if (!state.onWifi) {
            Notice(
                "Conecte-se a uma rede Wi-Fi para medir o sinal.",
                icon = Icons.Rounded.Warning,
                color = RcColors.Warning,
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            val rssi = state.rssi
            Text(
                rssi?.toString() ?: "—",
                style = MonoBig,
                color = rssi?.let { Color(SignalHeatmap.colorFor(it.toFloat())) } ?: RcColors.TextDim,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (rssi != null) "dBm · ${signalLabel(rssi)}" else "dBm",
                style = MaterialTheme.typography.labelLarge,
                color = RcColors.TextDim,
            )
        }
        Spacer(Modifier.height(12.dp))
        val summary = SignalHeatmap.summary(state.samples)
        Row(Modifier.fillMaxWidth()) {
            Metric("Passos", state.steps.toString(), Modifier.weight(1f))
            Metric("Distância", Formatters.number(state.distanceM.toDouble(), 1), Modifier.weight(1f), unit = "m")
            Metric("Pontos", state.samples.size.toString(), Modifier.weight(1f))
        }
        if (summary != null) {
            Spacer(Modifier.height(10.dp))
            val (worst, avg, best) = summary
            Row(Modifier.fillMaxWidth()) {
                Metric("Pior", worst.roundToInt().toString(), Modifier.weight(1f), unit = "dBm", color = Color(SignalHeatmap.colorFor(worst)))
                Metric("Média", avg.roundToInt().toString(), Modifier.weight(1f), unit = "dBm", color = Color(SignalHeatmap.colorFor(avg)))
                Metric("Melhor", best.roundToInt().toString(), Modifier.weight(1f), unit = "dBm", color = Color(SignalHeatmap.colorFor(best)))
            }
        }
    }
}

/** Escala de cores do mapa (dBm). */
@Composable
private fun SignalLegend() {
    val colors = remember {
        (0..8).map { Color(SignalHeatmap.colorFor(SignalHeatmap.SCALE_MIN_DBM + it * 5f)) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(colors)),
    )
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("−90 fraco", "−70", "−50 dBm ótimo").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = RcColors.TextFaint)
        }
    }
}

/** Distância entre as linhas da grade, em metros, conforme o tamanho da área mostrada. */
private fun gridStep(span: Float): Float = when {
    span <= 16f -> 1f
    span <= 40f -> 2f
    else -> 5f
}

@Composable
private fun SignalMapCanvas(
    state: SignalMapper.State,
    heading: Float,
    onTap: (MapPoint) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val aspect = if (canvasSize.height > 0) canvasSize.width.toFloat() / canvasSize.height else 1f
    val target = remember(state.path, state.position, aspect) {
        SignalHeatmap.boundsFor(state.path.flatten() + state.position, aspect)
    }
    // A área visível desliza até a nova, em vez de pular a cada passo.
    val spec = tween<Float>(500, easing = FastOutSlowInEasing)
    val minX by animateFloatAsState(target.minX, spec, label = "minX")
    val minY by animateFloatAsState(target.minY, spec, label = "minY")
    val maxX by animateFloatAsState(target.maxX, spec, label = "maxX")
    val maxY by animateFloatAsState(target.maxY, spec, label = "maxY")
    val shown = MapBounds(minX, minY, maxX, maxY)

    // Mapa de calor calculado fora da thread principal, sobre a área-alvo.
    val heat by produceState<Pair<ImageBitmap, MapBounds>?>(null, state.samples, target) {
        value = renderHeat(state.samples, target)
    }

    val currentShown by rememberUpdatedState(shown)
    val currentSize by rememberUpdatedState(canvasSize)
    val gridLabel = "Quadrícula de ${Formatters.number(gridStep(target.width).toDouble(), 0)} m"

    Box {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RcColors.Background)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val b = currentShown
                        val s = currentSize
                        if (s.width > 0 && s.height > 0) {
                            onTap(MapPoint(b.minX + o.x / s.width * b.width, b.maxY - o.y / s.height * b.height))
                        }
                    }
                },
        ) {
            if (shown.width <= 0f || shown.height <= 0f) return@Canvas
            val sx = this.size.width / shown.width
            val sy = this.size.height / shown.height
            fun toScreen(p: MapPoint) = Offset((p.x - shown.minX) * sx, (shown.maxY - p.y) * sy)

            // Grade em metros.
            val step = gridStep(target.width)
            var gx = kotlin.math.floor(shown.minX / step) * step
            while (gx <= shown.maxX) {
                val x = (gx - shown.minX) * sx
                drawLine(RcColors.Outline.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, this.size.height), 1f)
                gx += step
            }
            var gy = kotlin.math.floor(shown.minY / step) * step
            while (gy <= shown.maxY) {
                val y = (shown.maxY - gy) * sy
                drawLine(RcColors.Outline.copy(alpha = 0.45f), Offset(0f, y), Offset(this.size.width, y), 1f)
                gy += step
            }

            // Mapa de calor (ampliado com filtro bilinear, o que suaviza as transições de cor).
            heat?.let { (img, b) ->
                val tl = toScreen(MapPoint(b.minX, b.maxY))
                val br = toScreen(MapPoint(b.maxX, b.minY))
                drawImage(
                    image = img,
                    dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()),
                    dstSize = IntSize((br.x - tl.x).roundToInt(), (br.y - tl.y).roundToInt()),
                    filterQuality = FilterQuality.Low,
                )
            }

            // Caminho percorrido.
            state.path.forEach { seg ->
                if (seg.size < 2) return@forEach
                val p = Path()
                seg.forEachIndexed { i, pt ->
                    val o = toScreen(pt)
                    if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
                }
                drawPath(
                    p,
                    Color.White.copy(alpha = 0.55f),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // Ponto de partida.
            drawCircle(RcColors.TextDim, 5.dp.toPx(), toScreen(MapPoint(0f, 0f)), style = Stroke(2.dp.toPx()))

            // Posição atual com a seta da direção.
            val pos = toScreen(state.position)
            rotate(heading, pivot = pos) {
                val arrow = Path().apply {
                    moveTo(pos.x, pos.y - 20.dp.toPx())
                    lineTo(pos.x - 7.dp.toPx(), pos.y - 8.dp.toPx())
                    lineTo(pos.x + 7.dp.toPx(), pos.y - 8.dp.toPx())
                    close()
                }
                drawPath(arrow, RcColors.Crimson)
            }
            drawCircle(Color.White, 9.dp.toPx(), pos)
            drawCircle(RcColors.Crimson, 6.5.dp.toPx(), pos)
        }
        Text(
            gridLabel,
            style = MaterialTheme.typography.labelSmall,
            color = RcColors.TextFaint,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
    }
}

private suspend fun renderHeat(samples: List<SignalSample>, bounds: MapBounds): Pair<ImageBitmap, MapBounds>? {
    if (samples.isEmpty()) return null
    return withContext(Dispatchers.Default) {
        // ~5 pixels por metro, com limite para não pesar em áreas grandes.
        val w = (bounds.width * 5f).roundToInt().coerceIn(16, 200)
        val h = (bounds.height * 5f).roundToInt().coerceIn(16, 200)
        val pixels = SignalHeatmap.render(samples, bounds, w, h)
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap() to bounds
    }
}
