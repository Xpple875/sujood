import os

ROOT = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(ROOT, "Sujood", "app", "src", "main", "java", "com", "sujood", "app")

files = {}

# ─────────────────────────────────────────────────────────────────────────────
# 1. Models.kt  — fix Aladhan calculation method codes
#    Official codes: https://aladhan.com/calculation-methods
#    1=Karachi  2=ISNA  3=MWL  4=Makkah(Umm Al-Qura)  5=Egyptian  8=Gulf  16=Dubai
# ─────────────────────────────────────────────────────────────────────────────
models_path = os.path.join(BASE, "domain/model/Models.kt")
with open(models_path, "r", encoding="utf-8") as f:
    models = f.read()

import re
models = re.sub(
    r'enum class CalculationMethod\(val displayName: String, val code: Int\) \{[^}]+\}',
    '''enum class CalculationMethod(val displayName: String, val code: Int) {
    KARACHI("University of Islamic Sciences, Karachi", 1),
    ISNA("Islamic Society of North America", 2),
    MWL("Muslim World League", 3),
    MAKKAH("Umm Al-Qura, Makkah", 4),
    EGYPTIAN("Egyptian General Authority of Survey", 5),
    GULF("Gulf Region (Kuwait / Qatar)", 8),
    DUBAI("Dubai", 16)
}''',
    models,
    flags=re.DOTALL
)

