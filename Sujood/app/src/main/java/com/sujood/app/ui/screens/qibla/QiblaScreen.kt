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
            val cityLabel = if (userLatitude != 0.0 && userLongitude != 0.0 && hasLocation)
    remember(userLatitude, userLongitude) {
        userPreferences.let { null } // resolved below
    }.let { getCityLabel(context, userLatitude, userLongitude) }
else "Kaaba, Makkah Al-Mukarramah"
Text(cityLabel,
                style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(4.dp))
            Text("21.4225° N, 39.8262° E",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}


private fun getCityLabel(context: android.content.Context, lat: Double, lon: Double): String {
    return try {
        val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        val city = addresses?.firstOrNull()?.let { addr ->
            addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName
        }
        city ?: "Your Location"
    } catch (e: Exception) {
        "Your Location"
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
