package com.rainhacarmesim.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.rainhacarmesim.app.RainhaApp

/** Recebe o resultado da sessão do PackageInstaller. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = (context.applicationContext as RainhaApp).container.updateManager
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                        manager.onInstallerShown()
                    } catch (e: Exception) {
                        manager.onInstallFailed("Não foi possível abrir o instalador: ${e.message}")
                    }
                } else {
                    manager.onInstallFailed("O sistema não forneceu a tela de confirmação")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> manager.onInstallSucceeded()
            PackageInstaller.STATUS_FAILURE_ABORTED -> manager.onInstallFailed("Instalação cancelada")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                manager.onInstallFailed("Instalação não concluída (código $status)${if (msg != null) ": $msg" else ""}")
            }
        }
    }
}
