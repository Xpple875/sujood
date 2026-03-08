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
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun QiblaScreen() {
    val context = LocalContext.current
    var userLatitude by remember { mutableStateOf(0.0) }
    var userLongitude by remember { mutableStateOf(0.0) }
    var currentHeading by remember { mutableFloatStateOf(0f) }
    // Default to Mecca direction from UAE as fallback
    var qiblaDirection by remember { mutableFloatStateOf(277f) }
    var isCalibrated by remember { mutableStateOf(false) }
    var isFacingQibla by remember { mutableStateOf(false) }
    var hasLocation by remember { mutableStateOf(false) }

    // Get last known location to compute accurate Qibla direction
    LaunchedEffect(Unit) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            location?.let {
                userLatitude = it.latitude
                userLongitude = it.longitude
                hasLocation = true

                qiblaDirection = calculateQiblaDirection(
                    userLatitude, userLongitude,
                    KAABA_LATITUDE, KAABA_LONGITUDE
                )
            }
        } catch (e: SecurityException) {
            // Keep default direction
        }
    }

    // Compass sensor
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var gravity: FloatArray? = null
        var geomagnetic: FloatArray? = null

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> gravity = event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values.clone()
                }

                val g = gravity
                val geo = geomagnetic
                if (g != null && geo != null) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, g, geo)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(r, orientation)
                        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

                        if (hasLocation) {
                            val geoField = android.hardware.GeomagneticField(
                                userLatitude.toFloat(), userLongitude.toFloat(),
                                0f, System.currentTimeMillis()
                            )
                            azimuth += geoField.declination
                        }

                        azimuth = (azimuth + 360) % 360
                        currentHeading = azimuth

                        val angleDiff = calculateAngleDifference(azimuth, qiblaDirection)
                        isFacingQibla = angleDiff < 5f
                        isCalibrated = true
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                isCalibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
        }

        accelerometer?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }

        onDispose { sensorManager.unregisterListener(sensorListener) }
    }

    val animatedHeading by animateFloatAsState(
        targetValue = currentHeading,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "headingAnimation"
    )

    // Needle angle: how much to rotate the needle so it points at Qibla
    // If heading = 0 (facing North) and qibla = 90° (East), needle should point right = 90°
    val needleRotation = (qiblaDirection - animatedHeading + 360) % 360

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
                text = if (hasLocation) "Based on your current location" else "Using approximate direction",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ── Compass Widget ──
            Box(
                modifier = Modifier.size(300.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring (static — always facing up)
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

                // Static compass ring — cardinal directions stay fixed
                Canvas(modifier = Modifier.size(280.dp)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = size.minDimension / 2,
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = size.minDimension / 2 - 20
                    )
                    // Cardinal tick marks (N, E, S, W) — STATIC
                    listOf(0f, 90f, 180f, 270f).forEach { angle ->
                        val radian = Math.toRadians((angle - 90).toDouble())
                        val outerR = size.minDimension / 2
                        val innerR = outerR - 20
                        drawLine(
                            color = Color.White.copy(alpha = 0.35f),
                            start = Offset(
                                size.width / 2 + outerR * cos(radian).toFloat(),
                                size.height / 2 + outerR * sin(radian).toFloat()
                            ),
                            end = Offset(
                                size.width / 2 + innerR * cos(radian).toFloat(),
                                size.height / 2 + innerR * sin(radian).toFloat()
                            ),
                            strokeWidth = 3f
                        )
                    }
                }

                // ── NEEDLE ONLY ROTATES — label is separate ──
                // Needle canvas — rotated to point at Qibla
                Canvas(
                    modifier = Modifier
                        .size(200.dp)
                        .rotate(needleRotation)
                ) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2

                    // Needle shadow/glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isFacingQibla) WarmAmber.copy(alpha = 0.3f) else LavenderGlow.copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            center = Offset(centerX, centerY - 55f),
                            radius = 30f
                        ),
                        radius = 30f,
                        center = Offset(centerX, centerY - 55f)
                    )

                    val needlePath = Path().apply {
                        moveTo(centerX, centerY - 80)
                        lineTo(centerX - 12, centerY + 24)
                        lineTo(centerX, centerY + 14)
                        lineTo(centerX + 12, centerY + 24)
                        close()
                    }

                    drawPath(
                        path = needlePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (isFacingQibla) WarmAmber else LavenderGlow,
                                if (isFacingQibla) WarmAmber.copy(alpha = 0.4f) else SoftPurple.copy(alpha = 0.4f)
                            )
                        )
                    )

                    // Center dot
                    drawCircle(
                        color = Color.White,
                        radius = 8f,
                        center = Offset(centerX, centerY)
                    )
                    drawCircle(
                        color = if (isFacingQibla) WarmAmber else SoftPurple,
                        radius = 4f,
                        center = Offset(centerX, centerY)
                    )
                }

                // ── KAABA LABEL — STATIC, always at top of compass ──
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
                    Text(
                        text = "N",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isFacingQibla) WarmAmber else Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (isFacingQibla) "✓ Facing Qibla" else "${qiblaDirection.roundToInt()}° from North",
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
                    text = "⚠️ Calibrate compass — move phone in figure-8 pattern",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmAmber,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(text = "Kaaba, Makkah Al-Mukarramah", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "21.4225° N, 39.8262° E", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

private fun calculateQiblaDirection(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    var bearing = Math.toDegrees(atan2(y, x)).toFloat()
    bearing = (bearing + 360) % 360
    return bearing
}

private fun calculateAngleDifference(angle1: Float, angle2: Float): Float {
    var diff = kotlin.math.abs(angle1 - angle2)
    if (diff > 180) diff = 360 - diff
    return diff
}

private const val KAABA_LATITUDE = 21.4225
private const val KAABA_LONGITUDE = 39.8262
