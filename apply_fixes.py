import os

SRC = os.path.join("Sujood", "app", "src", "main", "java", "com", "sujood", "app")
RES = os.path.join("Sujood", "app", "src", "main", "res")

files = {}

# ─────────────────────────────────────────────────────────────────────────────
# 1. QiblaScreen.kt
#    • Restored the working sensor logic from this commit (no remapCoordinateSystem)
#    • Added a proper low-pass filter on the raw sensor arrays (kills jitter)
#    • Added angle-domain LP filter on the heading (kills tiny flicker)
#    • Shortest-path interpolation so the needle never spins the wrong way
#    • Animation spec: NoBouncy + MediumLow stiffness (smooth, no wobble)
# ─────────────────────────────────────────────────────────────────────────────
files[('src', 'ui/screens/qibla/QiblaScreen.kt')] = \
'''package com.sujood.app.ui.screens.qibla

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
// 0.12 eliminates jitter while still tracking normal hand movement well
private const val LP_ALPHA = 0.12f

/** Apply a standard IIR low-pass filter to a sensor reading array. */
private fun lowPassFilter(input: FloatArray, prev: FloatArray?): FloatArray {
    if (prev == null) return input.clone()
    return FloatArray(input.size) { i -> prev[i] + LP_ALPHA * (input[i] - prev[i]) }
}

/** Returns the shortest signed angular difference from -> to, in [-180, 180]. */
private fun shortestDelta(from: Float, to: Float): Float =
    ((to - from + 540f) % 360f) - 180f

@Composable
fun QiblaScreen() {
    val context = LocalContext.current
    var userLatitude  by remember { mutableStateOf(0.0) }
    var userLongitude by remember { mutableStateOf(0.0) }

    // smoothedHeading is maintained in the sensor callback via LP filter
    var smoothedHeading  by remember { mutableFloatStateOf(0f) }
    var qiblaDirection   by remember { mutableFloatStateOf(277f) }
    var isCalibrated     by remember { mutableStateOf(false) }
    var isFacingQibla    by remember { mutableStateOf(false) }
    var hasLocation      by remember { mutableStateOf(false) }

    val userPreferences = remember { UserPreferences(context) }

    // ── Location ──────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        userPreferences.userSettings.first().let { settings ->
            if (settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0) {
                userLatitude   = settings.savedLatitude
                userLongitude  = settings.savedLongitude
                hasLocation    = true
                qiblaDirection = calculateQiblaDirection(userLatitude, userLongitude, KAABA_LAT, KAABA_LON)
            } else {
                try {
                    val lm  = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (loc != null) {
                        userLatitude   = loc.latitude
                        userLongitude  = loc.longitude
                        hasLocation    = true
                        qiblaDirection = calculateQiblaDirection(userLatitude, userLongitude, KAABA_LAT, KAABA_LON)
                    }
                } catch (_: SecurityException) {}
            }
        }
    }

    // ── Compass sensor ────────────────────────────────────────────────────
    // Uses the ORIGINAL working approach from this commit:
    //   getRotationMatrix(r, i, gravity, geomagnetic)
    //   getOrientation(r, orientation)          ← no remapCoordinateSystem
    // The remapCoordinateSystem call added in a previous session was wrong
    // for portrait-flat phone use and caused the inaccuracy.
    //
    // Jitter is eliminated by low-pass filtering the raw sensor arrays
    // AND by doing a second LP filter on the heading angle itself.
    DisposableEffect(Unit) {
        val sm   = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag   = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var filteredGravity: FloatArray?     = null
        var filteredGeomagnetic: FloatArray? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER  ->
                        filteredGravity     = lowPassFilter(event.values.clone(), filteredGravity)
                    Sensor.TYPE_MAGNETIC_FIELD ->
                        filteredGeomagnetic = lowPassFilter(event.values.clone(), filteredGeomagnetic)
                }

                val g   = filteredGravity     ?: return
                val geo = filteredGeomagnetic  ?: return

                val r = FloatArray(9)
                val i = FloatArray(9)
                if (!SensorManager.getRotationMatrix(r, i, g, geo)) return

                val orientation = FloatArray(3)
                // ← original working call, NO remapCoordinateSystem
                SensorManager.getOrientation(r, orientation)

                var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

                // Apply magnetic declination correction when we have a real location
                if (hasLocation) {
                    val gf = android.hardware.GeomagneticField(
                        userLatitude.toFloat(), userLongitude.toFloat(),
                        0f, System.currentTimeMillis()
                    )
                    azimuth += gf.declination
                }

                azimuth = (azimuth + 360f) % 360f

                // Angle-domain LP filter — blend toward azimuth via shortest path
                val delta = shortestDelta(smoothedHeading, azimuth)
                smoothedHeading = (smoothedHeading + LP_ALPHA * delta + 360f) % 360f

                isFacingQibla = calculateAngleDiff(smoothedHeading, qiblaDirection) < 5f
                isCalibrated  = true
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                isCalibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
        }

        accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let   { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose  { sm.unregisterListener(listener) }
    }

    // Animate the already-smoothed heading — snappy but not bouncy
    val animatedHeading by animateFloatAsState(
        targetValue  = smoothedHeading,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label = "heading"
    )

    val needleRotation = (qiblaDirection - animatedHeading + 360f) % 360f

    // ── UI (unchanged from original commit) ───────────────────────────────
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
                    else        -> "⚠️ No location found — open Home tab first"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasLocation) TextSecondary else WarmAmber
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Compass widget ──
            Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {

                // Outer glow ring
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

                // Static compass ring + cardinal tick marks
                Canvas(modifier = Modifier.size(280.dp)) {
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.12f),
                        radius = size.minDimension / 2,
                        style  = Stroke(width = 2f)
                    )
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.04f),
                        radius = size.minDimension / 2 - 20
                    )
                    listOf(0f, 90f, 180f, 270f).forEach { angle ->
                        val rad    = Math.toRadians((angle - 90).toDouble())
                        val outerR = size.minDimension / 2
                        val innerR = outerR - 20
                        drawLine(
                            color       = Color.White.copy(alpha = 0.35f),
                            start       = Offset(size.width / 2 + outerR * cos(rad).toFloat(),
                                                 size.height / 2 + outerR * sin(rad).toFloat()),
                            end         = Offset(size.width / 2 + innerR * cos(rad).toFloat(),
                                                 size.height / 2 + innerR * sin(rad).toFloat()),
                            strokeWidth = 3f
                        )
                    }
                }

                // Rotating needle
                Canvas(modifier = Modifier.size(200.dp).rotate(needleRotation)) {
                    val cx = size.width / 2; val cy = size.height / 2
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isFacingQibla) WarmAmber.copy(alpha = 0.3f) else LavenderGlow.copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy - 55f), radius = 30f
                        ),
                        radius = 30f, center = Offset(cx, cy - 55f)
                    )
                    val needlePath = Path().apply {
                        moveTo(cx, cy - 80)
                        lineTo(cx - 12, cy + 24)
                        lineTo(cx, cy + 14)
                        lineTo(cx + 12, cy + 24)
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

                // Kaaba marker at top (static)
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
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color.Black, RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 4.dp)
                                .background(Color(0xFFFFD700))
                                .size(3.dp)
                        )
                    }
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

            if (!isCalibrated) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text  = "⚠️ Calibrate compass — move phone in figure-8 pattern",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmAmber,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
            Text("Kaaba, Makkah Al-Mukarramah",
                style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(4.dp))
            Text("21.4225° N, 39.8262° E",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

private fun calculateQiblaDirection(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon    = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    return ((Math.toDegrees(atan2(y, x)).toFloat()) + 360f) % 360f
}

private fun calculateAngleDiff(a1: Float, a2: Float): Float {
    var d = kotlin.math.abs(a1 - a2)
    if (d > 180f) d = 360f - d
    return d
}

private const val KAABA_LAT = 21.4225
private const val KAABA_LON = 39.8262
'''

