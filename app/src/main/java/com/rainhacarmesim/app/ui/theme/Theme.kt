package com.rainhacarmesim.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val RcColorScheme = darkColorScheme(
    primary = RcColors.Crimson,
    onPrimary = RcColors.Text,
    primaryContainer = RcColors.Bordeaux,
    onPrimaryContainer = RcColors.Text,
    secondary = RcColors.Bordeaux,
    onSecondary = RcColors.Text,
    secondaryContainer = RcColors.BordeauxDeep,
    onSecondaryContainer = RcColors.Text,
    tertiary = RcColors.Positive,
    background = RcColors.Background,
    onBackground = RcColors.Text,
    surface = RcColors.Surface,
    onSurface = RcColors.Text,
    surfaceVariant = RcColors.SurfaceHigh,
    onSurfaceVariant = RcColors.TextDim,
    surfaceContainer = RcColors.Surface,
    surfaceContainerHigh = RcColors.SurfaceHigh,
    surfaceContainerHighest = RcColors.SurfaceHigh,
    surfaceContainerLow = RcColors.Surface,
    outline = RcColors.Outline,
    outlineVariant = RcColors.Outline,
    error = RcColors.Danger,
    onError = RcColors.Text,
)

val RcShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun RainhaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RcColorScheme,
        typography = RcTypography,
        shapes = RcShapes,
        content = content,
    )
}
