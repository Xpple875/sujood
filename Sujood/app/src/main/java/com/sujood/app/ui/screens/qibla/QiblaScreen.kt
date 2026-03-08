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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.sp
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.flow.first
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val PrimaryBlue    = Color(0xFF1132D4)
private val BackgroundDark = Color(0xFF101322)
private val CardBg         = Color(0xFF0D1020)
private val GlassStroke    = Color(0xFFFFFFFF).copy(alpha = 0.06f)

@Composable
fun QiblaScreen() {
    val context = LocalContext.current
    var userLatitude  by remember { mutableStateOf(0.0) }
    var userLongitude by remember { mutableStateOf(0.0) }
    var currentHeading  by remember { mutableFloatStateOf(0f) }
    var qiblaDirection  by remember { mutableFloatStateOf(277f) }
    var isCalibrated    by remember { mutableStateOf(false) }
    var isFacingQibla   by remember { mutableStateOf(false) }
    var hasLocation     by remember { mutableStateOf(false) }

    val userPreferences = remember { UserPreferences(context) }

    // ── Location lookup ──
    LaunchedEffect(Unit) {
        userPreferences.userSettings.first().let { settings ->
            if (settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0) {
                userLatitude  = settings.savedLatitude
                userLongitude = settings.savedLongitude
                hasLocation   = true
                qiblaDirection = calculateQiblaDirection(userLatitude, userLongitude, KAABA_LATITUDE, KAABA_LONGITUDE)
            } else {
                try {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (loc != null) {
                        userLatitude  = loc.latitude
                        userLongitude = loc.longitude
                        hasLocation   = true
                        qiblaDirection = calculateQiblaDirection(userLatitude, userLongitude, KAABA_LATITUDE, KAABA_LONGITUDE)
                    }
                } catch (e: SecurityException) { }
            }
        }
    }

    // ── Compass sensor (unchanged logic) ──
    DisposableEffect(Unit) {
        val sensorManager  = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer   = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        var gravity: FloatArray?     = null
        var geomagnetic: FloatArray? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER  -> gravity     = event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values.clone()
                }
                val g = gravity; val geo = geomagnetic
                if (g != null && geo != null) {
                    val r = FloatArray(9); val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, g, geo)) {
                        val remapped    = FloatArray(9)
                        SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(remapped, orientation)
                        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        if (hasLocation) {
                            val gf = android.hardware.GeomagneticField(
                                userLatitude.toFloat(), userLongitude.toFloat(), 0f, System.currentTimeMillis()
                            )
                            azimuth += gf.declination
                        }
                        azimuth = (azimuth + 360) % 360
                        currentHeading = azimuth
                        isFacingQibla  = calculateAngleDifference(azimuth, qiblaDirection) < 5f
                        isCalibrated   = true
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                isCalibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
        }

        accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let  { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val animatedHeading by animateFloatAsState(
        targetValue  = currentHeading,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "headingAnimation"
    )
    val needleRotation = (qiblaDirection - animatedHeading + 360) % 360

    // ── UI ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Background blobs matching the design
        Box(modifier = Modifier.fillMaxSize().background(
            brush = Brush.radialGradient(
                listOf(PrimaryBlue.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(0f, 0f), radius = 900f
            )
        ))
        Box(modifier = Modifier.fillMaxSize().background(
            brush = Brush.radialGradient(
                listOf(Color(0xFF312E81).copy(alpha = 0.18f), Color.Transparent),
                center = Offset(Float.MAX_VALUE, Float.MAX_VALUE), radius = 900f
            )
        ))

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, GlassStroke, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }

                Text("Qibla Direction", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Color.White)

                // Re-locate button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, GlassStroke, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MyLocation, "Re-locate",
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // Subtitle
            Text(
                text = when {
                    !hasLocation -> "⚠️ No location — open Home tab first"
                    else         -> "Based on your current location"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (hasLocation) TextSecondary else WarmAmber,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // ── Compass + direction data (takes all remaining vertical space) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Central radial glow behind compass
                Box(
                    modifier = Modifier
                        .size(360.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(PrimaryBlue.copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // ── Compass dial (unchanged from original) ──
                    Box(
                        modifier = Modifier.size(290.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer glow ring
                        Box(
                            modifier = Modifier
                                .size(290.dp)
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

                        // Static compass ring with cardinal tick marks
                        Canvas(modifier = Modifier.size(280.dp)) {
                            // Outermost ring
                            drawCircle(color = Color.White.copy(alpha = 0.07f),
                                radius = size.minDimension / 2, style = Stroke(width = 1.dp.toPx()))
                            // Second ring
                            drawCircle(color = Color.White.copy(alpha = 0.12f),
                                radius = size.minDimension / 2 - 8f, style = Stroke(width = 1.dp.toPx()))
                            // Third ring
                            drawCircle(color = Color.White.copy(alpha = 0.20f),
                                radius = size.minDimension / 2 - 18f, style = Stroke(width = 1.5f))
                            // Inner fill
                            drawCircle(color = Color.White.copy(alpha = 0.03f),
                                radius = size.minDimension / 2 - 20)
                            // Cardinal tick marks
                            listOf(0f, 90f, 180f, 270f).forEach { angle ->
                                val radian  = Math.toRadians((angle - 90).toDouble())
                                val outerR  = size.minDimension / 2
                                val innerR  = outerR - 20
                                drawLine(
                                    color = Color.White.copy(alpha = 0.35f),
                                    start = Offset(size.width/2 + outerR * cos(radian).toFloat(),
                                        size.height/2 + outerR * sin(radian).toFloat()),
                                    end   = Offset(size.width/2 + innerR * cos(radian).toFloat(),
                                        size.height/2 + innerR * sin(radian).toFloat()),
                                    strokeWidth = 3f
                                )
                            }
                        }

                        // ── Rotating needle ──
                        Canvas(modifier = Modifier.size(200.dp).rotate(needleRotation)) {
                            val cx = size.width / 2; val cy = size.height / 2
                            // Glow
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
                            // Needle shape
                            val needlePath = Path().apply {
                                moveTo(cx, cy - 80)
                                lineTo(cx - 12, cy + 24)
                                lineTo(cx, cy + 14)
                                lineTo(cx + 12, cy + 24)
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
                            // Centre dot
                            drawCircle(color = Color.White, radius = 8f, center = Offset(cx, cy))
                            drawCircle(color = if (isFacingQibla) WarmAmber else SoftPurple, radius = 4f, center = Offset(cx, cy))
                        }

                        // ── Kaaba marker at top (static) ──
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFBBF24).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Simplified mosque/kaaba icon using Canvas
                            Canvas(modifier = Modifier.size(22.dp)) {
                                val w = size.width; val h = size.height
                                // Kaaba body (black rectangle)
                                drawRect(color = Color.Black, size = androidx.compose.ui.geometry.Size(w, h * 0.75f),
                                    topLeft = Offset(0f, h * 0.25f))
                                // Gold belt
                                drawRect(color = Color(0xFFFFD700),
                                    size = androidx.compose.ui.geometry.Size(w, h * 0.1f),
                                    topLeft = Offset(0f, h * 0.4f))
                                // Door
                                drawRect(color = Color(0xFFFFD700).copy(alpha = 0.6f),
                                    size = androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.28f),
                                    topLeft = Offset(w * 0.39f, h * 0.47f))
                                // Dome top hint
                                drawArc(color = Color(0xFFFBBF24).copy(alpha = 0.7f),
                                    startAngle = 180f, sweepAngle = 180f, useCenter = true,
                                    size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.3f),
                                    topLeft = Offset(w * 0.3f, 0f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // ── Degree readout ──
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                        Text(
                            text = if (isFacingQibla) "✓" else "${qiblaDirection.roundToInt()}°",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (!isFacingQibla) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "from North",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Facing Qibla", fontSize = 18.sp,
                                fontWeight = FontWeight.Medium, color = WarmAmber,
                                modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isFacingQibla)
                            "May Allah accept your prayer."
                        else
                            "Rotate your phone until the arrow\npoints North to find the Qibla",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    if (!isCalibrated) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "⚠️ Move phone in figure-8 to calibrate",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarmAmber, textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Footer info card ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark.copy(alpha = 0.6f))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Destination pill
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📍", fontSize = 14.sp)
                    }
                    Column {
                        Text(
                            text = "DESTINATION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Kaaba, Makkah Al-Mukarramah",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // Coordinates
                Text(
                    text = "21.4225° N,  39.8262° E",
                    fontSize = 11.sp,
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.5.sp
                )

                // Blue underline accent (matches design)
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(PrimaryBlue)
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private fun calculateQiblaDirection(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon    = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    return ((Math.toDegrees(atan2(y, x)).toFloat()) + 360) % 360
}

private fun calculateAngleDifference(angle1: Float, angle2: Float): Float {
    var diff = kotlin.math.abs(angle1 - angle2)
    if (diff > 180) diff = 360 - diff
    return diff
}

private const val KAABA_LATITUDE  = 21.4225
private const val KAABA_LONGITUDE = 39.8262
