package com.sujood.app.domain.usecase

import com.sujood.app.data.repository.PrayerTimesRepository
import com.sujood.app.domain.model.Prayer
import com.sujood.app.domain.model.PrayerTime

/**
 * Use case for getting prayer times based on location.
 * Encapsulates the business logic for fetching and processing prayer times.
 */
class GetPrayerTimesUseCase(
    private val repository: PrayerTimesRepository
) {
    /**
     * Fetches prayer times for a given location.
     *
     * @param latitude User's latitude
     * @param longitude User's longitude
     * @param calculationMethodCode Calculation method code
     * @param madhabCode Madhab code (0=Shafi, 1=Hanafi)
     * @return Result containing list of PrayerTime or error
     */
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        calculationMethodCode: Int,
        madhabCode: Int
    ): Result<List<PrayerTime>> {
        return repository.getPrayerTimes(
            latitude = latitude,
            longitude = longitude,
            method = com.sujood.app.domain.model.CalculationMethod.entries.getOrElse(calculationMethodCode) {
                com.sujood.app.domain.model.CalculationMethod.MWL
            },
            madhab = com.sujood.app.domain.model.Madhab.entries.getOrElse(madhabCode) {
                com.sujood.app.domain.model.Madhab.SHAFI
            }
        )
    }
}

/**
 * Use case for logging prayer completion.
 * Handles the business logic for recording when a user completes a prayer.
 */
class LogPrayerCompletionUseCase(
    private val repository: PrayerTimesRepository
) {
    /**
     * Logs that the user has completed a specific prayer.
     *
     * @param prayer The prayer that was completed
     * @return Result containing the log ID or error
     */
    suspend operator fun invoke(prayer: Prayer): Result<Long> {
        return try {
            // Check if already completed today
            if (repository.isPrayerCompletedToday(prayer)) {
                return Result.failure(Exception("Prayer already logged for today"))
            }
            
            val logId = repository.logPrayerCompletion(prayer)
            Result.success(logId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Use case for getting prayer streak.
 * Calculates how many consecutive days the user has completed all prayers.
 */
class GetPrayerStreakUseCase(
    private val repository: PrayerTimesRepository
) {
    /**
     * Gets the current prayer streak count.
     *
     * @return Number of consecutive days with all prayers completed
     */
    suspend operator fun invoke(): Int {
        return repository.getPrayerStreak()
    }
}

/**
 * Use case for getting next prayer information.
 * Determines which prayer is next and calculates countdown time.
 */
class GetNextPrayerUseCase {
    
    /**
     * Data class containing next prayer information.
     */
    data class NextPrayerInfo(
        val prayer: Prayer,
        val timeRemainingMillis: Long,
        val prayerTime: PrayerTime
    )

    /**
     * Determines the next prayer based on current time.
     *
     * @param prayerTimes List of all prayer times for today
     * @return NextPrayerInfo or null if all prayers passed
     */
    operator fun invoke(prayerTimes: List<PrayerTime>): NextPrayerInfo? {
        val currentTime = System.currentTimeMillis()

        // Sort prayer times by timestamp
        val sortedTimes = prayerTimes.sortedBy { it.timestamp }

        // Find the next prayer (first one that's in the future)
        val nextPrayer = sortedTimes.firstOrNull { it.timestamp > currentTime }

        return if (nextPrayer != null) {
            NextPrayerInfo(
                prayer = nextPrayer.prayer,
                timeRemainingMillis = nextPrayer.timestamp - currentTime,
                prayerTime = nextPrayer
            )
        } else {
            // All prayers for today have passed — show Fajr for tomorrow
            val fajr = sortedTimes.firstOrNull { it.prayer == Prayer.FAJR }
            if (fajr != null) {
                val tomorrowFajrTimestamp = fajr.timestamp + 24 * 60 * 60 * 1000L
                NextPrayerInfo(
                    prayer = Prayer.FAJR,
                    timeRemainingMillis = tomorrowFajrTimestamp - currentTime,
                    prayerTime = fajr.copy(timestamp = tomorrowFajrTimestamp)
                )
            } else null
        }
    }
}
