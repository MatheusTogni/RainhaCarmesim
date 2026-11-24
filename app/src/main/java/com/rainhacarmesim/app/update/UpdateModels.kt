package com.rainhacarmesim.app.update

import org.json.JSONObject
import java.security.MessageDigest
import java.io.File

/** Conteúdo do latest.json publicado junto de cada GitHub Release. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val releaseNotes: String,
    val apkUrl: String,
    val sha256: String,
    val publishedAt: String,
)

object UpdateMetadata {
    private val shaRegex = Regex("^[0-9a-f]{64}$")

    /**
     * Interpreta e valida o JSON. Lança [IllegalArgumentException] com mensagem amigável se algo
     * estiver ausente ou inseguro (URL fora do repositório oficial, sem HTTPS, hash inválido).
     */
    fun parse(json: String, repo: String): UpdateInfo {
        val o = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Metadados de versão inválidos")
        }
        val versionName = o.optString("versionName").trim()
        val versionCode = o.optLong("versionCode", -1)
        val apkUrl = o.optString("apkUrl").trim()
        val sha = o.optString("sha256").trim().lowercase()
        require(versionName.isNotEmpty()) { "Metadados sem versionName" }
        require(versionCode > 0) { "Metadados sem versionCode válido" }
        require(apkUrl.startsWith("https://github.com/$repo/releases/download/")) {
            "URL do APK fora do repositório oficial"
        }
        require(shaRegex.matches(sha)) { "Hash SHA-256 ausente ou inválido" }
        return UpdateInfo(
            versionName = versionName,
            versionCode = versionCode,
            releaseNotes = o.optString("releaseNotes").trim(),
            apkUrl = apkUrl,
            sha256 = sha,
            publishedAt = o.optString("publishedAt").trim(),
        )
    }
}

object VersionComparator {
    /** Só há atualização se o versionCode publicado for MAIOR que o instalado (evita downgrade). */
    fun isNewer(remoteVersionCode: Long, installedVersionCode: Long): Boolean =
        remoteVersionCode > installedVersionCode

    /** Converte "1.2.3" em versionCode (MAJ*10000 + MIN*100 + PATCH), o mesmo esquema do CI. */
    fun codeFromName(name: String): Long? {
        val parts = name.trim().removePrefix("v").split('.')
        if (parts.size != 3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums[1] !in 0..99 || nums[2] !in 0..99 || nums[0] < 0) return null
        return nums[0] * 10000L + nums[1] * 100L + nums[2]
    }
}

object Sha256 {
    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return hex(digest.digest())
    }

    fun of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
