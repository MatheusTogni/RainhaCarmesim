package com.rainhacarmesim.app.network

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Totais de um intervalo, separados por Wi-Fi e rede móvel. */
data class UsageBucket(
    val start: Long,
    val end: Long,
    val wifiRx: Long,
    val wifiTx: Long,
    val mobileRx: Long,
    val mobileTx: Long,
    /** Falso quando o Android não entregou os dados móveis (então eles não entram nos totais). */
    val mobileAvailable: Boolean,
) {
    val rx: Long get() = wifiRx + mobileRx
    val tx: Long get() = wifiTx + mobileTx
}

/**
 * Histórico de consumo de dados do próprio aparelho registrado pelo Android (NetworkStatsManager).
 * Inclui períodos com o app fechado, mas exige que o usuário conceda "Acesso de uso" manualmente.
 */
class SystemUsageHistory(private val context: Context) {

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Consulta os intervalos [boundaries] (n+1 marcos → n buckets). Null se sem acesso ou erro. */
    suspend fun query(boundaries: List<Long>): List<UsageBucket>? = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext null
        val nsm = context.getSystemService(NetworkStatsManager::class.java) ?: return@withContext null
        try {
            boundaries.zipWithNext().map { (start, end) ->
                val wifi = summary(nsm, ConnectivityManager.TYPE_WIFI, start, end)
                    ?: return@withContext null
                val mobile = summary(nsm, ConnectivityManager.TYPE_MOBILE, start, end)
                UsageBucket(
                    start, end, wifi.first, wifi.second,
                    mobile?.first ?: 0L, mobile?.second ?: 0L, mobileAvailable = mobile != null,
                )
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun summary(nsm: NetworkStatsManager, type: Int, start: Long, end: Long): Pair<Long, Long>? =
        try {
            val bucket: NetworkStats.Bucket = nsm.querySummaryForDevice(type, null, start, end)
            bucket.rxBytes to bucket.txBytes
        } catch (e: SecurityException) {
            // Em versões antigas, dados móveis exigem o ID do assinante, que não solicitamos.
            null
        } catch (e: Exception) {
            null
        }
}
