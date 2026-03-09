import os, re

ROOT = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(ROOT, "Sujood", "app", "src", "main", "java", "com", "sujood", "app")

def write(rel, content):
    path = os.path.join(BASE, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"✅ {rel}")

# ─────────────────────────────────────────────────────────────────────────────
# 1. Models.kt — fix Aladhan method codes
# ─────────────────────────────────────────────────────────────────────────────
mp = os.path.join(BASE, "domain/model/Models.kt")
with open(mp, "r", encoding="utf-8") as f:
    m = f.read()
m = re.sub(
    r'enum class CalculationMethod\(val displayName: String, val code: Int\) \{[^}]+\}',
    'enum class CalculationMethod(val displayName: String, val code: Int) {\n'
    '    KARACHI("University of Islamic Sciences, Karachi", 1),\n'
    '    ISNA("Islamic Society of North America", 2),\n'
    '    MWL("Muslim World League", 3),\n'
    '    MAKKAH("Umm Al-Qura, Makkah", 4),\n'
    '    EGYPTIAN("Egyptian General Authority of Survey", 5),\n'
    '    GULF("Gulf Region (Kuwait / Qatar)", 8),\n'
    '    DUBAI("Dubai", 16)\n'
    '}',
    m, flags=re.DOTALL
)
with open(mp, "w", encoding="utf-8", newline="\n") as f:
    f.write(m)
print("✅ Models.kt — method codes fixed (Karachi=1 ISNA=2 MWL=3 Makkah=4 Egyptian=5 Gulf=8 Dubai=16)")

# ─────────────────────────────────────────────────────────────────────────────
# 2. PrayerTimesRepository.kt — timezone-aware timestamp parsing
# ─────────────────────────────────────────────────────────────────────────────
write("data/repository/PrayerTimesRepository.kt", r'''package com.sujood.app.data.repository

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
    ): Result<List<PrayerTime>> = try {
        val r = apiService.getPrayerTimes(latitude, longitude, method.code, madhab.code)
        Result.success(parsePrayerTimesResponse(r))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getPrayerTimesByCity(
        cityName: String,
        method: CalculationMethod = CalculationMethod.MAKKAH,
        madhab: Madhab = Madhab.SHAFI
    ): Result<List<PrayerTime>> = try {
        val r = apiService.getPrayerTimesByCity(cityName, null, method.code, madhab.code)
        Result.success(parsePrayerTimesResponse(r))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun searchCity(query: String) = apiService.searchCity(query)

    private fun parsePrayerTimesResponse(response: PrayerTimesResponse): List<PrayerTime> {
        val t  = response.data.timings
        val ds = response.data.date.gregorian.date   // "dd-MM-yyyy"
        val tz = response.data.meta.timezone         // e.g. "Asia/Dubai"
        fun s(raw: String) = raw.substring(0, 5)
        fun ts(raw: String) = parseTs(raw, ds, tz)
        return listOf(
            PrayerTime(Prayer.FAJR,    s(t.fajr),    ts(t.fajr)),
            PrayerTime(Prayer.DHUHR,   s(t.dhuhr),   ts(t.dhuhr)),
            PrayerTime(Prayer.ASR,     s(t.asr),     ts(t.asr)),
            PrayerTime(Prayer.MAGHRIB, s(t.maghrib), ts(t.maghrib)),
            PrayerTime(Prayer.ISHA,    s(t.isha),    ts(t.isha))
        )
    }

    /** Parse time string in the prayer location's timezone, not the device's. */
    private fun parseTs(time: String, dateStr: String, timezone: String): Long = try {
        val clean = time.substringBefore(" ").trim().substring(0, 5)
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone(timezone) }
            .parse("$dateStr $clean")?.time ?: System.currentTimeMillis()
    } catch (_: Exception) { System.currentTimeMillis() }

    fun getCurrentDateString(): String = dateFormat.format(Date())
    fun getCurrentDateKey(): String    = dateKeyFormat.format(Date())

    suspend fun logPrayerCompletion(prayer: Prayer): Long =
        prayerLogDao.insertPrayerLog(PrayerLogEntity(
            prayerName  = prayer.name,
            completedAt = System.currentTimeMillis(),
            date        = getCurrentDateKey()
        ))

    suspend fun deletePrayerLog(prayer: Prayer) =
        prayerLogDao.deletePrayerLog(getCurrentDateKey(), prayer.name)

    suspend fun isPrayerCompletedToday(prayer: Prayer): Boolean =
        prayerLogDao.isPrayerCompleted(getCurrentDateKey(), prayer.name)

    suspend fun getCompletedPrayersToday(): List<Prayer> =
        prayerLogDao.getPrayerLogsForDate(getCurrentDateKey()).mapNotNull { log ->
            try { Prayer.valueOf(log.prayerName) } catch (_: Exception) { null }
        }

    fun getAllPrayerLogs(): Flow<List<PrayerLog>> =
        prayerLogDao.getAllPrayerLogs().map { entities ->
            entities.map { e ->
                PrayerLog(id = e.id, prayer = Prayer.valueOf(e.prayerName),
                          completedAt = e.completedAt, date = e.date)
            }
        }

    suspend fun getPrayerStreak(): Int {
        val dates = prayerLogDao.getFullyCompletedDates()
        if (dates.isEmpty()) return 0
        val fmt  = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal  = java.util.Calendar.getInstance()
        val today     = fmt.format(cal.time)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterday = fmt.format(cal.time)
        val start = when { dates.first() == today -> today; dates.first() == yesterday -> yesterday; else -> return 0 }
        val check = java.util.Calendar.getInstance().also { it.time = fmt.parse(start) ?: return 0 }
        var streak = 0
        for (d in dates) {
            if (d == fmt.format(check.time)) { streak++; check.add(java.util.Calendar.DAY_OF_YEAR, -1) } else break
        }
        return streak
    }
}
''')

