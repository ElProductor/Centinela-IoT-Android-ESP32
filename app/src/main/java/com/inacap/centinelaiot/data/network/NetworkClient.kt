package com.inacap.centinelaiot.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente de Red dinámico que permite conectarse a cualquier ESP32 o servidor local/nube.
 */
object NetworkClient {

    private var currentBaseUrl: String = "http://192.168.1.100:3000/"
    private var currentService: Esp32ApiService? = null

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun getService(baseUrl: String): Esp32ApiService {
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (currentService == null || currentBaseUrl != formattedUrl) {
            currentBaseUrl = formattedUrl
            currentService = Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(Esp32ApiService::class.java)
        }
        return currentService!!
    }
}
