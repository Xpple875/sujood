package com.sujood.app.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        private const val LOCATION_TIMEOUT_MS = 8000L
        private const val API_TIMEOUT_MS      = 10000L

        // Hardcoded coordinates for popular cities shown in the city picker.
        // Using coordinates + /timings is MUCH more reliable than /timingsByCity.
        private val CITY_COORDS = mapOf(
            "dubai"          to Pair(25.2048,  55.2708),
            "abu dhabi"      to Pair(24.4539,  54.3773),
            "sharjah"        to Pair(25.3463,  55.4209),
            "ajman"          to Pair(25.4052,  55.5136),
            "al ain"         to Pair(24.2075,  55.7447),
            "riyadh"         to Pair(24.7136,  46.6753),
            "jeddah"         to Pair(21.4858,  39.1925),
            "mecca"          to Pair(21.3891,  39.8579),
            "medina"         to Pair(24.5247,  39.5692),
            "london"         to Pair(51.5074,  -0.1278),
            "manchester"     to Pair(53.4808,  -2.2426),
            "birmingham"     to Pair(52.4862,  -1.8904),
            "glasgow"        to Pair(55.8642,  -4.2518),
            "new york"       to Pair(40.7128, -74.0060),
            "los angeles"    to Pair(34.0522,-118.2437),
            "chicago"        to Pair(41.8781, -87.6298),
            "houston"        to Pair(29.7604, -95.3698),
            "toronto"        to Pair(43.6532, -79.3832),
            "montreal"       to Pair(45.5017, -73.5673),
            "vancouver"      to Pair(49.2827,-123.1207),
            "cairo"          to Pair(30.0444,  31.2357),
            "alexandria"     to Pair(31.2001,  29.9187),
            "istanbul"       to Pair(41.0082,  28.9784),
            "ankara"         to Pair(39.9334,  32.8597),
            "kuala lumpur"   to Pair( 3.1390, 101.6869),
            "jakarta"        to Pair(-6.2088, 106.8456),
            "karachi"        to Pair(24.8607,  67.0011),
            "lahore"         to Pair(31.5497,  74.3436),
            "islamabad"      to Pair(33.7294,  73.0931),
            "dhaka"          to Pair(23.8103,  90.4125),
            "mumbai"         to Pair(19.0760,  72.8777),
            "delhi"          to Pair(28.7041,  77.1025),
            "paris"          to Pair(48.8566,   2.3522),
            "berlin"         to Pair(52.5200,  13.4050),
            "amsterdam"      to Pair(52.3676,   4.9041),
            "lagos"          to Pair( 6.5244,   3.3792),
            "nairobi"        to Pair(-1.2921,  36.8219),
            "casablanca"     to Pair(33.5731,  -7.5898),
            "doha"           to Pair(25.2854,  51.5310),
            "kuwait city"    to Pair(29.3759,  47.9774),
            "manama"         to Pair(26.2235,  50.5876),
            "muscat"         to Pair(23.5880,  58.3829),
            "amman"          to Pair(31.9454,  35.9284),
            "beirut"         to Pair(33.8938,  35.5018),
            "baghdad"        to Pair(33.3152,  44.3661),
            "tehran"         to Pair(35.6892,  51.3890),
            "sydney"         to Pair(-33.8688, 151.2093),
            "singapore"      to Pair( 1.3521, 103.8198),
            "tokyo"          to Pair(35.6762, 139.6503),
            "cape town"      to Pair(-33.9249,  18.4241),
            "johannesburg"   to Pair(-26.2041,  28.0473)
        )
    }

    init {
        loadUserData()
        startTimeUpdates()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Called whenever Home tab becomes visible.
     * Strategy:
     *   1. If we already have times loaded this session → skip (no re-fetch).
     *   2. Try today's cached times → show instantly (works offline).
     *   3. If online → refresh from network in background and update silently.
     *   4. If offline and no cache → show error with offline message.
     */
    fun initializeAndRefresh(context: Context, forceRefresh: Boolean = false) {
        cachedContext = context.applicationContext

        viewModelScope.launch {
            val settings = userPreferences.userSettings.first()
            val today    = todayKey()
            val online   = isOnline(context)

            // Step 1: Try today's cache immediately (unless forced to refresh)
            if (!forceRefresh) {
                val cache = userPreferences.getCachedPrayerTimesForToday(today)
                if (cache != null) {
                    Log.d(TAG, "[DIAG] Using today's cache: $cache")
                    val cached = buildPrayerTimesFromCache(cache, today)
                    handlePrayerTimesSuccessNoCache(cached)
                    if (!online) return@launch
                    refreshInBackground(settings, context)
                    return@launch
                } else {
                    Log.d(TAG, "[DIAG] No cache for today ($today), will fetch fresh")
                }
            } else {
                Log.d(TAG, "[DIAG] forceRefresh=true, skipping cache")
            }

            // Step 2: No cache. Must go online.
            if (!online) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No internet connection. Prayer times will load when you're back online."
                )
                return@launch
            }

            // Step 3: Online, no cache — normal fetch
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when {
                settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 ->
                    fetchPrayerTimesByLocation(settings.savedLatitude, settings.savedLongitude, settings.prayerTune)
                settings.savedCity.isNotEmpty() -> {
                    val bareCity = settings.savedCity.substringBefore(",").trim().lowercase()
                    val coords   = CITY_COORDS[bareCity]
                    
                    // Dubai Check pre-fetch — force coordinate-based fetch
                    // (Aladhan /timingsByCity ignores the tune parameter)
                    if (bareCity == "dubai") {
                        val dubaiCoords = CITY_COORDS["dubai"]!!
                        fetchPrayerTimesByLocation(dubaiCoords.first, dubaiCoords.second)
                    } else if (coords != null) {
                        fetchPrayerTimesByLocation(coords.first, coords.second)
                    } else {
                        repository.getPrayerTimesByCity(
                            settings.savedCity.substringBefore(",").trim(),
                            settings.calculationMethod, settings.madhab, settings.prayerTune
                        ).fold(
                            onSuccess = { handlePrayerTimesSuccess(it) },
                            onFailure = { _uiState.value = _uiState.value.copy(isLoading = false,
                                error = "Could not load times for ${settings.savedCity}") }
                        )
                    }
                }
                else -> fetchPrayerTimes(context)
            }
        }
    }

    /** Silent background refresh — updates times without showing loading spinner. */
    private suspend fun refreshInBackground(settings: UserSettings, context: Context) {
        try {
            when {
                settings.savedLatitude != 0.0 && settings.savedLongitude != 0.0 -> {
                    val isDubaiGps = settings.savedLatitude in 24.8..25.4 && settings.savedLongitude in 54.8..55.6
                    if (isDubaiGps) {
                        // Use hardcoded Dubai coords for consistency
                        val dc = CITY_COORDS["dubai"]!!
                        val method = com.sujood.app.domain.model.CalculationMethod.DUBAI
                        val tune = "0,-1,0,0,2,0,0,0,0"
                        val result = withTimeout(API_TIMEOUT_MS) {
                            repository.getPrayerTimes(dc.first, dc.second, method, settings.madhab, tune)
                        }
                        result.onSuccess {
                            userPreferences.saveCalculationMethod(method)
                            userPreferences.savePrayerTune(tune)
                            handlePrayerTimesSuccess(applyDubaiMaghribFix(it))
                        }
                    } else {
                        val result = withTimeout(API_TIMEOUT_MS) {
                            repository.getPrayerTimes(settings.savedLatitude, settings.savedLongitude,
                                settings.calculationMethod, settings.madhab, settings.prayerTune)
                        }
                        result.onSuccess { handlePrayerTimesSuccess(it) }
                    }
                }
                settings.savedCity.isNotEmpty() -> {
                    val bareCity = settings.savedCity.substringBefore(",").trim().lowercase()
                    val coords   = CITY_COORDS[bareCity]
                    if (bareCity == "dubai") {
                        val dc = CITY_COORDS["dubai"]!!
                        val method = com.sujood.app.domain.model.CalculationMethod.DUBAI
                        val tune = "0,-1,0,0,2,0,0,0,0"
                        val result = withTimeout(API_TIMEOUT_MS) {
                            repository.getPrayerTimes(dc.first, dc.second, method, settings.madhab, tune)
                        }
                        result.onSuccess {
                            userPreferences.saveCalculationMethod(method)
                            userPreferences.savePrayerTune(tune)
                            handlePrayerTimesSuccess(applyDubaiMaghribFix(it))
                        }
                    } else if (coords != null) {
                        val result = withTimeout(API_TIMEOUT_MS) {
                            repository.getPrayerTimes(coords.first, coords.second,
                                settings.calculationMethod, settings.madhab, settings.prayerTune)
                        }
                        result.onSuccess { handlePrayerTimesSuccess(it) }
                    }
                }
                else -> {
                    fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    val loc = getLocationWithTimeout(context)
                    if (loc != null) {
                        val isDubaiGps = loc.latitude in 24.8..25.4 && loc.longitude in 54.8..55.6
                        if (isDubaiGps) {
                            val dc = CITY_COORDS["dubai"]!!
                            val method = com.sujood.app.domain.model.CalculationMethod.DUBAI
                            val tune = "0,-1,0,0,2,0,0,0,0"
                            val result = withTimeout(API_TIMEOUT_MS) {
                                repository.getPrayerTimes(dc.first, dc.second, method, settings.madhab, tune)
                            }
                            result.onSuccess {
                                userPreferences.saveCalculationMethod(method)
                                userPreferences.savePrayerTune(tune)
                                handlePrayerTimesSuccess(applyDubaiMaghribFix(it))
                            }
                        } else {
                            val result = withTimeout(API_TIMEOUT_MS) {
                                repository.getPrayerTimes(loc.latitude, loc.longitude,
                                    settings.calculationMethod, settings.madhab, settings.prayerTune)
                            }
                            result.onSuccess { handlePrayerTimesSuccess(it) }
                        }
                    }
                }
            }
        } catch (_: Exception) { /* silent — cache is already shown */ }
    }

    private fun loadUserData() {
        viewModelScope.launch {
            userPreferences.userSettings.collect { settings ->
                val oldSettings = _uiState.value.settings
                val calculationChanged = oldSettings.calculationMethod != settings.calculationMethod
                val madhabChanged = oldSettings.madhab != settings.madhab
                val locationChanged = oldSettings.savedCity != settings.savedCity ||
                                     oldSettings.savedLatitude != settings.savedLatitude ||
                                     oldSettings.savedLongitude != settings.savedLongitude
                
                _uiState.value = _uiState.value.copy(userName = settings.name, settings = settings)
                
                // If critical settings changed, refresh prayer times
                if (calculationChanged || madhabChanged || locationChanged) {
                    if (cachedContext != null) {
                        Log.d(TAG, "Settings changed, forcing refresh of prayer times")
                        initializeAndRefresh(cachedContext!!, forceRefresh = true)
                    }
                }
            }
        }
    }

    private fun startTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val pt = _uiState.value.prayerTimes
                if (pt.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        currentTimeMillis = System.currentTimeMillis(),
                        nextPrayerInfo    = getNextPrayerUseCase(pt)
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
            val settings = userPreferences.userSettings.first()
            try {
                val location = getLocationWithTimeout(context)
                if (location != null) {
                    // Save GPS coords for future offline use
                    viewModelScope.launch {
                        userPreferences.saveLocationSettings(true, "", "",
                            location.latitude, location.longitude)
                    }
                    fetchPrayerTimesByLocation(location.latitude, location.longitude, settings.prayerTune)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoadingLocation = false,
                        showCityInput = true,
                        error = "Could not get location. Please enter your city manually.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching location", e)
                _uiState.value = _uiState.value.copy(isLoading = false, isLoadingLocation = false,
                    showCityInput = true,
                    error = "Could not get location. Please enter your city manually.")
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
            val key      = bareCity.lowercase()

            // Try hardcoded coords first — fastest and most reliable
            val coords = CITY_COORDS[key]
            if (coords != null) {
                Log.d(TAG, "Using hardcoded coords for $bareCity: ${coords.first}, ${coords.second}")
                var targetMethod = settings.calculationMethod
                var targetTune   = settings.prayerTune
                
                // Dubai Specific Fix — use coordinate-based API (city API ignores tune)
                if (key == "dubai") {
                    Log.d(TAG, "Dubai detected, auto-tuning methods via coordinate API")
                    targetMethod = com.sujood.app.domain.model.CalculationMethod.DUBAI
                    targetTune   = "0,-1,0,0,2,0,0,0,0" // Fajr -1, Asr +2 (Maghrib fixed in post-processing)
                }

                viewModelScope.launch {
                    userPreferences.saveLocationSettings(false, cityName, "",
                        coords.first, coords.second)
                    if (key == "dubai") {
                        userPreferences.saveCalculationMethod(targetMethod)
                        userPreferences.savePrayerTune(targetTune)
                    }
                }
                fetchPrayerTimesByLocation(coords.first, coords.second, targetTune)
                return@launch
            }

            // Fallback: try Aladhan /timingsByCity
            try {
                repository.getPrayerTimesByCity(bareCity, settings.calculationMethod, settings.madhab, settings.prayerTune)
                    .fold(
                        onSuccess = { prayerTimes ->
                            viewModelScope.launch {
                                userPreferences.saveLocationSettings(false, cityName, "", 0.0, 0.0)
                            }
                            handlePrayerTimesSuccess(prayerTimes)
                        },
                        onFailure = {
                            // Last resort: citySearch for coordinates
                            try {
                                val cityData = repository.searchCity(bareCity).data.firstOrNull()
                                if (cityData != null) {
                                    viewModelScope.launch {
                                        userPreferences.saveLocationSettings(false, cityData.name,
                                            cityData.country, cityData.latitude, cityData.longitude)
                                    }
                                    fetchPrayerTimesByLocation(cityData.latitude, cityData.longitude, settings.prayerTune)
                                } else {
                                    _uiState.value = _uiState.value.copy(isLoading = false,
                                        error = "Could not find \"$cityName\". Try another city name.")
                                }
                            } catch (_: Exception) {
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
        } catch (e: Exception) { Log.e(TAG, "Location timeout", e); null }
    }

    private suspend fun fetchPrayerTimesByLocation(latitude: Double, longitude: Double, tune: String? = null) {
        _uiState.value = _uiState.value.copy(isLoadingLocation = false)
        try {
            val settings = userPreferences.userSettings.first()
            var targetMethod = settings.calculationMethod
            var targetTune   = tune ?: settings.prayerTune
            var isDubai = false
            
            // GPS-based Dubai detection (approximate bounding box for Dubai)
            val isDubaiGps = latitude in 24.8..25.4 && longitude in 54.8..55.6
            if (isDubaiGps) {
                Log.d(TAG, "GPS location in Dubai detected, using coordinate API with tune")
                isDubai = true
                targetMethod = com.sujood.app.domain.model.CalculationMethod.DUBAI
                // Tune: Fajr -1, Asr +2 (Maghrib can't be tuned via API, fixed in post-processing)
                targetTune   = "0,-1,0,0,2,0,0,0,0"
                viewModelScope.launch {
                    userPreferences.saveCalculationMethod(targetMethod)
                    userPreferences.savePrayerTune(targetTune)
                }
            }

            // Always use coordinate-based /timings endpoint (city endpoint ignores tune)
            val fetchLat = if (isDubai) CITY_COORDS["dubai"]!!.first else latitude
            val fetchLng = if (isDubai) CITY_COORDS["dubai"]!!.second else longitude

            withTimeout(API_TIMEOUT_MS) {
                repository.getPrayerTimes(fetchLat, fetchLng, targetMethod, settings.madhab, targetTune)
            }.fold(
                onSuccess  = { times ->
                    val finalTimes = if (isDubai) applyDubaiMaghribFix(times) else times
                    handlePrayerTimesSuccess(finalTimes)
                },
                onFailure  = { e ->
                    Log.e(TAG, "API error", e)
                    _uiState.value = _uiState.value.copy(isLoading = false,
                        error = "Failed to load prayer times. Please check your internet connection.")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Timeout", e)
            _uiState.value = _uiState.value.copy(isLoading = false,
                error = "Connection timed out. Please check your internet and try again.")
        }
    }

    /**
     * Post-processing fix for Dubai Maghrib time.
     * The Aladhan API ignores the tune parameter for Maghrib/Sunset fields.
     * Method 16 already adds +3 min to Maghrib, but Awqaf says it should be +4.
     * This function adds +1 minute to the Maghrib time string and timestamp.
     */
    private fun applyDubaiMaghribFix(times: List<PrayerTime>): List<PrayerTime> {
        return times.map { pt ->
            if (pt.prayer == Prayer.MAGHRIB) {
                try {
                    val inputFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
                    val date = inputFmt.parse(pt.time)
                    if (date != null) {
                        val cal = java.util.Calendar.getInstance()
                        cal.time = date
                        cal.add(java.util.Calendar.MINUTE, 1)
                        val newTime = inputFmt.format(cal.time)
                        PrayerTime(pt.prayer, newTime, pt.timestamp + 60_000L)
                    } else pt
                } catch (_: Exception) { pt }
            } else pt
        }
    }

    /** Build PrayerTime list from cache strings — times are local so no TZ conversion needed. */
    private fun buildPrayerTimesFromCache(cache: Map<String, String>, dateKey: String): List<PrayerTime> {
        val fmt24 = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getDefault()
        }
        val fmt12 = java.text.SimpleDateFormat("yyyy-MM-dd h:mm a", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getDefault()
        }
        fun ts(time: String) = try { 
            if (time.contains("AM") || time.contains("PM")) {
                fmt12.parse("$dateKey $time")?.time ?: System.currentTimeMillis()
            } else {
                fmt24.parse("$dateKey $time")?.time ?: System.currentTimeMillis()
            }
        } catch (_: Exception) { System.currentTimeMillis() }
        return listOf(
            PrayerTime(Prayer.FAJR,    cache["FAJR"]    ?: "", ts(cache["FAJR"]    ?: "00:00")),
            PrayerTime(Prayer.DHUHR,   cache["DHUHR"]   ?: "", ts(cache["DHUHR"]   ?: "00:00")),
            PrayerTime(Prayer.ASR,     cache["ASR"]     ?: "", ts(cache["ASR"]     ?: "00:00")),
            PrayerTime(Prayer.MAGHRIB, cache["MAGHRIB"] ?: "", ts(cache["MAGHRIB"] ?: "00:00")),
            PrayerTime(Prayer.ISHA,    cache["ISHA"]    ?: "", ts(cache["ISHA"]    ?: "00:00"))
        )
    }

    private suspend fun handlePrayerTimesSuccess(prayerTimes: List<PrayerTime>) {
        // Cache today's times for offline use
        Log.d(TAG, "[DIAG] handlePrayerTimesSuccess called with ${prayerTimes.size} prayer times:")
        prayerTimes.forEach { Log.d(TAG, "[DIAG]   ${it.prayer.name} = ${it.time}") }
        if (prayerTimes.size == 5) {
            userPreferences.saveCachedPrayerTimes(
                fajr    = prayerTimes[0].time, dhuhr   = prayerTimes[1].time,
                asr     = prayerTimes[2].time, maghrib = prayerTimes[3].time,
                isha    = prayerTimes[4].time, dateKey = todayKey()
            )
        }
        handlePrayerTimesSuccessNoCache(prayerTimes)
    }

    /** Same as handlePrayerTimesSuccess but skips writing cache (used when loading FROM cache). */
    private suspend fun handlePrayerTimesSuccessNoCache(prayerTimes: List<PrayerTime>) {
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
            Prayer.FAJR    -> settings.fajrNotificationEnabled
            Prayer.DHUHR   -> settings.dhuhrNotificationEnabled
            Prayer.ASR     -> settings.asrNotificationEnabled
            Prayer.MAGHRIB -> settings.maghribNotificationEnabled
            Prayer.ISHA    -> settings.ishaNotificationEnabled
        }}.toBooleanArray()
        val lock = Prayer.entries.map { p ->
            if (!settings.prayerLockEnabled) false
            else when (p) {
                Prayer.FAJR    -> settings.fajrLockEnabled
                Prayer.DHUHR   -> settings.dhuhrLockEnabled
                Prayer.ASR     -> settings.asrLockEnabled
                Prayer.MAGHRIB -> settings.maghribLockEnabled
                Prayer.ISHA    -> settings.ishaLockEnabled
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
