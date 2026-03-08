#!/bin/bash
# Run from the ROOT of your sujood repo (where .git folder is)
set -e
BASE="Sujood/app/src/main/java/com/sujood/app"
echo "Applying sujood fixes..."

mkdir -p "$(dirname "$BASE/data/local/room/Dao.kt")"
cat > "$BASE/data/local/room/Dao.kt" << 'SUJOOD_HEREDOC_DATA_LOCAL_ROOM_DAO_KT'
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

SUJOOD_HEREDOC_DATA_LOCAL_ROOM_DAO_KT
echo "  ✓ data/local/room/Dao.kt"

mkdir -p "$(dirname "$BASE/data/repository/PrayerTimesRepository.kt")"
cat > "$BASE/data/repository/PrayerTimesRepository.kt" << 'SUJOOD_HEREDOC_DATA_REPOSITORY_PRAYERTIMESREPOSITORY_KT'
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

SUJOOD_HEREDOC_DATA_REPOSITORY_PRAYERTIMESREPOSITORY_KT
echo "  ✓ data/repository/PrayerTimesRepository.kt"

mkdir -p "$(dirname "$BASE/domain/usecase/PrayerUseCases.kt")"
cat > "$BASE/domain/usecase/PrayerUseCases.kt" << 'SUJOOD_HEREDOC_DOMAIN_USECASE_PRAYERUSECASES_KT'
package com.sujood.app.domain.usecase

import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerTime

/**
 * Use case for getting prayer times based on location.
 * Encapsulates the business logic for fetching and processing prayer times.
 */
class GetPrayerTimesUseCase(
    private val repository: PrayerTimesRepository
) {
    /**
     * Fetches prayer times for a given location.
     *
     * @param latitude User's latitude
     * @param longitude User's longitude
     * @param calculationMethodCode Calculation method code
     * @param madhabCode Madhab code (0=Shafi, 1=Hanafi)
     * @return Result containing list of PrayerTime or error
     */
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        calculationMethodCode: Int,
        madhabCode: Int
    ): Result<List<PrayerTime>> {
        return repository.getPrayerTimes(
            latitude = latitude,
            longitude = longitude,
            method = com.sujood.app.domain.model.CalculationMethod.entries.getOrElse(calculationMethodCode) {
                com.sujood.app.domain.model.CalculationMethod.MWL
            },
            madhab = com.sujood.app.domain.model.Madhab.entries.getOrElse(madhabCode) {
                com.sujood.app.domain.model.Madhab.SHAFI
            }
        )
    }
}

/**
 * Use case for logging prayer completion.
 * Handles the business logic for recording when a user completes a prayer.
 */
class LogPrayerCompletionUseCase(
    private val repository: PrayerTimesRepository
) {
    /**
     * Logs that the user has completed a specific prayer.
     *
     * @param prayer The prayer that was completed
     * @return Result containing the log ID or error
     */
    suspend operator fun invoke(prayer: Prayer): Result<Long> {
        return try {
            // Check if already completed today
            if (repository.isPrayerCompletedToday(prayer)) {
                return Result.failure(Exception("Prayer already logged for today"))
            }

            val logId = repository.logPrayerCompletion(prayer)
            Result.success(logId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Use case for getting prayer streak.
 * Calculates how many consecutive days the user has completed all prayers.
 */
class GetPrayerStreakUseCase(
    private val repository: PrayerTimesRepository
) {
    /**
     * Gets the current prayer streak count.
     *
     * @return Number of consecutive days with all prayers completed
     */
    suspend operator fun invoke(): Int {
        return repository.getPrayerStreak()
    }
}

/**
 * Use case for getting next prayer information.
 * Determines which prayer is next and calculates countdown time.
 */
class GetNextPrayerUseCase {

    /**
     * Data class containing next prayer information.
     */
    data class NextPrayerInfo(
        val prayer: Prayer,
        val timeRemainingMillis: Long,
        val prayerTime: PrayerTime
    )

    /**
     * Determines the next prayer based on current time.
     *
     * @param prayerTimes List of all prayer times for today
     * @return NextPrayerInfo or null if all prayers passed
     */
    operator fun invoke(prayerTimes: List<PrayerTime>): NextPrayerInfo? {
        val currentTime = System.currentTimeMillis()

        // Sort prayer times by timestamp
        val sortedTimes = prayerTimes.sortedBy { it.timestamp }

        // Find the next prayer (first one that's in the future)
        val nextPrayer = sortedTimes.firstOrNull { it.timestamp > currentTime }

        return if (nextPrayer != null) {
            NextPrayerInfo(
                prayer = nextPrayer.prayer,
                timeRemainingMillis = nextPrayer.timestamp - currentTime,
                prayerTime = nextPrayer
            )
        } else {
            // All prayers for today have passed — show Fajr for tomorrow
            val fajr = sortedTimes.firstOrNull { it.prayer == Prayer.FAJR }
            if (fajr != null) {
                val tomorrowFajrTimestamp = fajr.timestamp + 24 * 60 * 60 * 1000L
                NextPrayerInfo(
                    prayer = Prayer.FAJR,
                    timeRemainingMillis = tomorrowFajrTimestamp - currentTime,
                    prayerTime = fajr.copy(timestamp = tomorrowFajrTimestamp)
                )
            } else null
        }
    }
}

SUJOOD_HEREDOC_DOMAIN_USECASE_PRAYERUSECASES_KT
echo "  ✓ domain/usecase/PrayerUseCases.kt"

mkdir -p "$(dirname "$BASE/notifications/PrayerAlarmReceiver.kt")"
cat > "$BASE/notifications/PrayerAlarmReceiver.kt" << 'SUJOOD_HEREDOC_NOTIFICATIONS_PRAYERALARMRECEIVER_KT'
package com.sujood.app.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sujood.app.MainActivity
import com.sujood.app.R
import com.sujood.app.SujoodApplication
import com.sujood.app.domain.model.Prayer
import com.sujood.app.service.PrayerLockOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerAlarmScheduler.ACTION_PRAYER_ALARM) return

        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val notificationEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATION_ENABLED, true)
        val lockEnabled = intent.getBooleanExtra(EXTRA_LOCK_ENABLED, true)

        val prayer = try {
            Prayer.valueOf(prayerName)
        } catch (e: Exception) {
            return
        }

        if (notificationEnabled) {
            showNotification(context, prayer)
        }

        if (lockEnabled) {
            PrayerLockOverlayService.start(context, prayer.displayName, prayer.arabicName)
        }
    }

    private fun showNotification(context: Context, prayer: Prayer) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            prayer.ordinal,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iPrayedIntent = Intent(context, PrayerActionReceiver::class.java).apply {
            action = PrayerActionReceiver.ACTION_I_PRAYED
            putExtra(EXTRA_PRAYER_NAME, prayer.name)
        }

        val iPrayedPendingIntent = PendingIntent.getBroadcast(
            context,
            prayer.ordinal + 100,
            iPrayedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SujoodApplication.PRAYER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time for ${prayer.displayName}")
            .setContentText("It's time to pray ${prayer.displayName}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_check,
                "I Prayed ✓",
                iPrayedPendingIntent
            )
            .build()

        notificationManager.notify(prayer.ordinal, notification)
    }

    companion object {
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_NOTIFICATION_ENABLED = "extra_notification_enabled"
        const val EXTRA_LOCK_ENABLED = "extra_lock_enabled"
    }
}

class PrayerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_I_PRAYED) return

        val prayerName = intent.getStringExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME) ?: return

        val prayer = try {
            Prayer.valueOf(prayerName)
        } catch (e: Exception) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(prayer.ordinal)

        val application = context.applicationContext as SujoodApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = com.sujood.app.data.repository.PrayerTimesRepository(
                    com.sujood.app.data.api.RetrofitClient.aladhanApiService,
                    application.database.prayerLogDao()
                )
                repository.logPrayerCompletion(prayer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        PrayerLockOverlayService.stop(context)
    }

    companion object {
        const val ACTION_I_PRAYED = "com.sujood.app.I_PRAYED"
    }
}

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as SujoodApplication
        val repository = com.sujood.app.data.repository.PrayerTimesRepository(
            com.sujood.app.data.api.RetrofitClient.aladhanApiService,
            app.database.prayerLogDao()
        )
        val userPreferences = com.sujood.app.data.local.datastore.UserPreferences(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = userPreferences.userSettings.first()

                // Fetch prayer times using saved location
                val result = when {
                    settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 -> {
                        repository.getPrayerTimes(
                            latitude = settings.savedLatitude,
                            longitude = settings.savedLongitude,
                            method = settings.calculationMethod,
                            madhab = settings.madhab
                        )
                    }
                    settings.savedCity.isNotEmpty() -> {
                        repository.getPrayerTimesByCity(settings.savedCity)
                    }
                    else -> return@launch
                }

                result.onSuccess { prayerTimes ->
                    val scheduler = PrayerAlarmScheduler(context)
                    val notificationEnabled = com.sujood.app.domain.model.Prayer.entries.map { prayer ->
                        when (prayer) {
                            com.sujood.app.domain.model.Prayer.FAJR -> settings.fajrNotificationEnabled
                            com.sujood.app.domain.model.Prayer.DHUHR -> settings.dhuhrNotificationEnabled
                            com.sujood.app.domain.model.Prayer.ASR -> settings.asrNotificationEnabled
                            com.sujood.app.domain.model.Prayer.MAGHRIB -> settings.maghribNotificationEnabled
                            com.sujood.app.domain.model.Prayer.ISHA -> settings.ishaNotificationEnabled
                        }
                    }.toBooleanArray()
                    val lockEnabled = com.sujood.app.domain.model.Prayer.entries.map { prayer ->
                        when (prayer) {
                            com.sujood.app.domain.model.Prayer.FAJR -> settings.fajrLockEnabled
                            com.sujood.app.domain.model.Prayer.DHUHR -> settings.dhuhrLockEnabled
                            com.sujood.app.domain.model.Prayer.ASR -> settings.asrLockEnabled
                            com.sujood.app.domain.model.Prayer.MAGHRIB -> settings.maghribLockEnabled
                            com.sujood.app.domain.model.Prayer.ISHA -> settings.ishaLockEnabled
                        }
                    }.toBooleanArray()
                    scheduler.scheduleAllAlarms(prayerTimes, notificationEnabled, lockEnabled, settings.gracePeriodMinutes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

SUJOOD_HEREDOC_NOTIFICATIONS_PRAYERALARMRECEIVER_KT
echo "  ✓ notifications/PrayerAlarmReceiver.kt"

mkdir -p "$(dirname "$BASE/notifications/PrayerAlarmScheduler.kt")"
cat > "$BASE/notifications/PrayerAlarmScheduler.kt" << 'SUJOOD_HEREDOC_NOTIFICATIONS_PRAYERALARMSCHEDULER_KT'
package com.sujood.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerTime
import java.util.Calendar

/**
 * Handles scheduling and managing prayer time alarms.
 * Uses AlarmManager to trigger notifications at exact prayer times.
 */
class PrayerAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules an alarm for a specific prayer time.
     *
     * @param prayer The prayer to schedule
     * @param prayerTime The time to trigger the alarm
     * @param isNotificationEnabled Whether to show notification
     * @param isLockEnabled Whether to show lock overlay
     * @param gracePeriodMinutes Additional grace period before alarm
     */
    fun scheduleAlarm(
        prayer: Prayer,
        prayerTime: PrayerTime,
        isNotificationEnabled: Boolean,
        isLockEnabled: Boolean,
        gracePeriodMinutes: Int = 0
    ) {
        // Parse the time string to calendar
        val timeParts = prayerTime.time.split(":")
        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()

        // Create calendar for the prayer time, applying grace period as early trigger
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Grace period: alert this many minutes BEFORE the prayer time
            add(Calendar.MINUTE, -gracePeriodMinutes)

            // If time has passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Create intent for the alarm receiver
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, prayer.name)
            putExtra(PrayerAlarmReceiver.EXTRA_NOTIFICATION_ENABLED, isNotificationEnabled)
            putExtra(PrayerAlarmReceiver.EXTRA_LOCK_ENABLED, isLockEnabled)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayer.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule exact alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Fall back to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Handle case where exact alarm permission is not granted
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    /**
     * Cancels a scheduled alarm for a specific prayer.
     *
     * @param prayer The prayer to cancel
     */
    fun cancelAlarm(prayer: Prayer) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayer.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }

    /**
     * Cancels all scheduled prayer alarms.
     */
    fun cancelAllAlarms() {
        Prayer.entries.forEach { prayer ->
            cancelAlarm(prayer)
        }
    }

    /**
     * Schedules all prayer alarms based on prayer times and settings.
     */
    fun scheduleAllAlarms(
        prayerTimes: List<PrayerTime>,
        notificationEnabled: BooleanArray,
        lockEnabled: BooleanArray,
        gracePeriodMinutes: Int
    ) {
        // Cancel existing alarms first
        cancelAllAlarms()

        // Schedule new alarms
        prayerTimes.forEachIndexed { index, prayerTime ->
            if (index < Prayer.entries.size) {
                val prayer = Prayer.entries[index]
                val isNotificationEnabled = notificationEnabled.getOrElse(index) { true }
                val isLockEnabled = lockEnabled.getOrElse(index) { true }

                if (isNotificationEnabled || isLockEnabled) {
                    scheduleAlarm(
                        prayer = prayer,
                        prayerTime = prayerTime,
                        isNotificationEnabled = isNotificationEnabled,
                        isLockEnabled = isLockEnabled,
                        gracePeriodMinutes = gracePeriodMinutes
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_PRAYER_ALARM = "com.sujood.app.PRAYER_ALARM"
    }
}

SUJOOD_HEREDOC_NOTIFICATIONS_PRAYERALARMSCHEDULER_KT
echo "  ✓ notifications/PrayerAlarmScheduler.kt"

mkdir -p "$(dirname "$BASE/service/PrayerLockOverlayService.kt")"
cat > "$BASE/service/PrayerLockOverlayService.kt" << 'SUJOOD_HEREDOC_SERVICE_PRAYERLOCKOVERLAYSERVICE_KT'
package com.sujood.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.sujood.app.MainActivity
import com.sujood.app.R
import com.sujood.app.SujoodApplication
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay

/**
 * Service that displays a full-screen overlay during prayer times.
 * This is a foreground service to ensure it stays active and can draw over other apps.
 */
class PrayerLockOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var mediaPlayer: MediaPlayer? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val z = event.values[2]
                // Detect face down (z < -8)
                if (z < -8f) {
                    muteAudio()
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayerName = intent?.getStringExtra(PRAYER_NAME) ?: "Prayer"
        val prayerArabic = intent?.getStringExtra(PRAYER_ARABIC) ?: "الصلاة"

        // Create notification for foreground service
        startForeground(NOTIFICATION_ID, createNotification(prayerName))

        // Show the full-screen overlay
        showOverlay(prayerName, prayerArabic)

        // Read settings before playing audio / vibrating
        serviceScope.launch {
            val userPrefs = com.sujood.app.data.local.datastore.UserPreferences(applicationContext)
            val settings = userPrefs.userSettings.first()

            if (settings.adhanEnabled) {
                launch(Dispatchers.Main) { playAlert() }
            }

            if (settings.vibrationEnabled) {
                launch(Dispatchers.Main) { vibrateDevice() }
            }
        }

        // Start Sensor
        accelerometer?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        return START_NOT_STICKY
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                manager.defaultVibrator.vibrate(
                    android.os.VibrationEffect.createWaveform(longArrayOf(0, 500, 500, 500), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 500, 500), -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotification(prayerName: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, SujoodApplication.OVERLAY_CHANNEL_ID)
            .setContentTitle("Prayer Reminder")
            .setContentText("It's time for $prayerName")
            .setSmallIcon(R.drawable.ic_prayer_icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()
    }

    @SuppressLint("InflateParams")
    private fun showOverlay(prayerName: String, prayerArabic: String) {
        if (overlayView != null) return

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.prayer_lock_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // Do NOT include FLAG_NOT_TOUCH_MODAL — we want to block all background touches
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Setup Overlay Content
        overlayView?.apply {
            findViewById<TextView>(R.id.prayerNameText)?.text = "Time for $prayerName"
            findViewById<TextView>(R.id.prayerArabicText)?.text = prayerArabic

            val iPrayedButton = findViewById<Button>(R.id.iPrayedButton)
            iPrayedButton?.setOnClickListener {
                onPrayerCompleted(prayerName)
            }

            findViewById<Button>(R.id.muteButton)?.setOnClickListener {
                muteAudio()
            }

            // Enforce minimum duration
            serviceScope.launch(Dispatchers.Main) {
                val userPrefs = com.sujood.app.data.local.datastore.UserPreferences(applicationContext)
                val settings = userPrefs.userSettings.first()
                val minDurationMs = settings.minLockDurationMinutes * 60 * 1000L

                if (minDurationMs > 0L) {
                    iPrayedButton?.isEnabled = false
                    val startTime = System.currentTimeMillis()

                    while (System.currentTimeMillis() - startTime < minDurationMs) {
                        val remainingSec = ((minDurationMs - (System.currentTimeMillis() - startTime)) / 1000L).toInt()
                        val mins = remainingSec / 60
                        val secs = remainingSec % 60
                        iPrayedButton?.text = String.format("Praying... (%02d:%02d)", mins, secs)
                        delay(1000)
                    }
                }

                iPrayedButton?.isEnabled = true
                iPrayedButton?.text = "I've Prayed ✓"
            }
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun onPrayerCompleted(prayerName: String) {
        serviceScope.launch {
            try {
                val app = application as SujoodApplication
                val repository = PrayerTimesRepository(
                    RetrofitClient.aladhanApiService,
                    app.database.prayerLogDao()
                )

                val prayer = try {
                    Prayer.valueOf(prayerName.uppercase())
                } catch (e: Exception) {
                    null
                }

                prayer?.let { repository.logPrayerCompletion(it) }

                // Switch back to Main thread for UI updates
                launch(Dispatchers.Main) {
                    dismissOverlay()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    dismissOverlay()
                }
            }
        }
    }

    private fun playAlert() {
        try {
            val alertUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun muteAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                    it.release()
                }
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissOverlay() {
        muteAudio()
        sensorManager?.unregisterListener(sensorListener)
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        muteAudio()
        sensorManager?.unregisterListener(sensorListener)
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
        const val PRAYER_NAME = "PRAYER_NAME"
        const val PRAYER_ARABIC = "PRAYER_ARABIC"

        fun start(context: Context, prayerName: String, prayerArabic: String) {
            val intent = Intent(context, PrayerLockOverlayService::class.java).apply {
                putExtra(PRAYER_NAME, prayerName)
                putExtra(PRAYER_ARABIC, prayerArabic)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrayerLockOverlayService::class.java))
        }
    }
}

SUJOOD_HEREDOC_SERVICE_PRAYERLOCKOVERLAYSERVICE_KT
echo "  ✓ service/PrayerLockOverlayService.kt"

mkdir -p "$(dirname "$BASE/ui/screens/home/HomeScreen.kt")"
cat > "$BASE/ui/screens/home/HomeScreen.kt" << 'SUJOOD_HEREDOC_UI_SCREENS_HOME_HOMESCREEN_KT'
package com.sujood.app.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerTime
import com.sujood.app.domain.usecase.GetNextPrayerUseCase
import com.sujood.app.domain.usecase.GetPrayerStreakUseCase
import com.sujood.app.domain.usecase.LogPrayerCompletionUseCase
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.components.CountdownTimer
import com.sujood.app.ui.components.DailyQuoteCard
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.components.GreetingSection
import com.sujood.app.ui.components.PrayerStreakCard
import com.sujood.app.ui.components.PrayerTimeCard
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    userPreferences: UserPreferences = UserPreferences(LocalContext.current),
    repository: PrayerTimesRepository? = null
) {
    val context = LocalContext.current
    val database = (context.applicationContext as? com.sujood.app.SujoodApplication)?.database
    val repo = repository ?: remember {
        database?.let {
            PrayerTimesRepository(
                RetrofitClient.aladhanApiService,
                it.prayerLogDao()
            )
        }
    }

    val viewModel: HomeViewModel = remember(userPreferences, repo) {
        if (repo != null) {
            HomeViewModel(
                repository = repo,
                userPreferences = userPreferences,
                logPrayerCompletionUseCase = LogPrayerCompletionUseCase(repo),
                getNextPrayerUseCase = GetNextPrayerUseCase(),
                getPrayerStreakUseCase = GetPrayerStreakUseCase(repo)
            )
        } else {
            throw IllegalStateException("Repository not available")
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var visible by remember { mutableStateOf(false) }

    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // On every app open: try to refresh using saved location, otherwise use GPS
    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted) {
            viewModel.initializeAndRefresh(context)
        }
    }

    LaunchedEffect(locationPermission.status.isGranted) {
        if (!locationPermission.status.isGranted) {
            locationPermission.launchPermissionRequest()
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    AnimatedGradientBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            !locationPermission.status.isGranted -> {
                LocationPermissionCard(
                    onRequestPermission = { locationPermission.launchPermissionRequest() }
                )
            }
            uiState.isLoading || uiState.isLoadingLocation -> {
                LoadingContent()
            }
            uiState.showCityInput -> {
                CityInputContent(
                    error = uiState.error,
                    onSubmitCity = { city ->
                        viewModel.fetchPrayerTimesByCity(context, city)
                    },
                    onRetry = { viewModel.fetchPrayerTimes(context) }
                )
            }
            uiState.error != null && uiState.prayerTimes.isEmpty() -> {
                ErrorContent(
                    error = uiState.error!!,
                    onRetry = { viewModel.fetchPrayerTimes(context) },
                    onUseCity = { viewModel.showCityInput() },
                    context = context
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    item {
                        HeroSection(
                            userName = uiState.userName,
                            nextPrayerInfo = uiState.nextPrayerInfo,
                            prayerTimes = uiState.prayerTimes,
                            completedPrayers = uiState.completedPrayersToday.toSet(),
                            onCompleteClick = { viewModel.logPrayerCompletion(it) }
                        )
                    }

                    // ── Streak card FIRST ──
                    item {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(600, delayMillis = 150)) +
                                    slideInVertically(tween(600, delayMillis = 150)) { 30 }
                        ) {
                            PrayerStreakCard(
                                streakDays = uiState.streakDays,
                                completedPrayersToday = uiState.completedPrayersToday,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(800, delayMillis = 200)) +
                                    slideInVertically(tween(800, delayMillis = 200)) { 50 }
                        ) {
                            Text(
                                text = "Today's Prayers",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                            )
                        }
                    }

                    itemsIndexed(uiState.prayerTimes) { index, prayerTime ->
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(400, delayMillis = 250 + index * 80)) +
                                    slideInVertically(tween(400, delayMillis = 250 + index * 80)) { 30 }
                        ) {
                            PrayerTimeCard(
                                prayerTime = prayerTime,
                                isCompleted = uiState.completedPrayersToday.toSet().contains(prayerTime.prayer),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                onClick = {
                                    viewModel.logPrayerCompletion(prayerTime.prayer)
                                }
                            )
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(600, delayMillis = 700))
                        ) {
                            DailyQuoteCard(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    userName: String,
    nextPrayerInfo: GetNextPrayerUseCase.NextPrayerInfo?,
    prayerTimes: List<PrayerTime>,
    completedPrayers: Set<Prayer>,
    onCompleteClick: (Prayer) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
    ) {
        // Background gradient layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0F2A),
                            Color(0xFF1B1B3A),
                            Color(0xFF2E1A47)
                        )
                    )
                )
        )

        // Subtle radial glow spots
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(SoftPurple.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.BottomStart)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(WarmAmber.copy(alpha = 0.07f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GreetingSection(
                userName = userName,
                nextPrayer = nextPrayerInfo?.prayer
            )

            Spacer(modifier = Modifier.height(12.dp))

            val currentTimeProgress = getDayProgress(System.currentTimeMillis())

            SunArcWidget(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                currentTimeProgress = currentTimeProgress,
                prayerTimes = prayerTimes,
                completedPrayers = completedPrayers
            )

            Spacer(modifier = Modifier.height(12.dp))

            nextPrayerInfo?.let { nextInfo ->
                CountdownTimer(
                    nextPrayerName = nextInfo.prayer.displayName,
                    timeRemainingMillis = nextInfo.timeRemainingMillis,
                    modifier = Modifier.fillMaxWidth(),
                    isCompleted = completedPrayers.contains(nextInfo.prayer),
                    onCompleteClick = { onCompleteClick(nextInfo.prayer) }
                )
            }
        }
    }
}

@Composable
private fun SunArcWidget(
    modifier: Modifier = Modifier,
    currentTimeProgress: Float,
    prayerTimes: List<PrayerTime>,
    completedPrayers: Set<Prayer>
) {
    val animatedProgress by animateFloatAsState(
        targetValue = currentTimeProgress,
        animationSpec = tween(durationMillis = 1500, easing = LinearOutSlowInEasing),
        label = "sunProgress"
    )

    // Map 0–1 over the full 24h to 180°–360° (left to right across the top)
    // We display sunrise (6AM = 0.25) at 0° and sunset (6PM = 0.75) at 180°
    // So the arc maps [0.25 .. 0.75] in time to [180° .. 360°] sweep
    fun timeToArcAngle(progress: Float): Double {
        // clamp to [0, 1]
        val p = progress.coerceIn(0f, 1f)
        // 0.25 (6am) → 180°, 0.75 (6pm) → 360°
        return 180.0 + (p * 360.0)
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            // Arc center is BELOW the canvas — so we see only the top half
            val centerY = size.height * 1.05f
            val arcRadius = size.width * 0.42f

            // ── 1. Subtle background glow ring ──
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SoftPurple.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = arcRadius + 30f
                ),
                radius = arcRadius + 30f,
                center = Offset(centerX, centerY)
            )

            // ── 2. Dashed arc baseline ──
            val arcPath = Path().apply {
                addArc(
                    oval = androidx.compose.ui.geometry.Rect(
                        centerX - arcRadius, centerY - arcRadius,
                        centerX + arcRadius, centerY + arcRadius
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f
                )
            }
            drawPath(
                path = arcPath,
                color = Color.White.copy(alpha = 0.10f),
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                )
            )

            // ── 3. Prayer time dots on the arc ──
            prayerTimes.forEach { prayerTime ->
                val prog = getDayProgress(prayerTime.timestamp)
                // Map the 6am–6pm window (0.25..0.75) to the visible arc (0f..1f)
                val arcFrac = ((prog - 0.05f) / 0.90f).coerceIn(0f, 1f)
                val angleDeg = 180.0 + (arcFrac * 180.0)
                val rad = Math.toRadians(angleDeg)
                val ptX = centerX + arcRadius * cos(rad).toFloat()
                val ptY = centerY + arcRadius * sin(rad).toFloat()

                val isCompleted = completedPrayers.contains(prayerTime.prayer)

                // Glow halo
                drawCircle(
                    color = if (isCompleted) WarmAmber.copy(alpha = 0.25f) else SoftPurple.copy(alpha = 0.15f),
                    radius = 10f,
                    center = Offset(ptX, ptY)
                )
                // Dot
                drawCircle(
                    color = if (isCompleted) WarmAmber else Color.White.copy(alpha = 0.4f),
                    radius = 4.5f,
                    center = Offset(ptX, ptY)
                )
            }

            // ── 4. Sun indicator ──
            val sunAngleDeg = 180.0 + (animatedProgress * 180.0)
            val sunRad = Math.toRadians(sunAngleDeg)
            val sunX = centerX + arcRadius * cos(sunRad).toFloat()
            val sunY = centerY + arcRadius * sin(sunRad).toFloat()

            // Outer soft glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(WarmAmber.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(sunX, sunY),
                    radius = 55f
                ),
                radius = 55f,
                center = Offset(sunX, sunY)
            )
            // Mid glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(WarmAmber.copy(alpha = 0.6f), Color.Transparent),
                    center = Offset(sunX, sunY),
                    radius = 22f
                ),
                radius = 22f,
                center = Offset(sunX, sunY)
            )
            // Core
            drawCircle(color = WarmAmber, radius = 11f, center = Offset(sunX, sunY))
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 5f, center = Offset(sunX, sunY))
        }
    }
}

