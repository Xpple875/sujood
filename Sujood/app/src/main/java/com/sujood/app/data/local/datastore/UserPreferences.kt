package com.sujood.app.data.local.datastore

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
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sujood_preferences")

class UserPreferences(private val context: Context) {

    private object PreferencesKeys {
        val NAME = stringPreferencesKey("user_name")
        val CALCULATION_METHOD = intPreferencesKey("calculation_method")
        val MADHAB = intPreferencesKey("madhab")
        val GRACE_PERIOD = intPreferencesKey("grace_period_minutes")
        
        val FAJR_NOTIFICATION = booleanPreferencesKey("fajr_notification_enabled")
        val DHUHR_NOTIFICATION = booleanPreferencesKey("dhuhr_notification_enabled")
        val ASR_NOTIFICATION = booleanPreferencesKey("asr_notification_enabled")
        val MAGHRIB_NOTIFICATION = booleanPreferencesKey("maghrib_notification_enabled")
        val ISHA_NOTIFICATION = booleanPreferencesKey("isha_notification_enabled")
        
        val FAJR_LOCK = booleanPreferencesKey("fajr_lock_enabled")
        val DHUHR_LOCK = booleanPreferencesKey("dhuhr_lock_enabled")
        val ASR_LOCK = booleanPreferencesKey("asr_lock_enabled")
        val MAGHRIB_LOCK = booleanPreferencesKey("maghrib_lock_enabled")
        val ISHA_LOCK = booleanPreferencesKey("isha_lock_enabled")
        
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        
        // Location settings
        val USE_GPS_LOCATION = booleanPreferencesKey("use_gps_location")
        val SAVED_CITY = stringPreferencesKey("saved_city")
        val SAVED_COUNTRY = stringPreferencesKey("saved_country")
        val SAVED_LATITUDE = doublePreferencesKey("saved_latitude")
        val SAVED_LONGITUDE = doublePreferencesKey("saved_longitude")
        
