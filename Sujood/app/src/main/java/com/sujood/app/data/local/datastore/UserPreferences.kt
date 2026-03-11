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
        val APP_OPEN_COUNT          = intPreferencesKey("app_open_count")
        val SKIPPED_AUTH            = booleanPreferencesKey("skipped_auth")
        val IS_PREMIUM              = booleanPreferencesKey("is_premium")
        val PURCHASE_TOKEN          = stringPreferencesKey("purchase_token")

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
        val PRAYER_TUNE             = stringPreferencesKey("prayer_tune")
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        // Restore calculation method by code value, not by list index
        val savedMethodCode = prefs[PreferencesKeys.CALCULATION_METHOD] ?: CalculationMethod.MWL.code
        val calcMethod = CalculationMethod.entries.firstOrNull { it.code == savedMethodCode }
            ?: CalculationMethod.MWL

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
            overlayQuote             = prefs[PreferencesKeys.OVERLAY_QUOTE] ?: "",
            prayerTune               = prefs[PreferencesKeys.PRAYER_TUNE] ?: "0,0,0,0,0,0,0,0,0"
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

    suspend fun savePrayerTune(tune: String) {
        context.dataStore.edit { prefs -> prefs[PreferencesKeys.PRAYER_TUNE] = tune }
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

    /** True if the user explicitly chose "continue without account". */
    val skippedAuth: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SKIPPED_AUTH] ?: false
    }

    suspend fun setSkippedAuth(skipped: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SKIPPED_AUTH] = skipped }
    }

    /** Whether the user has purchased premium. */
    val isPremium: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.IS_PREMIUM] ?: false
    }

    suspend fun setPremium(isPremium: Boolean, purchaseToken: String = "") {
        context.dataStore.edit {
            it[PreferencesKeys.IS_PREMIUM]      = isPremium
            it[PreferencesKeys.PURCHASE_TOKEN]  = purchaseToken
        }
    }

    suspend fun getPurchaseToken(): String {
        return context.dataStore.data.map { it[PreferencesKeys.PURCHASE_TOKEN] ?: "" }.first()
    }

    /** Increments the app open counter and returns the new count. */
    suspend fun incrementAndGetAppOpenCount(): Int {
        var newCount = 0
        context.dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.APP_OPEN_COUNT] ?: 0
            newCount = current + 1
            prefs[PreferencesKeys.APP_OPEN_COUNT] = newCount
        }
        return newCount
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
