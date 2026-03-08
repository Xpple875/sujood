import os

SRC = os.path.join("Sujood", "app", "src", "main", "java", "com", "sujood", "app")
RES = os.path.join("Sujood", "app", "src", "main", "res")

files = {}

# ── 1. BottomNavBar.kt — glassmorphic pill design matching the HTML/screenshot ──
files[('src', 'ui/components/BottomNavBar.kt')] = \
'''package com.sujood.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujood.app.domain.model.BottomNavItem

private val PrimaryBlue   = Color(0xFF1132D4)
private val NavBackground = Color(0xFF0D1020).copy(alpha = 0.85f)
private val GlassStroke   = Color(0xFFFFFFFF).copy(alpha = 0.10f)
private val TextMuted     = Color(0xFF94A3B8)

@Composable
fun GlassmorphicBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Dhikr,
        BottomNavItem.Qibla,
        BottomNavItem.Insights,
        BottomNavItem.Settings
    )

    // Outer padding layer — sits above the system nav bar
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // The pill container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50.dp))
                .background(NavBackground)
                // Glass border
                .then(
                    Modifier.background(
                        brush = Brush.verticalGradient(
                            listOf(GlassStroke, Color.Transparent)
                        )
                    )
                )
        ) {
            // Hair-line top border to simulate the glass edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(1.dp)
                    .align(Alignment.TopCenter)
                    .background(GlassStroke)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    NavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onNavigate(item.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else TextMuted,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tint"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryBlue else TextMuted,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "labelColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .scale(scale)
    ) {
        // Icon — active gets a filled blue circle, inactive gets nothing
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .then(
                    if (isSelected) {
                        Modifier
                            .clip(CircleShape)
                            .background(PrimaryBlue)
                    } else Modifier
                )
        ) {
            Icon(
                imageVector = getIconForItem(item),
                contentDescription = item.title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Text(
            text = item.title.uppercase(),
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
            letterSpacing = 1.sp
        )
    }
}

private fun getIconForItem(item: BottomNavItem): ImageVector {
    return when (item) {
        BottomNavItem.Home     -> Icons.Default.Home
        BottomNavItem.Dhikr   -> Icons.Default.Lock
        BottomNavItem.Qibla   -> Icons.Default.Explore
        BottomNavItem.Insights -> Icons.Default.BarChart
        BottomNavItem.Settings -> Icons.Default.Settings
    }
}
'''

# ── 2. QiblaScreen.kt — compass low-pass filter to kill jitter ──
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

// Low-pass filter alpha: 0.1 = very smooth (slow to respond), 0.3 = balanced
private const val LP_ALPHA = 0.15f

/** Shortest-path interpolation for angles so we never spin the wrong way around 360 */
private fun shortestAngleDiff(from: Float, to: Float): Float {
    var diff = (to - from + 540f) % 360f - 180f
    return diff
}