/**
 * Calculates the progress of a given timestamp within its 24-hour day (0.0 to 1.0).
 */
private fun getDayProgress(timestamp: Long): Float {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val hours = calendar.get(Calendar.HOUR_OF_DAY)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)
    val totalSecondsInDay = 24 * 60 * 60
    val currentSeconds = (hours * 3600) + (minutes * 60) + seconds
    return (currentSeconds.toFloat() / totalSecondsInDay).coerceIn(0f, 1f)
}

@Composable
private fun LocationPermissionCard(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FrostedGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = SoftPurple,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Location Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sujood needs your location to calculate accurate prayer times for your city.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = SoftPurple, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Getting your location...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun CityInputContent(
    error: String?,
    onSubmitCity: (String) -> Unit,
    onRetry: () -> Unit
) {
    var cityName by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val quickCities = listOf(
        "Dubai", "Abu Dhabi", "Sharjah", "London", "Toronto", "New York"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = WarmAmber,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Where are you praying?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Light,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Type your city or pick from the list below",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── City text field ──
            OutlinedTextField(
                value = cityName,
                onValueChange = { cityName = it },
                label = { Text("Search City") },
                placeholder = { Text("e.g. Dubai") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SoftPurple,
                    unfocusedBorderColor = GlassBorder,
                    focusedLabelColor = SoftPurple,
                    cursorColor = SoftPurple,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    unfocusedLabelColor = TextSecondary
                ),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        val trimmed = cityName.trim()
                        if (trimmed.isNotBlank()) onSubmitCity(trimmed)
                    }
                )
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Common Cities",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )

            // Quick city chips — two rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickCities.take(3).forEach { city ->
                    CityChip(
                        city = city,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            cityName = city
                            focusManager.clearFocus()
                            onSubmitCity(city)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickCities.drop(3).forEach { city ->
                    CityChip(
                        city = city,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            cityName = city
                            focusManager.clearFocus()
                            onSubmitCity(city)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    val trimmed = cityName.trim()
                    if (trimmed.isNotBlank()) onSubmitCity(trimmed)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = cityName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Confirm Location", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onRetry) {
                Text("Detect my location automatically", color = LavenderGlow)
            }
        }
    }
}

@Composable
private fun CityChip(
    city: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, SoftPurple.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = city,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onUseCity: () -> Unit,
    context: Context
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FrostedGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            cornerRadius = 24.dp,
            borderColor = WarmAmber.copy(alpha = 0.4f)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = WarmAmber,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Couldn't access your location",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Please make sure location permission is granted and GPS is on, or enter your city manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                if (error.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MidnightBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Settings")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedActionButton(
                        onClick = onUseCity,
                        modifier = Modifier.weight(1f)
                    ) { Text("Enter City", color = SoftPurple) }

                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SoftPurple),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun OutlinedActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SoftPurple, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MidnightBlue.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