# ─────────────────────────────────────────────────────────────────────────────
# 3. QiblaScreen.kt — re-entry fix + 0/360 wrap fix
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/qibla/QiblaScreen.kt", r'''package com.sujood.app.ui.screens.qibla

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

    // smoothedRaw  — normalised 0-360 heading, NaN until first sensor tick
    // accumulatedHead — unbounded running total; lets animateFloatAsState
    //   always take the short path (no 0<->360 wraparound spin)
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
                val rawNorm = ((Math.toDegrees(orient[0].toDouble()).toFloat() % 360f) + 360f) % 360f
                if (smoothedRaw.isNaN()) {
                    // First tick after entering screen — snap instantly, no spin from 0
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
    // Needle target: accumulated base + shortest-arc offset to qibla
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
''')

# ─────────────────────────────────────────────────────────────────────────────
# 4. HomeViewModel.kt — complete replacement
#    - initializeAndRefresh skips re-fetch if times loaded
#    - fetchPrayerTimesByCity uses user's method + strips country suffix
#    - prayerLockEnabled master toggle (from fix_v3)
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/home/HomeViewModel.kt", r'''package com.sujood.app.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerTime
import com.sujood.app.domain.model.UserSettings
import com.sujood.app.domain.usecase.GetNextPrayerUseCase
import com.sujood.app.domain.usecase.GetPrayerStreakUseCase
import com.sujood.app.domain.usecase.LogPrayerCompletionUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class HomeUiState(
    val isLoading: Boolean = true,
    val isLoadingLocation: Boolean = false,
    val userName: String = "",
    val prayerTimes: List<PrayerTime> = emptyList(),
    val completedPrayersToday: List<Prayer> = emptyList(),
    val streakDays: Int = 0,
    val nextPrayerInfo: GetNextPrayerUseCase.NextPrayerInfo? = null,
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val error: String? = null,
    val showCityInput: Boolean = false,
    val settings: UserSettings = UserSettings()
)

class HomeViewModel(
    private val repository: PrayerTimesRepository,
    private val userPreferences: UserPreferences,
    private val logPrayerCompletionUseCase: LogPrayerCompletionUseCase,
    private val getNextPrayerUseCase: GetNextPrayerUseCase,
    private val getPrayerStreakUseCase: GetPrayerStreakUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var cachedContext: Context? = null

    companion object {
        private const val TAG = "HomeViewModel"
        private const val LOCATION_TIMEOUT_MS = 10000L
        private const val API_TIMEOUT_MS = 10000L
    }

    init {
        loadUserData()
        startTimeUpdates()
    }

    fun initializeAndRefresh(context: Context) {
        cachedContext = context.applicationContext
        // Skip re-fetch if prayer times are already loaded — prevents redundant
        // GPS / network calls every time the user navigates back to Home.
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
                    // Strip country suffix e.g. "Dubai, UAE" -> "Dubai"
                    val bareCity = settings.savedCity.substringBefore(",").trim()
                    repository.getPrayerTimesByCity(bareCity, settings.calculationMethod, settings.madhab)
                        .fold(
                            onSuccess = { handlePrayerTimesSuccess(it) },
                            onFailure = {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = "Could not load times for ${settings.savedCity}")
                            }
                        )
                }
                else -> fetchPrayerTimes(context)
            }
        }
    }

    private fun loadUserData() {
        viewModelScope.launch {
            userPreferences.userSettings.collect { settings ->
                _uiState.value = _uiState.value.copy(userName = settings.name, settings = settings)
            }
        }
    }

    private fun startTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val prayerTimes = _uiState.value.prayerTimes
                if (prayerTimes.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        currentTimeMillis = System.currentTimeMillis(),
                        nextPrayerInfo    = getNextPrayerUseCase(prayerTimes)
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchPrayerTimes(context: Context) {
        cachedContext = context.applicationContext
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (!hasLocationPermission(context)) {
            _uiState.value = _uiState.value.copy(isLoading = false, isLoadingLocation = false,
                error = "Location permission required")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isLoadingLocation = true,
                error = null, showCityInput = false)
            try {
                val location = getLocationWithTimeout(context)
                if (location != null) {
                    viewModelScope.launch {
                        userPreferences.saveLocationSettings(true, "", "", location.latitude, location.longitude)
                    }
                    fetchPrayerTimesByLocation(location.latitude, location.longitude)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoadingLocation = false,
                        showCityInput = true, error = "Location timed out. Please enter your city manually.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching location", e)
                _uiState.value = _uiState.value.copy(isLoading = false, isLoadingLocation = false,
                    showCityInput = true, error = "Could not get location. Please enter your city manually.")
            }
        }
    }

    fun fetchPrayerTimesByCity(context: Context, cityName: String) {
        cachedContext = context.applicationContext
        if (cityName.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a city name"); return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val settings = userPreferences.userSettings.first()
            val bareCity = cityName.substringBefore(",").trim()
            try {
                // Primary: /timingsByCity with user's calculation method
                repository.getPrayerTimesByCity(bareCity, settings.calculationMethod, settings.madhab)
                    .fold(
                        onSuccess = { prayerTimes ->
                            viewModelScope.launch {
                                userPreferences.saveLocationSettings(false, cityName, "", 0.0, 0.0)
                            }
                            handlePrayerTimesSuccess(prayerTimes)
                        },
                        onFailure = {
                            // Fallback: coordinate lookup
                            try {
                                val cityData = repository.searchCity(bareCity).data.firstOrNull()
                                if (cityData != null) {
                                    viewModelScope.launch {
                                        userPreferences.saveLocationSettings(false, cityData.name,
                                            cityData.country, cityData.latitude, cityData.longitude)
                                    }
                                    fetchPrayerTimesByLocation(cityData.latitude, cityData.longitude)
                                } else {
                                    _uiState.value = _uiState.value.copy(isLoading = false,
                                        error = "Could not find prayer times for \"$cityName\".")
                                }
                            } catch (e2: Exception) {
                                _uiState.value = _uiState.value.copy(isLoading = false,
                                    error = "Could not find prayer times for \"$cityName\".")
                            }
                        }
                    )
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching by city", e)
                _uiState.value = _uiState.value.copy(isLoading = false,
                    error = "Failed to fetch prayer times: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocationWithTimeout(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        return try {
            val ct = CancellationTokenSource()
            withTimeout(LOCATION_TIMEOUT_MS) {
                try { fusedLocationClient?.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, ct.token)?.await() }
                catch (e: Exception) { Log.e(TAG, "Location task failed", e); null }
            }
        } catch (e: Exception) { Log.e(TAG, "Error getting location", e); null }
    }

    private suspend fun fetchPrayerTimesByLocation(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(isLoadingLocation = false)
        try {
            val settings = userPreferences.userSettings.first()
            withTimeout(API_TIMEOUT_MS) {
                repository.getPrayerTimes(latitude, longitude, settings.calculationMethod, settings.madhab)
            }.fold(
                onSuccess = { handlePrayerTimesSuccess(it) },
                onFailure = { e ->
                    Log.e(TAG, "API error: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(isLoading = false,
                        error = "Failed to load prayer times: ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Timeout fetching prayer times", e)
            _uiState.value = _uiState.value.copy(isLoading = false,
                error = "Connection timed out. Please check your internet and try again.")
        }
    }

    private suspend fun handlePrayerTimesSuccess(prayerTimes: List<PrayerTime>) {
        val completedToday = repository.getCompletedPrayersToday()
        val streak         = getPrayerStreakUseCase()
        val nextInfo       = getNextPrayerUseCase(prayerTimes)
        _uiState.value = _uiState.value.copy(
            isLoading = false, prayerTimes = prayerTimes,
            completedPrayersToday = completedToday, streakDays = streak,
            nextPrayerInfo = nextInfo, error = null, showCityInput = false
        )
        scheduleAlarmsForPrayerTimes(prayerTimes)
    }

    private suspend fun scheduleAlarmsForPrayerTimes(prayerTimes: List<PrayerTime>) {
        if (cachedContext == null) return
        val settings  = userPreferences.userSettings.first()
        val scheduler = com.sujood.app.notifications.PrayerAlarmScheduler(cachedContext!!)
        val notif = Prayer.entries.map { p -> when (p) {
            Prayer.FAJR -> settings.fajrNotificationEnabled; Prayer.DHUHR -> settings.dhuhrNotificationEnabled
            Prayer.ASR  -> settings.asrNotificationEnabled;  Prayer.MAGHRIB -> settings.maghribNotificationEnabled
            Prayer.ISHA -> settings.ishaNotificationEnabled
        }}.toBooleanArray()
        // prayerLockEnabled = master toggle: if off, no prayer triggers a lock
        val lock = Prayer.entries.map { p ->
            if (!settings.prayerLockEnabled) false
            else when (p) {
                Prayer.FAJR -> settings.fajrLockEnabled; Prayer.DHUHR -> settings.dhuhrLockEnabled
                Prayer.ASR  -> settings.asrLockEnabled;  Prayer.MAGHRIB -> settings.maghribLockEnabled
                Prayer.ISHA -> settings.ishaLockEnabled
            }
        }.toBooleanArray()
        scheduler.scheduleAllAlarms(prayerTimes, notif, lock, settings.gracePeriodMinutes)
    }

    fun showCityInput() {
        _uiState.value = _uiState.value.copy(showCityInput = true, isLoading = false,
            isLoadingLocation = false, error = null)
    }

    fun hideCityInput() { _uiState.value = _uiState.value.copy(showCityInput = false) }

    private fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun logPrayerCompletion(prayer: Prayer) = togglePrayerCompletion(prayer)

    fun togglePrayerCompletion(prayer: Prayer) {
        viewModelScope.launch {
            if (_uiState.value.completedPrayersToday.contains(prayer))
                repository.deletePrayerLog(prayer)
            else
                logPrayerCompletionUseCase(prayer)
            _uiState.value = _uiState.value.copy(
                completedPrayersToday = repository.getCompletedPrayersToday(),
                streakDays            = getPrayerStreakUseCase()
            )
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    class Factory(
        private val repository: PrayerTimesRepository,
        private val userPreferences: UserPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(
            repository, userPreferences,
            LogPrayerCompletionUseCase(repository), GetNextPrayerUseCase(),
            GetPrayerStreakUseCase(repository)
        ) as T
    }
}
''')

# ─────────────────────────────────────────────────────────────────────────────
# 5. HomeScreen.kt — complete replacement: location button + inline dialog
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/home/HomeScreen.kt", r'''package com.sujood.app.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sujood.app.data.api.RetrofitClient
import com.sujood.app.data.local.datastore.UserPreferences
import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerTime
import com.sujood.app.domain.usecase.GetNextPrayerUseCase
import com.sujood.app.domain.usecase.GetPrayerStreakUseCase
import com.sujood.app.domain.usecase.LogPrayerCompletionUseCase
import com.sujood.app.ui.components.DailyQuoteCard
import com.sujood.app.ui.components.FrostedGlassCard
import com.sujood.app.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

private val PrimaryBlue    = Color(0xFF1132D4)
private val BackgroundDark = Color(0xFF101322)
private val SurfaceCard    = Color(0xFF1A1F35)
private val BorderSubtle   = Color(0x40FFFFFF)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    userPreferences: UserPreferences = UserPreferences(LocalContext.current),
    repository: PrayerTimesRepository? = null
) {
    val context  = LocalContext.current
    val database = (context.applicationContext as? com.sujood.app.SujoodApplication)?.database
    val repo     = repository ?: remember {
        database?.let { PrayerTimesRepository(RetrofitClient.aladhanApiService, it.prayerLogDao()) }
    }

    val viewModel: HomeViewModel = remember(userPreferences, repo) {
        if (repo != null) HomeViewModel(
            repo, userPreferences,
            LogPrayerCompletionUseCase(repo), GetNextPrayerUseCase(), GetPrayerStreakUseCase(repo)
        ) else throw IllegalStateException("Repository not available")
    }

    val uiState   by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var visible   by remember { mutableStateOf(false) }
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // ── Location dialog state — declared here so HeroSection can use it ──
    var showLocationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted) viewModel.initializeAndRefresh(context)
    }
    LaunchedEffect(locationPermission.status.isGranted) {
        if (!locationPermission.status.isGranted) locationPermission.launchPermissionRequest()
    }
    LaunchedEffect(Unit) { delay(100); visible = true }

    // ── Inline location picker dialog ─────────────────────────────────────
    if (showLocationDialog) {
        var locQuery    by remember { mutableStateOf("") }
        var locSelected by remember { mutableStateOf("") }
        val locFm = LocalFocusManager.current
        val locCities = remember {
            listOf("Dubai","Abu Dhabi","Sharjah","Ajman","Al Ain",
                "Riyadh","Jeddah","Mecca","Medina","London","Manchester","Birmingham",
                "New York","Los Angeles","Chicago","Toronto","Cairo","Alexandria",
                "Istanbul","Kuala Lumpur","Jakarta","Karachi","Lahore","Islamabad",
                "Dhaka","Mumbai","Delhi","Doha","Kuwait City","Muscat","Manama",
                "Amman","Beirut","Baghdad","Lagos","Nairobi","Singapore","Sydney"
            ).sorted()
        }
        val locFiltered = remember(locQuery) {
            if (locQuery.length < 2) emptyList()
            else locCities.filter { it.contains(locQuery, ignoreCase = true) }.take(6)
        }
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Change Location", color = Color.White) },
            text  = {
                Column {
                    OutlinedTextField(
                        value = locQuery, onValueChange = { locQuery = it; locSelected = "" },
                        label = { Text("Search city") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White
                        )
                    )
                    if (locFiltered.isNotEmpty() && locSelected.isEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Column(Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1F35))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        ) {
                            locFiltered.forEachIndexed { idx, city ->
                                Row(Modifier.fillMaxWidth()
                                    .clickable { locSelected = city; locQuery = city; locFm.clearFocus() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(city, fontSize = 13.sp, color = Color.White)
                                }
                                if (idx < locFiltered.lastIndex)
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val city = locSelected.ifEmpty { locQuery }.trim()
                        if (city.isNotBlank()) { viewModel.fetchPrayerTimesByCity(context, city); showLocationDialog = false }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showLocationDialog = false }) { Text("Cancel") } },
            containerColor = Color(0xFF0D1020)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(
            listOf(PrimaryBlue.copy(alpha = 0.15f), Color.Transparent), Offset(0f, 0f), 600f)))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(
            listOf(Color(0xFF9333EA).copy(alpha = 0.10f), Color.Transparent),
            Offset(Float.MAX_VALUE, Float.MAX_VALUE), 800f)))

        when {
            !locationPermission.status.isGranted ->
                LocationPermissionCard(onRequestPermission = { locationPermission.launchPermissionRequest() })
            uiState.isLoading || uiState.isLoadingLocation -> LoadingContent()
            uiState.showCityInput -> CityInputContent(
                error = uiState.error,
                onSubmitCity = { city -> viewModel.fetchPrayerTimesByCity(context, city) },
                onRetry = { viewModel.fetchPrayerTimes(context) }
            )
            uiState.error != null && uiState.prayerTimes.isEmpty() -> ErrorContent(
                error = uiState.error!!, onRetry = { viewModel.fetchPrayerTimes(context) },
                onUseCity = { viewModel.showCityInput() }, context = context
            )
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)) {
                item {
                    HeroSection(
                        userName         = uiState.userName,
                        nextPrayerInfo   = uiState.nextPrayerInfo,
                        prayerTimes      = uiState.prayerTimes,
                        completedPrayers = uiState.completedPrayersToday.toSet(),
                        streakDays       = uiState.streakDays,
                        onCompleteClick  = { viewModel.logPrayerCompletion(it) },
                        onLocationClick  = { showLocationDialog = true }
                    )
                }
                item {
                    AnimatedVisibility(visible, fadeIn(tween(400, delayMillis = 100))) {
                        Text("DAILY PRAYERS", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.45f),
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                    }
                }
                itemsIndexed(uiState.prayerTimes) { index, prayerTime ->
                    val isCompleted = uiState.completedPrayersToday.contains(prayerTime.prayer)
                    val np = uiState.nextPrayerInfo
                    val isCurrent = np != null && np.isCurrentPrayer && np.prayer == prayerTime.prayer
                    AnimatedVisibility(visible,
                        fadeIn(tween(400, delayMillis = 150 + index * 70)) +
                        slideInVertically(tween(400, delayMillis = 150 + index * 70)) { 24 }) {
                        PrayerRow(prayerTime, isCompleted, isCurrent,
                            Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                            viewModel.logPrayerCompletion(prayerTime.prayer)
                        }
                    }
                }
                item {
                    AnimatedVisibility(visible, fadeIn(tween(600, delayMillis = 600))) {
                        DailyQuoteCard(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(
    userName: String,
    nextPrayerInfo: GetNextPrayerUseCase.NextPrayerInfo?,
    prayerTimes: List<PrayerTime>,
    completedPrayers: Set<Prayer>,
    streakDays: Int,
    onCompleteClick: (Prayer) -> Unit,
    onLocationClick: () -> Unit
) {
    Box(Modifier.fillMaxWidth()
        .shadow(24.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
            spotColor = PrimaryBlue.copy(alpha = 0.3f))
        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
        .background(Brush.verticalGradient(listOf(PrimaryBlue.copy(alpha = 0.18f), BackgroundDark)))
        .border(1.dp, Brush.verticalGradient(listOf(PrimaryBlue.copy(alpha = 0.3f), Color.Transparent)),
            RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
    ) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Row(Modifier.fillMaxWidth(), Alignment.CenterVertically, Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(PrimaryBlue.copy(alpha = 0.2f))
                        .border(1.dp, PrimaryBlue.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                    }
                    Row {
                        Text("Salam, ", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Text(userName.ifEmpty { "Friend" }, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
                // Location button — tap to change city
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                    .clickable { onLocationClick() },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.EditLocation, "Change location",
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            SunArcWidget(Modifier.fillMaxWidth().height(120.dp), prayerTimes, completedPrayers, nextPrayerInfo)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), Arrangement.SpaceBetween) {
                listOf("FAJR","DHUHR","ASR","MAGHRIB","ISHA").forEach { name ->
                    Text(name, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.35f))
                }
            }
            Spacer(Modifier.height(20.dp))
            StreakAndDotsCard(streakDays, prayerTimes, completedPrayers)
        }
    }
}

// ── Sun arc ───────────────────────────────────────────────────────────────────

@Composable
private fun SunArcWidget(modifier: Modifier, prayerTimes: List<PrayerTime>,
    completedPrayers: Set<Prayer>, nextPrayerInfo: GetNextPrayerUseCase.NextPrayerInfo?) {
    val progress by animateFloatAsState(getDayProgress(System.currentTimeMillis()),
        tween(1500, easing = LinearOutSlowInEasing), label = "sun")
    val pillText = when {
        nextPrayerInfo != null && nextPrayerInfo.isCurrentPrayer -> "Time for ${nextPrayerInfo.prayer.displayName}"
        nextPrayerInfo != null -> "Next: ${nextPrayerInfo.prayer.displayName}"
        else -> ""
    }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f; val cy = size.height + size.height * 0.05f; val r = size.width * 0.44f
            drawPath(Path().apply {
                addArc(Rect(cx - r, cy - r, cx + r, cy + r), 180f, 180f)
            }, Color.White.copy(alpha = 0.18f), style = Stroke(2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
            val ang = Math.toRadians(180.0 + progress * 180.0)
            val sx = cx + r * cos(ang).toFloat(); val sy = cy + r * sin(ang).toFloat()
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFBBF24).copy(alpha = 0.35f), Color.Transparent),
                Offset(sx, sy), 40f), 40f, Offset(sx, sy))
            drawCircle(Color(0xFFFBBF24), 9f, Offset(sx, sy))
        }
        if (pillText.isNotEmpty()) {
            Row(Modifier.clip(CircleShape).background(BackgroundDark.copy(alpha = 0.75f))
                .border(1.dp, PrimaryBlue.copy(alpha = 0.3f), CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(20.dp).clip(CircleShape).border(1.5.dp, PrimaryBlue.copy(alpha = 0.6f), CircleShape))
                Text(pillText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ── Streak & dots ─────────────────────────────────────────────────────────────

@Composable
private fun StreakAndDotsCard(streakDays: Int, prayerTimes: List<PrayerTime>, completedPrayers: Set<Prayer>) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.horizontalGradient(listOf(PrimaryBlue.copy(alpha = 0.22f), Color(0xFF9333EA).copy(alpha = 0.10f))))
        .border(1.dp, PrimaryBlue.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
        .padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("TODAY'S PRAYERS", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp, color = PrimaryBlue)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$streakDays", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Days", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            val prayers = prayerTimes.map { it.prayer }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrayerDotRow(prayers.take(3), completedPrayers)
                Row(Modifier.padding(end = 20.dp)) { PrayerDotRow(prayers.drop(3), completedPrayers) }
            }
        }
    }
}

@Composable
private fun PrayerDotRow(prayers: List<Prayer>, completedPrayers: Set<Prayer>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        prayers.forEach { prayer ->
            val done = completedPrayers.contains(prayer)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(32.dp).clip(CircleShape)
                    .background(if (done) PrimaryBlue else Color.White.copy(alpha = 0.07f))
                    .border(2.dp, BackgroundDark.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text(prayer.displayName.first().toString(), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (done) Color.White else Color.White.copy(alpha = 0.3f))
                }
                Spacer(Modifier.height(3.dp))
                Text(prayer.displayName.uppercase(), fontSize = 7.sp, fontWeight = FontWeight.Bold,
                    color = if (done) PrimaryBlue else Color.White.copy(alpha = 0.25f), letterSpacing = 0.5.sp)
            }
        }
    }
}

