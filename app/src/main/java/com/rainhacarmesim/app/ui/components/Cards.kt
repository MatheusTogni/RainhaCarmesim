package com.rainhacarmesim.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rainhacarmesim.app.ui.theme.MonoValue
import com.rainhacarmesim.app.ui.theme.RcColors

private val CardShape = RoundedCornerShape(18.dp)

/**
 * Card padrão: fundo grafite com leve gradiente bordô no topo e borda fina carmesim.
 */
@Composable
fun RcCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    accent: Color = RcColors.Crimson,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(CardShape)
        .background(
            Brush.verticalGradient(
                0f to accent.copy(alpha = 0.10f),
                0.45f to RcColors.Surface,
                1f to RcColors.Surface,
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), RcColors.Outline, RcColors.Outline)),
            shape = CardShape,
        )
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(16.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = RcColors.TextDim,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/** Linha "rótulo .......... valor" usada nos cards de informação. */
@Composable
fun InfoRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    unavailableText: String = "indisponível",
    valueStyle: TextStyle = MonoValue,
    valueColor: Color = RcColors.Text,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RcColors.TextDim)
        Spacer(Modifier.width(12.dp))
        if (value != null) {
            Text(value, style = valueStyle, color = valueColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            Text(unavailableText, style = MaterialTheme.typography.bodyMedium, color = RcColors.TextFaint)
        }
    }
}

/** Ponto de status; pulsa quando [pulsing] é verdadeiro. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, pulsing: Boolean = false, size: Int = 10) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val a by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
        a
    } else 1f
    Box(
        modifier
            .size(size.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}

/** Etiqueta compacta (ex.: "NOVO", "ONLINE"). */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Valor grande com rótulo, usado em métricas. */
@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    color: Color = RcColors.Text,
    icon: ImageVector? = null,
    iconTint: Color = RcColors.Crimson,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = RcColors.TextDim)
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MonoValue.copy(fontSize = MonoValue.fontSize * 1.35f), color = color)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = RcColors.TextDim, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}
