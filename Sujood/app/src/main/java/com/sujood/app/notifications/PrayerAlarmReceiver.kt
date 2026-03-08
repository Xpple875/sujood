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
