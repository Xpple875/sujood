import os, re

ROOT = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(ROOT, "Sujood", "app", "src", "main", "java", "com", "sujood", "app")

def write(rel, content):
    path = os.path.join(BASE, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"✅ Written: {rel}")

# ─────────────────────────────────────────────────────────────────────────────
# 1. UserPreferences.kt
#    - Fix calculationMethod read: store/restore by code not by list index
#    - Add CACHED_PRAYER_TIMES + CACHED_DATE keys
#    - Add saveCachedPrayerTimes() / getCachedPrayerTimes() helpers
# ─────────────────────────────────────────────────────────────────────────────
write("data/local/datastore/UserPreferences.kt", r'''package com.sujood.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sujood.app.domain.model.CalculationMethod
import com.sujood.app.domain.model.LockMode
import com.sujood.app.domain.model.Madhab
import com.sujood.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sujood_preferences")

class UserPreferences(private val context: Context) {

    private object PreferencesKeys {
        val NAME                    = stringPreferencesKey("user_name")
        val CALCULATION_METHOD      = intPreferencesKey("calculation_method")
        val MADHAB                  = intPreferencesKey("madhab")
        val GRACE_PERIOD            = intPreferencesKey("grace_period_minutes")

        val FAJR_NOTIFICATION       = booleanPreferencesKey("fajr_notification_enabled")
        val DHUHR_NOTIFICATION      = booleanPreferencesKey("dhuhr_notification_enabled")
        val ASR_NOTIFICATION        = booleanPreferencesKey("asr_notification_enabled")
        val MAGHRIB_NOTIFICATION    = booleanPreferencesKey("maghrib_notification_enabled")
        val ISHA_NOTIFICATION       = booleanPreferencesKey("isha_notification_enabled")

        val FAJR_LOCK               = booleanPreferencesKey("fajr_lock_enabled")
        val DHUHR_LOCK              = booleanPreferencesKey("dhuhr_lock_enabled")
        val ASR_LOCK                = booleanPreferencesKey("asr_lock_enabled")
        val MAGHRIB_LOCK            = booleanPreferencesKey("maghrib_lock_enabled")
        val ISHA_LOCK               = booleanPreferencesKey("isha_lock_enabled")

        val ONBOARDING_COMPLETED    = booleanPreferencesKey("onboarding_completed")

        // Location
        val USE_GPS_LOCATION        = booleanPreferencesKey("use_gps_location")
        val SAVED_CITY              = stringPreferencesKey("saved_city")
        val SAVED_COUNTRY           = stringPreferencesKey("saved_country")
        val SAVED_LATITUDE          = doublePreferencesKey("saved_latitude")
        val SAVED_LONGITUDE         = doublePreferencesKey("saved_longitude")

        // Offline cache — pipe-separated "HH:mm" times + date key "yyyy-MM-dd"
        val CACHED_PRAYER_TIMES     = stringPreferencesKey("cached_prayer_times")
        val CACHED_DATE             = stringPreferencesKey("cached_date")

        // Theme & misc
        val IS_DARK_MODE            = booleanPreferencesKey("is_dark_mode")
        val DHIKR_REMINDER_ENABLED  = booleanPreferencesKey("dhikr_reminder_enabled")
        val DHIKR_REMINDER_INTERVAL = intPreferencesKey("dhikr_reminder_interval")

        // Prayer Lock
        val LOCK_MODE               = stringPreferencesKey("lock_mode")
        val LOCK_TRIGGER_MINUTES    = intPreferencesKey("lock_trigger_minutes")
        val LOCK_DURATION_MINUTES   = intPreferencesKey("lock_duration_minutes")
        val MIN_LOCK_DURATION_MINUTES = intPreferencesKey("min_lock_duration_minutes")
        val LOCKED_APPS_PACKAGE_NAMES = stringPreferencesKey("locked_apps_package_names")
        val ADHAN_ENABLED           = booleanPreferencesKey("adhan_enabled")
        val ADHAN_SOUND_NAME        = stringPreferencesKey("adhan_sound_name")
        val ADHAN_SOUND_URL         = stringPreferencesKey("adhan_sound_url")
        val ADHAN_VOLUME            = floatPreferencesKey("adhan_volume")
        val VIBRATION_ENABLED       = booleanPreferencesKey("vibration_enabled")
        val PRAYER_LOCK_ENABLED     = booleanPreferencesKey("prayer_lock_enabled")
        val OVERLAY_QUOTE           = stringPreferencesKey("overlay_quote")
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        // Restore calculation method by code value, not by list index
        val savedMethodCode = prefs[PreferencesKeys.CALCULATION_METHOD] ?: CalculationMethod.MAKKAH.code
        val calcMethod = CalculationMethod.entries.firstOrNull { it.code == savedMethodCode }
            ?: CalculationMethod.MAKKAH

        val savedMadhabCode = prefs[PreferencesKeys.MADHAB] ?: Madhab.SHAFI.code
        val madhab = Madhab.entries.firstOrNull { it.code == savedMadhabCode } ?: Madhab.SHAFI

        UserSettings(
            name                     = prefs[PreferencesKeys.NAME] ?: "",
            calculationMethod        = calcMethod,
            madhab                   = madhab,
            gracePeriodMinutes       = prefs[PreferencesKeys.GRACE_PERIOD] ?: 0,
            fajrNotificationEnabled  = prefs[PreferencesKeys.FAJR_NOTIFICATION] ?: true,
            dhuhrNotificationEnabled = prefs[PreferencesKeys.DHUHR_NOTIFICATION] ?: true,
            asrNotificationEnabled   = prefs[PreferencesKeys.ASR_NOTIFICATION] ?: true,
            maghribNotificationEnabled = prefs[PreferencesKeys.MAGHRIB_NOTIFICATION] ?: true,
            ishaNotificationEnabled  = prefs[PreferencesKeys.ISHA_NOTIFICATION] ?: true,
            fajrLockEnabled          = prefs[PreferencesKeys.FAJR_LOCK] ?: true,
            dhuhrLockEnabled         = prefs[PreferencesKeys.DHUHR_LOCK] ?: true,
            asrLockEnabled           = prefs[PreferencesKeys.ASR_LOCK] ?: true,
            maghribLockEnabled       = prefs[PreferencesKeys.MAGHRIB_LOCK] ?: true,
            ishaLockEnabled          = prefs[PreferencesKeys.ISHA_LOCK] ?: true,
            hasCompletedOnboarding   = prefs[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
            useGpsLocation           = prefs[PreferencesKeys.USE_GPS_LOCATION] ?: true,
            savedCity                = prefs[PreferencesKeys.SAVED_CITY] ?: "",
            savedCountry             = prefs[PreferencesKeys.SAVED_COUNTRY] ?: "",
            savedLatitude            = prefs[PreferencesKeys.SAVED_LATITUDE] ?: 0.0,
            savedLongitude           = prefs[PreferencesKeys.SAVED_LONGITUDE] ?: 0.0,
            isDarkMode               = prefs[PreferencesKeys.IS_DARK_MODE] ?: true,
            dhikrReminderEnabled     = prefs[PreferencesKeys.DHIKR_REMINDER_ENABLED] ?: false,
            dhikrReminderIntervalHours = prefs[PreferencesKeys.DHIKR_REMINDER_INTERVAL] ?: 2,
            lockMode = try { LockMode.valueOf(prefs[PreferencesKeys.LOCK_MODE] ?: LockMode.WHOLE_PHONE.name) }
                       catch (_: Exception) { LockMode.WHOLE_PHONE },
            lockTriggerMinutes       = prefs[PreferencesKeys.LOCK_TRIGGER_MINUTES] ?: 0,
            lockDurationMinutes      = prefs[PreferencesKeys.LOCK_DURATION_MINUTES] ?: 10,
            minLockDurationMinutes   = prefs[PreferencesKeys.MIN_LOCK_DURATION_MINUTES] ?: 5,
            lockedAppsPackageNames   = prefs[PreferencesKeys.LOCKED_APPS_PACKAGE_NAMES] ?: "",
            adhanEnabled             = prefs[PreferencesKeys.ADHAN_ENABLED] ?: true,
            adhanSoundName           = prefs[PreferencesKeys.ADHAN_SOUND_NAME] ?: "",
            adhanSoundUrl            = prefs[PreferencesKeys.ADHAN_SOUND_URL] ?: "",
            adhanVolume              = prefs[PreferencesKeys.ADHAN_VOLUME] ?: 0.5f,
            vibrationEnabled         = prefs[PreferencesKeys.VIBRATION_ENABLED] ?: true,
            prayerLockEnabled        = prefs[PreferencesKeys.PRAYER_LOCK_ENABLED] ?: true,
            overlayQuote             = prefs[PreferencesKeys.OVERLAY_QUOTE] ?: ""
        )
    }

    // ── Offline prayer times cache ──────────────────────────────────────────
    // Format: "fajr|dhuhr|asr|maghrib|isha" e.g. "05:43|12:22|15:45|18:10|19:35"

    suspend fun saveCachedPrayerTimes(fajr: String, dhuhr: String, asr: String,
                                       maghrib: String, isha: String, dateKey: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.CACHED_PRAYER_TIMES] = "$fajr|$dhuhr|$asr|$maghrib|$isha"
            prefs[PreferencesKeys.CACHED_DATE] = dateKey
        }
    }

    /** Returns map of prayer name -> "HH:mm" if cache is for today, else null. */
    suspend fun getCachedPrayerTimesForToday(todayKey: String): Map<String, String>? {
        val prefs = context.dataStore.data.first()
        val cachedDate = prefs[PreferencesKeys.CACHED_DATE] ?: return null
        if (cachedDate != todayKey) return null
        val times = prefs[PreferencesKeys.CACHED_PRAYER_TIMES] ?: return null
        val parts  = times.split("|")
        if (parts.size != 5) return null
        return mapOf("FAJR" to parts[0], "DHUHR" to parts[1],
                     "ASR"  to parts[2], "MAGHRIB" to parts[3], "ISHA" to parts[4])
    }

    // ── Other save methods (unchanged) ─────────────────────────────────────

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.NAME] = name }
    }

    suspend fun saveCalculationMethod(method: CalculationMethod) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.CALCULATION_METHOD] = method.code }
    }

    suspend fun saveMadhab(madhab: Madhab) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.MADHAB] = madhab.code }
    }

    suspend fun saveGracePeriod(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.GRACE_PERIOD] = minutes }
    }

    suspend fun saveNotificationEnabled(prayer: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            when (prayer.uppercase()) {
                "FAJR"    -> prefs[PreferencesKeys.FAJR_NOTIFICATION] = enabled
                "DHUHR"   -> prefs[PreferencesKeys.DHUHR_NOTIFICATION] = enabled
                "ASR"     -> prefs[PreferencesKeys.ASR_NOTIFICATION] = enabled
                "MAGHRIB" -> prefs[PreferencesKeys.MAGHRIB_NOTIFICATION] = enabled
                "ISHA"    -> prefs[PreferencesKeys.ISHA_NOTIFICATION] = enabled
            }
        }
    }

    suspend fun saveLockEnabled(prayer: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            when (prayer.uppercase()) {
                "FAJR"    -> prefs[PreferencesKeys.FAJR_LOCK] = enabled
                "DHUHR"   -> prefs[PreferencesKeys.DHUHR_LOCK] = enabled
                "ASR"     -> prefs[PreferencesKeys.ASR_LOCK] = enabled
                "MAGHRIB" -> prefs[PreferencesKeys.MAGHRIB_LOCK] = enabled
                "ISHA"    -> prefs[PreferencesKeys.ISHA_LOCK] = enabled
            }
        }
    }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.ONBOARDING_COMPLETED] = true }
    }

    suspend fun saveLocationSettings(useGps: Boolean, city: String, country: String,
                                      latitude: Double, longitude: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.USE_GPS_LOCATION] = useGps
            prefs[PreferencesKeys.SAVED_CITY]       = city
            prefs[PreferencesKeys.SAVED_COUNTRY]    = country
            prefs[PreferencesKeys.SAVED_LATITUDE]   = latitude
            prefs[PreferencesKeys.SAVED_LONGITUDE]  = longitude
        }
    }

    suspend fun clearAllData() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.IS_DARK_MODE] = isDark }
    }

    suspend fun setDhikrReminder(enabled: Boolean, intervalHours: Int = 2) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DHIKR_REMINDER_ENABLED] = enabled
            prefs[PreferencesKeys.DHIKR_REMINDER_INTERVAL] = intervalHours
        }
    }

    suspend fun saveLockSettings(lockMode: LockMode, triggerMinutes: Int, durationMinutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LOCK_MODE]             = lockMode.name
            prefs[PreferencesKeys.LOCK_TRIGGER_MINUTES]  = triggerMinutes
            prefs[PreferencesKeys.LOCK_DURATION_MINUTES] = durationMinutes
        }
    }

    suspend fun saveLockBehavior(minDuration: Int, lockedApps: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.MIN_LOCK_DURATION_MINUTES]  = minDuration
            prefs[PreferencesKeys.LOCKED_APPS_PACKAGE_NAMES] = lockedApps
        }
    }

    suspend fun savePrayerLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.PRAYER_LOCK_ENABLED] = enabled }
    }

    suspend fun saveOverlayQuote(quote: String) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.OVERLAY_QUOTE] = quote }
    }

    suspend fun saveAdhanSound(name: String, url: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ADHAN_SOUND_NAME] = name
            prefs[PreferencesKeys.ADHAN_SOUND_URL]  = url
        }
    }

    suspend fun saveAudioSettings(adhanEnabled: Boolean, vibrationEnabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ADHAN_ENABLED]      = adhanEnabled
            prefs[PreferencesKeys.VIBRATION_ENABLED]  = vibrationEnabled
        }
    }

    suspend fun saveAdhanVolume(volume: Float) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.ADHAN_VOLUME] = volume }
    }

    val userName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.NAME] ?: ""
    }
}
''')

