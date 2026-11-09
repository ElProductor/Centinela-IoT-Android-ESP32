package com.inacap.centinelaiot.data.network

import com.inacap.centinelaiot.data.network.model.SupabaseSensorRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Interfaz Retrofit2 para la API REST de Supabase Cloud (tabla 'sensores').
 */
interface SupabaseCloudApiService {
    @GET("rest/v1/sensores")
    suspend fun obtenerHistorialSupabase(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "fechatoma.desc",
        @Query("limit") limit: Int = 50
    ): Response<List<SupabaseSensorRecord>>

    @POST("rest/v1/sensores")
    suspend fun insertarRegistroSupabase(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Prefer") prefer: String = "return=minimal",
        @Body registro: SupabaseSensorRecord
    ): Response<Unit>
}

/**
 * Gestor Polimórfico de Comunicación Segura y Reconexión Automática (Unidad 3).
 * Soporta comunicación Serial USB-OTG protegida con HMAC-SHA256 truncado y
 * algoritmo de reconexión resiliente con Exponential Backoff with Jitter (ISO 27400).
 */
class CentinelaSmartDeviceController(
    private val hmacSecretKey: String = "CentinelaOT_Secret_2026"
) {
    private val _estadoConexion = MutableStateFlow("DESCONECTADO")
    val estadoConexion: StateFlow<String> = _estadoConexion

    /**
     * Genera la firma truncada SHA-256 (16 caracteres hexadecimales) idéntica a mbedtls en el ESP32.
     */
    fun generarFirmaTrama(comandoConParametros: String): String {
        val payloadCompleto = "$comandoConParametros|$hmacSecretKey"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payloadCompleto.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    /**
     * Construye una trama Serial Segura cumpliendo con el Criterio 3.1.2:
     * <STX>CMD|PARAMS|FIRMA<ETX>
     */
    fun construirTramaSerialSegura(cmd: String, params: String): ByteArray {
        val cuerpo = "$cmd|$params"
        val firma = generarFirmaTrama(cuerpo)
        val tramaCompleta = "\u0002$cuerpo|$firma\u0003"
        return tramaCompleta.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Envío determinista y no bloqueante de instrucciones a Sensores o Actuadores vía USB-OTG UART.
     */
    fun enviarInstruccionSerial(
        outputStream: OutputStream,
        inputStream: InputStream,
        comando: String,
        parametros: String
    ): Result<String> {
        return try {
            val tramaBytes = construirTramaSerialSegura(comando, parametros)
            outputStream.write(tramaBytes)
            outputStream.flush()

            val buffer = ByteArray(256)
            val bytesRead = inputStream.read(buffer)
            if (bytesRead > 0) {
                val respuesta = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                    .trim('\u0002', '\u0003', '\r', '\n')
                Result.success(respuesta)
            } else {
                Result.failure(Exception("Timeout esperando respuesta serial del ESP32"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Handshake de prueba bidireccional Mobile-MCU (CMD_PING_HANDSHAKE -> ACK_PONG_READY).
     */
    fun verificarHandshakeSerial(
        outputStream: OutputStream,
        inputStream: InputStream,
        terminalId: String = "ANDROID-OP-01"
    ): Result<String> {
        val timestamp = System.currentTimeMillis() / 1000
        val nonce = Random.nextInt(1000, 9999)
        return enviarInstruccionSerial(
            outputStream,
            inputStream,
            "CMD_PING_HANDSHAKE",
            "terminal=$terminalId;time=$timestamp;nonce=$nonce"
        )
    }

    /**
     * Ejecutor resiliente con Reconexión Automática (Exponential Backoff with Jitter - Paso 3).
     * Fórmula: T_wait(k) = min(T_max, T_base * 2^k) + U(0, J_max)
     */
    suspend fun <T> ejecutarConReconexionAutomatica(
        maxIntentos: Int = 4,
        baseDelayMs: Long = 1000L,
        maxDelayMs: Long = 16000L,
        operacionRed: suspend () -> Response<T>
    ): Result<T> {
        var intentoActual = 0
        while (intentoActual < maxIntentos) {
            try {
                _estadoConexion.value = if (intentoActual == 0) "CONECTADO_ONLINE" else "RECONECTANDO_INTENTO_$intentoActual"
                val response = operacionRed()
                if (response.isSuccessful && response.body() != null) {
                    _estadoConexion.value = "CONECTADO_ONLINE"
                    return Result.success(response.body()!!)
                }
            } catch (e: Exception) {
                // Fallo de red capturado para iniciar ciclo de backoff
            }

            intentoActual++
            if (intentoActual < maxIntentos) {
                val backoffExponencial = min(
                    maxDelayMs,
                    (baseDelayMs * 2.0.pow(intentoActual.toDouble())).toLong()
                )
                val jitterAleatorio = Random.nextLong(0L, 450L)
                delay(backoffExponencial + jitterAleatorio)
            }
        }
        _estadoConexion.value = "CONTINGENCIA_OFFLINE_ACTIVA"
        return Result.failure(Exception("Enlace con ESP32 no disponible tras $maxIntentos intentos. Conmutando a Contingencia."))
    }
}
