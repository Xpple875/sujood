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
import java.util.TimeZone

class PrayerTimesRepository(
    private val apiService: AladhanApiService,
    private val prayerLogDao: PrayerLogDao
) {
    private val dateFormat    = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun getPrayerTimes(
        latitude: Double,
        longitude: Double,
        method: CalculationMethod = CalculationMethod.MAKKAH,
        madhab: Madhab = Madhab.SHAFI
    ): Result<List<PrayerTime>> {
        return try {
            val response = apiService.getPrayerTimes(
                latitude  = latitude,
                longitude = longitude,
                method    = method.code,
                school    = madhab.code
            )
            Result.success(parsePrayerTimesResponse(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPrayerTimesByCity(
        cityName: String,
        method: CalculationMethod = CalculationMethod.MAKKAH,
        madhab: Madhab = Madhab.SHAFI
    ): Result<List<PrayerTime>> {
        return try {
            val response = apiService.getPrayerTimesByCity(
                city   = cityName,
                method = method.code,
                school = madhab.code
            )
            Result.success(parsePrayerTimesResponse(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchCity(query: String): com.sujood.app.data.api.CitySearchResponse {
        return apiService.searchCity(query)
    }

    /**
     * Parses API response into domain PrayerTime objects.
     * Uses the timezone returned by the API (meta.timezone e.g. "Asia/Dubai")
     * so timestamps are correct regardless of the device's timezone.
     */
    private fun parsePrayerTimesResponse(response: PrayerTimesResponse): List<PrayerTime> {
        val timings  = response.data.timings
        val dateStr  = response.data.date.gregorian.date   // "dd-MM-yyyy"
        val timezone = response.data.meta.timezone         // e.g. "Asia/Dubai"

        fun t(raw: String)  = raw.substring(0, 5)
        fun ts(raw: String) = parseTimeToTimestamp(raw, dateStr, timezone)

        return listOf(
            PrayerTime(Prayer.FAJR,    t(timings.fajr),    ts(timings.fajr)),
            PrayerTime(Prayer.DHUHR,   t(timings.dhuhr),   ts(timings.dhuhr)),
            PrayerTime(Prayer.ASR,     t(timings.asr),     ts(timings.asr)),
            PrayerTime(Prayer.MAGHRIB, t(timings.maghrib), ts(timings.maghrib)),
            PrayerTime(Prayer.ISHA,    t(timings.isha),    ts(timings.isha))
        )
    }

    /**
     * Parses "HH:mm" + "dd-MM-yyyy" into a UTC Unix timestamp,
     * treating the time as being in the prayer location's timezone.
     * Aladhan sometimes appends " (GMT+X)" — we strip that first.
     */
    private fun parseTimeToTimestamp(time: String, dateStr: String, timezone: String): Long {
        return try {
            val cleanTime = time.substringBefore(" ").trim().substring(0, 5)
            val fmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone(timezone)
            }
            fmt.parse("$dateStr $cleanTime")?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun getCurrentDateString(): String = dateFormat.format(Date())
    fun getCurrentDateKey(): String    = dateKeyFormat.format(Date())

    suspend fun logPrayerCompletion(prayer: Prayer): Long {
        val entity = PrayerLogEntity(
            prayerName  = prayer.name,
            completedAt = System.currentTimeMillis(),
            date        = getCurrentDateKey()
        )
        return prayerLogDao.insertPrayerLog(entity)
    }

    suspend fun deletePrayerLog(prayer: Prayer) {
        prayerLogDao.deletePrayerLog(getCurrentDateKey(), prayer.name)
    }

    suspend fun isPrayerCompletedToday(prayer: Prayer): Boolean =
        prayerLogDao.isPrayerCompleted(getCurrentDateKey(), prayer.name)

    suspend fun getCompletedPrayersToday(): List<Prayer> {
        return prayerLogDao.getPrayerLogsForDate(getCurrentDateKey()).mapNotNull { log ->
            try { Prayer.valueOf(log.prayerName) } catch (_: Exception) { null }
        }
    }

    fun getAllPrayerLogs(): Flow<List<PrayerLog>> {
        return prayerLogDao.getAllPrayerLogs().map { entities ->
            entities.map { entity ->
                PrayerLog(
                    id          = entity.id,
                    prayer      = Prayer.valueOf(entity.prayerName),
                    completedAt = entity.completedAt,
                    date        = entity.date
                )
            }
        }
    }

    suspend fun getPrayerStreak(): Int {
        val completedDates = prayerLogDao.getFullyCompletedDates()
        if (completedDates.isEmpty()) return 0
        val fmt  = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal  = java.util.Calendar.getInstance()
        val todayStr     = fmt.format(cal.time)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = fmt.format(cal.time)
        val startDate = when {
            completedDates.first() == todayStr     -> todayStr
            completedDates.first() == yesterdayStr -> yesterdayStr
            else -> return 0
        }
        val checkCal = java.util.Calendar.getInstance()
        checkCal.time = fmt.parse(startDate) ?: return 0
        var streak = 0
        for (dateStr in completedDates) {
            if (dateStr == fmt.format(checkCal.time)) {
                streak++
                checkCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else break
        }
        return streak
    }
}
