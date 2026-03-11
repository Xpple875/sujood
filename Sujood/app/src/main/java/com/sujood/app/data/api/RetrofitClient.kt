package com.sujood.app.data.api

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client for the Aladhan prayer times API.
 *
 * Security hardening:
 *  - HttpLoggingInterceptor disabled in release builds (no request/response body leaks)
 *  - Certificate pinning on api.aladhan.com (blocks MitM attacks)
 *  - Network Security Config (network_security_config.xml) enforces HTTPS app-wide
 *
 * To update the certificate pin when Cloudflare rotates:
 *   openssl s_client -connect api.aladhan.com:443 2>/dev/null \
 *     | openssl x509 -pubkey -noout | openssl pkey -pubin -outform DER \
 *     | openssl dgst -sha256 -binary | base64
 */
object RetrofitClient {

    private const val BASE_URL = "https://api.aladhan.com/v1/"

    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT    = 30L
    private const val WRITE_TIMEOUT   = 30L

    /**
     * Certificate pinner for api.aladhan.com.
     *
     * Two pins are set so the app keeps working if Cloudflare rotates
     * their intermediate — at least one pin will still match.
     *
     * IMPORTANT: Replace PLACEHOLDER_PIN with the real SHA-256 SPKI pin
     * by running the openssl command above on your machine against the
     * live aladhan.com endpoint, then commit the result.
     */
    private val certificatePinner = CertificatePinner.Builder()
        .add("api.aladhan.com", "sha256/OZkL8XmZKY0ryyxsBXKpHvLU3+3xabxGPWC6bNO0CjA=")
        .add("api.aladhan.com", "sha256/sRHdihwgkaib1P1gxX8HFszlD+7/gTfNvuAybgLPNis=")
        .build()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            // Logging only in debug builds — never log bodies in production
            .apply {
                if (com.sujood.app.BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .certificatePinner(certificatePinner)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val aladhanApiService: AladhanApiService by lazy {
        retrofit.create(AladhanApiService::class.java)
    }
}
