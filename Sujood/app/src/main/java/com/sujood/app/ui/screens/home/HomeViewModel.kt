package com.sujood.app.ui.screens.home

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
