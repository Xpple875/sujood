package com.sujood.app.data.api

import com.google.gson.annotations.SerializedName

/**
 * Response model from Aladhan API for prayer times.
 * Contains timing data and other metadata.
 */
data class PrayerTimesResponse(
    @SerializedName("data")
    val data: PrayerTimesData,
    @SerializedName("code")
    val code: Int,
    @SerializedName("status")
    val status: String
)

data class PrayerTimesData(
    @SerializedName("timings")
    val timings: Timings,
    @SerializedName("date")
    val date: DateInfo,
    @SerializedName("meta")
    val meta: MetaInfo
)

data class Timings(
    @SerializedName("Fajr")
    val fajr: String,
    @SerializedName("Sunrise")
    val sunrise: String,
    @SerializedName("Dhuhr")
    val dhuhr: String,
    @SerializedName("Asr")
    val asr: String,
    @SerializedName("Sunset")
    val sunset: String,
    @SerializedName("Maghrib")
    val maghrib: String,
    @SerializedName("Isha")
    val isha: String,
    @SerializedName("Imsak")
    val imsak: String,
    @SerializedName("Midnight")
    val midnight: String
)

data class DateInfo(
    @SerializedName("readable")
    val readable: String,
    @SerializedName("timestamp")
    val timestamp: String,
    @SerializedName("hijri")
    val hijri: HijriDate,
    @SerializedName("gregorian")
    val gregorian: GregorianDate
)

data class HijriDate(
    @SerializedName("date")
    val date: String,
    @SerializedName("format")
    val format: String,
    @SerializedName("day")
    val day: String,
    @SerializedName("weekday")
    val weekday: HijriWeekday,
    @SerializedName("month")
    val month: HijriMonth,
    @SerializedName("year")
    val year: String
)

data class HijriWeekday(
    @SerializedName("en")
    val en: String,
    @SerializedName("ar")
    val ar: String
)

data class HijriMonth(
    @SerializedName("number")
    val number: Int,
    @SerializedName("en")
    val en: String,
    @SerializedName("ar")
    val ar: String
)

data class GregorianDate(
    @SerializedName("date")
    val date: String,
    @SerializedName("format")
    val format: String,
    @SerializedName("day")
    val day: String,
    @SerializedName("weekday")
    val weekday: GregorianWeekday,
    @SerializedName("month")
    val month: GregorianMonth,
    @SerializedName("year")
    val year: String
)

data class GregorianWeekday(
    @SerializedName("en")
    val en: String
)

data class GregorianMonth(
    @SerializedName("number")
    val number: Int,
    @SerializedName("en")
    val en: String
)

data class MetaInfo(
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("timezone")
    val timezone: String,
    @SerializedName("method")
    val method: CalculationMethodInfo,
    @SerializedName("latitudeAdjustmentMethod")
    val latitudeAdjustmentMethod: String,
    @SerializedName("midnightMode")
    val midnightMode: String,
    @SerializedName("school")
    val school: String,
    @SerializedName("offset")
    val offset: OffsetInfo
)

data class CalculationMethodInfo(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("params")
    val params: Map<String, Any>?
)

data class OffsetInfo(
    @SerializedName("Imsak")
    val imsak: Int,
    @SerializedName("Fajr")
    val fajr: Int,
    @SerializedName("Sunrise")
    val sunrise: Int,
    @SerializedName("Dhuhr")
    val dhuhr: Int,
    @SerializedName("Asr")
    val asr: Int,
    @SerializedName("Maghrib")
    val maghrib: Int,
    @SerializedName("Isha")
    val isha: Int,
    @SerializedName("Midnight")
    val midnight: Int
)
