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
        method: CalculationMethod = CalculationMethod.MWL,
        madhab: Madhab = Madhab.SHAFI,
        tune: String? = null
    ): Result<List<PrayerTime>> = try {
        val response = apiService.getPrayerTimes(
            latitude = latitude,
            longitude = longitude,
            method   = method.code,
            school   = madhab.code,
            tune     = tune
        )
        Result.success(parsePrayerTimesResponse(response))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getPrayerTimesByCity(
        cityName: String,
        method: CalculationMethod = CalculationMethod.MWL,
        madhab: Madhab = Madhab.SHAFI,
        tune: String? = null
    ): Result<List<PrayerTime>> = try {
        val response = apiService.getPrayerTimesByCity(
            city   = cityName,
            method = method.code,
            school = madhab.code,
            tune   = tune
        )
        Result.success(parsePrayerTimesResponse(response))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun searchCity(query: String) = apiService.searchCity(query)

    private fun parsePrayerTimesResponse(response: PrayerTimesResponse): List<PrayerTime> {
        val t  = response.data.timings
        val ds = response.data.date.gregorian.date   // "dd-MM-yyyy"
        val tz = response.data.meta.timezone         // e.g. "Asia/Dubai"
        
        android.util.Log.d("PrayerRepo", "Parsing prayer times for $ds ($tz): $t")
        
        val inputFormat  = SimpleDateFormat("HH:mm", Locale.US)
        val outputFormat = SimpleDateFormat("h:mm a", Locale.US)

        fun formatP(raw: String): String = try {
            val clean = raw.substringBefore(" ").trim().substring(0, 5)
            val date = inputFormat.parse(clean)
            if (date != null) outputFormat.format(date) else clean
        } catch (e: Exception) { raw }

        fun ts(raw: String) = parseTs(raw, ds, tz)
        
        return listOf(
            PrayerTime(Prayer.FAJR,    formatP(t.fajr),    ts(t.fajr)),
            PrayerTime(Prayer.DHUHR,   formatP(t.dhuhr),   ts(t.dhuhr)),
            PrayerTime(Prayer.ASR,     formatP(t.asr),     ts(t.asr)),
            PrayerTime(Prayer.MAGHRIB, formatP(t.maghrib), ts(t.maghrib)),
            PrayerTime(Prayer.ISHA,    formatP(t.isha),    ts(t.isha))
        )
    }

    /** Parse time string in the prayer location's timezone, not the device's. */
    private fun parseTs(time: String, dateStr: String, timezone: String): Long = try {
        val clean = time.substringBefore(" ").trim()
        val timeWithMinutes = if (clean.length >= 5) clean.substring(0, 5) else clean
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone(timezone) }
            .parse("$dateStr $timeWithMinutes")?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        android.util.Log.e("PrayerRepo", "Error parsing timestamp: $time, $dateStr, $timezone", e)
        System.currentTimeMillis()
    }

    fun getCurrentDateString(): String = dateFormat.format(Date())
    fun getCurrentDateKey(): String    = dateKeyFormat.format(Date())

    suspend fun logPrayerCompletion(prayer: Prayer): Long =
        prayerLogDao.insertPrayerLog(PrayerLogEntity(
            prayerName  = prayer.name,
            completedAt = System.currentTimeMillis(),
            date        = getCurrentDateKey()
        ))

    suspend fun deletePrayerLog(prayer: Prayer) =
        prayerLogDao.deletePrayerLog(getCurrentDateKey(), prayer.name)

    suspend fun isPrayerCompletedToday(prayer: Prayer): Boolean =
        prayerLogDao.isPrayerCompleted(getCurrentDateKey(), prayer.name)

    suspend fun getCompletedPrayersToday(): List<Prayer> =
        prayerLogDao.getPrayerLogsForDate(getCurrentDateKey()).mapNotNull { log ->
            try { Prayer.valueOf(log.prayerName) } catch (_: Exception) { null }
        }

    fun getAllPrayerLogs(): Flow<List<PrayerLog>> =
        prayerLogDao.getAllPrayerLogs().map { entities ->
            entities.map { e ->
                PrayerLog(id = e.id, prayer = Prayer.valueOf(e.prayerName),
                          completedAt = e.completedAt, date = e.date)
            }
        }

    suspend fun getPrayerStreak(): Int {
        val dates = prayerLogDao.getFullyCompletedDates()
        if (dates.isEmpty()) return 0
        val fmt  = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal  = java.util.Calendar.getInstance()
        val today     = fmt.format(cal.time)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterday = fmt.format(cal.time)
        val start = when { dates.first() == today -> today; dates.first() == yesterday -> yesterday; else -> return 0 }
        val check = java.util.Calendar.getInstance().also { it.time = fmt.parse(start) ?: return 0 }
        var streak = 0
        for (d in dates) {
            if (d == fmt.format(check.time)) { streak++; check.add(java.util.Calendar.DAY_OF_YEAR, -1) } else break
        }
        return streak
    }
}