SUJOOD_HEREDOC_UI_SCREENS_HOME_HOMESCREEN_KT
echo "  ✓ ui/screens/home/HomeScreen.kt"

mkdir -p "$(dirname "$BASE/ui/screens/home/HomeViewModel.kt")"
cat > "$BASE/ui/screens/home/HomeViewModel.kt" << 'SUJOOD_HEREDOC_UI_SCREENS_HOME_HOMEVIEWMODEL_KT'
package com.sujood.app.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerTime
import com.sujood.app.domain.model.UserSettings
import com.sujood.app.domain.usecase.GetNextPrayerUseCase
import com.sujood.app.domain.usecase.GetPrayerStreakUseCase
import com.sujood.app.domain.usecase.LogPrayerCompletionUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class HomeUiState(
    val isLoading: Boolean = true,
    val isLoadingLocation: Boolean = false,
    val userName: String = "",
    val prayerTimes: List<PrayerTime> = emptyList(),
    val completedPrayersToday: List<Prayer> = emptyList(),
    val streakDays: Int = 0,
    val nextPrayerInfo: GetNextPrayerUseCase.NextPrayerInfo? = null,
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val error: String? = null,
    val showCityInput: Boolean = false,
    val settings: UserSettings = UserSettings()
)

class HomeViewModel(
    private val repository: PrayerTimesRepository,
    private val userPreferences: UserPreferences,
    private val logPrayerCompletionUseCase: LogPrayerCompletionUseCase,
    private val getNextPrayerUseCase: GetNextPrayerUseCase,
    private val getPrayerStreakUseCase: GetPrayerStreakUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var fusedLocationClient: FusedLocationProviderClient? = null
    // Cached context for alarm scheduling (set on first location/prayer fetch)
    private var cachedContext: Context? = null

    companion object {
        private const val TAG = "HomeViewModel"
        private const val LOCATION_TIMEOUT_MS = 10000L
        private const val API_TIMEOUT_MS = 10000L
    }

    init {
        loadUserData()
        startTimeUpdates()
    }

    fun initializeAndRefresh(context: Context) {
        cachedContext = context.applicationContext
        // Called on every app open — always refresh from network using saved or GPS location
        viewModelScope.launch {
            val settings = userPreferences.userSettings.first()
            when {
                settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 -> {
                    // We have a saved location — use it to refresh silently
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    fetchPrayerTimesByLocation(settings.savedLatitude, settings.savedLongitude)
                }
                settings.savedCity.isNotEmpty() -> {
                    // We have a saved city name — try searching for its coordinates for better accuracy
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    try {
                        val searchResult = repository.searchCity(settings.savedCity)
                        val cityData = searchResult.data.firstOrNull()
                        if (cityData != null) {
                            fetchPrayerTimesByLocation(cityData.latitude, cityData.longitude)
                        } else {
                            // Fallback to direct city search if coordinate search fails
                            val result = repository.getPrayerTimesByCity(settings.savedCity)
                            result.fold(onSuccess = { handlePrayerTimesSuccess(it) }, onFailure = {
                                _uiState.value = _uiState.value.copy(isLoading = false, error = "Could not load times for ${settings.savedCity}")
                            })
                        }
                    } catch (e: Exception) {
                         _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
                else -> fetchPrayerTimes(context)
            }
        }
    }

    private fun loadUserData() {
        viewModelScope.launch {
            userPreferences.userSettings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    userName = settings.name,
                    settings = settings
                )
            }
        }
    }

    private fun startTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                updateCountdown()
            }
        }
    }

    private fun updateCountdown() {
        val prayerTimes = _uiState.value.prayerTimes
        if (prayerTimes.isNotEmpty()) {
            val nextInfo = getNextPrayerUseCase(prayerTimes)
            _uiState.value = _uiState.value.copy(
                currentTimeMillis = System.currentTimeMillis(),
                nextPrayerInfo = nextInfo
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchPrayerTimes(context: Context) {
        cachedContext = context.applicationContext
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        if (!hasLocationPermission(context)) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoadingLocation = false,
                error = "Location permission required"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isLoadingLocation = true,
                error = null,
                showCityInput = false
            )

            try {
                val location = getLastKnownLocationWithTimeout(context)

                if (location != null) {
                    Log.d(TAG, "Got location: ${location.latitude}, ${location.longitude}")
                    // Save GPS location for future app opens
                    viewModelScope.launch {
                        userPreferences.saveLocationSettings(
                            useGps = true,
                            city = "",
                            country = "",
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    }
                    fetchPrayerTimesByLocation(location.latitude, location.longitude)
                } else {
                    Log.d(TAG, "Location fetch timed out or failed")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingLocation = false,
                        showCityInput = true,
                        error = "Location timed out. Please enter your city manually."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching location", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingLocation = false,
                    showCityInput = true,
                    error = "Could not get location. Please enter your city manually."
                )
            }
        }
    }

    fun fetchPrayerTimesByCity(context: Context, cityName: String) {
        cachedContext = context.applicationContext
        if (cityName.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a city name")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Step 1: Search for city coordinates (more accurate for calculation methods)
                val searchResult = repository.searchCity(cityName)
                val cityData = searchResult.data.firstOrNull()

                if (cityData != null) {
                    // Save the coordinates and city name
                    viewModelScope.launch {
                        userPreferences.saveLocationSettings(
                            useGps = false,
                            city = cityData.name,
                            country = cityData.country,
                            latitude = cityData.latitude,
                            longitude = cityData.longitude
                        )
                    }
                    fetchPrayerTimesByLocation(cityData.latitude, cityData.longitude)
                } else {
                    // Step 2: Fallback to direct city name API if coordinates search fails
                    val result = repository.getPrayerTimesByCity(cityName)
                    result.fold(
                        onSuccess = { prayerTimes ->
                            viewModelScope.launch {
                                userPreferences.saveLocationSettings(
                                    useGps = false,
                                    city = cityName,
                                    country = "",
                                    latitude = 0.0,
                                    longitude = 0.0
                                )
                            }
                            handlePrayerTimesSuccess(prayerTimes)
                        },
                        onFailure = { error ->
                             _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Could not find prayer times for \"$cityName\". Please try another city."
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching by city", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to fetch prayer times: ${e.message}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocationWithTimeout(context: Context): Location? {
        if (!hasLocationPermission(context)) return null

        return try {
            val cancellationToken = CancellationTokenSource()

            val locationTask = fusedLocationClient?.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            )

            withTimeout(LOCATION_TIMEOUT_MS) {
                try {
                    locationTask?.await()
                } catch (e: Exception) {
                    Log.e(TAG, "Location task failed", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }

    private suspend fun fetchPrayerTimesByLocation(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(isLoadingLocation = false)

        try {
            val settings = userPreferences.userSettings.first()

            val result = withTimeout(API_TIMEOUT_MS) {
                repository.getPrayerTimes(
                    latitude = latitude,
                    longitude = longitude,
                    method = settings.calculationMethod,
                    madhab = settings.madhab
                )
            }

            result.fold(
                onSuccess = { prayerTimes ->
                    handlePrayerTimesSuccess(prayerTimes)
                },
                onFailure = { error ->
                    Log.e(TAG, "API error: ${error.message}", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load prayer times: ${error.message}"
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Timeout or error fetching prayer times", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Connection timed out. Please check your internet and try again."
            )
        }
    }

    private suspend fun handlePrayerTimesSuccess(prayerTimes: List<PrayerTime>) {
        val completedToday = repository.getCompletedPrayersToday()
        val streak = getPrayerStreakUseCase()
        val nextInfo = getNextPrayerUseCase(prayerTimes)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            prayerTimes = prayerTimes,
            completedPrayersToday = completedToday,
            streakDays = streak,
            nextPrayerInfo = nextInfo,
            error = null,
            showCityInput = false
        )

        // Schedule alarms for all prayers using latest settings
        scheduleAlarmsForPrayerTimes(prayerTimes)
    }

    private suspend fun scheduleAlarmsForPrayerTimes(prayerTimes: List<PrayerTime>) {
        if (cachedContext == null) return
        val settings = userPreferences.userSettings.first()
        val scheduler = com.sujood.app.notifications.PrayerAlarmScheduler(cachedContext!!)
        val notificationEnabled = Prayer.entries.map { prayer ->
            when (prayer) {
                Prayer.FAJR -> settings.fajrNotificationEnabled
                Prayer.DHUHR -> settings.dhuhrNotificationEnabled
                Prayer.ASR -> settings.asrNotificationEnabled
                Prayer.MAGHRIB -> settings.maghribNotificationEnabled
                Prayer.ISHA -> settings.ishaNotificationEnabled
            }
        }.toBooleanArray()
        val lockEnabled = Prayer.entries.map { prayer ->
            when (prayer) {
                Prayer.FAJR -> settings.fajrLockEnabled
                Prayer.DHUHR -> settings.dhuhrLockEnabled
                Prayer.ASR -> settings.asrLockEnabled
                Prayer.MAGHRIB -> settings.maghribLockEnabled
                Prayer.ISHA -> settings.ishaLockEnabled
            }
        }.toBooleanArray()
        scheduler.scheduleAllAlarms(prayerTimes, notificationEnabled, lockEnabled, settings.gracePeriodMinutes)
    }

    fun showCityInput() {
        _uiState.value = _uiState.value.copy(
            showCityInput = true,
            isLoading = false,
            isLoadingLocation = false,
            error = null
        )
    }

    fun hideCityInput() {
        _uiState.value = _uiState.value.copy(showCityInput = false)
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun logPrayerCompletion(prayer: Prayer) {
        viewModelScope.launch {
            // logPrayerCompletionUseCase checks for duplicates before inserting
            logPrayerCompletionUseCase(prayer)

            val completedToday = repository.getCompletedPrayersToday()
            val streak = getPrayerStreakUseCase()

            _uiState.value = _uiState.value.copy(
                completedPrayersToday = completedToday,
                streakDays = streak
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    class Factory(
        private val repository: PrayerTimesRepository,
        private val userPreferences: UserPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                repository = repository,
                userPreferences = userPreferences,
                logPrayerCompletionUseCase = LogPrayerCompletionUseCase(repository),
                getNextPrayerUseCase = GetNextPrayerUseCase(),
                getPrayerStreakUseCase = GetPrayerStreakUseCase(repository)
            ) as T
        }
    }
}

SUJOOD_HEREDOC_UI_SCREENS_HOME_HOMEVIEWMODEL_KT
echo "  ✓ ui/screens/home/HomeViewModel.kt"

mkdir -p "$(dirname "$BASE/ui/screens/insights/InsightsScreen.kt")"
cat > "$BASE/ui/screens/insights/InsightsScreen.kt" << 'SUJOOD_HEREDOC_UI_SCREENS_INSIGHTS_INSIGHTSSCREEN_KT'
package com.sujood.app.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.SujoodApplication
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.usecase.GetPrayerStreakUseCase
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun InsightsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as SujoodApplication
    val repository = remember {
        PrayerTimesRepository(RetrofitClient.aladhanApiService, app.database.prayerLogDao())
    }
    val streakUseCase = remember { GetPrayerStreakUseCase(repository) }

    var currentStreak by remember { mutableStateOf(0) }
    var longestStreak by remember { mutableStateOf(0) }
    var totalPrayers by remember { mutableStateOf(0) }
    var weeklyData by remember { mutableStateOf(listOf(0, 0, 0, 0, 0, 0, 0)) }
    var monthlyProgress by remember { mutableStateOf<Map<Prayer, Float>>(emptyMap()) }

    val allLogs by repository.getAllPrayerLogs().collectAsState(initial = emptyList())

    LaunchedEffect(allLogs) {
        withContext(Dispatchers.Default) {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = Calendar.getInstance()

            totalPrayers = allLogs.size
            currentStreak = streakUseCase()

            // Longest streak
            val allFullDates = allLogs
                .groupBy { it.date }
                .filter { (_, logs) -> logs.map { it.prayer.name }.toSet().size >= 5 }
                .keys
                .sortedDescending()

            var maxStreak = 0
            var runStreak = 0
            for (i in allFullDates.indices) {
                if (i == 0) {
                    runStreak = 1
                } else {
                    val prev = fmt.parse(allFullDates[i - 1])
                    val curr = fmt.parse(allFullDates[i])
                    if (prev != null && curr != null) {
                        val diff = ((prev.time - curr.time) / (24 * 60 * 60 * 1000)).toInt()
                        if (diff == 1) runStreak++ else runStreak = 1
                    }
                }
                if (runStreak > maxStreak) maxStreak = runStreak
            }
            longestStreak = maxOf(maxStreak, currentStreak)

            // Weekly data (Mon=0 .. Sun=6)
            val weekly = MutableList(7) { 0 }
            val weekStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            for (log in allLogs) {
                val logDate = fmt.parse(log.date) ?: continue
                val logCal = Calendar.getInstance().apply { time = logDate }
                if (!logCal.before(weekStart) && !logCal.after(today)) {
                    val dow = ((logCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7)
                    weekly[dow] = (weekly[dow] + 1).coerceAtMost(5)
                }
            }
            weeklyData = weekly

            // Monthly progress per prayer
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val daysInMonth = today.get(Calendar.DAY_OF_MONTH)
            val monthMap = mutableMapOf<Prayer, Float>()
            for (prayer in Prayer.entries) {
                val completedDays = allLogs
                    .filter { it.prayer == prayer }
                    .map { it.date }
                    .toSet()
                    .count { dateStr ->
                        val d = fmt.parse(dateStr) ?: return@count false
                        val c = Calendar.getInstance().apply { time = d }
                        !c.before(monthStart) && !c.after(today)
                    }
                monthMap[prayer] = (completedDays.toFloat() / daysInMonth).coerceIn(0f, 1f)
            }
            monthlyProgress = monthMap
        }
    }

    AnimatedGradientBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Insights", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light, color = Color.White)
            }

            // Streak Card
            item {
                FrostedGlassCard(modifier = Modifier.fillMaxWidth(),
                    borderColor = if (currentStreak > 0) WarmAmber.copy(alpha = 0.5f) else GlassBorder) {
                    Row(modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Current Streak", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥", fontSize = 32.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$currentStreak days", style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Light, color = WarmAmber)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Best", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$longestStreak days", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium, color = LavenderGlow)
                        }
                    }
                }
            }

            // Weekly Heatmap
            item {
                Text("This Week", style = MaterialTheme.typography.titleMedium, color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp))
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        val days = listOf("M", "T", "W", "T", "F", "S", "S")
                        weeklyData.forEachIndexed { index, count ->
                            val intensity = count / 5f
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(when {
                                            count == 5 -> WarmAmber.copy(alpha = intensity)
                                            count >= 3 -> SoftPurple.copy(alpha = intensity)
                                            count > 0 -> LavenderGlow.copy(alpha = intensity * 0.5f)
                                            else -> MidnightBlue
                                        })
                                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (count > 0) {
                                        Text("$count", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(days[index], style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // Monthly Progress
            item {
                Text("Monthly Progress", style = MaterialTheme.typography.titleMedium, color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp))
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Prayer.entries.forEach { prayer ->
                            val progress = monthlyProgress[prayer] ?: 0f
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(prayer.displayName, style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White, modifier = Modifier.width(80.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = SoftPurple, trackColor = MidnightBlue
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                                    modifier = Modifier.width(40.dp))
                            }
                        }
                    }
                }
            }

            // Total
            item {
                FrostedGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$totalPrayers", style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Light, color = SoftPurple)
                            Text("Total Prayers Logged", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            // Motivational
            item {
                val message = when {
                    currentStreak == 0 -> "Every journey begins with a single step. Start your streak today!"
                    currentStreak < 7 -> "Great start! Keep going to build a consistent habit."
                    currentStreak < 14 -> "You're doing amazing! Consistency is key to success."
                    else -> "SubhanAllah! You're on fire! May Allah keep you steadfast."
                }
                FrostedGlassCard(modifier = Modifier.fillMaxWidth(), borderColor = LavenderGlow.copy(alpha = 0.3f)) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💡", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

SUJOOD_HEREDOC_UI_SCREENS_INSIGHTS_INSIGHTSSCREEN_KT
echo "  ✓ ui/screens/insights/InsightsScreen.kt"

mkdir -p "$(dirname "$BASE/ui/screens/settings/SettingsScreen.kt")"
cat > "$BASE/ui/screens/settings/SettingsScreen.kt" << 'SUJOOD_HEREDOC_UI_SCREENS_SETTINGS_SETTINGSSCREEN_KT'
package com.sujood.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.sujood.app.SujoodApplication
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.notifications.PrayerAlarmScheduler
import com.sujood.app.domain.model.CalculationMethod
import com.sujood.app.domain.model.LockMode
import com.sujood.app.domain.model.Madhab
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.UserSettings
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by userPreferences.userSettings.collectAsState(initial = UserSettings())
    var showNameDialog by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }
    var showMadhabDialog by remember { mutableStateOf(false) }
    var showGracePeriodDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var showLockTriggerDialog by remember { mutableStateOf(false) }
    var showLockDurationDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Light
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy)
            )
        },
        containerColor = DeepNavy
    ) { paddingValues ->
        AnimatedGradientBackground(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Profile ──
                SettingsSectionHeader("Profile")
                SettingsCard {
                    SettingsClickableItem(
                        icon = Icons.Default.Person,
                        title = "Name",
                        subtitle = settings.name.ifEmpty { "Not set" },
                        onClick = { showNameDialog = true }
                    )
                }

                // ── Location ──
                SettingsSectionHeader("Location")
                SettingsCard {
                    SettingsClickableItem(
                        icon = Icons.Default.LocationOn,
                        title = "Prayer Location",
                        subtitle = if (settings.savedCity.isNotEmpty()) settings.savedCity
                                   else if (settings.savedLatitude != 0.0) "GPS: %.2f°, %.2f°".format(settings.savedLatitude, settings.savedLongitude)
                                   else "Not set — tap to change",
                        onClick = { showCityDialog = true }
                    )
                }

                // ── Prayer Calculation ──
                SettingsSectionHeader("Prayer Settings")
                SettingsCard {
                    SettingsClickableItem(
                        title = "Calculation Method",
                        subtitle = settings.calculationMethod.displayName,
                        onClick = { showMethodDialog = true }
                    )
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Madhab (Asr)",
                        subtitle = settings.madhab.displayName,
                        onClick = { showMadhabDialog = true }
                    )
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Grace Period",
                        subtitle = if (settings.gracePeriodMinutes == 0) "No grace period" else "${settings.gracePeriodMinutes} minutes",
                        onClick = { showGracePeriodDialog = true }
                    )
                }

                // ── Notifications ──
                SettingsSectionHeader("Notifications")
                SettingsCard {
                    Text(
                        text = "Receive a notification when each prayer time arrives",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                    )
                    Prayer.entries.forEach { prayer ->
                        SettingsToggleItem(
                            title = "${prayer.displayName} Notification",
                            isEnabled = when (prayer) {
                                Prayer.FAJR -> settings.fajrNotificationEnabled
                                Prayer.DHUHR -> settings.dhuhrNotificationEnabled
                                Prayer.ASR -> settings.asrNotificationEnabled
                                Prayer.MAGHRIB -> settings.maghribNotificationEnabled
                                Prayer.ISHA -> settings.ishaNotificationEnabled
                            },
                            onToggle = { enabled ->
                                scope.launch {
                                    userPreferences.saveNotificationEnabled(prayer.name, enabled)
                                    rescheduleAlarms(context, userPreferences)
                                }
                            }
                        )
                        if (prayer != Prayer.entries.last()) SettingsDivider()
                    }
                }

                // ── Prayer Lock ──
                SettingsSectionHeader("Prayer Lock")
                SettingsCard {
                    Text(
                        text = "Lock the phone at prayer time and block distractions",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                    )
                    Prayer.entries.forEach { prayer ->
                        SettingsToggleItem(
                            title = "Lock for ${prayer.displayName}",
                            isEnabled = when (prayer) {
                                Prayer.FAJR -> settings.fajrLockEnabled
                                Prayer.DHUHR -> settings.dhuhrLockEnabled
                                Prayer.ASR -> settings.asrLockEnabled
                                Prayer.MAGHRIB -> settings.maghribLockEnabled
                                Prayer.ISHA -> settings.ishaLockEnabled
                            },
                            onToggle = { enabled ->
                                scope.launch {
                                    userPreferences.saveLockEnabled(prayer.name, enabled)
                                    rescheduleAlarms(context, userPreferences)
                                }
                            }
                        )
                        if (prayer != Prayer.entries.last()) SettingsDivider()
                    }
                }

                // ── Lock Behavior ──
                SettingsSectionHeader("Lock Behavior")
                SettingsCard {
                    // Lock Mode
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Lock Mode", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LockMode.entries.forEach { mode ->
                                val isSelected = settings.lockMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            scope.launch {
                                                userPreferences.saveLockSettings(mode, settings.lockTriggerMinutes, settings.lockDurationMinutes)
                                            }
                                        }
                                        .background(
                                            if (isSelected) SoftPurple.copy(alpha = 0.2f) else MidnightBlue.copy(alpha = 0.5f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) SoftPurple else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Trigger Timing",
                        subtitle = if (settings.lockTriggerMinutes == 0) "At prayer time"
                                   else "${settings.lockTriggerMinutes} minutes after prayer",
                        onClick = { showLockTriggerDialog = true }
                    )
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Lock Duration",
                        subtitle = "${settings.lockDurationMinutes} minutes",
                        onClick = { showLockDurationDialog = true }
                    )
                }

                // ── Audio & Haptics ──
                SettingsSectionHeader("Audio & Haptics")
                SettingsCard {
                    SettingsToggleItem(
                        title = "Adhan Sound",
                        subtitle = "Play adhan when prayer time arrives",
                        isEnabled = settings.adhanEnabled,
                        onToggle = { enabled ->
                            scope.launch { userPreferences.saveAudioSettings(enabled, settings.vibrationEnabled) }
                        }
                    )
                    SettingsDivider()
                    SettingsToggleItem(
                        title = "Vibration",
                        subtitle = "Vibrate at prayer time",
                        isEnabled = settings.vibrationEnabled,
                        onToggle = { enabled ->
                            scope.launch { userPreferences.saveAudioSettings(settings.adhanEnabled, enabled) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Dialogs
    if (showNameDialog) {
        NameDialog(
            currentName = settings.name,
            onDismiss = { showNameDialog = false },
            onConfirm = { name -> scope.launch { userPreferences.saveUserName(name) }; showNameDialog = false }
        )
    }
    if (showCityDialog) {
        ChangeCityDialog(
            currentCity = settings.savedCity,
            onDismiss = { showCityDialog = false },
            onConfirm = { city ->
                scope.launch {
                    userPreferences.saveLocationSettings(
                        useGps = false, city = city, country = "", latitude = 0.0, longitude = 0.0
                    )
                }
                showCityDialog = false
            },
            onUseGps = {
                scope.launch {
                    userPreferences.saveLocationSettings(useGps = true, city = "", country = "", latitude = 0.0, longitude = 0.0)
                }
                showCityDialog = false
            }
        )
    }
    if (showMethodDialog) {
        CalculationMethodDialog(
            currentMethod = settings.calculationMethod,
            onDismiss = { showMethodDialog = false },
            onSelect = { method -> scope.launch { userPreferences.saveCalculationMethod(method) }; showMethodDialog = false }
        )
    }
    if (showMadhabDialog) {
        MadhabDialog(
            currentMadhab = settings.madhab,
            onDismiss = { showMadhabDialog = false },
            onSelect = { madhab -> scope.launch { userPreferences.saveMadhab(madhab) }; showMadhabDialog = false }
        )
    }
    if (showGracePeriodDialog) {
        GracePeriodDialog(
            currentMinutes = settings.gracePeriodMinutes,
            onDismiss = { showGracePeriodDialog = false },
            onSelect = { minutes -> scope.launch { userPreferences.saveGracePeriod(minutes) }; showGracePeriodDialog = false }
        )
    }
    if (showLockTriggerDialog) {
        TriggerDialog(
            currentMinutes = settings.lockTriggerMinutes,
            onDismiss = { showLockTriggerDialog = false },
            onSelect = { minutes ->
                scope.launch { userPreferences.saveLockSettings(settings.lockMode, minutes, settings.lockDurationMinutes) }
                showLockTriggerDialog = false
            }
        )
    }
    if (showLockDurationDialog) {
        DurationDialog(
            currentMinutes = settings.lockDurationMinutes,
            onDismiss = { showLockDurationDialog = false },
            onSelect = { minutes ->
                scope.launch { userPreferences.saveLockSettings(settings.lockMode, settings.lockTriggerMinutes, minutes) }
                showLockDurationDialog = false
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = SoftPurple,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    FrostedGlassCard(cornerRadius = 20.dp, contentPadding = 0.dp) {
        Column { content() }
    }
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = SoftPurple, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SoftPurple,
                checkedTrackColor = SoftPurple.copy(alpha = 0.4f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = MidnightBlue
            )
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(GlassBorder))
}

// ── Dialogs ──

@Composable
private fun NameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your Name") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SoftPurple, unfocusedBorderColor = GlassBorder)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, colors = ButtonDefaults.buttonColors(containerColor = SoftPurple)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MidnightBlue
    )
}

@Composable
private fun ChangeCityDialog(
    currentCity: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onUseGps: () -> Unit
) {
    var city by remember { mutableStateOf(currentCity) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Location") },
        text = {
            Column {
                OutlinedTextField(
                    value = city, onValueChange = { city = it },
                    label = { Text("City Name") },
                    placeholder = { Text("e.g. Dubai") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SoftPurple, unfocusedBorderColor = GlassBorder,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onUseGps) {
                    Text("Use GPS instead", color = LavenderGlow)
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (city.isNotBlank()) onConfirm(city.trim()) },
                   colors = ButtonDefaults.buttonColors(containerColor = SoftPurple)) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MidnightBlue
    )
}

@Composable
private fun CalculationMethodDialog(currentMethod: CalculationMethod, onDismiss: () -> Unit, onSelect: (CalculationMethod) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calculation Method") },
        text = {
            Column {
                CalculationMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(method) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(method.displayName)
                        if (method == currentMethod) Icon(Icons.Default.Check, null, tint = SoftPurple)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun MadhabDialog(currentMadhab: Madhab, onDismiss: () -> Unit, onSelect: (Madhab) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Madhab") },
        text = {
            Column {
                Madhab.entries.forEach { madhab ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(madhab) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(madhab.displayName)
                        if (madhab == currentMadhab) Icon(Icons.Default.Check, null, tint = SoftPurple)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun GracePeriodDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grace Period") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (minutes == 0) "No grace period" else "$minutes minutes")
                        if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = SoftPurple)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun TriggerDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock Trigger") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (minutes == 0) "At prayer time" else "$minutes minutes after prayer")
                        if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun DurationDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(5, 10, 15, 20, 30, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock Duration") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$minutes minutes")
                        if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

// Helper: re-fetch prayer times with updated settings and reschedule alarms
private suspend fun rescheduleAlarms(
    context: android.content.Context,
    userPreferences: com.sujood.app.data.local.datastore.UserPreferences
) {
    val settings = userPreferences.userSettings.first()
    val app = context.applicationContext as SujoodApplication
    val repository = PrayerTimesRepository(RetrofitClient.aladhanApiService, app.database.prayerLogDao())

    val result = when {
        settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 ->
            repository.getPrayerTimes(settings.savedLatitude, settings.savedLongitude, settings.calculationMethod, settings.madhab)
        settings.savedCity.isNotEmpty() -> repository.getPrayerTimesByCity(settings.savedCity)
        else -> return
    }

    result.onSuccess { prayerTimes ->
        val scheduler = PrayerAlarmScheduler(context)
        val notif = com.sujood.app.domain.model.Prayer.entries.map { p ->
            when (p) {
                com.sujood.app.domain.model.Prayer.FAJR -> settings.fajrNotificationEnabled
                com.sujood.app.domain.model.Prayer.DHUHR -> settings.dhuhrNotificationEnabled
                com.sujood.app.domain.model.Prayer.ASR -> settings.asrNotificationEnabled
                com.sujood.app.domain.model.Prayer.MAGHRIB -> settings.maghribNotificationEnabled
                com.sujood.app.domain.model.Prayer.ISHA -> settings.ishaNotificationEnabled
            }
        }.toBooleanArray()
        val lock = com.sujood.app.domain.model.Prayer.entries.map { p ->
            when (p) {
                com.sujood.app.domain.model.Prayer.FAJR -> settings.fajrLockEnabled
                com.sujood.app.domain.model.Prayer.DHUHR -> settings.dhuhrLockEnabled
                com.sujood.app.domain.model.Prayer.ASR -> settings.asrLockEnabled
                com.sujood.app.domain.model.Prayer.MAGHRIB -> settings.maghribLockEnabled
                com.sujood.app.domain.model.Prayer.ISHA -> settings.ishaLockEnabled
            }
        }.toBooleanArray()
        scheduler.scheduleAllAlarms(prayerTimes, notif, lock, settings.gracePeriodMinutes)
    }
}

SUJOOD_HEREDOC_UI_SCREENS_SETTINGS_SETTINGSSCREEN_KT
echo "  ✓ ui/screens/settings/SettingsScreen.kt"

mkdir -p "$(dirname "$BASE/ui/screens/qibla/QiblaScreen.kt")"
cat > "$BASE/ui/screens/qibla/QiblaScreen.kt" << 'SUJOOD_HEREDOC_UI_SCREENS_QIBLA_QIBLASCREEN_KT'
package com.sujood.app.ui.screens.qibla

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun QiblaScreen() {
    val context = LocalContext.current
    var userLatitude by remember { mutableStateOf(0.0) }
    var userLongitude by remember { mutableStateOf(0.0) }
    var currentHeading by remember { mutableFloatStateOf(0f) }
    // Default to Mecca direction from UAE as fallback
    var qiblaDirection by remember { mutableFloatStateOf(277f) }
    var isCalibrated by remember { mutableStateOf(false) }
    var isFacingQibla by remember { mutableStateOf(false) }
    var hasLocation by remember { mutableStateOf(false) }

    val userPreferences = remember { UserPreferences(context) }

    // Get last known location to compute accurate Qibla direction
    LaunchedEffect(Unit) {
        userPreferences.userSettings.first().let { settings ->
            if (settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0) {
                userLatitude = settings.savedLatitude
                userLongitude = settings.savedLongitude
                hasLocation = true
                qiblaDirection = calculateQiblaDirection(
                    userLatitude, userLongitude,
                    KAABA_LATITUDE, KAABA_LONGITUDE
                )
            } else {
                // Try last-known GPS as fallback
                try {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                    if (location != null) {
                        userLatitude = location.latitude
                        userLongitude = location.longitude
                        hasLocation = true
                        qiblaDirection = calculateQiblaDirection(
                            userLatitude, userLongitude,
                            KAABA_LATITUDE, KAABA_LONGITUDE
                        )
                    }
                    // If location is still null, hasLocation stays false and the UI
                    // will show "Using approximate direction" warning — no fake angle shown
                } catch (e: SecurityException) { }
            }
        }
    }

    // Compass sensor
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var gravity: FloatArray? = null
        var geomagnetic: FloatArray? = null

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> gravity = event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values.clone()
                }

                val g = gravity
                val geo = geomagnetic
                if (g != null && geo != null) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, g, geo)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(r, orientation)
                        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

                        if (hasLocation) {
                            val geoField = android.hardware.GeomagneticField(
                                userLatitude.toFloat(), userLongitude.toFloat(),
                                0f, System.currentTimeMillis()
                            )
                            azimuth += geoField.declination
                        }

                        azimuth = (azimuth + 360) % 360
                        currentHeading = azimuth

                        val angleDiff = calculateAngleDifference(azimuth, qiblaDirection)
                        isFacingQibla = angleDiff < 5f
                        isCalibrated = true
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                isCalibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
        }

        accelerometer?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }

        onDispose { sensorManager.unregisterListener(sensorListener) }
    }

    val animatedHeading by animateFloatAsState(
        targetValue = currentHeading,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "headingAnimation"
    )

    // Needle angle: how much to rotate the needle so it points at Qibla
    // If heading = 0 (facing North) and qibla = 90° (East), needle should point right = 90°
    val needleRotation = (qiblaDirection - animatedHeading + 360) % 360

    AnimatedGradientBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Qibla Direction",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    hasLocation -> "Based on your current location"
                    else -> "⚠️ No location found — open Home tab first"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasLocation) TextSecondary else WarmAmber
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Compass Widget ──
            Box(
                modifier = Modifier.size(300.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring (static — always facing up)
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .scale(if (isFacingQibla) 1.05f else 1f)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    if (isFacingQibla) WarmAmber.copy(alpha = 0.25f)
                                    else SoftPurple.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Static compass ring — cardinal directions stay fixed
                Canvas(modifier = Modifier.size(280.dp)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = size.minDimension / 2,
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = size.minDimension / 2 - 20
                    )
                    // Cardinal tick marks (N, E, S, W) — STATIC
                    listOf(0f, 90f, 180f, 270f).forEach { angle ->
                        val radian = Math.toRadians((angle - 90).toDouble())
                        val outerR = size.minDimension / 2
                        val innerR = outerR - 20
                        drawLine(
                            color = Color.White.copy(alpha = 0.35f),
                            start = Offset(
                                size.width / 2 + outerR * cos(radian).toFloat(),
                                size.height / 2 + outerR * sin(radian).toFloat()
                            ),
                            end = Offset(
                                size.width / 2 + innerR * cos(radian).toFloat(),
                                size.height / 2 + innerR * sin(radian).toFloat()
                            ),
                            strokeWidth = 3f
                        )
                    }
                }

                // ── NEEDLE ONLY ROTATES — label is separate ──
                // Needle canvas — rotated to point at Qibla
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .rotate(needleRotation)
                ) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2

                    // Needle shadow/glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isFacingQibla) WarmAmber.copy(alpha = 0.3f) else LavenderGlow.copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            center = Offset(centerX, centerY - 55f),
                            radius = 30f
                        ),
                        radius = 30f,
                        center = Offset(centerX, centerY - 55f)
                    )

                    val needlePath = Path().apply {
                        moveTo(centerX, centerY - 80)
                        lineTo(centerX - 12, centerY + 24)
                        lineTo(centerX, centerY + 14)
                        lineTo(centerX + 12, centerY + 24)
                        close()
                    }

                    drawPath(
                        path = needlePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (isFacingQibla) WarmAmber else LavenderGlow,
                                if (isFacingQibla) WarmAmber.copy(alpha = 0.4f) else SoftPurple.copy(alpha = 0.4f)
                            )
                        )
                    )

                    // Center dot
                    drawCircle(
                        color = Color.White,
                        radius = 8f,
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = if (isFacingQibla) WarmAmber else SoftPurple,
                        radius = 4f,
                        center = Offset(centerX, centerY)
                    )
                }

                // ── KAABA LABEL — STATIC, always at top of compass ──
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFacingQibla) WarmAmber.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            1.dp,
                            if (isFacingQibla) WarmAmber.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    // Kaaba Icon instead of "N"
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color.Black, RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Golden belt of the Kaaba
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .padding(top = 4.dp)
                                .background(Color(0xFFFFD700))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (isFacingQibla) "✓ Facing Qibla" else "${qiblaDirection.roundToInt()}° from North",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = if (isFacingQibla) WarmAmber else LavenderGlow
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isFacingQibla)
                    "You are facing the Kaaba. May Allah accept your prayer."
                else
                    "Rotate your phone until the arrow points North, then face that direction",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            if (!isCalibrated) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "⚠️ Calibrate compass — move phone in figure-8 pattern",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmAmber,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(text = "Kaaba, Makkah Al-Mukarramah", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "21.4225° N, 39.8262° E", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

private fun calculateQiblaDirection(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    var bearing = Math.toDegrees(atan2(y, x)).toFloat()
    bearing = (bearing + 360) % 360
    return bearing
}

private fun calculateAngleDifference(angle1: Float, angle2: Float): Float {
    var diff = kotlin.math.abs(angle1 - angle2)
    if (diff > 180) diff = 360 - diff
    return diff
}

private const val KAABA_LATITUDE = 21.4225
private const val KAABA_LONGITUDE = 39.8262

SUJOOD_HEREDOC_UI_SCREENS_QIBLA_QIBLASCREEN_KT
echo "  ✓ ui/screens/qibla/QiblaScreen.kt"

mkdir -p "$(dirname "$BASE/ui/components/MiscComponents.kt")"
cat > "$BASE/ui/components/MiscComponents.kt" << 'SUJOOD_HEREDOC_UI_COMPONENTS_MISCCOMPONENTS_KT'
package com.sujood.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sujood.app.domain.model.Prayer
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.delay

@Composable
fun GreetingSection(
    userName: String,
    nextPrayer: Prayer?,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { -50 }
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Assalamu Alaikum",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = LavenderGlow
            )

            if (userName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
            }
            // "Time for X" removed here — shown by CountdownTimer below the sun arc
        }
    }
}

@Composable
fun DailyQuoteCard(
    modifier: Modifier = Modifier
) {
    data class Quote(val text: String, val source: String, val isHadith: Boolean)

    val quotes = listOf(
        Quote("Indeed, those who have believed and done righteous deeds – the Most Merciful will appoint for them affection.", "Quran 19:96", false),
        Quote("Whoever prays Fajr is under the protection of Allah.", "Sahih Muslim", true),
        Quote("The best of deeds in the sight of Allah are those done regularly, even if they are small.", "Sahih Bukhari & Muslim", true),
        Quote("Indeed, prayer has been decreed upon the believers at specified times.", "Quran 4:103", false),
        Quote("The strong person is not the one who overcomes others by force, but the one who controls himself when angry.", "Sahih Bukhari", true),
        Quote("And whoever relies upon Allah – then He is sufficient for him.", "Quran 65:3", false),
        Quote("The best of people are those most beneficial to others.", "Al-Mu'jam al-Awsat", true),
        Quote("So remember Me; I will remember you. And be grateful to Me and do not deny Me.", "Quran 2:152", false),
        Quote("Make things easy and do not make them difficult. Give glad tidings and do not drive people away.", "Sahih Bukhari", true),
        Quote("He who thanks people has thanked Allah.", "Sunan Abu Dawud", true)
    )

    val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    val quote = quotes[dayOfYear % quotes.size]

    FrostedGlassCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = WarmAmber,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.size(12.dp))

            Column {
                Text(
                    text = if (quote.isHadith) "Hadith of the Day" else "Verse of the Day",
                    style = MaterialTheme.typography.labelMedium,
                    color = SoftPurple
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = quote.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "— ${quote.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun PrayerStreakCard(
    streakDays: Int,
    completedPrayersToday: List<Prayer>,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 50 }
    ) {
        FrostedGlassCard(
            modifier = modifier,
            cornerRadius = 16.dp,
            borderColor = if (streakDays > 0) WarmAmber.copy(alpha = 0.5f) else Color.Unspecified
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Prayer Streak",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "glow")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )

                        Text(
                            text = streakDays.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Light,
                            color = if (streakDays > 0) WarmAmber.copy(alpha = alpha.coerceIn(0f, 1f)) else TextSecondary
                        )

                        Text(
                            text = " days",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                // Progress indicator
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row {
                        Prayer.entries.forEach { prayer ->
                            val isCompleted = completedPrayersToday.contains(prayer)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCompleted) WarmAmber
                                        else Color.White.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }

                    Text(
                        text = "${completedPrayersToday.size}/5",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

SUJOOD_HEREDOC_UI_COMPONENTS_MISCCOMPONENTS_KT
echo "  ✓ ui/components/MiscComponents.kt"

mkdir -p "$(dirname "$BASE/MainActivity.kt")"
cat > "$BASE/MainActivity.kt" << 'SUJOOD_HEREDOC_MAINACTIVITY_KT'
package com.sujood.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.ui.components.GlassmorphicBottomNavBar
import com.sujood.app.ui.screens.dhikr.DhikrScreen
import com.sujood.app.ui.screens.home.HomeScreen
import com.sujood.app.ui.screens.insights.InsightsScreen
import com.sujood.app.ui.screens.onboarding.EmotionalOnboardingScreen
import com.sujood.app.ui.screens.qibla.QiblaScreen
import com.sujood.app.ui.screens.monetization.MonetizationScreen
import com.sujood.app.ui.screens.splash.SplashScreen
import com.sujood.app.ui.screens.settings.SettingsScreen
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.SujoodTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var userPreferences: UserPreferences
    private lateinit var repository: PrayerTimesRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled silently — HomeScreen shows error if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Request overlay (SYSTEM_ALERT_WINDOW) permission if not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)
        ) {
            val overlayIntent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
        }

        userPreferences = UserPreferences(this)
        repository = PrayerTimesRepository(
            RetrofitClient.aladhanApiService,
            (application as SujoodApplication).database.prayerLogDao()
        )

        setContent {
            SujoodTheme {
                SujoodApp(
                    userPreferences = userPreferences,
                    repository = repository
                )
            }
        }
    }
}

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Monetization : Screen("monetization")
    data object Home : Screen("home")
    data object Dhikr : Screen("dhikr")
    data object Qibla : Screen("qibla")
    data object Insights : Screen("insights")
    data object Settings : Screen("settings")
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SujoodApp(
    userPreferences: UserPreferences,
    repository: PrayerTimesRepository
) {
    val navController = rememberNavController()
    val settings by userPreferences.userSettings.collectAsState(initial = com.sujood.app.domain.model.UserSettings())
    val hasCompletedOnboarding = settings.hasCompletedOnboarding

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Splash.route

    val screensWithBottomNav = listOf(
        Screen.Home.route,
        Screen.Dhikr.route,
        Screen.Qibla.route,
        Screen.Insights.route,
        Screen.Settings.route
    )

    val showBottomNavBar = currentRoute in screensWithBottomNav

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                if (showBottomNavBar) {
                    AnimatedVisibility(
                        visible = showBottomNavBar,
                        enter = fadeIn() + slideInVertically { fullHeight -> fullHeight },
                        exit = fadeOut() + slideOutVertically { fullHeight -> fullHeight }
                    ) {
                        GlassmorphicBottomNavBar(
                            currentRoute = currentRoute,
                            onNavigate = { newRoute ->
                                navController.navigate(newRoute) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(Screen.Splash.route) {
                    SplashScreen(
                        onNavigate = {
                            if (hasCompletedOnboarding) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Screen.Onboarding.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable(Screen.Onboarding.route) {
                    EmotionalOnboardingScreen(
                        userPreferences = userPreferences,
                        onComplete = {
                            navController.navigate(Screen.Monetization.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Monetization.route) {
                    MonetizationScreen(
                        userPreferences = userPreferences,
                        onContinue = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Monetization.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Home.route) {
                    HomeScreen(
                        userPreferences = userPreferences,
                        repository = repository
                    )
                }

                composable(Screen.Dhikr.route) {
                    DhikrScreen()
                }

                composable(Screen.Qibla.route) {
                    QiblaScreen()
                }

                composable(Screen.Insights.route) {
                    InsightsScreen()
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        userPreferences = userPreferences,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

SUJOOD_HEREDOC_MAINACTIVITY_KT
echo "  ✓ MainActivity.kt"

echo ""
echo "All fixes applied!"
echo "Run: git add . && git commit -m \"fix: alarms, streak, insights, permissions, overlay\" && git push"
