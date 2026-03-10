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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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

// ── How the compass works ─────────────────────────────────────────────────────
// ONE state variable: `heading` — phone bearing 0-360 degrees, smoothed.
//
// Compass rose:  rotates by -heading  →  North stays pointing up on screen.
// Needle:        rotates by (qiblaDirection - heading)  →  points at Qibla
//                relative to whatever direction the screen faces.
//
// No unbounded accumulated floats. No double-subtraction bugs. Simple and correct.
// ─────────────────────────────────────────────────────────────────────────────

private const val LP = 0.15f

/** Shortest signed arc from `from` to `to` in degrees, result in [-180, 180]. */
private fun shortArc(from: Float, to: Float): Float =
    ((to - from + 540f) % 360f) - 180f

@Composable
fun QiblaScreen() {
    val context         = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }

    var qiblaDirection by remember { mutableFloatStateOf(0f) }
    var isCalibrated   by remember { mutableStateOf(false) }
    var statusMessage  by remember { mutableStateOf("Initialising sensors...") }

    LaunchedEffect(Unit) {
        val settings = userPreferences.userSettings.first()
        when {
            settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 -> {
                qiblaDirection = qibla(settings.savedLatitude, settings.savedLongitude)
                statusMessage  = "Location loaded"
            }
            settings.savedCity.isNotEmpty() -> {
                try {
                    val lm  = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (loc != null) {
                        qiblaDirection = qibla(loc.latitude, loc.longitude)
                        statusMessage  = "Location from GPS"
                    } else {
                        statusMessage = "Open Home tab to set your location"
                    }
                } catch (_: Exception) {
                    statusMessage = "Open Home tab to set your location"
                }
            }
            else -> statusMessage = "Open Home tab to set your location"
        }
    }

    // Smoothed phone heading in 0-360 degrees. NaN until first sensor reading.
    var heading by remember { mutableFloatStateOf(Float.NaN) }

    DisposableEffect(Unit) {
        val sm    = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag   = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        var lpA: FloatArray? = null
        var lpM: FloatArray? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> lpA = lpA?.let { p ->
                        FloatArray(3) { i -> p[i] + LP * (event.values[i] - p[i]) }
                    } ?: event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> lpM = lpM?.let { p ->
                        FloatArray(3) { i -> p[i] + LP * (event.values[i] - p[i]) }
                    } ?: event.values.clone()
                }
                val a = lpA ?: return
                val m = lpM ?: return
                val R = FloatArray(9); val I = FloatArray(9)
                if (!SensorManager.getRotationMatrix(R, I, a, m)) return
                val orient = FloatArray(3)
                SensorManager.getOrientation(R, orient)
                val raw = ((Math.toDegrees(orient[0].toDouble()).toFloat()) + 360f) % 360f
                heading = if (heading.isNaN()) raw
                          else ((heading + LP * shortArc(heading, raw)) + 360f) % 360f
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD)
                    isCalibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
        }
        accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let   { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose  { sm.unregisterListener(listener) }
    }

    val h = if (heading.isNaN()) 0f else heading

    // Unwrapped targets — accumulate via shortArc so animation always takes
    // the short path and never spins 350° when crossing the 0/360 boundary.
    var unwrappedRose   by remember { mutableFloatStateOf(-h) }
    var unwrappedNeedle by remember { mutableFloatStateOf((qiblaDirection - h + 360f) % 360f) }

    val targetRose   = -h
    val targetNeedle = (qiblaDirection - h + 360f) % 360f

    // Step each unwrapped value forward by the shortest arc from its current wrapped position
    unwrappedRose   += shortArc((unwrappedRose   % 360f + 360f) % 360f, (targetRose   % 360f + 360f) % 360f)
    unwrappedNeedle += shortArc((unwrappedNeedle % 360f + 360f) % 360f, targetNeedle)

    // Rose counter-rotates to keep North pointing up
    val roseAngle by animateFloatAsState(
        targetValue   = unwrappedRose,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
        label         = "rose"
    )

    // Needle points at Qibla relative to screen-top
    val needleAngle by animateFloatAsState(
        targetValue   = unwrappedNeedle,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
        label         = "needle"
    )

    val isFacingQibla = !heading.isNaN() &&
        kotlin.math.abs(shortArc(h, qiblaDirection)) < 5f

    val compassScale by animateFloatAsState(
        targetValue   = if (isFacingQibla) 1.05f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "scale"
    )

    AnimatedGradientBackground {
        Column(
            modifier             = Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.Top
        ) {
            Text(
                "Qibla Compass",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = if (!isCalibrated) "Move phone in a figure-8 to calibrate"
                            else statusMessage,
                style     = MaterialTheme.typography.bodySmall,
                color     = if (!isCalibrated) Color(0xFFFBBF24) else TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(48.dp))

            Box(Modifier.size(280.dp).scale(compassScale), contentAlignment = Alignment.Center) {

                // ── Compass rose ──────────────────────────────────────────────
                Canvas(Modifier.fillMaxSize().rotate(roseAngle)) {
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val r  = size.minDimension / 2f
                    drawCircle(
                        Brush.radialGradient(
                            listOf(LavenderGlow.copy(alpha = 0.15f), Color.Transparent),
                            Offset(cx, cy), r
                        ), r
                    )
                    drawCircle(Color.White.copy(alpha = 0.08f), r - 4f, style = Stroke(1.5f))
                    drawCircle(Color.White.copy(alpha = 0.04f), r * 0.75f, style = Stroke(1f))
                    for (i in 0 until 8) {
                        val ang   = Math.toRadians(i * 45.0)
                        val major = i % 2 == 0
                        val ri    = r * if (major) 0.82f else 0.88f
                        val ro    = r * 0.94f
                        drawLine(
                            Color.White.copy(alpha = if (major) 0.5f else 0.25f),
                            Offset(cx + (ri * sin(ang)).toFloat(), cy - (ri * cos(ang)).toFloat()),
                            Offset(cx + (ro * sin(ang)).toFloat(), cy - (ro * cos(ang)).toFloat()),
                            if (major) 2f else 1f
                        )
                    }
                }

                // ── Qibla needle ──────────────────────────────────────────────
                Canvas(Modifier.fillMaxSize().rotate(needleAngle)) {
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val tip = Path().apply {
                        moveTo(cx, cy - size.minDimension * 0.38f)
                        lineTo(cx - 12f, cy + 14f)
                        lineTo(cx,       cy + 24f)
                        lineTo(cx + 12f, cy + 14f)
                        close()
                    }
                    drawPath(
                        tip,
                        Brush.verticalGradient(listOf(
                            if (isFacingQibla) WarmAmber else LavenderGlow,
                            if (isFacingQibla) WarmAmber.copy(alpha = 0.4f)
                            else SoftPurple.copy(alpha = 0.4f)
                        ))
                    )
                    drawCircle(Color.White, 8f, Offset(cx, cy))
                    drawCircle(if (isFacingQibla) WarmAmber else SoftPurple, 4f, Offset(cx, cy))
                }

                // ── Mosque icon pinned at screen-top ──────────────────────────
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFacingQibla) WarmAmber.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            1.dp,
                            if (isFacingQibla) WarmAmber.copy(alpha = 0.7f)
                            else Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Mosque, "Qibla",
                        tint     = if (isFacingQibla) WarmAmber else Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                if (isFacingQibla) "Facing Qibla" else "${qiblaDirection.roundToInt()}° from North",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color      = if (isFacingQibla) WarmAmber else LavenderGlow
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isFacingQibla)
                    "You are facing the Kaaba. May Allah accept your prayer."
                else
                    "Rotate your phone until the arrow points toward the Mosque icon",
                style     = MaterialTheme.typography.bodyMedium,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    "21.4225 N, 39.8262 E",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun qibla(fromLat: Double, fromLon: Double): Float {
    val dLon = Math.toRadians(KAABA_LON - fromLon)
    val lat1 = Math.toRadians(fromLat)
    val lat2 = Math.toRadians(KAABA_LAT)
    return ((Math.toDegrees(
        atan2(
            sin(dLon) * cos(lat2),
            cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        )
    ) + 360.0) % 360.0).toFloat()
}

private const val KAABA_LAT = 21.4225
private const val KAABA_LON = 39.8262