# ─────────────────────────────────────────────────────────────────────────────
# 2. HomeViewModel.kt — complete replacement
#    - On launch: try to load today's cache FIRST (instant, works offline)
#    - Then attempt network refresh silently in background
#    - fetchPrayerTimes: if GPS works offline (it does), still need network for API
#      so we always try cache first, show stale data, then refresh when online
#    - City fetch: use a hardcoded lat/lon table for the listed cities
#      so we call /timings?lat=&lon= (reliable) not /timingsByCity (unreliable)
#    - handlePrayerTimesSuccess: always writes cache after a successful API call
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/home/HomeViewModel.kt", r'''package com.sujood.app.ui.screens.home

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
    fun initializeAndRefresh(context: Context) {
        cachedContext = context.applicationContext
        if (_uiState.value.prayerTimes.isNotEmpty()) return

        viewModelScope.launch {
            val settings = userPreferences.userSettings.first()
            val today    = todayKey()
            val online   = isOnline(context)

            // Step 1: Try today's cache immediately (instant, no network needed)
            val cache = userPreferences.getCachedPrayerTimesForToday(today)
            if (cache != null) {
                val cached = buildPrayerTimesFromCache(cache, today)
                handlePrayerTimesSuccessNoCache(cached)   // show cache without re-caching
                if (!online) return@launch                // offline + cache = we're done
                // Online: fall through to silently refresh in background
                refreshInBackground(settings, context)
                return@launch
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
                    fetchPrayerTimesByLocation(settings.savedLatitude, settings.savedLongitude)
                settings.savedCity.isNotEmpty() -> {
                    val bareCity = settings.savedCity.substringBefore(",").trim().lowercase()
                    val coords   = CITY_COORDS[bareCity]
                    if (coords != null) {
                        fetchPrayerTimesByLocation(coords.first, coords.second)
                    } else {
                        repository.getPrayerTimesByCity(
                            settings.savedCity.substringBefore(",").trim(),
                            settings.calculationMethod, settings.madhab
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
                    val result = withTimeout(API_TIMEOUT_MS) {
                        repository.getPrayerTimes(settings.savedLatitude, settings.savedLongitude,
                            settings.calculationMethod, settings.madhab)
                    }
                    result.onSuccess { handlePrayerTimesSuccess(it) }
                }
                settings.savedCity.isNotEmpty() -> {
                    val bareCity = settings.savedCity.substringBefore(",").trim().lowercase()
                    val coords   = CITY_COORDS[bareCity]
                    if (coords != null) {
                        val result = withTimeout(API_TIMEOUT_MS) {
                            repository.getPrayerTimes(coords.first, coords.second,
                                settings.calculationMethod, settings.madhab)
                        }
                        result.onSuccess { handlePrayerTimesSuccess(it) }
                    }
                }
                else -> {
                    // Try GPS quietly
                    fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    val loc = getLocationWithTimeout(context)
                    if (loc != null) {
                        val result = withTimeout(API_TIMEOUT_MS) {
                            repository.getPrayerTimes(loc.latitude, loc.longitude,
                                settings.calculationMethod, settings.madhab)
                        }
                        result.onSuccess { handlePrayerTimesSuccess(it) }
                    }
                }
            }
        } catch (_: Exception) { /* silent — cache is already shown */ }
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
            try {
                val location = getLocationWithTimeout(context)
                if (location != null) {
                    // Save GPS coords for future offline use
                    viewModelScope.launch {
                        userPreferences.saveLocationSettings(true, "", "",
                            location.latitude, location.longitude)
                    }
                    fetchPrayerTimesByLocation(location.latitude, location.longitude)
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
                viewModelScope.launch {
                    userPreferences.saveLocationSettings(false, cityName, "",
                        coords.first, coords.second)
                }
                fetchPrayerTimesByLocation(coords.first, coords.second)
                return@launch
            }

            // Fallback: try Aladhan /timingsByCity
            try {
                repository.getPrayerTimesByCity(bareCity, settings.calculationMethod, settings.madhab)
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
                                    fetchPrayerTimesByLocation(cityData.latitude, cityData.longitude)
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

    private suspend fun fetchPrayerTimesByLocation(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(isLoadingLocation = false)
        try {
            val settings = userPreferences.userSettings.first()
            withTimeout(API_TIMEOUT_MS) {
                repository.getPrayerTimes(latitude, longitude, settings.calculationMethod, settings.madhab)
            }.fold(
                onSuccess  = { handlePrayerTimesSuccess(it) },
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

    /** Build PrayerTime list from cache strings — times are local so no TZ conversion needed. */
    private fun buildPrayerTimesFromCache(cache: Map<String, String>, dateKey: String): List<PrayerTime> {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getDefault()
        }
        fun ts(time: String) = try { fmt.parse("$dateKey $time")?.time ?: System.currentTimeMillis() }
                               catch (_: Exception) { System.currentTimeMillis() }
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
''')

print("\n✅ fix_v5.py complete.")
print("Files changed:")
print("  UserPreferences.kt  — offline cache keys + method code fix")
print("  HomeViewModel.kt    — cache-first load, hardcoded city coords, online check")
print()
print("Run:")
print('  cd "C:\\Users\\hamza\\Desktop\\sujood (prayer lock)"')
print("  python fix_v5.py")
print("  git add .")
print('  git commit -m "fix: offline cache, city coords, calculation method restore"')
print("  git push")
print("  cd Sujood")
print("  ./gradlew assembleDebug")