with open(models_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(models)
print("✅ Models.kt — calculation method codes fixed (Karachi=1,ISNA=2,MWL=3,Makkah=4,Egyptian=5,Gulf=8,Dubai=16)")

# ─────────────────────────────────────────────────────────────────────────────
# 2. PrayerTimesRepository.kt  — fix timezone-aware timestamp parsing
# ─────────────────────────────────────────────────────────────────────────────
files["data/repository/PrayerTimesRepository.kt"] = r'''package com.sujood.app.data.repository

import com.sujood.app.data.api.AladhanApiService
import com.sujood.app.data.api.PrayerTimesResponse
import com.sujood.app.data.local.room.CachedPrayerTimesEntity
import com.sujood.app.data.local.room.PrayerLogDao
import com.sujood.app.data.local.room.PrayerLogEntity
import com.sujood.app.domain.model.CalculationMethod
import com.sujood.app.domain.model.Madhab
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerLog
import com.sujood.app.domain.model.PrayerTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PrayerTimesRepository(
    private val apiService: AladhanApiService,
    private val prayerLogDao: PrayerLogDao
) {
    private val dateFormat    = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun getPrayerTimes(
        latitude: Double,
        longitude: Double,
        method: CalculationMethod = CalculationMethod.MAKKAH,
        madhab: Madhab = Madhab.SHAFI
    ): Result<List<PrayerTime>> {
        return try {
            val response = apiService.getPrayerTimes(
                latitude  = latitude,
                longitude = longitude,
                method    = method.code,
                school    = madhab.code
            )
            Result.success(parsePrayerTimesResponse(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPrayerTimesByCity(
        cityName: String,
        method: CalculationMethod = CalculationMethod.MAKKAH,
        madhab: Madhab = Madhab.SHAFI
    ): Result<List<PrayerTime>> {
        return try {
            val response = apiService.getPrayerTimesByCity(
                city   = cityName,
                method = method.code,
                school = madhab.code
            )
            Result.success(parsePrayerTimesResponse(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchCity(query: String): com.sujood.app.data.api.CitySearchResponse {
        return apiService.searchCity(query)
    }

    /**
     * Parses API response into domain PrayerTime objects.
     * Uses the timezone returned by the API (meta.timezone e.g. "Asia/Dubai")
     * so timestamps are correct regardless of the device's timezone.
     */
    private fun parsePrayerTimesResponse(response: PrayerTimesResponse): List<PrayerTime> {
        val timings  = response.data.timings
        val dateStr  = response.data.date.gregorian.date   // "dd-MM-yyyy"
        val timezone = response.data.meta.timezone         // e.g. "Asia/Dubai"

        fun t(raw: String)  = raw.substring(0, 5)
        fun ts(raw: String) = parseTimeToTimestamp(raw, dateStr, timezone)

        return listOf(
            PrayerTime(Prayer.FAJR,    t(timings.fajr),    ts(timings.fajr)),
            PrayerTime(Prayer.DHUHR,   t(timings.dhuhr),   ts(timings.dhuhr)),
            PrayerTime(Prayer.ASR,     t(timings.asr),     ts(timings.asr)),
            PrayerTime(Prayer.MAGHRIB, t(timings.maghrib), ts(timings.maghrib)),
            PrayerTime(Prayer.ISHA,    t(timings.isha),    ts(timings.isha))
        )
    }

    /**
     * Parses "HH:mm" + "dd-MM-yyyy" into a UTC Unix timestamp,
     * treating the time as being in the prayer location's timezone.
     * Aladhan sometimes appends " (GMT+X)" — we strip that first.
     */
    private fun parseTimeToTimestamp(time: String, dateStr: String, timezone: String): Long {
        return try {
            val cleanTime = time.substringBefore(" ").trim().substring(0, 5)
            val fmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone(timezone)
            }
            fmt.parse("$dateStr $cleanTime")?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun getCurrentDateString(): String = dateFormat.format(Date())
    fun getCurrentDateKey(): String    = dateKeyFormat.format(Date())

    suspend fun logPrayerCompletion(prayer: Prayer): Long {
        val entity = PrayerLogEntity(
            prayerName  = prayer.name,
            completedAt = System.currentTimeMillis(),
            date        = getCurrentDateKey()
        )
        return prayerLogDao.insertPrayerLog(entity)
    }

    suspend fun deletePrayerLog(prayer: Prayer) {
        prayerLogDao.deletePrayerLog(getCurrentDateKey(), prayer.name)
    }

    suspend fun isPrayerCompletedToday(prayer: Prayer): Boolean =
        prayerLogDao.isPrayerCompleted(getCurrentDateKey(), prayer.name)

    suspend fun getCompletedPrayersToday(): List<Prayer> {
        return prayerLogDao.getPrayerLogsForDate(getCurrentDateKey()).mapNotNull { log ->
            try { Prayer.valueOf(log.prayerName) } catch (_: Exception) { null }
        }
    }

    fun getAllPrayerLogs(): Flow<List<PrayerLog>> {
        return prayerLogDao.getAllPrayerLogs().map { entities ->
            entities.map { entity ->
                PrayerLog(
                    id          = entity.id,
                    prayer      = Prayer.valueOf(entity.prayerName),
                    completedAt = entity.completedAt,
                    date        = entity.date
                )
            }
        }
    }

    suspend fun getPrayerStreak(): Int {
        val completedDates = prayerLogDao.getFullyCompletedDates()
        if (completedDates.isEmpty()) return 0
        val fmt  = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal  = java.util.Calendar.getInstance()
        val todayStr     = fmt.format(cal.time)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = fmt.format(cal.time)
        val startDate = when {
            completedDates.first() == todayStr     -> todayStr
            completedDates.first() == yesterdayStr -> yesterdayStr
            else -> return 0
        }
        val checkCal = java.util.Calendar.getInstance()
        checkCal.time = fmt.parse(startDate) ?: return 0
        var streak = 0
        for (dateStr in completedDates) {
            if (dateStr == fmt.format(checkCal.time)) {
                streak++
                checkCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else break
        }
        return streak
    }
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 3. QiblaScreen.kt  — fix compass re-entry break + 0°/360° wrap spinning
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

private const val LP_ALPHA = 0.12f

private fun lowPassFilter(input: FloatArray, prev: FloatArray?): FloatArray {
    if (prev == null) return input.clone()
    return FloatArray(input.size) { i -> prev[i] + LP_ALPHA * (input[i] - prev[i]) }
}

/** Shortest signed arc from -> to, result in [-180, 180]. */
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
                    } else {
                        statusMessage = "Open Home tab to load your city location"
                    }
                } catch (_: Exception) {
                    statusMessage = "Open Home tab to load your city location"
                }
            }
            else -> statusMessage = "No location found - open Home tab first"
        }
    }

    // ── Heading state ────────────────────────────────────────────────────────
    //
    // WHY TWO VARIABLES?
    //
    // smoothedRaw  (0-360): the LP-filtered heading in normal compass degrees.
    //   Used for the "facing Qibla" check and to compute deltas.
    //   Initialised to NaN so we know when the first sensor reading arrives.
    //
    // accumulatedHead (unbounded): we add the shortest-arc delta to this each
    //   sensor tick. It can be 361, 720, -45, etc. Compose animates it
    //   linearly — which is always the short path because delta is always
    //   in [-180, 180]. This eliminates the 0<->360 wrap-around spin.
    //
    var smoothedRaw     by remember { mutableFloatStateOf(Float.NaN) }
    var accumulatedHead by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sm    = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag   = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var prevAccel: FloatArray? = null
        var prevMag:   FloatArray? = null
        var accelVals: FloatArray? = null
        var magVals:   FloatArray? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        prevAccel = lowPassFilter(event.values.clone(), prevAccel)
                        accelVals = prevAccel
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        prevMag = lowPassFilter(event.values.clone(), prevMag)
                        magVals = prevMag
                    }
                }
                val a = accelVals ?: return
                val m = magVals   ?: return

                val R = FloatArray(9); val I = FloatArray(9)
                if (!SensorManager.getRotationMatrix(R, I, a, m)) return
                val orient = FloatArray(3)
                SensorManager.getOrientation(R, orient)

                val rawDeg  = Math.toDegrees(orient[0].toDouble()).toFloat()
                val rawNorm = ((rawDeg % 360f) + 360f) % 360f

                if (smoothedRaw.isNaN()) {
                    // First reading after entering screen — snap immediately,
                    // no animation from 0 to wherever the phone is pointing.
                    smoothedRaw     = rawNorm
                    accumulatedHead = rawNorm
                } else {
                    val delta       = shortestDelta(smoothedRaw, rawNorm)
                    smoothedRaw     = ((smoothedRaw + LP_ALPHA * delta) + 360f) % 360f
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

    // Compass rose: rotate by -accumulatedHead (North stays up)
    val animatedHead by animateFloatAsState(
        targetValue   = accumulatedHead,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
        label         = "compassHead"
    )
    val compassRotation = -animatedHead

    // Needle points toward Qibla. We compute the Qibla bearing relative to
    // smoothedRaw (the 0-360 normalised heading) then express that as an
    // offset from accumulatedHead so the animation stays in the linear domain.
    val currentSmoothed = smoothedRaw.takeIf { !it.isNaN() } ?: 0f
    val qiblaOffset     = shortestDelta(currentSmoothed,
        ((qiblaDirection - currentSmoothed + 360f) % 360f))
    val needleTarget    = accumulatedHead + qiblaOffset

    val animatedNeedle by animateFloatAsState(
        targetValue   = needleTarget,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
        label         = "needleRot"
    )

    val isFacingQibla  = !smoothedRaw.isNaN() &&
        kotlin.math.abs(shortestDelta(currentSmoothed, qiblaDirection)) < 5f

    val compassScale by animateFloatAsState(
        targetValue   = if (isFacingQibla) 1.04f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label         = "scale"
    )

    AnimatedGradientBackground {
        Column(
            modifier            = Modifier.fillMaxSize().padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("Qibla Compass", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                text      = if (!isCalibrated) "Move your phone in a figure-8 to calibrate"
                            else statusMessage,
                style     = MaterialTheme.typography.bodySmall,
                color     = if (!isCalibrated) Color(0xFFFBBF24) else TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(48.dp))

            Box(modifier = Modifier.size(280.dp).scale(compassScale),
                contentAlignment = Alignment.Center) {

                Canvas(modifier = Modifier.fillMaxSize().rotate(compassRotation)) {
                    val cx = size.width  / 2f
                    val cy = size.height / 2f
                    val r  = size.minDimension / 2f

                    drawCircle(
                        brush  = Brush.radialGradient(
                            colors = listOf(LavenderGlow.copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(cx, cy), radius = r),
                        radius = r)
                    drawCircle(color = Color.White.copy(alpha = 0.08f),
                        radius = r - 4f, style = Stroke(width = 1.5f))
                    drawCircle(color = Color.White.copy(alpha = 0.04f),
                        radius = r * 0.75f, style = Stroke(width = 1f))
                    for (i in 0 until 8) {
                        val angle = Math.toRadians((i * 45.0))
                        val isMaj = i % 2 == 0
                        val inner = r * (if (isMaj) 0.82f else 0.88f)
                        val outer = r * 0.94f
                        drawLine(
                            color = Color.White.copy(alpha = if (isMaj) 0.5f else 0.25f),
                            start = Offset(cx + (inner * sin(angle)).toFloat(),
                                          cy - (inner * cos(angle)).toFloat()),
                            end   = Offset(cx + (outer * sin(angle)).toFloat(),
                                          cy - (outer * cos(angle)).toFloat()),
                            strokeWidth = if (isMaj) 2f else 1f
                        )
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize().rotate(animatedNeedle)) {
                    val cx = size.width  / 2f
                    val cy = size.height / 2f
                    val path = Path().apply {
                        moveTo(cx, cy - size.minDimension * 0.38f)
                        lineTo(cx - 12, cy + 14)
                        lineTo(cx, cy + 24)
                        lineTo(cx + 12, cy + 14)
                        close()
                    }
                    drawPath(path, brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isFacingQibla) WarmAmber else LavenderGlow,
                            if (isFacingQibla) WarmAmber.copy(alpha = 0.4f) else SoftPurple.copy(alpha = 0.4f)
                        )))
                    drawCircle(Color.White, 8f, Offset(cx, cy))
                    drawCircle(if (isFacingQibla) WarmAmber else SoftPurple, 4f, Offset(cx, cy))
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(CircleShape)
                        .background(if (isFacingQibla) WarmAmber.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.08f))
                        .border(1.dp,
                            if (isFacingQibla) WarmAmber.copy(alpha = 0.7f)
                            else Color.White.copy(alpha = 0.2f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Mosque, "Qibla direction",
                        tint     = if (isFacingQibla) WarmAmber else Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text       = if (isFacingQibla) "Facing Qibla"
                             else "${qiblaDirection.roundToInt()} from North",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color      = if (isFacingQibla) WarmAmber else LavenderGlow
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = if (isFacingQibla)
                    "You are facing the Kaaba. May Allah accept your prayer."
                else
                    "Rotate your phone until the arrow points toward the Mosque icon",
                style     = MaterialTheme.typography.bodyMedium,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(24.dp))
            Box(modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("21.4225 N, 39.8262 E",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f))
            }
        }
    }
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
    return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
}

private const val KAABA_LAT = 21.4225
private const val KAABA_LON = 39.8262
'''

# ─────────────────────────────────────────────────────────────────────────────
# 4. HomeViewModel.kt  — stop re-fetching on every home visit + fix city fetch
# ─────────────────────────────────────────────────────────────────────────────
vm_path = os.path.join(BASE, "ui/screens/home/HomeViewModel.kt")
with open(vm_path, "r", encoding="utf-8") as f:
    vm = f.read()

# Patch initializeAndRefresh
old_init = vm[vm.find("    fun initializeAndRefresh(context: Context) {"):
              vm.find("    fun initializeAndRefresh(context: Context) {") +
              vm[vm.find("    fun initializeAndRefresh(context: Context) {"):].find("\n    fun ")]
new_init = '''    fun initializeAndRefresh(context: Context) {
        cachedContext = context.applicationContext
        // Skip re-fetch if prayer times are already loaded — prevents GPS/network
        // call every time the user navigates back to the Home tab.
        if (_uiState.value.prayerTimes.isNotEmpty()) return

        viewModelScope.launch {
            val settings = userPreferences.userSettings.first()
            when {
                settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 -> {
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    fetchPrayerTimesByLocation(settings.savedLatitude, settings.savedLongitude)
                }
                settings.savedCity.isNotEmpty() -> {
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    // Strip country suffix (e.g. "Dubai, UAE" -> "Dubai")
                    val bareCity = settings.savedCity.substringBefore(",").trim()
                    val result = repository.getPrayerTimesByCity(
                        cityName = bareCity,
                        method   = settings.calculationMethod,
                        madhab   = settings.madhab
                    )
                    result.fold(
                        onSuccess = { handlePrayerTimesSuccess(it) },
                        onFailure = {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Could not load times for ${settings.savedCity}"
                            )
                        }
                    )
                }
                else -> fetchPrayerTimes(context)
            }
        }
    }

'''

# Find the old initializeAndRefresh function boundaries precisely
start_marker = "    fun initializeAndRefresh(context: Context) {"
end_marker   = "\n    fun fetchPrayerTimes(context: Context) {"
start_idx    = vm.find(start_marker)
end_idx      = vm.find(end_marker)
if start_idx != -1 and end_idx != -1:
    vm = vm[:start_idx] + new_init + vm[end_idx + 1:]
    print("✅ HomeViewModel.kt — initializeAndRefresh patched")
else:
    print("⚠️  HomeViewModel.kt — could not find initializeAndRefresh boundaries")

# Patch fetchPrayerTimesByCity to strip country suffix and use user's method
old_fetch_start = "    fun fetchPrayerTimesByCity(context: Context, cityName: String) {"
old_fetch_end   = "\n    @SuppressLint(\"MissingPermission\")"
fs = vm.find(old_fetch_start)
fe = vm.find(old_fetch_end)
if fs != -1 and fe != -1:
    new_city_fetch = '''    fun fetchPrayerTimesByCity(context: Context, cityName: String) {
        cachedContext = context.applicationContext
        if (cityName.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a city name")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val settings = userPreferences.userSettings.first()
            // Strip country suffix e.g. "Dubai, UAE" -> "Dubai"
            val bareCity = cityName.substringBefore(",").trim()

            try {
                // Try /timingsByCity with the user's saved calculation method
                val result = repository.getPrayerTimesByCity(
                    cityName = bareCity,
                    method   = settings.calculationMethod,
                    madhab   = settings.madhab
                )
                result.fold(
                    onSuccess = { prayerTimes ->
                        viewModelScope.launch {
                            userPreferences.saveLocationSettings(
                                useGps    = false,
                                city      = cityName,
                                country   = "",
                                latitude  = 0.0,
                                longitude = 0.0
                            )
                        }
                        handlePrayerTimesSuccess(prayerTimes)
                    },
                    onFailure = {
                        // Fallback: coordinate lookup
                        try {
                            val searchResult = repository.searchCity(bareCity)
                            val cityData     = searchResult.data.firstOrNull()
                            if (cityData != null) {
                                viewModelScope.launch {
                                    userPreferences.saveLocationSettings(
                                        useGps    = false,
                                        city      = cityData.name,
                                        country   = cityData.country,
                                        latitude  = cityData.latitude,
                                        longitude = cityData.longitude
                                    )
                                }
                                fetchPrayerTimesByLocation(cityData.latitude, cityData.longitude)
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = "Could not find prayer times for \\"$cityName\\". Try another city."
                                )
                            }
                        } catch (e2: Exception) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Could not find prayer times for \\"$cityName\\"."
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching by city", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to fetch prayer times: ${e.message}"
                )
            }
        }
    }

'''
    vm = vm[:fs] + new_city_fetch + vm[fe + 1:]
    print("✅ HomeViewModel.kt — fetchPrayerTimesByCity patched")
else:
    print("⚠️  HomeViewModel.kt — fetchPrayerTimesByCity boundaries not found")

with open(vm_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(vm)

# ─────────────────────────────────────────────────────────────────────────────
# 5. HomeScreen.kt  — replace notification bell with clickable location button
# ─────────────────────────────────────────────────────────────────────────────
hs_path = os.path.join(BASE, "ui/screens/home/HomeScreen.kt")
with open(hs_path, "r", encoding="utf-8") as f:
    hs = f.read()

# Swap import
hs = hs.replace(
    "import androidx.compose.material.icons.filled.Notifications\n",
    "import androidx.compose.material.icons.filled.EditLocation\n"
)

# Add showLocationDialog state variable + dialog after the LaunchedEffect block
launcher_block_end = "    LaunchedEffect(Unit) { delay(100); visible = true }"
if launcher_block_end in hs:
    location_dialog = '''    LaunchedEffect(Unit) { delay(100); visible = true }

    // ── Inline location picker ─────────────────────────────────────────────
    var showLocationDialog by remember { mutableStateOf(false) }
    if (showLocationDialog) {
        var locQuery    by remember { mutableStateOf("") }
        var locSelected by remember { mutableStateOf("") }
        val locFm = LocalFocusManager.current
        val locCities = listOf(
            "Dubai","Abu Dhabi","Sharjah","Ajman","Al Ain",
            "Riyadh","Jeddah","Mecca","Medina",
            "London","Manchester","Birmingham",
            "New York","Los Angeles","Chicago","Toronto",
            "Cairo","Alexandria","Istanbul",
            "Kuala Lumpur","Jakarta","Karachi","Lahore",
            "Dhaka","Mumbai","Delhi",
            "Doha","Kuwait City","Muscat","Manama","Amman",
            "Lagos","Nairobi","Singapore","Sydney"
        ).sorted()
        val locFiltered = remember(locQuery) {
            if (locQuery.length < 2) emptyList()
            else locCities.filter { it.contains(locQuery, ignoreCase = true) }.take(6)
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Change Location", color = Color.White) },
            text  = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value        = locQuery,
                        onValueChange = { locQuery = it; locSelected = "" },
                        label        = { Text("Search city") },
                        singleLine   = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White
                        )
                    )
                    if (locFiltered.isNotEmpty() && locSelected.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1F35))
                            .border(1.dp, Color.White.copy(alpha = 0.1f),
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        ) {
                            locFiltered.forEachIndexed { idx, city ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { locSelected = city; locQuery = city; locFm.clearFocus() }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, null,
                                        tint     = PrimaryBlue,
                                        modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(city, fontSize = 13.sp, color = Color.White)
                                }
                                if (idx < locFiltered.lastIndex)
                                    androidx.compose.material3.HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        val city = locSelected.ifEmpty { locQuery }.trim()
                        if (city.isNotBlank()) {
                            viewModel.fetchPrayerTimesByCity(context, city)
                            showLocationDialog = false
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue)
                ) { Text("Confirm") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showLocationDialog = false }
                ) { Text("Cancel") }
            },
            containerColor = Color(0xFF0D1020)
        )
    }'''
    hs = hs.replace(launcher_block_end, location_dialog)
    print("✅ HomeScreen.kt — location dialog added")
else:
    print("⚠️  HomeScreen.kt — launcher block end not found")

# Replace the notification bell box with a clickable location button
old_bell = '''                Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }'''
new_bell = '''                Box(modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { showLocationDialog = true },
                    contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.EditLocation,
                        contentDescription = "Change location",
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }'''

if old_bell in hs:
    hs = hs.replace(old_bell, new_bell)
    print("✅ HomeScreen.kt — bell replaced with location button")
else:
    print("⚠️  HomeScreen.kt — bell box not matched, trying fallback")
    # Fallback: replace just the icon inside the existing box
    hs = hs.replace(
        "Icon(imageVector = Icons.Default.Notifications, contentDescription = null,\n                        tint = Color.White, modifier = Modifier.size(22.dp))",
        "Icon(imageVector = Icons.Default.EditLocation, contentDescription = \"Change location\",\n                        tint = Color.White, modifier = Modifier.size(22.dp))"
    )

# Add width import if missing
if "import androidx.compose.foundation.layout.width" not in hs and "import androidx.compose.foundation.layout.*" not in hs:
    hs = hs.replace(
        "import androidx.compose.foundation.layout.height\n",
        "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.width\n"
    )

with open(hs_path, "w", encoding="utf-8", newline="\n") as f:
    f.write(hs)

# ─────────────────────────────────────────────────────────────────────────────
# Write all new full files
# ─────────────────────────────────────────────────────────────────────────────
for rel_path, content in files.items():
    abs_path = os.path.join(BASE, rel_path)
    os.makedirs(os.path.dirname(abs_path), exist_ok=True)
    with open(abs_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"✅ Written: {rel_path}")

print("\n✅ fix_v4.py complete.")
print("Run: cd Sujood && ./gradlew assembleDebug")
