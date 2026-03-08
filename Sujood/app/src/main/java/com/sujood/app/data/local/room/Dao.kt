package com.sujood.app.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for prayer log operations.
 * Provides methods to insert, query, and delete prayer log entries.
 */
@Dao
interface PrayerLogDao {

    /**
     * Inserts a new prayer log entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrayerLog(prayerLog: PrayerLogEntity): Long

    /**
     * Gets all prayer logs for a specific date.
     */
    @Query("SELECT * FROM prayer_logs WHERE date = :date")
    suspend fun getPrayerLogsForDate(date: String): List<PrayerLogEntity>

    /**
     * Gets all prayer logs for a date range.
     */
    @Query("SELECT * FROM prayer_logs WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getPrayerLogsInRange(startDate: String, endDate: String): List<PrayerLogEntity>

    /**
     * Gets all prayer logs as a Flow for reactive updates.
     */
    @Query("SELECT * FROM prayer_logs ORDER BY completedAt DESC")
    fun getAllPrayerLogs(): Flow<List<PrayerLogEntity>>

    /**
     * Checks if a specific prayer was completed on a given date.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM prayer_logs WHERE date = :date AND prayerName = :prayerName)")
    suspend fun isPrayerCompleted(date: String, prayerName: String): Boolean

    /**
     * Deletes a specific prayer log entry.
     */
    @Query("DELETE FROM prayer_logs WHERE date = :date AND prayerName = :prayerName")
    suspend fun deletePrayerLog(date: String, prayerName: String)

    /**
     * Gets all dates where all 5 prayers were completed, ordered descending.
     * Used by the repository to calculate consecutive streak in Kotlin.
     */
    @Query("""
        SELECT date FROM prayer_logs
        GROUP BY date
        HAVING COUNT(DISTINCT prayerName) >= 5
        ORDER BY date DESC
    """)
    suspend fun getFullyCompletedDates(): List<String>
}

/**
 * Data Access Object for cached prayer times.
 * Provides methods to store and retrieve prayer times for offline use.
 */
@Dao
interface CachedPrayerTimesDao {

    /**
     * Saves prayer times to cache.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTimes(cachedTimes: CachedPrayerTimesEntity)

    /**
     * Gets cached prayer times for a specific date.
     */
    @Query("SELECT * FROM cached_prayer_times WHERE date = :date")
    suspend fun getCachedTimesForDate(date: String): CachedPrayerTimesEntity?

    /**
     * Gets cached prayer times for a date range.
     */
    @Query("SELECT * FROM cached_prayer_times WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getCachedTimesInRange(startDate: String, endDate: String): List<CachedPrayerTimesEntity>

    /**
     * Clears old cache entries (older than specified days).
     */
    @Query("DELETE FROM cached_prayer_times WHERE cachedAt < :timestamp")
    suspend fun clearOldCache(timestamp: Long)
}
