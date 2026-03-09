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
    // Cached context for alarm scheduling (set on first location/prayer fetch)
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

    fun fetchPrayerTimes(context: Context) {
        cachedContext = context.applicationContext
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        if (!hasLocationPermission(context)) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoadingLocation = false,
                error = "Location permission required"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isLoadingLocation = true,
                error = null,
                showCityInput = false
            )

            try {
                val location = getLastKnownLocationWithTimeout(context)
                
                if (location != null) {
                    Log.d(TAG, "Got location: ${location.latitude}, ${location.longitude}")
                    // Save GPS location for future app opens
                    viewModelScope.launch {
                        userPreferences.saveLocationSettings(
                            useGps = true,
                            city = "",
                            country = "",
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    }
                    fetchPrayerTimesByLocation(location.latitude, location.longitude)
                } else {
                    Log.d(TAG, "Location fetch timed out or failed")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingLocation = false,
                        showCityInput = true,
                        error = "Location timed out. Please enter your city manually."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching location", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingLocation = false,
                    showCityInput = true,
                    error = "Could not get location. Please enter your city manually."
                )
            }
        }
    }

    fun fetchPrayerTimesByCity(context: Context, cityName: String) {
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
                                    error = "Could not find prayer times for \"$cityName\". Try another city."
                                )
                            }
                        } catch (e2: Exception) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Could not find prayer times for \"$cityName\"."
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

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocationWithTimeout(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        
        return try {
            val cancellationToken = CancellationTokenSource()
            
            val locationTask = fusedLocationClient?.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            )
            
            withTimeout(LOCATION_TIMEOUT_MS) {
                try {
                    locationTask?.await()
                } catch (e: Exception) {
                    Log.e(TAG, "Location task failed", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }

    private suspend fun fetchPrayerTimesByLocation(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(isLoadingLocation = false)
        
        try {
            val settings = userPreferences.userSettings.first()
            
            val result = withTimeout(API_TIMEOUT_MS) {
                repository.getPrayerTimes(
                    latitude = latitude,
                    longitude = longitude,
                    method = settings.calculationMethod,
                    madhab = settings.madhab
                )
            }

            result.fold(
                onSuccess = { prayerTimes ->
                    handlePrayerTimesSuccess(prayerTimes)
                },
                onFailure = { error ->
                    Log.e(TAG, "API error: ${error.message}", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load prayer times: ${error.message}"
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Timeout or error fetching prayer times", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Connection timed out. Please check your internet and try again."
            )
        }
    }

    private suspend fun handlePrayerTimesSuccess(prayerTimes: List<PrayerTime>) {
        val completedToday = repository.getCompletedPrayersToday()
        val streak = getPrayerStreakUseCase()
        val nextInfo = getNextPrayerUseCase(prayerTimes)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            prayerTimes = prayerTimes,
            completedPrayersToday = completedToday,
            streakDays = streak,
            nextPrayerInfo = nextInfo,
            error = null,
            showCityInput = false
        )

        // Schedule alarms for all prayers using latest settings
        scheduleAlarmsForPrayerTimes(prayerTimes)
    }

    private suspend fun scheduleAlarmsForPrayerTimes(prayerTimes: List<PrayerTime>) {
        if (cachedContext == null) return
        val settings = userPreferences.userSettings.first()
        val scheduler = com.sujood.app.notifications.PrayerAlarmScheduler(cachedContext!!)
        val notificationEnabled = Prayer.entries.map { prayer ->
            when (prayer) {
                Prayer.FAJR -> settings.fajrNotificationEnabled
                Prayer.DHUHR -> settings.dhuhrNotificationEnabled
                Prayer.ASR -> settings.asrNotificationEnabled
                Prayer.MAGHRIB -> settings.maghribNotificationEnabled
                Prayer.ISHA -> settings.ishaNotificationEnabled
            }
        }.toBooleanArray()
        // prayerLockEnabled is the master toggle — if it's off, no prayer triggers a lock
        val lockEnabled = Prayer.entries.map { prayer ->
            if (!settings.prayerLockEnabled) false
            else when (prayer) {
                Prayer.FAJR -> settings.fajrLockEnabled
                Prayer.DHUHR -> settings.dhuhrLockEnabled
                Prayer.ASR -> settings.asrLockEnabled
                Prayer.MAGHRIB -> settings.maghribLockEnabled
                Prayer.ISHA -> settings.ishaLockEnabled
            }
        }.toBooleanArray()
        scheduler.scheduleAllAlarms(prayerTimes, notificationEnabled, lockEnabled, settings.gracePeriodMinutes)
    }

    fun showCityInput() {
        _uiState.value = _uiState.value.copy(
            showCityInput = true,
            isLoading = false,
            isLoadingLocation = false,
            error = null
        )
    }

    fun hideCityInput() {
        _uiState.value = _uiState.value.copy(showCityInput = false)
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun logPrayerCompletion(prayer: Prayer) = togglePrayerCompletion(prayer)

    /** Tick = log completion. Tick again = undo (delete) and recalc streak. */
    fun togglePrayerCompletion(prayer: Prayer) {
        viewModelScope.launch {
            val alreadyDone = _uiState.value.completedPrayersToday.contains(prayer)
            if (alreadyDone) {
                // Untick — remove from DB
                repository.deletePrayerLog(prayer)
            } else {
                // Tick — insert via use case (handles duplicates)
                logPrayerCompletionUseCase(prayer)
            }
            val completedToday = repository.getCompletedPrayersToday()
            val streak         = getPrayerStreakUseCase()
            _uiState.value = _uiState.value.copy(
                completedPrayersToday = completedToday,
                streakDays = streak
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    class Factory(
        private val repository: PrayerTimesRepository,
        private val userPreferences: UserPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                repository = repository,
                userPreferences = userPreferences,
                logPrayerCompletionUseCase = LogPrayerCompletionUseCase(repository),
                getNextPrayerUseCase = GetNextPrayerUseCase(),
                getPrayerStreakUseCase = GetPrayerStreakUseCase(repository)
            ) as T
        }
    }
}
