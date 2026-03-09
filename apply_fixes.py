import os

ROOT = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(ROOT, "Sujood", "app", "src", "main", "java", "com", "sujood", "app")
MANIFEST = os.path.join(ROOT, "Sujood", "app", "src", "main", "AndroidManifest.xml")

files = {}

# ─────────────────────────────────────────────────────────────────────────────
# 1. AndroidManifest.xml  — add <queries> so Android 11+ lets us read app icons
# ─────────────────────────────────────────────────────────────────────────────
manifest_content = r'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Location permissions -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Notification permissions -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- Alarm permissions -->
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <!-- Overlay permission for prayer lock -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <!-- Foreground service permission -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

    <!-- Internet for API -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Wake lock for alarms -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!--
        Required on Android 11+ (API 30+) to read icons / info for other apps.
        Without this block PackageManager.getApplicationIcon() always throws
        NameNotFoundException and the "Choose Apps to Block" list stays empty.
    -->
    <queries>
        <package android:name="com.zhiliaoapp.musically" />
        <package android:name="com.instagram.android" />
        <package android:name="com.google.android.youtube" />
        <package android:name="com.twitter.android" />
        <package android:name="com.snapchat.android" />
        <package android:name="com.facebook.katana" />
        <package android:name="com.whatsapp" />
        <package android:name="com.reddit.frontpage" />
        <package android:name="com.netflix.mediaclient" />
        <package android:name="tv.twitch.android.app" />
        <package android:name="com.discord" />
        <package android:name="org.telegram.messenger" />
        <package android:name="com.spotify.music" />
        <package android:name="com.pinterest" />
        <package android:name="com.linkedin.android" />
    </queries>

    <application
        android:name=".SujoodApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Sujood"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Sujood">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Overlay Service for Prayer Lock -->
        <service
            android:name=".service.PrayerLockOverlayService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="prayer_reminder_lock" />
        </service>

        <!-- Alarm Receiver -->
        <receiver
            android:name=".notifications.PrayerAlarmReceiver"
            android:enabled="true"
            android:exported="false">
            <intent-filter>
                <action android:name="com.sujood.app.PRAYER_ALARM" />
            </intent-filter>
        </receiver>

        <!-- Prayer Action Receiver -->
        <receiver
            android:name=".notifications.PrayerActionReceiver"
            android:enabled="true"
            android:exported="false">
            <intent-filter>
                <action android:name="com.sujood.app.I_PRAYED" />
            </intent-filter>
        </receiver>

        <!-- Boot Receiver to reschedule alarms -->
        <receiver
            android:name=".notifications.BootReceiver"
            android:enabled="true"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>

</manifest>
'''

# ─────────────────────────────────────────────────────────────────────────────
# 2. QiblaScreen.kt  — replace yellow Kaaba box with Icons.Filled.Mosque
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/qibla/QiblaScreen.kt"] = r'''package com.sujood.app.ui.screens.qibla

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.flow.first

// Low-pass filter strength: 0.10 = very smooth, 0.25 = balanced, 0.4 = responsive
private const val LP_ALPHA = 0.12f

private fun lowPassFilter(input: FloatArray, prev: FloatArray?): FloatArray {
    if (prev == null) return input.clone()
    return FloatArray(input.size) { i -> prev[i] + LP_ALPHA * (input[i] - prev[i]) }
}

private fun shortestDelta(from: Float, to: Float): Float =
    ((to - from + 540f) % 360f) - 180f

