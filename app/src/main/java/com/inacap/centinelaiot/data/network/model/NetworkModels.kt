package com.inacap.centinelaiot.data.network.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de Telemetría recibida desde el ESP32 o servidor intermediario Node.js.
 */
data class TelemetryResponse(
    @SerializedName("dist", alternate = ["distancia"])
    val distancia: Long = 0,

    @SerializedName("luz")
    val nivelLuz: Int = 0,

    @SerializedName("temperatura", alternate = ["temp"])
    val temperatura: Float = 22.0f,

    @SerializedName("humedad", alternate = ["hum"])
    val humedad: Float = 50.0f,

    @SerializedName("alarma")
    val alarmaActiva: Boolean = false,

    @SerializedName("pestillo", alternate = ["anguloServo"])
    val pestilloGrados: Int = if (alarmaActiva) 0 else 90,

    @SerializedName("boton")
    val botonPresionado: Boolean = false,

    @SerializedName("umbralDist")
    val umbralDist: Long = 50,

    @SerializedName("ultimoOrigen")
    val ultimoOrigen: String = "EDGE_BOOT",

    @SerializedName("ip")
    val ipDispositivo: String? = null,

    @SerializedName("uptime")
    val tiempoActivoSegundos: Long? = null
)

/**
 * Modelo Unificado Unidad 3: Telemetría multiparamétrica de sensores y actuadores.
 */
data class LecturaUnificadaDto(
    @SerializedName("distancia") val distancia: Long,
    @SerializedName("luz") val luz: Int,
    @SerializedName("temperatura") val temperatura: Float = 22.0f,
    @SerializedName("humedad") val humedad: Float = 50.0f,
    @SerializedName("alarma") val alarma: Boolean,
    @SerializedName("anguloServo") val anguloServo: Int = 90,
    @SerializedName("umbralDist") val umbralDist: Long = 50,
    @SerializedName("ultimoOrigen") val ultimoOrigen: String = "EDGE_BOOT"
)

/**
 * Registro de Telemetría para tabla 'sensores' en Supabase Cloud PostgreSQL.
 */
data class SupabaseSensorRecord(
    @SerializedName("fechatoma") val fechatoma: String,
    @SerializedName("temperatura") val temperatura: Float,
    @SerializedName("humedad") val humedad: Float,
    @SerializedName("distancia_cm") val distancia_cm: Long,
    @SerializedName("luz_adc") val luz_adc: Int,
    @SerializedName("alarma_activa") val alarma_activa: Boolean,
    @SerializedName("operador_android") val operador_android: String = "ANDROID_OP_01"
)

/**
 * Petición de Control y Acción remota hacia el ESP32.
 */
data class CommandRequest(
    @SerializedName("comando")
    val comando: String, // "desbloquear", "bloquear", "armar", "desarmar", "silenciar", "gpio_set"

    @SerializedName("pin")
    val pin: Int? = null,

    @SerializedName("valor")
    val valor: Int? = null,

    @SerializedName("token")
    val token: String? = null,

    @SerializedName("usuario")
    val usuario: String? = null
)

/**
 * Respuesta del ESP32 tras procesar un comando.
 */
data class CommandResponse(
    @SerializedName("status")
    val status: String = "ok",

    @SerializedName("mensaje")
    val mensaje: String = "Comando ejecutado",

    @SerializedName("nuevo_estado")
    val nuevoEstado: String? = null
)