// ── Prayer row ────────────────────────────────────────────────────────────────

@Composable
private fun PrayerRow(prayerTime: PrayerTime, isCompleted: Boolean, isCurrent: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bgColor     = if (isCurrent) PrimaryBlue.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f)
    val borderColor = if (isCurrent) PrimaryBlue.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.07f)
    val (iconBg, iconTint) = when (prayerTime.prayer) {
        Prayer.FAJR    -> Color(0xFF172554).copy(alpha = 0.7f) to Color(0xFF60A5FA)
        Prayer.DHUHR   -> PrimaryBlue.copy(alpha = 0.9f)      to Color.White
        Prayer.ASR     -> Color(0xFF431407).copy(alpha = 0.7f) to Color(0xFFFB923C)
        Prayer.MAGHRIB -> Color(0xFF4C0519).copy(alpha = 0.7f) to Color(0xFFF87171)
        Prayer.ISHA    -> Color(0xFF1E1B4B).copy(alpha = 0.7f) to Color(0xFFA5B4FC)
    }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bgColor)
        .border(if (isCurrent) 2.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
        .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        Alignment.CenterVertically, Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),
                contentAlignment = Alignment.Center) {
                Icon(when (prayerTime.prayer) {
                    Prayer.FAJR -> Icons.Filled.Flare; Prayer.DHUHR -> Icons.Filled.WbSunny
                    Prayer.ASR  -> Icons.Filled.WbSunny; Prayer.MAGHRIB -> Icons.Filled.Brightness3
                    Prayer.ISHA -> Icons.Filled.NightlightRound
                }, prayerTime.prayer.displayName, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Column {
                Text(prayerTime.prayer.displayName, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold, color = if (isCurrent) PrimaryBlue else Color.White)
                Text(prayerTime.time, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
            }
        }
        when {
            isCompleted -> Canvas(Modifier.size(26.dp)) {
                drawCircle(Color(0xFF22C55E), size.minDimension / 2f)
                drawPath(Path().apply {
                    val s = size.minDimension
                    moveTo(s * 0.28f, s * 0.52f); lineTo(s * 0.44f, s * 0.68f); lineTo(s * 0.72f, s * 0.36f)
                }, Color.White, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            isCurrent -> Text("CURRENT", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = PrimaryBlue, letterSpacing = 1.sp)
            else -> Box(Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape))
        }
    }
}

private fun getDayProgress(timestamp: Long): Float {
    val c = Calendar.getInstance().apply { timeInMillis = timestamp }
    return ((c.get(Calendar.HOUR_OF_DAY) * 3600 + c.get(Calendar.MINUTE) * 60 + c.get(Calendar.SECOND)).toFloat() / 86400f).coerceIn(0f, 1f)
}

// ── Support screens ───────────────────────────────────────────────────────────

@Composable
private fun LocationPermissionCard(onRequestPermission: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
        FrostedGlassCard(Modifier.fillMaxWidth().padding(24.dp), cornerRadius = 24.dp) {
            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocationOn, null, tint = PrimaryBlue, modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(16.dp))
                Text("Location Permission Required", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Sujood needs your location to calculate accurate prayer times.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onRequestPermission, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(16.dp)) { Text("Grant Permission", fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Getting your location...", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun CityInputContent(error: String?, onSubmitCity: (String) -> Unit, onRetry: () -> Unit) {
    val allCities = remember {
        listOf("Dubai, UAE","Abu Dhabi, UAE","Sharjah, UAE","Ajman, UAE","Al Ain, UAE",
            "Riyadh, Saudi Arabia","Jeddah, Saudi Arabia","Mecca, Saudi Arabia","Medina, Saudi Arabia",
            "London, UK","Manchester, UK","Birmingham, UK","Glasgow, UK",
            "New York, USA","Los Angeles, USA","Chicago, USA","Houston, USA",
            "Toronto, Canada","Montreal, Canada","Vancouver, Canada",
            "Cairo, Egypt","Alexandria, Egypt","Istanbul, Turkey","Ankara, Turkey",
            "Kuala Lumpur, Malaysia","Jakarta, Indonesia","Karachi, Pakistan","Lahore, Pakistan",
            "Islamabad, Pakistan","Dhaka, Bangladesh","Mumbai, India","Delhi, India",
            "Paris, France","Berlin, Germany","Amsterdam, Netherlands",
            "Lagos, Nigeria","Nairobi, Kenya","Casablanca, Morocco",
            "Doha, Qatar","Kuwait City, Kuwait","Manama, Bahrain","Muscat, Oman",
            "Amman, Jordan","Beirut, Lebanon","Baghdad, Iraq","Tehran, Iran",
            "Sydney, Australia","Singapore, Singapore","Tokyo, Japan",
            "Cape Town, South Africa","Johannesburg, South Africa"
        ).sortedBy { it }
    }
    var query        by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val filtered = remember(query) {
        if (query.length < 2) emptyList()
        else allCities.filter { it.contains(query, ignoreCase = true) }.take(6)
    }
    Box(Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MyLocation, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("Where are you praying?", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Light, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Start typing your city to search", style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(value = query, onValueChange = { query = it; selectedCity = "" },
                label = { Text("Search City") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderSubtle, focusedLabelColor = PrimaryBlue, cursorColor = PrimaryBlue,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp))
            if (filtered.isNotEmpty() && selectedCity.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))) {
                    filtered.forEachIndexed { idx, city ->
                        Row(Modifier.fillMaxWidth()
                            .clickable { selectedCity = city; query = city; focusManager.clearFocus() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(city, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                        if (idx < filtered.lastIndex) HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                    }
                }
            }
            if (error != null && selectedCity.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                val city = selectedCity.ifEmpty { query }.trim()
                if (city.isNotBlank()) { focusManager.clearFocus(); onSubmitCity(city) }
            }, Modifier.fillMaxWidth().height(52.dp),
                enabled = selectedCity.isNotEmpty() || query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(26.dp)) {
                Text(if (selectedCity.isNotEmpty()) "Use $selectedCity" else "Search", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Detect my location automatically", color = PrimaryBlue) }
        }
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit, onUseCity: () -> Unit, context: Context) {
    Box(Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
        FrostedGlassCard(Modifier.fillMaxWidth().padding(24.dp), cornerRadius = 24.dp,
            borderColor = Color(0xFFFBBF24).copy(alpha = 0.4f)) {
            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocationOn, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Couldn't access your location", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Please enable location permission and GPS, or enter your city manually.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onUseCity, Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)) { Text("Enter City", color = PrimaryBlue) }
                    Button(onRetry, Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)) { Text("Retry") }
                }
            }
        }
    }
}
''')

print("\n✅ fix_v4b.py complete — full file replacements, no patching.")
print("Run: cd Sujood && ./gradlew assembleDebug")