@Composable
fun QiblaScreen() {
    val context         = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }

    var deviceHeading   by remember { mutableFloatStateOf(0f) }
    var qiblaDirection  by remember { mutableFloatStateOf(0f) }
    var isCalibrated    by remember { mutableStateOf(false) }
    var statusMessage   by remember { mutableStateOf("Initialising sensors…") }
    var userLatitude    by remember { mutableStateOf(0.0) }
    var userLongitude   by remember { mutableStateOf(0.0) }

    // Load saved location from DataStore
    LaunchedEffect(Unit) {
        val settings = userPreferences.userSettings.first()
        when {
            settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 -> {
                userLatitude  = settings.savedLatitude
                userLongitude = settings.savedLongitude
                qiblaDirection = calculateQiblaDirection(userLatitude, userLongitude, KAABA_LAT, KAABA_LON)
                statusMessage = "Location loaded from settings"
            }
            settings.savedCity.isNotEmpty() -> {
                try {
                    val lm  = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (loc != null) {
                        userLatitude  = loc.latitude
                        userLongitude = loc.longitude
                        qiblaDirection = calculateQiblaDirection(userLatitude, userLongitude, KAABA_LAT, KAABA_LON)
                        statusMessage = "Location from GPS"
                    } else {
                        statusMessage = "Open Home tab to load your city location"
                    }
                } catch (_: Exception) {
                    statusMessage = "Open Home tab to load your city location"
                }
            }
            else -> statusMessage = "⚠️ No location found — open Home tab first"
        }
    }

    // Uses the ORIGINAL working approach from this commit:
    // 1. Raw accel + mag readings are each low-pass filtered independently
    // 2. SensorManager.getRotationMatrix() then getOrientation() gives azimuth
    // 3. The raw azimuth is converted to degrees, then a second LP filter is applied
    //    on the HEADING ANGLE itself (interpolating the shortest arc).
    // AND by doing a second LP filter on the heading angle itself.
    var smoothedHeading by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sm   = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag   = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var accelVals: FloatArray? = null
        var magVals:   FloatArray? = null
        var prevAccel: FloatArray? = null
        var prevMag:   FloatArray? = null
        var accuracy  = 0

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        prevAccel  = lowPassFilter(event.values.clone(), prevAccel)
                        accelVals  = prevAccel
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        prevMag  = lowPassFilter(event.values.clone(), prevMag)
                        magVals  = prevMag
                    }
                }
                val a = accelVals ?: return
                val m = magVals   ?: return
                val R = FloatArray(9); val I = FloatArray(9)
                if (!SensorManager.getRotationMatrix(R, I, a, m)) return
                val orient = FloatArray(3)
                SensorManager.getOrientation(R, orient)
                // ← original working call, NO remapCoordinateSystem
                val rawDeg = Math.toDegrees(orient[0].toDouble()).toFloat()
                val rawNorm = ((rawDeg % 360f) + 360f) % 360f
                // Second LP filter on the angle — interpolate shortest arc
                val delta = shortestDelta(smoothedHeading, rawNorm)
                smoothedHeading = ((smoothedHeading + LP_ALPHA * delta) + 360f) % 360f
                deviceHeading   = smoothedHeading
            }
            override fun onAccuracyChanged(sensor: Sensor, acc: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    accuracy     = acc
                    isCalibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                }
            }
        }
        accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let   { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose  { sm.unregisterListener(listener) }
    }

    val animatedRotation by animateFloatAsState(
        targetValue = -deviceHeading,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "compassRotation"
    )

    val needleRotation by animateFloatAsState(
        targetValue = (qiblaDirection - deviceHeading + 360f) % 360f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "needleRotation"
    )

    val isFacingQibla = kotlin.math.abs(
        shortestDelta(deviceHeading, qiblaDirection)
    ) < 5f

    val compassScale by animateFloatAsState(
        targetValue   = if (isFacingQibla) 1.04f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "compassScale"
    )

    AnimatedGradientBackground {
        Column(
            modifier            = Modifier.fillMaxSize().padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                "Qibla Compass",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = if (!isCalibrated) "Move your phone in a figure-8 to calibrate"
                        else statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (!isCalibrated) Color(0xFFFBBF24) else TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(48.dp))

            // ── Compass rose ──────────────────────────────────────────────
            Box(
                modifier            = Modifier.size(280.dp).scale(compassScale),
                contentAlignment    = Alignment.Center
            ) {
                // Outer ring rotates with device heading
                Canvas(modifier = Modifier.fillMaxSize().rotate(animatedRotation)) {
                    val cx = size.width  / 2f
                    val cy = size.height / 2f
                    val r  = size.minDimension / 2f

                    // Outer glow ring
                    drawCircle(
                        brush  = Brush.radialGradient(
                            colors = listOf(LavenderGlow.copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(cx, cy), radius = r
                        ),
                        radius = r
                    )
                    // Main ring
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.08f),
                        radius = r - 4f,
                        style  = Stroke(width = 1.5f)
                    )
                    // Inner ring
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.04f),
                        radius = r * 0.75f,
                        style  = Stroke(width = 1f)
                    )
                    // Cardinal tick marks (every 45°)
                    for (i in 0 until 8) {
                        val angle  = Math.toRadians((i * 45f).toDouble())
                        val isMajor = i % 2 == 0
                        val innerR = r * (if (isMajor) 0.82f else 0.88f)
                        val outerR = r * 0.94f
                        drawLine(
                            color       = Color.White.copy(alpha = if (isMajor) 0.5f else 0.25f),
                            start       = Offset(cx + (innerR * sin(angle)).toFloat(), cy - (innerR * cos(angle)).toFloat()),
                            end         = Offset(cx + (outerR * sin(angle)).toFloat(),  cy - (outerR * cos(angle)).toFloat()),
                            strokeWidth = if (isMajor) 2f else 1f
                        )
                    }
                }

                // Qibla needle — always points toward Mecca
                Canvas(modifier = Modifier.fillMaxSize().rotate(needleRotation)) {
                    val cx = size.width  / 2f
                    val cy = size.height / 2f
                    val needlePath = Path().apply {
                        moveTo(cx, cy - size.minDimension * 0.38f)
                        lineTo(cx - 12, cy + 14)
                        lineTo(cx, cy + 24)
                        lineTo(cx + 12, cy + 14)
                        close()
                    }
                    drawPath(
                        path  = needlePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (isFacingQibla) WarmAmber else LavenderGlow,
                                if (isFacingQibla) WarmAmber.copy(alpha = 0.4f) else SoftPurple.copy(alpha = 0.4f)
                            )
                        )
                    )
                    drawCircle(color = Color.White, radius = 8f, center = Offset(cx, cy))
                    drawCircle(
                        color  = if (isFacingQibla) WarmAmber else SoftPurple,
                        radius = 4f, center = Offset(cx, cy)
                    )
                }

                // ── Kaaba marker at top (static) — Mosque icon ────────────
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
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector    = Icons.Filled.Mosque,
                        contentDescription = "Kaaba / Qibla direction",
                        tint           = if (isFacingQibla) WarmAmber else Color.White.copy(alpha = 0.80f),
                        modifier       = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text  = if (isFacingQibla) "✓ Facing Qibla" else "${qiblaDirection.roundToInt()}° from North",
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

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    "21.4225° N, 39.8262° E",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun getCity(context: Context, lat: Double, lon: Double): String? {
    return try {
        val geocoder  = android.location.Geocoder(context, java.util.Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        val city = addresses?.firstOrNull()?.let { addr ->
            addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName
        }
        city
    } catch (_: Exception) { null }
}

private fun calculateQiblaDirection(
    fromLat: Double, fromLon: Double,
    toLat: Double,   toLon: Double
): Float {
    val dLon    = Math.toRadians(toLon - fromLon)
    val lat1    = Math.toRadians(fromLat)
    val lat2    = Math.toRadians(toLat)
    val y       = sin(dLon) * cos(lat2)
    val x       = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val bearing = Math.toDegrees(atan2(y, x))
    return ((bearing + 360.0) % 360.0).toFloat()
}

private const val KAABA_LAT = 21.4225
private const val KAABA_LON = 39.8262
'''

# ─────────────────────────────────────────────────────────────────────────────
# 3. HomeViewModel.kt  — wire prayerLockEnabled master toggle into scheduling
# ─────────────────────────────────────────────────────────────────────────────
# We only need to patch one function — read existing file and replace that block
home_vm_path = os.path.join(BASE, "ui/screens/home/HomeViewModel.kt")
with open(home_vm_path, "r", encoding="utf-8") as f:
    home_vm = f.read()

OLD_SCHEDULE = '''    private suspend fun scheduleAlarmsForPrayerTimes(prayerTimes: List<PrayerTime>) {
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
    }'''

NEW_SCHEDULE = '''    private suspend fun scheduleAlarmsForPrayerTimes(prayerTimes: List<PrayerTime>) {
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
        // prayerLockEnabled is the master toggle — if it\'s off, no prayer triggers a lock
        val lockEnabled = Prayer.entries.map { prayer ->
            if (!settings.prayerLockEnabled) false
            else when (prayer) {
                Prayer.FAJR -> settings.fajrLockEnabled
                Prayer.DHUHR -> settings.dhuhrLockEnabled
                Prayer.ASR -> settings.asrLockEnabled
                Prayer.MAGHRIB -> settings.maghribLockEnabled
                Prayer.ISHA -> settings.ishaLockEnabled
            }
        }.toBooleanArray()
        scheduler.scheduleAllAlarms(prayerTimes, notificationEnabled, lockEnabled, settings.gracePeriodMinutes)
    }'''

if OLD_SCHEDULE in home_vm:
    home_vm = home_vm.replace(OLD_SCHEDULE, NEW_SCHEDULE)
    print("✅ HomeViewModel.kt — prayerLockEnabled master toggle wired")
else:
    print("⚠️  HomeViewModel.kt — could not find scheduling block, skipping patch")

with open(home_vm_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(home_vm)

# ─────────────────────────────────────────────────────────────────────────────
# 4. UserPreferences.kt  — add clearAllData() for sign out
# ─────────────────────────────────────────────────────────────────────────────
prefs_path = os.path.join(BASE, "data/local/datastore/UserPreferences.kt")
with open(prefs_path, "r", encoding="utf-8") as f:
    prefs = f.read()

CLEAR_FN = '''
    /** Wipes all DataStore preferences — used by Sign Out. */
    suspend fun clearAllData() {
        context.dataStore.edit { it.clear() }
    }
'''

# Add before the final closing brace
if "clearAllData" not in prefs:
    prefs = prefs.rstrip()
    # insert before last }
    last_brace = prefs.rfind("}")
    prefs = prefs[:last_brace] + CLEAR_FN + "\n}"
    print("✅ UserPreferences.kt — clearAllData() added")
else:
    print("ℹ️  UserPreferences.kt — clearAllData() already present")

with open(prefs_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(prefs)

# ─────────────────────────────────────────────────────────────────────────────
# 5. SettingsScreen.kt  — wire Sign Out + Export CSV
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/settings/SettingsScreen.kt"] = r'''package com.sujood.app.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.SujoodApplication
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.CalculationMethod
import com.sujood.app.domain.model.LockMode
import com.sujood.app.domain.model.Madhab
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.UserSettings
import com.sujood.app.notifications.PrayerAlarmScheduler
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BgDark      = Color(0xFF101322)
private val PrimaryBlue = Color(0xFF1132D4)
private val GlassFill   = Color(0xFF1132D4).copy(alpha = 0.05f)
private val GlassDiv    = Color(0xFFFFFFFF).copy(alpha = 0.05f)
private val GlassBrd    = Color(0xFFFFFFFF).copy(alpha = 0.10f)
private val SlateMuted  = Color(0xFF64748B)
private val SlateText   = Color(0xFF94A3B8)

private val CITY_LIST = listOf(
    "Dubai, UAE","Abu Dhabi, UAE","Sharjah, UAE","Ajman, UAE","Al Ain, UAE",
    "Riyadh, Saudi Arabia","Jeddah, Saudi Arabia","Mecca, Saudi Arabia","Medina, Saudi Arabia",
    "London, UK","Manchester, UK","Birmingham, UK","Glasgow, UK",
    "New York, USA","Los Angeles, USA","Chicago, USA","Houston, USA",
    "Toronto, Canada","Montreal, Canada","Vancouver, Canada",
    "Cairo, Egypt","Alexandria, Egypt","Istanbul, Turkey","Ankara, Turkey",
    "Kuala Lumpur, Malaysia","Jakarta, Indonesia",
    "Karachi, Pakistan","Lahore, Pakistan","Islamabad, Pakistan",
    "Dhaka, Bangladesh","Mumbai, India","Delhi, India",
    "Paris, France","Berlin, Germany","Amsterdam, Netherlands",
    "Lagos, Nigeria","Nairobi, Kenya","Casablanca, Morocco",
    "Doha, Qatar","Kuwait City, Kuwait","Manama, Bahrain","Muscat, Oman",
    "Amman, Jordan","Beirut, Lebanon","Baghdad, Iraq","Tehran, Iran",
    "Sydney, Australia","Singapore, Singapore","Tokyo, Japan",
    "Cape Town, South Africa","Johannesburg, South Africa"
).sortedBy { it }

private val ADHAN_OPTIONS = listOf(
    "Mishary Al-Afasy" to "https://cdn.islamic.network/quran/audio/128/ar.alafasy/1.mp3",
    "Abdul Basit"       to "https://cdn.islamic.network/quran/audio/128/ar.abdulbasitmurattal/1.mp3",
    "Maher Al Mueaqly"  to "https://cdn.islamic.network/quran/audio/128/ar.mahermuaiqly/1.mp3",
    "Saad Al Ghamdi"    to "https://cdn.islamic.network/quran/audio/128/ar.saoodashurym/1.mp3",
    "Phone Ringtone"    to "ringtone"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    val context  = LocalContext.current
    val settings by userPreferences.userSettings.collectAsState(initial = UserSettings())
    val scope    = rememberCoroutineScope()

    var showNameDialog         by remember { mutableStateOf(false) }
    var showMethodDialog       by remember { mutableStateOf(false) }
    var showMadhabDialog       by remember { mutableStateOf(false) }
    var showCityDialog         by remember { mutableStateOf(false) }
    var showLockTriggerDialog  by remember { mutableStateOf(false) }
    var showLockDurationDialog by remember { mutableStateOf(false) }
    var showAdhanDialog        by remember { mutableStateOf(false) }
    var showSignOutDialog      by remember { mutableStateOf(false) }
    var cacheCleared           by remember { mutableStateOf(false) }
    var exportStatus           by remember { mutableStateOf("") }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch { userPreferences.saveLocationSettings(true, "", "", 0.0, 0.0) }
        }
    }

    // Sign out confirmation dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out", color = Color.White) },
            text  = { Text("This will clear all your preferences and prayer history. Are you sure?",
                          color = SlateText) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            userPreferences.clearAllData()
                            showSignOutDialog = false
                            onSignOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            },
            containerColor = MidnightBlue
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDark)
        .background(Brush.radialGradient(listOf(PrimaryBlue.copy(alpha = 0.15f), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 1000f))) {

        LazyColumn(modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)) {

            item {
                Row(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, GlassBrd, CircleShape).clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp).padding(top = 16.dp).clickable { showNameDialog = true }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.size(72.dp)) {
                            Box(modifier = Modifier.size(72.dp).clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.20f))
                                .border(2.dp, PrimaryBlue.copy(alpha = 0.50f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(settings.name.firstOrNull()?.uppercase() ?: "S",
                                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Box(modifier = Modifier.size(22.dp).align(Alignment.BottomEnd)
                                .clip(CircleShape).background(PrimaryBlue).border(2.dp, BgDark, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(settings.name.ifEmpty { "Tap to set name" },
                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("sujood@app.com", fontSize = 13.sp, color = SlateText)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.20f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)) {
                                Text("PREMIUM MEMBER", fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold, color = PrimaryBlue, letterSpacing = 1.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }

            item { SectionLabel("Location & Prayer") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(icon = Icons.Default.LocationOn, title = "Prayer Location",
                        subtitle = when {
                            settings.savedCity.isNotEmpty() -> settings.savedCity
                            settings.savedLatitude != 0.0  -> "GPS Active"
                            else -> "Not set"
                        },
                        trailing = {
                            if (settings.useGpsLocation || settings.savedLatitude != 0.0)
                                Text("GPS Active", fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
                            else
                                Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp))
                        },
                        onClick = { showCityDialog = true })
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.Calculate, title = "Calculation Method",
                        subtitle = settings.calculationMethod.displayName,
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = { showMethodDialog = true })
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.Mosque, title = "Madhab",
                        subtitle = settings.madhab.displayName + " / Standard",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = { showMadhabDialog = true })
                }
            }

            item { SectionLabel("Notifications") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    val allOn = settings.fajrNotificationEnabled && settings.dhuhrNotificationEnabled &&
                                settings.asrNotificationEnabled && settings.maghribNotificationEnabled &&
                                settings.ishaNotificationEnabled
                    SettingsRow(icon = Icons.Default.NotificationsActive, title = "Daily Prayer Alerts",
                        trailing = {
                            Switch(checked = allOn, onCheckedChange = { enabled ->
                                scope.launch {
                                    Prayer.entries.forEach { userPreferences.saveNotificationEnabled(it.name, enabled) }
                                    rescheduleAlarms(context, userPreferences)
                                }
                            }, colors = blueSwitchColors())
                        })
                    GlassDivider()
                    Row(modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Prayer.FAJR to settings.fajrNotificationEnabled,
                               Prayer.DHUHR to settings.dhuhrNotificationEnabled,
                               Prayer.ASR to settings.asrNotificationEnabled,
                               Prayer.MAGHRIB to settings.maghribNotificationEnabled,
                               Prayer.ISHA to settings.ishaNotificationEnabled
                        ).forEach { (prayer, enabled) ->
                            Column(modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (enabled) PrimaryBlue.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f))
                                .border(1.dp, if (enabled) PrimaryBlue.copy(alpha = 0.40f) else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch {
                                        userPreferences.saveNotificationEnabled(prayer.name, !enabled)
                                        rescheduleAlarms(context, userPreferences)
                                    }
                                }.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(prayer.displayName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Icon(if (enabled) Icons.Default.VolumeUp else Icons.Default.Vibration,
                                    null, tint = if (enabled) PrimaryBlue else SlateText, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.MusicNote, title = "Adhan Sound",
                        subtitle = settings.adhanSoundName.ifEmpty { "Mishary Al-Afasy" },
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = { showAdhanDialog = true })
                    GlassDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(PrimaryBlue.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.VolumeUp, null,
                                        tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                                }
                                Text("Adhan Volume", fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium, color = Color.White)
                            }
                            Text("${(settings.adhanVolume * 100).toInt()}%",
                                fontSize = 12.sp, color = SlateText)
                        }
                        Slider(
                            value = settings.adhanVolume,
                            onValueChange = { v -> scope.launch { userPreferences.saveAdhanVolume(v) } },
                            valueRange = 0f..1f,
                            steps = 9,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = PrimaryBlue,
                                activeTrackColor = PrimaryBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.Vibration, title = "Vibration",
                        trailing = {
                            Switch(checked = settings.vibrationEnabled, onCheckedChange = { enabled ->
                                scope.launch { userPreferences.saveAudioSettings(settings.adhanEnabled, enabled) }
                            }, colors = blueSwitchColors())
                        })
                }
            }

            item { SectionLabel("Audio & Haptics") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(icon = Icons.Default.VolumeUp, title = "Sound Effects",
                        trailing = {
                            Switch(checked = settings.adhanEnabled, onCheckedChange = { enabled ->
                                scope.launch { userPreferences.saveAudioSettings(enabled, settings.vibrationEnabled) }
                            }, colors = blueSwitchColors())
                        })
                }
            }

            item { SectionLabel("App Preferences") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(icon = Icons.Default.Language, title = "Language",
                        trailing = { Text("English (US)", fontSize = 12.sp, color = SlateText) })
                }
            }

            item { SectionLabel("Data Management") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    // ── Export Prayer History as CSV ────────────────────────
                    SettingsRow(
                        icon = Icons.Default.FileUpload,
                        title = "Export Prayer History",
                        subtitle = if (exportStatus.isNotEmpty()) exportStatus else "Share as CSV file",
                        trailing = {
                            Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            scope.launch {
                                try {
                                    val app = context.applicationContext as SujoodApplication
                                    val dao = app.database.prayerLogDao()
                                    // Collect all logs via a one-shot snapshot
                                    val logs = dao.getAllPrayerLogs().first()
                                    if (logs.isEmpty()) {
                                        exportStatus = "No prayer history yet"
                                        return@launch
                                    }
                                    val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                    val sb = StringBuilder("Date,Prayer,Completed At\n")
                                    val sortedLogs = logs.sortedByDescending { it.completedAt }
                                    sortedLogs.forEach { log ->
                                        sb.append("${log.date},${log.prayerName},${dateFmt.format(Date(log.completedAt))}\n")
                                    }
                                    val fileName = "sujood_prayer_history_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.csv"
                                    val file = File(context.cacheDir, fileName)
                                    file.writeText(sb.toString())
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Sujood Prayer History")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Export Prayer History"))
                                    exportStatus = "Exported ${logs.size} records"
                                } catch (e: Exception) {
                                    exportStatus = "Export failed"
                                }
                            }
                        }
                    )
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.DeleteSweep, title = "Clear Cache",
                        trailing = {
                            if (cacheCleared)
                                Text("Cleared!", fontSize = 12.sp, color = Color(0xFF22C55E), fontWeight = FontWeight.SemiBold)
                            else
                                Text("42 MB", fontSize = 12.sp, color = SlateMuted)
                        },
                        onClick = {
                            scope.launch {
                                try {
                                    context.cacheDir.deleteRecursively()
                                    context.externalCacheDir?.deleteRecursively()
                                } catch (_: Exception) {}
                                cacheCleared = true
                            }
                        })
                }
            }

            item { SectionLabel("About") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(icon = Icons.Default.Info, title = "App Version",
                        trailing = { Text("v1.0.0", fontSize = 12.sp, color = SlateText) })
                    GlassDivider()
                    SettingsRow(icon = Icons.Default.SupportAgent, title = "Support & Feedback",
                        trailing = { Icon(Icons.Default.OpenInNew, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:support@sujood.app"))
                            context.startActivity(intent)
                        })
                }
            }

            // ── Sign Out button ────────────────────────────────────────────
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.clip(CircleShape)
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.30f), CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.08f))
                        .padding(horizontal = 32.dp, vertical = 12.dp)
                        .clickable { showSignOutDialog = true }) {
                        Text("Sign Out", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444).copy(alpha = 0.90f))
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    if (showNameDialog) {
        NameDialog(settings.name, { showNameDialog = false }) { name ->
            scope.launch { userPreferences.saveUserName(name) }; showNameDialog = false
        }
    }
    if (showCityDialog) {
        CitySearchDialog(
            currentCity = settings.savedCity,
            onDismiss = { showCityDialog = false },
            onConfirm = { city ->
                scope.launch {
                    userPreferences.saveLocationSettings(false, city, "", 0.0, 0.0)
                    rescheduleAlarms(context, userPreferences)
                }
                showCityDialog = false
            },
            onUseGps = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val pm = context.packageManager
                    val granted = pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, context.packageName) ==
                                  android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else {
                        scope.launch { userPreferences.saveLocationSettings(true, "", "", 0.0, 0.0) }
                    }
                } else {
                    scope.launch { userPreferences.saveLocationSettings(true, "", "", 0.0, 0.0) }
                }
                showCityDialog = false
            }
        )
    }
    if (showMethodDialog) {
        CalculationMethodDialog(settings.calculationMethod, { showMethodDialog = false }) { method ->
            scope.launch { userPreferences.saveCalculationMethod(method) }; showMethodDialog = false
        }
    }
    if (showMadhabDialog) {
        MadhabDialog(settings.madhab, { showMadhabDialog = false }) { madhab ->
            scope.launch { userPreferences.saveMadhab(madhab) }; showMadhabDialog = false
        }
    }
    if (showAdhanDialog) {
        AdhanPickerDialog(
            currentName = settings.adhanSoundName.ifEmpty { "Mishary Al-Afasy" },
            onDismiss = { showAdhanDialog = false },
            onSelect = { name, url ->
                scope.launch { userPreferences.saveAdhanSound(name, url) }
                showAdhanDialog = false
            }
        )
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        color = SlateMuted, modifier = Modifier.padding(horizontal = 26.dp).padding(top = 24.dp, bottom = 8.dp))
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(GlassFill)
        .border(1.dp, GlassBrd, RoundedCornerShape(16.dp))) { content() }
}

