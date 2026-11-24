package com.rainhacarmesim.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Baixa o APK por HTTPS com retomada (cabeçalho Range) quando um download anterior foi interrompido.
 * Verifica o SHA-256 ao final; arquivos que não batem são apagados.
 */
class ApkDownloader(private val userAgent: String) {

    class IntegrityException : IOException("O arquivo baixado não confere com o SHA-256 publicado")

    suspend fun download(
        url: String,
        expectedSha256: String,
        target: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        // Já baixado e íntegro (ex.: usuário voltou depois): reaproveita.
        if (target.exists() && Sha256.of(target) == expectedSha256) return@withContext target
        target.delete()

        val partial = File(target.parentFile, target.name + ".part")
        var existing = if (partial.exists()) partial.length() else 0L

        var connection = open(url, existing)
        var code = connection.responseCode
        if (existing > 0 && code == 416) {
            // Faixa inválida (arquivo mudou?): recomeça do zero.
            connection.disconnect()
            partial.delete()
            existing = 0L
            connection = open(url, 0L)
            code = connection.responseCode
        }
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("Falha no download (HTTP $code)")
        }
        val resumed = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL
        if (!resumed) existing = 0L

        val length = connection.contentLengthLong.takeIf { it > 0 }
        val total = length?.let { it + existing }
        try {
            connection.inputStream.use { input ->
                FileOutputStream(partial, resumed).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var downloaded = existing
                    var lastReport = 0L
                    onProgress(downloaded, total)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        if (downloaded - lastReport >= 128 * 1024) {
                            lastReport = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                    out.fd.sync()
                    onProgress(downloaded, total)
                }
            }
        } finally {
            connection.disconnect()
        }

        if (total != null && partial.length() != total) {
            throw IOException("Download incompleto. Tente novamente para continuar de onde parou.")
        }
        val actual = Sha256.of(partial)
        if (actual != expectedSha256) {
            partial.delete()
            throw IntegrityException()
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        target
    }

    private fun open(url: String, from: Long): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", userAgent)
        c.setRequestProperty("Accept-Encoding", "identity")
        if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
        return c
    }
}
