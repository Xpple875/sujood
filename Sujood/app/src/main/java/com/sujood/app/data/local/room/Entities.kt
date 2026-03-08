package com.sujood.app.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing prayer log entries.
 * Records when each prayer was completed by the user.
 */
@Entity(tableName = "prayer_logs")
data class PrayerLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prayerName: String,  // Prayer enum name (FAJR, DHUHR, etc.)
    val completedAt: Long,   // Unix timestamp
    val date: String        // Date in format "yyyy-MM-dd"
)

/**
 * Room entity for caching prayer times.
 * Stores prayer times for offline access.
 */
@Entity(tableName = "cached_prayer_times")
data class CachedPrayerTimesEntity(
    @PrimaryKey
    val date: String,  // Date in format "yyyy-MM-dd"
    val fajrTime: String,
    val dhuhrTime: String,
    val asrTime: String,
    val maghribTime: String,
    val ishaTime: String,
    val latitude: Double,
    val longitude: Double,
    val cachedAt: Long
)