@Composable
private fun GlassDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassDiv))
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            .background(PrimaryBlue.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            if (subtitle != null)
                Text(subtitle, fontSize = 12.sp, color = SlateText, modifier = Modifier.padding(top = 1.dp))
        }
        trailing?.invoke()
    }
}

@Composable
private fun blueSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue,
    uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF334155))

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun NameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your Name") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = GlassBorder)) },
        confirmButton = { Button(onClick = { onConfirm(name) }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = MidnightBlue)
}

@Composable
private fun CitySearchDialog(
    currentCity: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onUseGps: () -> Unit
) {
    var query        by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf(currentCity) }
    val focusManager = LocalFocusManager.current
    val filtered = remember(query) {
        if (query.length < 2) emptyList()
        else CITY_LIST.filter { it.contains(query, ignoreCase = true) }.take(6)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Prayer Location") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it; selectedCity = "" },
                    label = { Text("Search city") }, placeholder = { Text("e.g. Dubai") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = GlassBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                if (filtered.isNotEmpty() && selectedCity.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1F35))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))) {
                        filtered.forEachIndexed { idx, city ->
                            Row(modifier = Modifier.fillMaxWidth()
                                .clickable { selectedCity = city; query = city; focusManager.clearFocus() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(city, fontSize = 13.sp, color = Color.White)
                            }
                            if (idx < filtered.lastIndex)
                                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onUseGps, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use GPS — scan my location now", color = PrimaryBlue)
                }
            }
        },
        confirmButton = { Button(onClick = { val c = selectedCity.ifEmpty { query }.trim(); if (c.isNotBlank()) onConfirm(c) },
            enabled = selectedCity.isNotEmpty() || query.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MidnightBlue)
}