        // Theme & features
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val DHIKR_REMINDER_ENABLED = booleanPreferencesKey("dhikr_reminder_enabled")
        val DHIKR_REMINDER_INTERVAL = intPreferencesKey("dhikr_reminder_interval")
        // Prayer Lock settings
        val LOCK_MODE = stringPreferencesKey("lock_mode")
        val LOCK_TRIGGER_MINUTES = intPreferencesKey("lock_trigger_minutes")
        val LOCK_DURATION_MINUTES = intPreferencesKey("lock_duration_minutes")
        val MIN_LOCK_DURATION_MINUTES = intPreferencesKey("min_lock_duration_minutes")
        val LOCKED_APPS_PACKAGE_NAMES = stringPreferencesKey("locked_apps_package_names")
        val ADHAN_ENABLED = booleanPreferencesKey("adhan_enabled")
        val ADHAN_SOUND_NAME = stringPreferencesKey("adhan_sound_name")
        val ADHAN_SOUND_URL  = stringPreferencesKey("adhan_sound_url")
        val ADHAN_VOLUME     = floatPreferencesKey("adhan_volume")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val PRAYER_LOCK_ENABLED = booleanPreferencesKey("prayer_lock_enabled")
        val OVERLAY_QUOTE = stringPreferencesKey("overlay_quote")
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            name = preferences[PreferencesKeys.NAME] ?: "",
            calculationMethod = CalculationMethod.entries.getOrElse(preferences[PreferencesKeys.CALCULATION_METHOD] ?: 0) { CalculationMethod.MWL },
            madhab = Madhab.entries.getOrElse(preferences[PreferencesKeys.MADHAB] ?: 0) { Madhab.SHAFI },
            gracePeriodMinutes = preferences[PreferencesKeys.GRACE_PERIOD] ?: 0,
            fajrNotificationEnabled = preferences[PreferencesKeys.FAJR_NOTIFICATION] ?: true,
            dhuhrNotificationEnabled = preferences[PreferencesKeys.DHUHR_NOTIFICATION] ?: true,
            asrNotificationEnabled = preferences[PreferencesKeys.ASR_NOTIFICATION] ?: true,
            maghribNotificationEnabled = preferences[PreferencesKeys.MAGHRIB_NOTIFICATION] ?: true,
            ishaNotificationEnabled = preferences[PreferencesKeys.ISHA_NOTIFICATION] ?: true,
            fajrLockEnabled = preferences[PreferencesKeys.FAJR_LOCK] ?: true,
            dhuhrLockEnabled = preferences[PreferencesKeys.DHUHR_LOCK] ?: true,
            asrLockEnabled = preferences[PreferencesKeys.ASR_LOCK] ?: true,
            maghribLockEnabled = preferences[PreferencesKeys.MAGHRIB_LOCK] ?: true,
            ishaLockEnabled = preferences[PreferencesKeys.ISHA_LOCK] ?: true,
            hasCompletedOnboarding = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
            useGpsLocation = preferences[PreferencesKeys.USE_GPS_LOCATION] ?: true,
            savedCity = preferences[PreferencesKeys.SAVED_CITY] ?: "",
            savedCountry = preferences[PreferencesKeys.SAVED_COUNTRY] ?: "",
            savedLatitude = preferences[PreferencesKeys.SAVED_LATITUDE] ?: 0.0,
            savedLongitude = preferences[PreferencesKeys.SAVED_LONGITUDE] ?: 0.0,
            isDarkMode = preferences[PreferencesKeys.IS_DARK_MODE] ?: true,
            dhikrReminderEnabled = preferences[PreferencesKeys.DHIKR_REMINDER_ENABLED] ?: false,
            dhikrReminderIntervalHours = preferences[PreferencesKeys.DHIKR_REMINDER_INTERVAL] ?: 2,
            lockMode = try { LockMode.valueOf(preferences[PreferencesKeys.LOCK_MODE] ?: LockMode.WHOLE_PHONE.name) } catch (e: Exception) { LockMode.WHOLE_PHONE },
            lockTriggerMinutes = preferences[PreferencesKeys.LOCK_TRIGGER_MINUTES] ?: 0,
            lockDurationMinutes = preferences[PreferencesKeys.LOCK_DURATION_MINUTES] ?: 10,
            minLockDurationMinutes = preferences[PreferencesKeys.MIN_LOCK_DURATION_MINUTES] ?: 5,
            lockedAppsPackageNames = preferences[PreferencesKeys.LOCKED_APPS_PACKAGE_NAMES] ?: "",
            adhanEnabled = preferences[PreferencesKeys.ADHAN_ENABLED] ?: true,
            adhanSoundName = preferences[PreferencesKeys.ADHAN_SOUND_NAME] ?: "",
            adhanSoundUrl  = preferences[PreferencesKeys.ADHAN_SOUND_URL]  ?: "",
            adhanVolume    = preferences[PreferencesKeys.ADHAN_VOLUME]    ?: 0.5f,
            vibrationEnabled = preferences[PreferencesKeys.VIBRATION_ENABLED] ?: true,
            prayerLockEnabled = preferences[PreferencesKeys.PRAYER_LOCK_ENABLED] ?: true,
            overlayQuote = preferences[PreferencesKeys.OVERLAY_QUOTE] ?: ""
        )
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.NAME] = name }
    }

    suspend fun saveCalculationMethod(method: CalculationMethod) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.CALCULATION_METHOD] = method.code }
    }

    suspend fun saveMadhab(madhab: Madhab) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.MADHAB] = madhab.code }
    }

    suspend fun saveGracePeriod(minutes: Int) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.GRACE_PERIOD] = minutes }
    }

    suspend fun saveNotificationEnabled(prayer: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            when (prayer.uppercase()) {
                "FAJR" -> preferences[PreferencesKeys.FAJR_NOTIFICATION] = enabled
                "DHUHR" -> preferences[PreferencesKeys.DHUHR_NOTIFICATION] = enabled
                "ASR" -> preferences[PreferencesKeys.ASR_NOTIFICATION] = enabled
                "MAGHRIB" -> preferences[PreferencesKeys.MAGHRIB_NOTIFICATION] = enabled
                "ISHA" -> preferences[PreferencesKeys.ISHA_NOTIFICATION] = enabled
            }
        }
    }

    suspend fun saveLockEnabled(prayer: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            when (prayer.uppercase()) {
                "FAJR" -> preferences[PreferencesKeys.FAJR_LOCK] = enabled
                "DHUHR" -> preferences[PreferencesKeys.DHUHR_LOCK] = enabled
                "ASR" -> preferences[PreferencesKeys.ASR_LOCK] = enabled
                "MAGHRIB" -> preferences[PreferencesKeys.MAGHRIB_LOCK] = enabled
                "ISHA" -> preferences[PreferencesKeys.ISHA_LOCK] = enabled
            }
        }
    }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.ONBOARDING_COMPLETED] = true }
    }

    suspend fun saveLocationSettings(useGps: Boolean, city: String, country: String, latitude: Double, longitude: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_GPS_LOCATION] = useGps
            preferences[PreferencesKeys.SAVED_CITY] = city
            preferences[PreferencesKeys.SAVED_COUNTRY] = country
            preferences[PreferencesKeys.SAVED_LATITUDE] = latitude
            preferences[PreferencesKeys.SAVED_LONGITUDE] = longitude
        }
    }

    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.IS_DARK_MODE] = isDark }
    }

    suspend fun setDhikrReminder(enabled: Boolean, intervalHours: Int = 2) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DHIKR_REMINDER_ENABLED] = enabled
            preferences[PreferencesKeys.DHIKR_REMINDER_INTERVAL] = intervalHours
        }
    }

    suspend fun saveLockSettings(lockMode: LockMode, triggerMinutes: Int, durationMinutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCK_MODE] = lockMode.name
            preferences[PreferencesKeys.LOCK_TRIGGER_MINUTES] = triggerMinutes
            preferences[PreferencesKeys.LOCK_DURATION_MINUTES] = durationMinutes
        }
    }

    suspend fun saveLockBehavior(minDuration: Int, lockedApps: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MIN_LOCK_DURATION_MINUTES] = minDuration
            preferences[PreferencesKeys.LOCKED_APPS_PACKAGE_NAMES] = lockedApps
        }
    }

    suspend fun savePrayerLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRAYER_LOCK_ENABLED] = enabled
        }
    }

    suspend fun saveOverlayQuote(quote: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.OVERLAY_QUOTE] = quote
        }
    }

    suspend fun saveAdhanSound(name: String, url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ADHAN_SOUND_NAME] = name
            preferences[PreferencesKeys.ADHAN_SOUND_URL]  = url
        }
    }

    suspend fun saveAudioSettings(adhanEnabled: Boolean, vibrationEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ADHAN_ENABLED] = adhanEnabled
            preferences[PreferencesKeys.VIBRATION_ENABLED] = vibrationEnabled
        }
    }

    suspend fun saveAdhanVolume(volume: Float) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.ADHAN_VOLUME] = volume }
    }

    val userName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NAME] ?: ""
    }
}
