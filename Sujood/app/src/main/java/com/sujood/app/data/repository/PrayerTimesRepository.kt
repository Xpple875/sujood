package com.sujood.app.data.repository

import com.sujood.app.data.api.AladhanApiService
import com.sujood.app.data.api.PrayerTimesResponse
import com.sujood.app.data.local.room.CachedPrayerTimesEntity
import com.sujood.app.data.local.room.PrayerLogDao
import com.sujood.app.data.local.room.PrayerLogEntity
import com.sujood.app.domain.model.CalculationMethod
import com.sujood.app.domain.model.Madhab
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerLog
import com.sujood.app.domain.model.PrayerTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository for prayer times data.
 * Handles data operations between API, cache, and UI.
 */
class PrayerTimesRepository(
    private val apiService: AladhanApiService,
    private val prayerLogDao: PrayerLogDao
) {
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Fetches prayer times from API based on location.
     *
     * @param latitude User's latitude
     * @param longitude User's longitude
     * @param method Calculation method
     * @param madhab School of jurisprudence
     * @return List of PrayerTime objects
     */
    suspend fun getPrayerTimes(
        latitude: Double,
        longitude: Double,
        method: CalculationMethod = CalculationMethod.MAKKAH, // Default to Makkah/Dubai
        madhab: Madhab = Madhab.SHAFI
    ): Result<List<PrayerTime>> {
        return try {
            val response = apiService.getPrayerTimes(
                latitude = latitude,
                longitude = longitude,
                method = method.code,
                school = madhab.code
            )
            
            val prayerTimes = parsePrayerTimesResponse(response)
            Result.success(prayerTimes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches prayer times from API based on city name.
     *
     * @param cityName Name of the city
     * @param method Calculation method
     * @param madhab School of jurisprudence
     * @return List of PrayerTime objects
     */
    suspend fun getPrayerTimesByCity(
        cityName: String,
        method: CalculationMethod = CalculationMethod.MWL,
        madhab: Madhab = Madhab.SHAFI
    ): Result<List<PrayerTime>> {
        return try {
            val response = apiService.getPrayerTimesByCity(
                city = cityName,
                method = method.code,
                school = madhab.code
            )
            
            val prayerTimes = parsePrayerTimesResponse(response)
            Result.success(prayerTimes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchCity(query: String): com.sujood.app.data.api.CitySearchResponse {
        return apiService.searchCity(query)
    }

    /**
     * Parses API response and converts to domain models.
     */
    private fun parsePrayerTimesResponse(response: PrayerTimesResponse): List<PrayerTime> {
        val timings = response.data.timings
        val date = response.data.date
        
        // Parse the date for timestamp calculations
        val dateString = date.gregorian.date
        
        return listOf(
            PrayerTime(
                prayer = Prayer.FAJR,
                time = timings.fajr.substring(0, 5),
                timestamp = parseTimeToTimestamp(timings.fajr, dateString)
            ),
            PrayerTime(
                prayer = Prayer.DHUHR,
                time = timings.dhuhr.substring(0, 5),
                timestamp = parseTimeToTimestamp(timings.dhuhr, dateString)
            ),
            PrayerTime(
                prayer = Prayer.ASR,
                time = timings.asr.substring(0, 5),
                timestamp = parseTimeToTimestamp(timings.asr, dateString)
            ),
            PrayerTime(
                prayer = Prayer.MAGHRIB,
                time = timings.maghrib.substring(0, 5),
                timestamp = parseTimeToTimestamp(timings.maghrib, dateString)
            ),
            PrayerTime(
                prayer = Prayer.ISHA,
                time = timings.isha.substring(0, 5),
                timestamp = parseTimeToTimestamp(timings.isha, dateString)
            )
        )
    }

    /**
     * Parses time string to Unix timestamp.
     */
    private fun parseTimeToTimestamp(time: String, dateStr: String): Long {
        return try {
            val fullDateTime = "$dateStr $time"
            val format = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
            format.parse(fullDateTime)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Gets current date string in API format.
     */
    fun getCurrentDateString(): String {
        return dateFormat.format(Date())
    }

    /**
     * Gets current date in key format (yyyy-MM-dd).
     */
    fun getCurrentDateKey(): String {
        return dateKeyFormat.format(Date())
    }

    /**
     * Logs a completed prayer.
     */
    suspend fun logPrayerCompletion(prayer: Prayer): Long {
        val entity = PrayerLogEntity(
            prayerName = prayer.name,
            completedAt = System.currentTimeMillis(),
            date = getCurrentDateKey()
        )
        return prayerLogDao.insertPrayerLog(entity)
    }

    /**
     * Checks if a prayer was completed today.
     */
    suspend fun isPrayerCompletedToday(prayer: Prayer): Boolean {
        return prayerLogDao.isPrayerCompleted(getCurrentDateKey(), prayer.name)
    }

    /**
     * Gets all prayers completed today.
     */
    suspend fun getCompletedPrayersToday(): List<Prayer> {
        val logs = prayerLogDao.getPrayerLogsForDate(getCurrentDateKey())
        return logs.mapNotNull { log ->
            try {
                Prayer.valueOf(log.prayerName)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Gets all prayer logs as a Flow.
     */
    fun getAllPrayerLogs(): Flow<List<PrayerLog>> {
        return prayerLogDao.getAllPrayerLogs().map { entities ->
            entities.map { entity ->
                PrayerLog(
                    id = entity.id,
                    prayer = Prayer.valueOf(entity.prayerName),
                    completedAt = entity.completedAt,
                    date = entity.date
                )
            }
        }
    }

    /**
     * Gets the current prayer streak (consecutive days with all 5 prayers completed).
     * Walks backwards from today counting unbroken consecutive days.
     */
    suspend fun getPrayerStreak(): Int {
        val completedDates = prayerLogDao.getFullyCompletedDates() // descending
        if (completedDates.isEmpty()) return 0

        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        val todayStr = fmt.format(cal.time)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = fmt.format(cal.time)

        // Streak must start from today or yesterday (if today isn't done yet)
        val startDate = when {
            completedDates.first() == todayStr -> todayStr
            completedDates.first() == yesterdayStr -> yesterdayStr
            else -> return 0
        }

        val checkCal = java.util.Calendar.getInstance()
        checkCal.time = fmt.parse(startDate) ?: return 0

        var streak = 0
        for (dateStr in completedDates) {
            val expected = fmt.format(checkCal.time)
            if (dateStr == expected) {
                streak++
                checkCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }
}
