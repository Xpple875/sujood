package com.sujood.app.service

import android.annotation.SuppressLint
import android.app.Notification
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
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.sujood.app.MainActivity
import com.sujood.app.R
import com.sujood.app.SujoodApplication
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.ui.views.CircularTimerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class PrayerLockOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var mediaPlayer: MediaPlayer? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values[2] < -8f) muteAudio()
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
        val prayerName   = intent?.getStringExtra(PRAYER_NAME)   ?: "Prayer"
        val prayerArabic = intent?.getStringExtra(PRAYER_ARABIC) ?: "الصلاة"

        startForeground(NOTIFICATION_ID, createNotification(prayerName))

        serviceScope.launch {
            val prefs    = UserPreferences(applicationContext)
            val settings = prefs.userSettings.first()

            // Respect global prayer lock toggle
            if (!settings.prayerLockEnabled) {
                stopSelf()
                return@launch
            }

            // Audio / vibration
            if (settings.adhanEnabled)    launch(Dispatchers.Main) { playAdhan() }
            if (settings.vibrationEnabled) launch(Dispatchers.Main) { vibrateDevice() }

            // Show overlay on main thread
            val minDurationMs = settings.minLockDurationMinutes * 60 * 1000L
            val quote = settings.overlayQuote.ifEmpty {
                "\"Indeed, prayer has been decreed upon the believers at specified times.\""
            }

            launch(Dispatchers.Main) {
                showOverlay(prayerName, quote, minDurationMs)
            }
        }

        accelerometer?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        return START_NOT_STICKY
    }

    @SuppressLint("InflateParams")
    private fun showOverlay(prayerName: String, quote: String, minDurationMs: Long) {
        if (overlayView != null) return

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView  = inflater.inflate(R.layout.prayer_lock_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.CENTER }

        overlayView!!.apply {
            // Quote
            findViewById<TextView>(R.id.quoteText)?.text = quote

            val circularTimer   = findViewById<CircularTimerView>(R.id.circularTimer)
            val timerText       = findViewById<TextView>(R.id.timerText)
            val progressBar     = findViewById<ProgressBar>(R.id.focusProgressBar)
            val progressPercent = findViewById<TextView>(R.id.progressPercent)
            val iPrayedButton   = findViewById<Button>(R.id.iPrayedButton)

            // Mute: tap the arc area
            circularTimer?.setOnClickListener { muteAudio() }

            if (minDurationMs <= 0L) {
                // No minimum — button active immediately, no timer shown
                timerText?.text  = ""
                circularTimer?.progress = 0f
                progressBar?.progress  = 100
                progressPercent?.text  = "100%"
                iPrayedButton?.isEnabled = true
                iPrayedButton?.setOnClickListener { onPrayerCompleted(prayerName) }
            } else {
                iPrayedButton?.isEnabled = false
                iPrayedButton?.alpha = 0.4f

                // Countdown loop
                serviceScope.launch(Dispatchers.Main) {
                    val totalMs   = minDurationMs
                    val startTime = System.currentTimeMillis()

                    while (true) {
                        val elapsed   = System.currentTimeMillis() - startTime
                        val remaining = (totalMs - elapsed).coerceAtLeast(0L)
                        val fraction  = remaining.toFloat() / totalMs.toFloat()
                        val pct       = ((1f - fraction) * 100).toInt().coerceIn(0, 100)

                        val totalSec = (remaining / 1000L).toInt()
                        val mins     = totalSec / 60
                        val secs     = totalSec % 60

                        timerText?.text       = String.format("%02d:%02d", mins, secs)
                        circularTimer?.progress = fraction
                        progressBar?.progress   = pct
                        progressPercent?.text   = "$pct%"

                        if (remaining <= 0L) break
                        delay(500)
                    }

                    // Timer done — unlock button
                    timerText?.text       = "00:00"
                    circularTimer?.progress = 0f
                    progressBar?.progress   = 100
                    progressPercent?.text   = "100%"
                    iPrayedButton?.isEnabled = true
                    iPrayedButton?.alpha    = 1f
                    iPrayedButton?.setOnClickListener { onPrayerCompleted(prayerName) }
                }
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
                val app  = application as SujoodApplication
                val repo = PrayerTimesRepository(RetrofitClient.aladhanApiService, app.database.prayerLogDao())
                val prayer = try { Prayer.valueOf(prayerName.uppercase()) } catch (e: Exception) { null }
                prayer?.let { repo.logPrayerCompletion(it) }
            } catch (e: Exception) { e.printStackTrace() }
            launch(Dispatchers.Main) { dismissOverlay() }
        }
    }

    private fun createNotification(prayerName: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, SujoodApplication.OVERLAY_CHANNEL_ID)
            .setContentTitle("Prayer Reminder")
            .setContentText("It's time for $prayerName")
            .setSmallIcon(R.drawable.ic_prayer_icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
    }

    private fun playAdhan() {
        try {
            val adhanUrl = settings.adhanSoundUrl.ifEmpty { "https://cdn.islamic.network/quran/audio/128/ar.alafasy/1.mp3" }
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(adhanUrl)
                isLooping = false
                prepareAsync()
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ ->
                    try {
                        reset()
                        setDataSource(applicationContext,
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                        isLooping = true; prepare(); start()
                    } catch (ex: Exception) { ex.printStackTrace() }
                    true
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun muteAudio() {
        try {
            mediaPlayer?.let { if (it.isPlaying) { it.stop(); it.release() } }
            mediaPlayer = null
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager)
                    .defaultVibrator.vibrate(
                        android.os.VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500), -1)
                    )
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(
                    longArrayOf(0, 500, 300, 500), -1
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun dismissOverlay() {
        muteAudio()
        sensorManager?.unregisterListener(sensorListener)
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { e.printStackTrace() }
            overlayView = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        muteAudio()
        sensorManager?.unregisterListener(sensorListener)
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { e.printStackTrace() }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
        const val PRAYER_NAME   = "PRAYER_NAME"
        const val PRAYER_ARABIC = "PRAYER_ARABIC"

        fun start(context: Context, prayerName: String, prayerArabic: String) {
            val intent = Intent(context, PrayerLockOverlayService::class.java).apply {
                putExtra(PRAYER_NAME, prayerName)
                putExtra(PRAYER_ARABIC, prayerArabic)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrayerLockOverlayService::class.java))
        }
    }
}
