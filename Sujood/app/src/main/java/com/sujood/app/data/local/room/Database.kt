package com.sujood.app.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for Sujood app.
 * Contains tables for prayer logs and cached prayer times.
 */
@Database(
    entities = [PrayerLogEntity::class, CachedPrayerTimesEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SujoodDatabase : RoomDatabase() {

    /**
     * Provides DAO for prayer log operations.
     */
    abstract fun prayerLogDao(): PrayerLogDao

    /**
     * Provides DAO for cached prayer times operations.
     */
    abstract fun cachedPrayerTimesDao(): CachedPrayerTimesDao

    companion object {
        @Volatile
        private var INSTANCE: SujoodDatabase? = null

        /**
         * Gets the singleton database instance.
         * Uses double-checked locking for thread safety.
         */
        fun getDatabase(context: Context): SujoodDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SujoodDatabase::class.java,
                    "sujood_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
