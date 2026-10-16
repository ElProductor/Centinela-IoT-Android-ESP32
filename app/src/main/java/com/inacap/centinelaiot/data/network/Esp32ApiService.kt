package com.inacap.centinelaiot.data.network

import com.inacap.centinelaiot.data.network.model.CommandRequest
import com.inacap.centinelaiot.data.network.model.CommandResponse
import com.inacap.centinelaiot.data.network.model.TelemetryResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Interfaz de Endpoints REST para el ESP32 o servidor Node.js
 */
interface Esp32ApiService {

    @GET("telemetria")
    suspend fun getTelemetria(
        @Header("Authorization") token: String? = null
    ): Response<TelemetryResponse>

    @POST("comando")
    suspend fun sendComando(
        @Body request: CommandRequest,
        @Header("Authorization") token: String? = null
    ): Response<CommandResponse>

    @POST("telemetria")
    suspend fun postTelemetria(
        @Body telemetry: TelemetryResponse
    ): Response<CommandResponse>
}
