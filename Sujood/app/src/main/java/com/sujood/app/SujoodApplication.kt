package com.sujood.app

import com.sujood.app.data.auth.AuthRepository

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.local.room.SujoodDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main Application class for Sujood app.
 * Initializes app-wide components like notification channels and databases.
 */
class SujoodApplication : Application() {

    lateinit var authRepository: AuthRepository
        private set


    // Application scope for coroutines
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Lazy initialization of database
    val database: SujoodDatabase by lazy {
        SujoodDatabase.getDatabase(this)
    }

    // Lazy initialization of user preferences
    val userPreferences: UserPreferences by lazy {
        UserPreferences(this)
    }

    override fun onCreate() {
        super.onCreate()
        authRepository = AuthRepository(this)
        createNotificationChannels()
    }

    /**
     * Creates notification channels for prayer times and overlay notifications.
     * Required for Android O (API 26) and above.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Prayer time notification channel
            val prayerChannel = NotificationChannel(
                PRAYER_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                enableVibration(true)
                setShowBadge(true)
            }

            // Overlay/lock notification channel
            val overlayChannel = NotificationChannel(
                OVERLAY_CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.overlay_channel_description)
                enableVibration(true)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(listOf(prayerChannel, overlayChannel))
        }
    }

    companion object {
        const val PRAYER_CHANNEL_ID = "prayer_times_channel"
        const val OVERLAY_CHANNEL_ID = "prayer_lock_channel"
    }
}
