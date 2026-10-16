package com.inacap.centinelaiot.data.network.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo de Telemetría recibida desde el ESP32 o servidor intermediario Node.js.
 */
data class TelemetryResponse(
    @SerializedName("dist")
    val distancia: Long = 0,

    @SerializedName("luz")
    val nivelLuz: Int = 0,

    @SerializedName("alarma")
    val alarmaActiva: Boolean = false,

    @SerializedName("pestillo")
    val pestilloGrados: Int = if (alarmaActiva) 0 else 90,

    @SerializedName("boton")
    val botonPresionado: Boolean = false,

    @SerializedName("ip")
    val ipDispositivo: String? = null,

    @SerializedName("uptime")
    val tiempoActivoSegundos: Long? = null
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
