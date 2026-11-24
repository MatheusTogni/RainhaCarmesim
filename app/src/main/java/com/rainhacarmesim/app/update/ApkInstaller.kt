package com.rainhacarmesim.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/**
 * Instalação SEMPRE pelo instalador oficial do Android, com confirmação do usuário.
 * Nunca há instalação silenciosa.
 */
class ApkInstaller(private val context: Context) {

    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Tela do sistema onde o usuário autoriza este app a instalar atualizações. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Confere se o APK é do próprio app e mais novo que o instalado (impede downgrade acidental).
     * Retorna mensagem de erro ou null se estiver tudo certo.
     */
    @Suppress("DEPRECATION")
    fun validateArchive(apk: File, installedVersionCode: Long): String? {
        val pm = context.packageManager
        val info = (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageArchiveInfo(apk.absolutePath, 0)
            }
        ) ?: return "O arquivo baixado não é um APK válido"
        if (info.packageName != context.packageName) return "O APK pertence a outro aplicativo"
        val code = PackageInfoCompat.getLongVersionCode(info)
        if (code <= installedVersionCode) return "O APK baixado não é mais novo que a versão instalada"
        return null
    }

    /** Abre uma sessão do PackageInstaller; o sistema mostra a confirmação ao usuário. */
    fun installWithSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("rainha-carmesim.apk", 0, apk.length()).use { out ->
                    input.copyTo(out, 64 * 1024)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    /** Alternativa: entrega o APK ao instalador do sistema via FileProvider. */
    fun installWithViewIntent(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
