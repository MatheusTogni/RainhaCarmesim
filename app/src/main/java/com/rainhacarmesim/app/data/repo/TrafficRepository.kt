package com.rainhacarmesim.app.data.repo

import com.rainhacarmesim.app.data.db.AppDatabase
import com.rainhacarmesim.app.data.db.SpeedTestEntity
import com.rainhacarmesim.app.data.db.TrafficMinuteEntity
import kotlinx.coroutines.flow.Flow

/** Histórico de tráfego do próprio celular e resultados de speed test. */
class TrafficRepository(private val db: AppDatabase) {
    private val traffic = db.trafficDao()
    private val speed = db.speedTestDao()

    companion object {
        /** Mantém 90 dias de amostras por minuto. */
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    }

    suspend fun addSample(minuteStart: Long, rx: Long, tx: Long) {
        if (rx == 0L && tx == 0L) return
        traffic.addToMinute(minuteStart, rx, tx)
    }

    suspend fun range(from: Long, to: Long): List<TrafficMinuteEntity> = traffic.range(from, to)

    suspend fun prune(now: Long) = traffic.deleteOlderThan(now - RETENTION_MS)

    suspend fun clearTraffic() = traffic.deleteAll()

    fun observeSpeedTests(): Flow<List<SpeedTestEntity>> = speed.observeRecent()

    suspend fun saveSpeedTest(result: SpeedTestEntity) = speed.insert(result)

    suspend fun clearSpeedTests() = speed.deleteAll()
}
