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

        // Start Audio
        playAlert()

        // Start Sensor
        accelerometer?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        return START_NOT_STICKY
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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            // Force focus to prevent background interaction
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
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
