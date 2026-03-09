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
    ): Result<List<PrayerTime>> = try {
        val r = apiService.getPrayerTimes(latitude, longitude, method.code, madhab.code)
        Result.success(parsePrayerTimesResponse(r))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getPrayerTimesByCity(
        cityName: String,
        method: CalculationMethod = CalculationMethod.MAKKAH,
        madhab: Madhab = Madhab.SHAFI
    ): Result<List<PrayerTime>> = try {
        val r = apiService.getPrayerTimesByCity(cityName, null, method.code, madhab.code)
        Result.success(parsePrayerTimesResponse(r))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun searchCity(query: String) = apiService.searchCity(query)

    private fun parsePrayerTimesResponse(response: PrayerTimesResponse): List<PrayerTime> {
        val t  = response.data.timings
        val ds = response.data.date.gregorian.date   // "dd-MM-yyyy"
        val tz = response.data.meta.timezone         // e.g. "Asia/Dubai"
        fun s(raw: String) = raw.substring(0, 5)
        fun ts(raw: String) = parseTs(raw, ds, tz)
        return listOf(
            PrayerTime(Prayer.FAJR,    s(t.fajr),    ts(t.fajr)),
            PrayerTime(Prayer.DHUHR,   s(t.dhuhr),   ts(t.dhuhr)),
            PrayerTime(Prayer.ASR,     s(t.asr),     ts(t.asr)),
            PrayerTime(Prayer.MAGHRIB, s(t.maghrib), ts(t.maghrib)),
            PrayerTime(Prayer.ISHA,    s(t.isha),    ts(t.isha))
        )
    }

    /** Parse time string in the prayer location's timezone, not the device's. */
    private fun parseTs(time: String, dateStr: String, timezone: String): Long = try {
        val clean = time.substringBefore(" ").trim().substring(0, 5)
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone(timezone) }
            .parse("$dateStr $clean")?.time ?: System.currentTimeMillis()
    } catch (_: Exception) { System.currentTimeMillis() }

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
