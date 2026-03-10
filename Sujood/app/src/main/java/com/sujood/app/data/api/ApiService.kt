package com.sujood.app.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AladhanApiService {

    @GET("timings")
    suspend fun getPrayerTimes(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int,
        @Query("school") school: Int = 0,
        @Query("date") date: String = "now",
        @Query("tune") tune: String? = null
    ): PrayerTimesResponse

    @GET("timings")
    suspend fun getPrayerTimesWithAdjustment(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int,
        @Query("school") school: Int = 0,
        @Query("date") date: String = "now",
        @Query("adjustment") adjustment: Int = 0,
        @Query("tune") tune: String? = null
    ): PrayerTimesResponse

    @GET("timingsByCity")
    suspend fun getPrayerTimesByCity(
        @Query("city") city: String,
        @Query("country") country: String? = null,
        @Query("method") method: Int,
        @Query("school") school: Int = 0,
        @Query("tune") tune: String? = null
    ): PrayerTimesResponse

    @GET("citySearch")
    suspend fun searchCity(
        @Query("q") query: String
    ): CitySearchResponse
}

data class CitySearchResponse(
    val data: List<CityData>
)

data class CityData(
    val name: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double
)
