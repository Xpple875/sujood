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
        // Parse the time string to calendar (robust against 12-hour AM/PM formats)
        val timeUpper = prayerTime.time.uppercase()
        val isPm = timeUpper.contains("PM")
        val isAm = timeUpper.contains("AM")

        // Strip out any non-numeric and non-colon characters to prevent NumberFormatException
        val cleanTime = prayerTime.time.replace(Regex("[^0-9:]"), "")
        val timeParts = cleanTime.split(":")
        
        var hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        // Adjust for 12-hour format
        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0

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