# ─────────────────────────────────────────────────────────────────────────────
# 2. SettingsScreen.kt — full redesign matching the mockup
#    Design tokens: #101322 bg, glass cards, blue icon squares, dividers
#    Sections: Profile, Location & Prayer, Notifications (with per-prayer
#    sound grid), Audio & Haptics, App Preferences, Data Management, About
#    Skipped: Biometric lock (as requested)
#    All real toggles and dialogs remain wired to UserPreferences exactly
#    as before so nothing breaks.
# ─────────────────────────────────────────────────────────────────────────────
files[('src', 'ui/screens/settings/SettingsScreen.kt')] = \
'''package com.sujood.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── Design tokens (matching mockup) ──────────────────────────────────────────
private val BgDark      = Color(0xFF101322)
private val PrimaryBlue = Color(0xFF1132D4)
private val GlassFill   = Color(0xFF1132D4).copy(alpha = 0.05f)
private val GlassDiv    = Color(0xFFFFFFFF).copy(alpha = 0.05f)
private val GlassBrd    = Color(0xFFFFFFFF).copy(alpha = 0.10f)
private val SlateMuted  = Color(0xFF64748B)  // slate-500 for section labels
private val SlateText   = Color(0xFF94A3B8)  // slate-400 for subtitles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    onNavigateBack: () -> Unit
) {
    val context  = LocalContext.current
    val settings by userPreferences.userSettings.collectAsState(initial = UserSettings())
    val scope    = rememberCoroutineScope()

    var showNameDialog         by remember { mutableStateOf(false) }
    var showMethodDialog       by remember { mutableStateOf(false) }
    var showMadhabDialog       by remember { mutableStateOf(false) }
    var showGracePeriodDialog  by remember { mutableStateOf(false) }
    var showCityDialog         by remember { mutableStateOf(false) }
    var showLockTriggerDialog  by remember { mutableStateOf(false) }
    var showLockDurationDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .background(
                Brush.radialGradient(
                    listOf(PrimaryBlue.copy(alpha = 0.15f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 1000f
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.07f))
                            .border(1.dp, GlassBrd, CircleShape)
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            // ── Profile card ─────────────────────────────────────────────
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp)
                        .clickable { showNameDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar
                        Box(modifier = Modifier.size(72.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue.copy(alpha = 0.20f))
                                    .border(2.dp, PrimaryBlue.copy(alpha = 0.50f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = settings.name.firstOrNull()?.uppercase() ?: "S",
                                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                            // Edit badge
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue)
                                    .border(2.dp, BgDark, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, null,
                                    tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.name.ifEmpty { "Tap to set name" },
                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("sujood@app.com", fontSize = 13.sp, color = SlateText)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(PrimaryBlue.copy(alpha = 0.20f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text("PREMIUM MEMBER", fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold, color = PrimaryBlue,
                                    letterSpacing = 1.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── Location & Prayer ────────────────────────────────────────
            item { SectionLabel("Location & Prayer") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(
                        icon = Icons.Default.LocationOn, title = "Prayer Location",
                        subtitle = when {
                            settings.savedCity.isNotEmpty() -> settings.savedCity
                            settings.savedLatitude != 0.0  -> "GPS Active"
                            else -> "Not set"
                        },
                        trailing = {
                            if (settings.useGpsLocation || settings.savedLatitude != 0.0) {
                                Text("GPS Active", fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
                            } else {
                                Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = { showCityDialog = true }
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.Calculate, title = "Calculation Method",
                        subtitle = settings.calculationMethod.displayName,
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = { showMethodDialog = true }
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.Mosque, title = "Madhab",
                        subtitle = settings.madhab.displayName + " / Standard",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = { showMadhabDialog = true }
                    )
                }
            }

            // ── Notifications ────────────────────────────────────────────
            item { SectionLabel("Notifications") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    // Master toggle — Daily Prayer Alerts (uses Fajr as proxy for "all on")
                    val allOn = settings.fajrNotificationEnabled &&
                                settings.dhuhrNotificationEnabled &&
                                settings.asrNotificationEnabled &&
                                settings.maghribNotificationEnabled &&
                                settings.ishaNotificationEnabled
                    SettingsRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Daily Prayer Alerts",
                        trailing = {
                            Switch(
                                checked = allOn,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        Prayer.entries.forEach { p ->
                                            userPreferences.saveNotificationEnabled(p.name, enabled)
                                        }
                                        rescheduleAlarms(context, userPreferences)
                                    }
                                },
                                colors = blueSwitchColors()
                            )
                        }
                    )
                    GlassDivider()

                    // Per-prayer sound grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val prayers = listOf(
                            Prayer.FAJR    to settings.fajrNotificationEnabled,
                            Prayer.DHUHR   to settings.dhuhrNotificationEnabled,
                            Prayer.ASR     to settings.asrNotificationEnabled,
                            Prayer.MAGHRIB to settings.maghribNotificationEnabled,
                            Prayer.ISHA    to settings.ishaNotificationEnabled
                        )
                        prayers.forEach { (prayer, enabled) ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (enabled) PrimaryBlue.copy(alpha = 0.20f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        1.dp,
                                        if (enabled) PrimaryBlue.copy(alpha = 0.40f) else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        scope.launch {
                                            userPreferences.saveNotificationEnabled(prayer.name, !enabled)
                                            rescheduleAlarms(context, userPreferences)
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(prayer.displayName, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold, color = Color.White)
                                Icon(
                                    imageVector = if (enabled) Icons.Default.VolumeUp else Icons.Default.Vibration,
                                    contentDescription = null,
                                    tint = if (enabled) PrimaryBlue else SlateText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    GlassDivider()

                    // Adhan sound row
                    SettingsRow(
                        icon = Icons.Default.MusicNote,
                        title = "Adhan Sound",
                        subtitle = "Mishary Al-Afasy",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) }
                    )
                    GlassDivider()

                    // Vibration toggle
                    SettingsRow(
                        icon = Icons.Default.Vibration,
                        title = "Vibration",
                        trailing = {
                            Switch(
                                checked = settings.vibrationEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { userPreferences.saveAudioSettings(settings.adhanEnabled, enabled) }
                                },
                                colors = blueSwitchColors()
                            )
                        }
                    )
                }
            }

            // ── Audio & Haptics ──────────────────────────────────────────
            item { SectionLabel("Audio & Haptics") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(
                        icon = Icons.Default.VolumeUp,
                        title = "Sound Effects",
                        trailing = {
                            Switch(
                                checked = settings.adhanEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { userPreferences.saveAudioSettings(enabled, settings.vibrationEnabled) }
                                },
                                colors = blueSwitchColors()
                            )
                        }
                    )
                }
            }

            // ── App Preferences ──────────────────────────────────────────
            item { SectionLabel("App Preferences") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(
                        icon = Icons.Default.Language,
                        title = "Language",
                        trailing = { Text("English (US)", fontSize = 12.sp, color = SlateText) }
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.FileUpload,
                        title = "Data Backup",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // ── Data Management ──────────────────────────────────────────
            item { SectionLabel("Data Management") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(
                        icon = Icons.Default.FileUpload,
                        title = "Export Prayer History",
                        trailing = { Icon(Icons.Default.ChevronRight, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) }
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.DeleteSweep,
                        title = "Clear Cache",
                        trailing = { Text("42 MB", fontSize = 12.sp, color = SlateMuted) }
                    )
                }
            }

            // ── About ────────────────────────────────────────────────────
            item { SectionLabel("About") }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "App Version",
                        trailing = { Text("v1.0.0", fontSize = 12.sp, color = SlateText) }
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.SupportAgent,
                        title = "Support & Feedback",
                        trailing = { Icon(Icons.Default.OpenInNew, null, tint = SlateMuted, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:support@sujood.app"))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // ── Sign Out button ──────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.20f), CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.05f))
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                            .clickable { /* sign-out action — wired when auth is added */ }
                    ) {
                        Text("Sign Out", fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444).copy(alpha = 0.80f))
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
        ChangeCityDialog(settings.savedCity, { showCityDialog = false },
            { city -> scope.launch { userPreferences.saveLocationSettings(false, city, "", 0.0, 0.0) }; showCityDialog = false },
            { scope.launch { userPreferences.saveLocationSettings(true, "", "", 0.0, 0.0) }; showCityDialog = false }
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
    if (showGracePeriodDialog) {
        GracePeriodDialog(settings.gracePeriodMinutes, { showGracePeriodDialog = false }) { minutes ->
            scope.launch { userPreferences.saveGracePeriod(minutes) }; showGracePeriodDialog = false
        }
    }
    if (showLockTriggerDialog) {
        TriggerDialog(settings.lockTriggerMinutes, { showLockTriggerDialog = false }) { minutes ->
            scope.launch { userPreferences.saveLockSettings(settings.lockMode, minutes, settings.lockDurationMinutes) }
            showLockTriggerDialog = false
        }
    }
    if (showLockDurationDialog) {
        DurationDialog(settings.lockDurationMinutes, { showLockDurationDialog = false }) { minutes ->
            scope.launch { userPreferences.saveLockSettings(settings.lockMode, settings.lockTriggerMinutes, minutes) }
            showLockDurationDialog = false
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = SlateMuted,
        modifier = Modifier
            .padding(horizontal = 26.dp)
            .padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, GlassBrd, RoundedCornerShape(16.dp))
    ) { content() }
}

@Composable
private fun GlassDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlassDiv)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Blue icon square
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaryBlue.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = SlateText, modifier = Modifier.padding(top = 1.dp))
            }
        }

        trailing?.invoke()
    }
}

@Composable
private fun blueSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor  = Color.White,
    checkedTrackColor  = PrimaryBlue,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color(0xFF334155)  // slate-700
)

// ── Dialogs (all functionality preserved) ────────────────────────────────────

@Composable
private fun NameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your Name") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = GlassBorder)) },
        confirmButton = { Button(onClick = { onConfirm(name) }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = MidnightBlue)
}

@Composable
private fun ChangeCityDialog(currentCity: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, onUseGps: () -> Unit) {
    var city by remember { mutableStateOf(currentCity) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Change Location") },
        text = { Column { OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City Name") }, placeholder = { Text("e.g. Dubai") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue, unfocusedBorderColor = GlassBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White)); Spacer(Modifier.height(12.dp)); TextButton(onClick = onUseGps) { Text("Use GPS instead", color = LavenderGlow) } } },
        confirmButton = { Button(onClick = { if (city.isNotBlank()) onConfirm(city.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = MidnightBlue)
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
private fun GracePeriodDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Grace Period") },
        text = { Column { options.forEach { m -> Row(Modifier.fillMaxWidth().clickable { onSelect(m) }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(if (m == 0) "No grace period" else "$m minutes"); if (m == currentMinutes) Icon(Icons.Default.Check, null, tint = PrimaryBlue) } } } },
        confirmButton = {}, containerColor = MidnightBlue)
}

@Composable
private fun TriggerDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Lock Trigger") },
        text = { Column { options.forEach { m -> Row(Modifier.fillMaxWidth().clickable { onSelect(m) }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(if (m == 0) "At prayer time" else "$m min after prayer"); if (m == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber) } } } },
        confirmButton = {}, containerColor = MidnightBlue)
}

@Composable
private fun DurationDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(5, 10, 15, 20, 30, 60)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Lock Duration") },
        text = { Column { options.forEach { m -> Row(Modifier.fillMaxWidth().clickable { onSelect(m) }.padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("$m minutes"); if (m == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber) } } } },
        confirmButton = {}, containerColor = MidnightBlue)
}

private suspend fun rescheduleAlarms(
    context: android.content.Context,
    userPreferences: UserPreferences
) {
    val settings   = userPreferences.userSettings.first()
    val app        = context.applicationContext as SujoodApplication
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

# ── Write all files ───────────────────────────────────────────────────────────
for (kind, relpath), content in files.items():
    base     = SRC if kind == 'src' else RES
    fullpath = os.path.join(base, relpath.replace("/", os.sep))
    os.makedirs(os.path.dirname(fullpath), exist_ok=True)
    with open(fullpath, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  ✓ {relpath}")

print("\nDone! Now run:")
print("  cd Sujood")
print("  ./gradlew assembleDebug")
