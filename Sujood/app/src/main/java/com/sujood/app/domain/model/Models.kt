package com.sujood.app.domain.model

enum class Prayer(val displayName: String, val arabicName: String) {
    FAJR("Fajr", "الفجر"),
    DHUHR("Dhuhr", "الظهر"),
    ASR("Asr", "العصر"),
    MAGHRIB("Maghrib", "المغرب"),
    ISHA("Isha", "العشاء")
}

data class PrayerTime(
    val prayer: Prayer,
    val time: String,
    val timestamp: Long
)

data class PrayerLog(
    val id: Long = 0,
    val prayer: Prayer,
    val completedAt: Long,
    val date: String,
    val isLate: Boolean = false
)

enum class CalculationMethod(val displayName: String, val code: Int) {
    MWL("Muslim World League", 0),
    ISNA("Islamic Society of North America", 1),
    EGYPTIAN("Egyptian General Authority", 2),
    MAKKAH("Umm Al-Qura University", 3),
    KARACHI("University of Islamic Sciences Karachi", 5),
    TEHRAN("Institute of Geophysics Tehran", 7)
}

enum class Madhab(val displayName: String, val code: Int) {
    SHAFI("Shafi", 0),
    HANAFI("Hanafi", 1)
}

data class CityInfo(
    val name: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double
)

data class Dhikr(
    val id: String,
    val name: String,
    val arabic: String,
    val transliteration: String,
    val meaning: String,
    val targetCount: Int = 33
)

val defaultDhikrList = listOf(
    Dhikr("subhanallah", "Subhanallah", "سُبْحَانَ ٱللَّهِ", "SubhanAllah", "Glory be to Allah", 33),
    Dhikr("alhamdulillah", "Alhamdulillah", "الْحَمْدُ لِلهِ", "AlHamdulillah", "Praise be to Allah", 33),
    Dhikr("allahuakbar", "Allahu Akbar", "ٱللَّهُ أَكْبَرُ", "Allahu Akbar", "God is the Greatest", 33),
    Dhikr("astaghfirullah", "Astaghfirullah", "أَسْتَغْفِرُ ٱللَّهَ", "Astaghfirullah", "I seek forgiveness from Allah", 33),
    Dhikr("la-ilaha", "La ilaha illallah", "لَا إِلَٰهَ إِلَّا ٱللَّهُ", "La ilaha illallah", "There is no god but Allah", 100)
)

data class DhikrLog(
    val id: Long = 0,
    val dhikrId: String,
    val count: Int,
    val completedAt: Long,
    val date: String
)

data class AyahOfTheDay(
    val id: Long = 0,
    val arabicText: String,
    val englishText: String,
    val surahName: String,
    val ayahNumber: Int,
    val date: String
)

data class SunnahTimes(
    val tahajjudStart: String,
    val tahajjudEnd: String,
    val ishraqStart: String,
    val ishraqEnd: String,
    val duhaStart: String,
    val duhaEnd: String
)

enum class LockMode(val displayName: String) {
    WHOLE_PHONE("Whole Phone"),
    APP_OVERLAY("App Overlay")
}

data class UserSettings(
    val name: String = "",
    val calculationMethod: CalculationMethod = CalculationMethod.MAKKAH,
    val madhab: Madhab = Madhab.SHAFI,
    val gracePeriodMinutes: Int = 0,
    val fajrNotificationEnabled: Boolean = true,
    val dhuhrNotificationEnabled: Boolean = true,
    val asrNotificationEnabled: Boolean = true,
    val maghribNotificationEnabled: Boolean = true,
    val ishaNotificationEnabled: Boolean = true,
    val fajrLockEnabled: Boolean = true,
    val dhuhrLockEnabled: Boolean = true,
    val asrLockEnabled: Boolean = true,
    val maghribLockEnabled: Boolean = true,
    val ishaLockEnabled: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,
    val useGpsLocation: Boolean = true,
    val savedCity: String = "",
    val savedCountry: String = "",
    val savedLatitude: Double = 0.0,
    val savedLongitude: Double = 0.0,
    val isDarkMode: Boolean = true,
    val dhikrReminderEnabled: Boolean = false,
    val dhikrReminderIntervalHours: Int = 2,
    // Prayer Lock settings
    val lockMode: LockMode = LockMode.WHOLE_PHONE,
    val lockTriggerMinutes: Int = 0,  // 0 = at prayer time, >0 = minutes after
    val lockDurationMinutes: Int = 10,
    val minLockDurationMinutes: Int = 5,
    val lockedAppsPackageNames: String = "",
    val adhanEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

sealed class BottomNavItem(val route: String, val title: String, val iconName: String) {
    data object Home : BottomNavItem("home", "Home", "mosque")
    data object Dhikr : BottomNavItem("dhikr", "Lock", "lock")
    data object Qibla : BottomNavItem("qibla", "Qibla", "compass")
    data object Insights : BottomNavItem("insights", "Insights", "chart")
    data object Settings : BottomNavItem("settings", "Settings", "settings")
}