@Composable
fun QiblaScreen() {
    val context = LocalContext.current
    var userLatitude  by remember { mutableStateOf(0.0) }
    var userLongitude by remember { mutableStateOf(0.0) }
    // smoothedHeading is the low-pass-filtered compass value we animate
    var smoothedHeading by remember { mutableFloatStateOf(0f) }
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

    // ── Compass sensor with LOW-PASS FILTER to kill jitter ──
    DisposableEffect(Unit) {
        val sensorManager  = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer   = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // Raw sensor arrays with their own low-pass buffers
        var filteredGravity: FloatArray?     = null
        var filteredGeomagnetic: FloatArray? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        filteredGravity = lowPassFilter(event.values.clone(), filteredGravity)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        filteredGeomagnetic = lowPassFilter(event.values.clone(), filteredGeomagnetic)
                    }
                }
                val g = filteredGravity; val geo = filteredGeomagnetic
                if (g != null && geo != null) {
                    val r = FloatArray(9); val iMatrix = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, iMatrix, g, geo)) {
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

                        // Apply a second angle-domain low-pass filter to smoothedHeading
                        val delta = shortestAngleDiff(smoothedHeading, azimuth)
                        smoothedHeading = (smoothedHeading + LP_ALPHA * delta + 360f) % 360f

                        isFacingQibla  = calculateAngleDifference(smoothedHeading, qiblaDirection) < 5f
                        isCalibrated   = true
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                isCalibrated = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
        }

        // SENSOR_DELAY_UI is fine — the LP filter does the smoothing
        accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let  { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // Animate the already-smoothed heading — use a snappy spring so it tracks fast but not twitchy
    val animatedHeading by animateFloatAsState(
        targetValue  = smoothedHeading,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label = "headingAnimation"
    )
    val needleRotation = (qiblaDirection - animatedHeading + 360) % 360

    // ── UI ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
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
                    Box(
                        modifier = Modifier.size(290.dp),
                        contentAlignment = Alignment.Center
                    ) {
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

                        Canvas(modifier = Modifier.size(280.dp)) {
                            drawCircle(color = Color.White.copy(alpha = 0.07f),
                                radius = size.minDimension / 2, style = Stroke(width = 1.dp.toPx()))
                            drawCircle(color = Color.White.copy(alpha = 0.12f),
                                radius = size.minDimension / 2 - 8f, style = Stroke(width = 1.dp.toPx()))
                            drawCircle(color = Color.White.copy(alpha = 0.20f),
                                radius = size.minDimension / 2 - 18f, style = Stroke(width = 1.5f))
                            drawCircle(color = Color.White.copy(alpha = 0.03f),
                                radius = size.minDimension / 2 - 20)
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
                                path = needlePath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        if (isFacingQibla) WarmAmber else LavenderGlow,
                                        if (isFacingQibla) WarmAmber.copy(alpha = 0.4f) else SoftPurple.copy(alpha = 0.4f)
                                    )
                                )
                            )
                            drawCircle(color = Color.White, radius = 8f, center = Offset(cx, cy))
                            drawCircle(color = if (isFacingQibla) WarmAmber else SoftPurple, radius = 4f, center = Offset(cx, cy))
                        }

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
                            Canvas(modifier = Modifier.size(22.dp)) {
                                val w = size.width; val h = size.height
                                drawRect(color = Color.Black, size = androidx.compose.ui.geometry.Size(w, h * 0.75f),
                                    topLeft = Offset(0f, h * 0.25f))
                                drawRect(color = Color(0xFFFFD700),
                                    size = androidx.compose.ui.geometry.Size(w, h * 0.1f),
                                    topLeft = Offset(0f, h * 0.4f))
                                drawRect(color = Color(0xFFFFD700).copy(alpha = 0.6f),
                                    size = androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.28f),
                                    topLeft = Offset(w * 0.39f, h * 0.47f))
                                drawArc(color = Color(0xFFFBBF24).copy(alpha = 0.7f),
                                    startAngle = 180f, sweepAngle = 180f, useCenter = true,
                                    size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.3f),
                                    topLeft = Offset(w * 0.3f, 0f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

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
                            "Rotate your phone until the arrow\\npoints North to find the Qibla",
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

            // ── Footer ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark.copy(alpha = 0.6f))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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

                Text(
                    text = "21.4225° N,  39.8262° E",
                    fontSize = 11.sp,
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.5.sp
                )

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

/** Standard IIR low-pass filter for sensor arrays */
private fun lowPassFilter(input: FloatArray, output: FloatArray?): FloatArray {
    if (output == null) return input
    return FloatArray(input.size) { i ->
        output[i] + LP_ALPHA * (input[i] - output[i])
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
'''

# ── 3. SettingsScreen.kt — included verbatim to protect fragile imports ──
files[('src', 'ui/screens/settings/SettingsScreen.kt')] = \
'''package com.sujood.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.sujood.app.SujoodApplication
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.notifications.PrayerAlarmScheduler
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.domain.model.CalculationMethod
import com.sujood.app.domain.model.LockMode
import com.sujood.app.domain.model.Madhab
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.UserSettings
import com.sujood.app.ui.components.AnimatedGradientBackground
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.DeepNavy
import com.sujood.app.ui.theme.GlassBorder
import com.sujood.app.ui.theme.LavenderGlow
import com.sujood.app.ui.theme.MidnightBlue
import com.sujood.app.ui.theme.SoftPurple
import com.sujood.app.ui.theme.TextSecondary
import com.sujood.app.ui.theme.WarmAmber
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by userPreferences.userSettings.collectAsState(initial = UserSettings())
    var showNameDialog by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }
    var showMadhabDialog by remember { mutableStateOf(false) }
    var showGracePeriodDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var showLockTriggerDialog by remember { mutableStateOf(false) }
    var showLockDurationDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Light
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy)
            )
        },
        containerColor = DeepNavy
    ) { paddingValues ->
        AnimatedGradientBackground(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsSectionHeader("Profile")
                SettingsCard {
                    SettingsClickableItem(
                        icon = Icons.Default.Person,
                        title = "Name",
                        subtitle = settings.name.ifEmpty { "Not set" },
                        onClick = { showNameDialog = true }
                    )
                }

                SettingsSectionHeader("Location")
                SettingsCard {
                    SettingsClickableItem(
                        icon = Icons.Default.LocationOn,
                        title = "Prayer Location",
                        subtitle = if (settings.savedCity.isNotEmpty()) settings.savedCity
                                   else if (settings.savedLatitude != 0.0) "GPS: %.2f°, %.2f°".format(settings.savedLatitude, settings.savedLongitude)
                                   else "Not set — tap to change",
                        onClick = { showCityDialog = true }
                    )
                }

                SettingsSectionHeader("Prayer Settings")
                SettingsCard {
                    SettingsClickableItem(
                        title = "Calculation Method",
                        subtitle = settings.calculationMethod.displayName,
                        onClick = { showMethodDialog = true }
                    )
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Madhab (Asr)",
                        subtitle = settings.madhab.displayName,
                        onClick = { showMadhabDialog = true }
                    )
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Grace Period",
                        subtitle = if (settings.gracePeriodMinutes == 0) "No grace period" else "${settings.gracePeriodMinutes} minutes",
                        onClick = { showGracePeriodDialog = true }
                    )
                }

                SettingsSectionHeader("Notifications")
                SettingsCard {
                    Text(
                        text = "Receive a notification when each prayer time arrives",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                    )
                    Prayer.entries.forEach { prayer ->
                        SettingsToggleItem(
                            title = "${prayer.displayName} Notification",
                            isEnabled = when (prayer) {
                                Prayer.FAJR -> settings.fajrNotificationEnabled
                                Prayer.DHUHR -> settings.dhuhrNotificationEnabled
                                Prayer.ASR -> settings.asrNotificationEnabled
                                Prayer.MAGHRIB -> settings.maghribNotificationEnabled
                                Prayer.ISHA -> settings.ishaNotificationEnabled
                            },
                            onToggle = { enabled ->
                                scope.launch {
                                    userPreferences.saveNotificationEnabled(prayer.name, enabled)
                                    rescheduleAlarms(context, userPreferences)
                                }
                            }
                        )
                        if (prayer != Prayer.entries.last()) SettingsDivider()
                    }
                }

                SettingsSectionHeader("Prayer Lock")
                SettingsCard {
                    Text(
                        text = "Lock the phone at prayer time and block distractions",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                    )
                    Prayer.entries.forEach { prayer ->
                        SettingsToggleItem(
                            title = "Lock for ${prayer.displayName}",
                            isEnabled = when (prayer) {
                                Prayer.FAJR -> settings.fajrLockEnabled
                                Prayer.DHUHR -> settings.dhuhrLockEnabled
                                Prayer.ASR -> settings.asrLockEnabled
                                Prayer.MAGHRIB -> settings.maghribLockEnabled
                                Prayer.ISHA -> settings.ishaLockEnabled
                            },
                            onToggle = { enabled ->
                                scope.launch {
                                    userPreferences.saveLockEnabled(prayer.name, enabled)
                                    rescheduleAlarms(context, userPreferences)
                                }
                            }
                        )
                        if (prayer != Prayer.entries.last()) SettingsDivider()
                    }
                }

                SettingsSectionHeader("Lock Behavior")
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Lock Mode", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LockMode.entries.forEach { mode ->
                                val isSelected = settings.lockMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            scope.launch {
                                                userPreferences.saveLockSettings(mode, settings.lockTriggerMinutes, settings.lockDurationMinutes)
                                            }
                                        }
                                        .background(
                                            if (isSelected) SoftPurple.copy(alpha = 0.2f) else MidnightBlue.copy(alpha = 0.5f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) SoftPurple else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Trigger Timing",
                        subtitle = if (settings.lockTriggerMinutes == 0) "At prayer time"
                                   else "${settings.lockTriggerMinutes} minutes after prayer",
                        onClick = { showLockTriggerDialog = true }
                    )
                    SettingsDivider()
                    SettingsClickableItem(
                        title = "Lock Duration",
                        subtitle = "${settings.lockDurationMinutes} minutes",
                        onClick = { showLockDurationDialog = true }
                    )
                }

                SettingsSectionHeader("Audio & Haptics")
                SettingsCard {
                    SettingsToggleItem(
                        title = "Adhan Sound",
                        subtitle = "Play adhan when prayer time arrives",
                        isEnabled = settings.adhanEnabled,
                        onToggle = { enabled ->
                            scope.launch { userPreferences.saveAudioSettings(enabled, settings.vibrationEnabled) }
                        }
                    )
                    SettingsDivider()
                    SettingsToggleItem(
                        title = "Vibration",
                        subtitle = "Vibrate at prayer time",
                        isEnabled = settings.vibrationEnabled,
                        onToggle = { enabled ->
                            scope.launch { userPreferences.saveAudioSettings(settings.adhanEnabled, enabled) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showNameDialog) {
        NameDialog(
            currentName = settings.name,
            onDismiss = { showNameDialog = false },
            onConfirm = { name -> scope.launch { userPreferences.saveUserName(name) }; showNameDialog = false }
        )
    }
    if (showCityDialog) {
        ChangeCityDialog(
            currentCity = settings.savedCity,
            onDismiss = { showCityDialog = false },
            onConfirm = { city ->
                scope.launch {
                    userPreferences.saveLocationSettings(
                        useGps = false, city = city, country = "", latitude = 0.0, longitude = 0.0
                    )
                }
                showCityDialog = false
            },
            onUseGps = {
                scope.launch {
                    userPreferences.saveLocationSettings(useGps = true, city = "", country = "", latitude = 0.0, longitude = 0.0)
                }
                showCityDialog = false
            }
        )
    }
    if (showMethodDialog) {
        CalculationMethodDialog(
            currentMethod = settings.calculationMethod,
            onDismiss = { showMethodDialog = false },
            onSelect = { method -> scope.launch { userPreferences.saveCalculationMethod(method) }; showMethodDialog = false }
        )
    }
    if (showMadhabDialog) {
        MadhabDialog(
            currentMadhab = settings.madhab,
            onDismiss = { showMadhabDialog = false },
            onSelect = { madhab -> scope.launch { userPreferences.saveMadhab(madhab) }; showMadhabDialog = false }
        )
    }
    if (showGracePeriodDialog) {
        GracePeriodDialog(
            currentMinutes = settings.gracePeriodMinutes,
            onDismiss = { showGracePeriodDialog = false },
            onSelect = { minutes -> scope.launch { userPreferences.saveGracePeriod(minutes) }; showGracePeriodDialog = false }
        )
    }
    if (showLockTriggerDialog) {
        TriggerDialog(
            currentMinutes = settings.lockTriggerMinutes,
            onDismiss = { showLockTriggerDialog = false },
            onSelect = { minutes ->
                scope.launch { userPreferences.saveLockSettings(settings.lockMode, minutes, settings.lockDurationMinutes) }
                showLockTriggerDialog = false
            }
        )
    }
    if (showLockDurationDialog) {
        DurationDialog(
            currentMinutes = settings.lockDurationMinutes,
            onDismiss = { showLockDurationDialog = false },
            onSelect = { minutes ->
                scope.launch { userPreferences.saveLockSettings(settings.lockMode, settings.lockTriggerMinutes, minutes) }
                showLockDurationDialog = false
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = SoftPurple,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    FrostedGlassCard(cornerRadius = 20.dp, contentPadding = 0.dp) {
        Column { content() }
    }
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = SoftPurple, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SoftPurple,
                checkedTrackColor = SoftPurple.copy(alpha = 0.4f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = MidnightBlue
            )
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(GlassBorder))
}

@Composable
private fun NameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your Name") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SoftPurple, unfocusedBorderColor = GlassBorder)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, colors = ButtonDefaults.buttonColors(containerColor = SoftPurple)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MidnightBlue
    )
}

@Composable
private fun ChangeCityDialog(
    currentCity: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onUseGps: () -> Unit
) {
    var city by remember { mutableStateOf(currentCity) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Location") },
        text = {
            Column {
                OutlinedTextField(
                    value = city, onValueChange = { city = it },
                    label = { Text("City Name") },
                    placeholder = { Text("e.g. Dubai") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SoftPurple, unfocusedBorderColor = GlassBorder,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onUseGps) {
                    Text("Use GPS instead", color = LavenderGlow)
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (city.isNotBlank()) onConfirm(city.trim()) },
                   colors = ButtonDefaults.buttonColors(containerColor = SoftPurple)) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MidnightBlue
    )
}

@Composable
private fun CalculationMethodDialog(currentMethod: CalculationMethod, onDismiss: () -> Unit, onSelect: (CalculationMethod) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calculation Method") },
        text = {
            Column {
                CalculationMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(method) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(method.displayName)
                        if (method == currentMethod) Icon(Icons.Default.Check, null, tint = SoftPurple)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun MadhabDialog(currentMadhab: Madhab, onDismiss: () -> Unit, onSelect: (Madhab) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Madhab") },
        text = {
            Column {
                Madhab.entries.forEach { madhab ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(madhab) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(madhab.displayName)
                        if (madhab == currentMadhab) Icon(Icons.Default.Check, null, tint = SoftPurple)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun GracePeriodDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grace Period") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (minutes == 0) "No grace period" else "$minutes minutes")
                        if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = SoftPurple)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun TriggerDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(0, 5, 10, 15, 30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock Trigger") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (minutes == 0) "At prayer time" else "$minutes minutes after prayer")
                        if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

@Composable
private fun DurationDialog(currentMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf(5, 10, 15, 20, 30, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock Duration") },
        text = {
            Column {
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$minutes minutes")
                        if (minutes == currentMinutes) Icon(Icons.Default.Check, null, tint = WarmAmber)
                    }
                }
            }
        },
        confirmButton = {},
        containerColor = MidnightBlue
    )
}

private suspend fun rescheduleAlarms(
    context: android.content.Context,
    userPreferences: com.sujood.app.data.local.datastore.UserPreferences
) {
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
        val notif = com.sujood.app.domain.model.Prayer.entries.map { p ->
            when (p) {
                com.sujood.app.domain.model.Prayer.FAJR -> settings.fajrNotificationEnabled
                com.sujood.app.domain.model.Prayer.DHUHR -> settings.dhuhrNotificationEnabled
                com.sujood.app.domain.model.Prayer.ASR -> settings.asrNotificationEnabled
                com.sujood.app.domain.model.Prayer.MAGHRIB -> settings.maghribNotificationEnabled
                com.sujood.app.domain.model.Prayer.ISHA -> settings.ishaNotificationEnabled
            }
        }.toBooleanArray()
        val lock = com.sujood.app.domain.model.Prayer.entries.map { p ->
            when (p) {
                com.sujood.app.domain.model.Prayer.FAJR -> settings.fajrLockEnabled
                com.sujood.app.domain.model.Prayer.DHUHR -> settings.dhuhrLockEnabled
                com.sujood.app.domain.model.Prayer.ASR -> settings.asrLockEnabled
                com.sujood.app.domain.model.Prayer.MAGHRIB -> settings.maghribLockEnabled
                com.sujood.app.domain.model.Prayer.ISHA -> settings.ishaLockEnabled
            }
        }.toBooleanArray()
        scheduler.scheduleAllAlarms(prayerTimes, notif, lock, settings.gracePeriodMinutes)
    }
}
'''

# ── Write all files ──
for (kind, relpath), content in files.items():
    base = SRC if kind == 'src' else RES
    fullpath = os.path.join(base, relpath.replace("/", os.sep))
    os.makedirs(os.path.dirname(fullpath), exist_ok=True)
    with open(fullpath, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  ✓ {relpath}")

print("\nDone! Now run:")
print("  cd Sujood")
print("  ./gradlew assembleDebug")
