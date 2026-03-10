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

    var qiblaDirection by remember { mutableFloatStateOf(0f) }
    var isCalibrated   by remember { mutableStateOf(false) }
    var statusMessage  by remember { mutableStateOf("Initialising sensors...") }

    LaunchedEffect(Unit) {
        val settings = userPreferences.userSettings.first()
        when {
            settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 -> {
                qiblaDirection = calculateQiblaDirection(
                    settings.savedLatitude, settings.savedLongitude, KAABA_LAT, KAABA_LON)
                statusMessage = "Location loaded from settings"
            }
            settings.savedCity.isNotEmpty() -> {
                try {
                    val lm  = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (loc != null) {
                        qiblaDirection = calculateQiblaDirection(
                            loc.latitude, loc.longitude, KAABA_LAT, KAABA_LON)
                        statusMessage = "Location from GPS"
                    } else statusMessage = "Open Home tab to load your location"
                } catch (_: Exception) { statusMessage = "Open Home tab to load your location" }
            }
            else -> statusMessage = "No location found - open Home tab first"
        }
    }

    var smoothedRaw     by remember { mutableFloatStateOf(Float.NaN) }
    var accumulatedHead by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sm    = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag   = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        var prevAccel: FloatArray? = null; var prevMag: FloatArray? = null
        var accelVals: FloatArray? = null; var magVals: FloatArray? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> { prevAccel = lowPassFilter(event.values.clone(), prevAccel); accelVals = prevAccel }
                    Sensor.TYPE_MAGNETIC_FIELD -> { prevMag = lowPassFilter(event.values.clone(), prevMag); magVals = prevMag }
                }
                val a = accelVals ?: return; val m = magVals ?: return
                val R = FloatArray(9); val I = FloatArray(9)
                if (!SensorManager.getRotationMatrix(R, I, a, m)) return
                val orient = FloatArray(3); SensorManager.getOrientation(R, orient)
                val rawDeg = Math.toDegrees(orient[0].toDouble()).toFloat()
                val rawNorm = (rawDeg + 360f) % 360f
                if (smoothedRaw.isNaN()) {
                    smoothedRaw = rawNorm; accumulatedHead = rawNorm
                } else {
                    val delta = shortestDelta(smoothedRaw, rawNorm)
                    smoothedRaw = ((smoothedRaw + LP_ALPHA * delta) + 360f) % 360f
                    accumulatedHead += LP_ALPHA * delta
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, acc: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD)
                    isCalibrated = acc >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
        }
        accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let   { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose  { sm.unregisterListener(listener) }
    }

    val animatedHead by animateFloatAsState(
        targetValue = accumulatedHead,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
        label = "head"
    )

    val cur = smoothedRaw.takeIf { !it.isNaN() } ?: 0f
    val needleTarget = accumulatedHead + shortestDelta(cur, ((qiblaDirection - cur + 360f) % 360f))
    val animatedNeedle by animateFloatAsState(
        targetValue = needleTarget,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
        label = "needle"
    )

    val isFacingQibla = !smoothedRaw.isNaN() && kotlin.math.abs(shortestDelta(cur, qiblaDirection)) < 5f
    val compassScale by animateFloatAsState(
        targetValue = if (isFacingQibla) 1.04f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "scale"
    )

    AnimatedGradientBackground {
        Column(Modifier.fillMaxSize().padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top) {

            Text("Qibla Compass", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (!isCalibrated) "Move your phone in a figure-8 to calibrate" else statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (!isCalibrated) Color(0xFFFBBF24) else TextSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(48.dp))

            Box(Modifier.size(280.dp).scale(compassScale), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().rotate(-animatedHead)) {
                    val cx = size.width / 2f; val cy = size.height / 2f; val r = size.minDimension / 2f
                    drawCircle(Brush.radialGradient(listOf(LavenderGlow.copy(alpha = 0.15f), Color.Transparent), Offset(cx, cy), r), r)
                    drawCircle(Color.White.copy(alpha = 0.08f), r - 4f, style = Stroke(1.5f))
                    drawCircle(Color.White.copy(alpha = 0.04f), r * 0.75f, style = Stroke(1f))
                    for (i in 0 until 8) {
                        val ang = Math.toRadians(i * 45.0); val maj = i % 2 == 0
                        val ri = r * if (maj) 0.82f else 0.88f; val ro = r * 0.94f
                        drawLine(Color.White.copy(alpha = if (maj) 0.5f else 0.25f),
                            Offset(cx + (ri * sin(ang)).toFloat(), cy - (ri * cos(ang)).toFloat()),
                            Offset(cx + (ro * sin(ang)).toFloat(), cy - (ro * cos(ang)).toFloat()),
                            if (maj) 2f else 1f)
                    }
                }
                Canvas(Modifier.fillMaxSize().rotate(animatedNeedle)) {
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val p = Path().apply {
                        moveTo(cx, cy - size.minDimension * 0.38f)
                        lineTo(cx - 12, cy + 14); lineTo(cx, cy + 24); lineTo(cx + 12, cy + 14); close()
                    }
                    drawPath(p, Brush.verticalGradient(listOf(
                        if (isFacingQibla) WarmAmber else LavenderGlow,
                        if (isFacingQibla) WarmAmber.copy(alpha = 0.4f) else SoftPurple.copy(alpha = 0.4f)
                    )))
                    drawCircle(Color.White, 8f, Offset(cx, cy))
                    drawCircle(if (isFacingQibla) WarmAmber else SoftPurple, 4f, Offset(cx, cy))
                }
                Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp).clip(CircleShape)
                    .background(if (isFacingQibla) WarmAmber.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                    .border(1.dp, if (isFacingQibla) WarmAmber.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mosque, "Qibla",
                        tint = if (isFacingQibla) WarmAmber else Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(if (isFacingQibla) "Facing Qibla" else "${qiblaDirection.roundToInt()} from North",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium,
                color = if (isFacingQibla) WarmAmber else LavenderGlow)
            Spacer(Modifier.height(8.dp))
            Text(
                if (isFacingQibla) "You are facing the Kaaba. May Allah accept your prayer."
                else "Rotate your phone until the arrow points toward the Mosque icon",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("21.4225 N, 39.8262 E", style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f))
            }
        }
    }
}

private fun calculateQiblaDirection(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Float {
    val dLon = Math.toRadians(toLon - fromLon)
    val lat1 = Math.toRadians(fromLat); val lat2 = Math.toRadians(toLat)
    return ((Math.toDegrees(atan2(sin(dLon) * cos(lat2),
        cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))) + 360.0) % 360.0).toFloat()
}

private const val KAABA_LAT = 21.4225
private const val KAABA_LON = 39.8262
