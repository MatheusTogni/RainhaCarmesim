package com.rainhacarmesim.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices WHERE networkKey = :networkKey ORDER BY online DESC, isSelf DESC, isGateway DESC, lastSeen DESC")
    fun observe(networkKey: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE networkKey = :networkKey")
    suspend fun getAll(networkKey: String): List<DeviceEntity>

    @Upsert
    suspend fun upsertAll(devices: List<DeviceEntity>)

    @Query("UPDATE devices SET isNew = 0 WHERE networkKey = :networkKey")
    suspend fun acknowledgeNew(networkKey: String)

    @Query("DELETE FROM devices WHERE networkKey = :networkKey AND deviceKey = :deviceKey")
    suspend fun delete(networkKey: String, deviceKey: String)

    @Query("DELETE FROM devices")
    suspend fun deleteAll()
}

@Dao
interface ScanDao {
    @Insert
    suspend fun insert(scan: ScanEntity): Long

    @Query("SELECT * FROM scans WHERE networkKey = :networkKey ORDER BY finishedAt DESC LIMIT 1")
    fun observeLast(networkKey: String): Flow<ScanEntity?>

    @Query("SELECT * FROM scans ORDER BY finishedAt DESC LIMIT 1")
    fun observeLastAny(): Flow<ScanEntity?>

    @Query("DELETE FROM scans")
    suspend fun deleteAll()
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC LIMIT 300")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT MAX(createdAt) FROM alerts WHERE networkKey = :networkKey AND deviceKey = :deviceKey AND type = :type")
    suspend fun lastAlertTime(networkKey: String, deviceKey: String, type: String): Long?

    @Insert
    suspend fun insertAll(alerts: List<AlertEntity>): List<Long>

    @Query("UPDATE alerts SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE alerts SET read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()
}

@Dao
abstract class TrafficDao {
    @Query("SELECT * FROM traffic_minutes WHERE minuteStart >= :from AND minuteStart < :to ORDER BY minuteStart")
    abstract suspend fun range(from: Long, to: Long): List<TrafficMinuteEntity>

    @Query("SELECT * FROM traffic_minutes WHERE minuteStart = :minute")
    abstract suspend fun get(minute: Long): TrafficMinuteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(entity: TrafficMinuteEntity)

    /** Soma bytes ao minuto informado (compatível com SQLite antigo, sem UPSERT nativo). */
    @Transaction
    open suspend fun addToMinute(minute: Long, rx: Long, tx: Long) {
        val current = get(minute)
        put(
            TrafficMinuteEntity(
                minuteStart = minute,
                rxBytes = (current?.rxBytes ?: 0L) + rx,
                txBytes = (current?.txBytes ?: 0L) + tx,
            )
        )
    }

    @Query("DELETE FROM traffic_minutes WHERE minuteStart < :before")
    abstract suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM traffic_minutes")
    abstract suspend fun deleteAll()
}

@Dao
interface SpeedTestDao {
    @Insert
    suspend fun insert(result: SpeedTestEntity): Long

    @Query("SELECT * FROM speed_tests ORDER BY timestamp DESC LIMIT 20")
    fun observeRecent(): Flow<List<SpeedTestEntity>>

    @Query("DELETE FROM speed_tests")
    suspend fun deleteAll()
}