@Composable
private fun CalculationMethodDialog(currentMethod: CalculationMethod, onDismiss: () -> Unit, onSelect: (CalculationMethod) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Calculation Method") },
        text = { Column { CalculationMethod.entries.forEach { method -> Row(Modifier.fillMaxWidth().clickable { onSelect(method) }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(method.displayName); if (method == currentMethod) Icon(Icons.Default.Check, null, tint = PrimaryBlue) } } } },
        confirmButton = {}, containerColor = MidnightBlue)
}

@Composable
private fun MadhabDialog(currentMadhab: Madhab, onDismiss: () -> Unit, onSelect: (Madhab) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Select Madhab") },
        text = { Column { Madhab.entries.forEach { madhab -> Row(Modifier.fillMaxWidth().clickable { onSelect(madhab) }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(madhab.displayName); if (madhab == currentMadhab) Icon(Icons.Default.Check, null, tint = PrimaryBlue) } } } },
        confirmButton = {}, containerColor = MidnightBlue)
}

@Composable
private fun AdhanPickerDialog(currentName: String, onDismiss: () -> Unit, onSelect: (String, String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Adhan Sound") },
        text = {
            Column {
                ADHAN_OPTIONS.forEach { (name, url) ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(name, url) }.padding(vertical = 12.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.MusicNote, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Text(name, color = Color.White)
                        }
                        if (name == currentName) Icon(Icons.Default.Check, null, tint = PrimaryBlue)
                    }
                }
            }
        },
        confirmButton = {}, containerColor = MidnightBlue)
}

