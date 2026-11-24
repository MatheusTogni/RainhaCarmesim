package com.rainhacarmesim.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rainhacarmesim.app.ui.theme.RcColors

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = RcColors.Crimson, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(message, color = RcColors.TextDim, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = RcColors.Bordeaux, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = RcColors.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = RcColors.TextDim, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyState(
        icon = Icons.Rounded.ErrorOutline,
        title = "Algo deu errado",
        message = message,
        modifier = modifier,
        actionLabel = if (onRetry != null) "Tentar novamente" else null,
        onAction = onRetry,
    )
}

/** Aviso informativo em linha (ex.: limitações do Android). */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Info,
    color: Color = RcColors.TextDim,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
