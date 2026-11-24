package com.rainhacarmesim.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceEntity::class,
        ScanEntity::class,
        AlertEntity::class,
        TrafficMinuteEntity::class,
        SpeedTestEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun scanDao(): ScanDao
    abstract fun alertDao(): AlertDao
    abstract fun trafficDao(): TrafficDao
    abstract fun speedTestDao(): SpeedTestDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rainha-carmesim.db")
                // Futuras mudanças de schema devem vir com Migration para preservar os dados nas atualizações.
                .build()
    }
}
