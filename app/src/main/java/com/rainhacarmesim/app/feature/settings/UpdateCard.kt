package com.rainhacarmesim.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rainhacarmesim.app.domain.Formatters
import com.rainhacarmesim.app.ui.components.InfoRow
import com.rainhacarmesim.app.ui.components.LoadingState
import com.rainhacarmesim.app.ui.components.Notice
import com.rainhacarmesim.app.ui.components.RcCard
import com.rainhacarmesim.app.ui.theme.RcColors
import com.rainhacarmesim.app.update.UpdateInfo
import com.rainhacarmesim.app.update.UpdateState

@Composable
fun UpdateCard(
    state: UpdateState,
    installedVersion: String,
    installedCode: Long,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    RcCard(title = "Atualizações", icon = Icons.Rounded.SystemUpdate) {
        InfoRow("Versão instalada", "$installedVersion ($installedCode)")
        Spacer(Modifier.height(8.dp))
        when (state) {
            UpdateState.Idle -> CheckButton(onCheck)
            UpdateState.Checking -> LoadingState("Consultando GitHub Releases…")
            is UpdateState.UpToDate -> {
                Notice(
                    "Você já está na versão mais recente (verificado em ${Formatters.shortDateTime(state.checkedAt)}).",
                    icon = Icons.Rounded.CheckCircle,
                    color = RcColors.Positive,
                )
                Spacer(Modifier.height(8.dp))
                CheckButton(onCheck)
            }
            is UpdateState.Available -> {
                ReleaseInfo(state.info)
                PrimaryButton("Baixar atualização", Icons.Rounded.Download, onDownload)
            }
            is UpdateState.Downloading -> {
                ReleaseInfo(state.info)
                val total = state.total
                val text = if (total != null) {
                    "${Formatters.bytes(state.downloaded)} de ${Formatters.bytes(total)}"
                } else Formatters.bytes(state.downloaded)
                Text("Baixando… $text", style = MaterialTheme.typography.bodySmall, color = RcColors.TextDim)
                Spacer(Modifier.height(6.dp))
                if (total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (state.downloaded.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = RcColors.Crimson,
                        trackColor = RcColors.SurfaceHigh,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = RcColors.Crimson, trackColor = RcColors.SurfaceHigh)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) { Text("Cancelar download") }
            }
            is UpdateState.ReadyToInstall -> {
                ReleaseInfo(state.info)
                Notice("Download concluído e verificado (SHA-256 confere).", icon = Icons.Rounded.CheckCircle, color = RcColors.Positive)
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Instalar", Icons.Rounded.SystemUpdate, onInstall)
            }
            is UpdateState.NeedsPermission -> {
                ReleaseInfo(state.info)
                Notice(
                    "Para instalar, autorize a Rainha Carmesim a instalar apps (\"Permitir desta fonte\") e volte aqui.",
                    icon = Icons.Rounded.Warning,
                    color = RcColors.Warning,
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Abrir autorização / instalar", Icons.Rounded.SystemUpdate, onInstall)
            }
            is UpdateState.Installing -> {
                ReleaseInfo(state.info)
                Notice("Confirme a instalação na tela do Android. Seus dados são preservados.")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("Abrir instalador novamente") }
            }
            is UpdateState.Error -> {
                Notice(state.message, icon = Icons.Rounded.Warning, color = RcColors.Danger)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        state.file != null -> PrimaryButton("Tentar instalar", Icons.Rounded.SystemUpdate, onInstall, Modifier.weight(1f))
                        state.info != null -> PrimaryButton("Tentar baixar", Icons.Rounded.Download, onDownload, Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = onCheck, modifier = Modifier.weight(1f)) { Text("Verificar") }
                }
            }
        }
    }
}

@Composable
private fun ReleaseInfo(info: UpdateInfo) {
    Text("Nova versão ${info.versionName}", style = MaterialTheme.typography.titleMedium, color = RcColors.Positive)
    if (info.publishedAt.isNotBlank()) {
        Text("Publicada em ${info.publishedAt.take(10)}", style = MaterialTheme.typography.bodySmall, color = RcColors.TextFaint)
    }
    if (info.releaseNotes.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(info.releaseNotes, style = MaterialTheme.typography.bodyMedium, color = RcColors.TextDim)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun CheckButton(onCheck: () -> Unit) {
    OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Refresh, null)
        Spacer(Modifier.width(6.dp))
        Text("Verificar atualizações")
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = RcColors.Crimson)) {
        Icon(icon, null)
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}