private suspend fun rescheduleAlarms(context: android.content.Context, userPreferences: UserPreferences) {
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
        val notif = Prayer.entries.map { p -> when (p) { Prayer.FAJR -> settings.fajrNotificationEnabled; Prayer.DHUHR -> settings.dhuhrNotificationEnabled; Prayer.ASR -> settings.asrNotificationEnabled; Prayer.MAGHRIB -> settings.maghribNotificationEnabled; Prayer.ISHA -> settings.ishaNotificationEnabled } }.toBooleanArray()
        val lock  = Prayer.entries.map { p -> when (p) { Prayer.FAJR -> settings.fajrLockEnabled; Prayer.DHUHR -> settings.dhuhrLockEnabled; Prayer.ASR -> settings.asrLockEnabled; Prayer.MAGHRIB -> settings.maghribLockEnabled; Prayer.ISHA -> settings.ishaLockEnabled } }.toBooleanArray()
        scheduler.scheduleAllAlarms(prayerTimes, notif, lock, settings.gracePeriodMinutes)
    }
}
'''

# ── Write manifest ─────────────────────────────────────────────────────────────
with open(MANIFEST, "w", encoding="utf-8", newline="\n") as f:
    f.write(manifest_content)
print("✅ AndroidManifest.xml — <queries> block added for app icon access")

# ── Write Kotlin files ─────────────────────────────────────────────────────────
for rel_path, content in files.items():
    abs_path = os.path.join(BASE, rel_path)
    os.makedirs(os.path.dirname(abs_path), exist_ok=True)
    with open(abs_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"✅ Written: {rel_path}")

# ── FileProvider: add to manifest content and create file_paths.xml ───────────
# The manifest written above already has the <application> block.
# We need to inject the FileProvider <provider> tag inside <application>.
# Re-read the manifest we just wrote and patch it.
with open(MANIFEST, "r", encoding="utf-8") as f:
    mf = f.read()

PROVIDER_XML = '''
        <!-- FileProvider for sharing exported CSV files -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.provider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

    </application>'''

if "FileProvider" not in mf:
    mf = mf.replace("    </application>", PROVIDER_XML)
    with open(MANIFEST, "w", encoding="utf-8", newline="\n") as f:
        f.write(mf)
    print("✅ AndroidManifest.xml — FileProvider added")

# file_paths.xml resource
res_xml_dir = os.path.join(ROOT, "Sujood", "app", "src", "main", "res", "xml")
os.makedirs(res_xml_dir, exist_ok=True)
file_paths_xml = os.path.join(res_xml_dir, "file_paths.xml")
with open(file_paths_xml, "w", encoding="utf-8", newline="\n") as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<paths>
    <!-- Cache dir — used for exported CSV files -->
    <cache-path name="shared_cache" path="." />
</paths>
''')
print("✅ res/xml/file_paths.xml — created for FileProvider")

print("\n✅ fix_v3.py complete.")
print("Run: cd Sujood && ./gradlew assembleDebug")
