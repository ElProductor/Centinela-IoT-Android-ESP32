package com.inacap.centinelaiot.data.simulator

import com.inacap.centinelaiot.data.network.model.CommandRequest
import com.inacap.centinelaiot.data.network.model.CommandResponse
import com.inacap.centinelaiot.data.network.model.TelemetryResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Simulador Virtual de Firmware ESP32 Centinela-IoT.
 * Permite ejecutar y evaluar la aplicación al 100% sin hardware conectado,
 * reproduciendo exactamente la lógica de fusión de sensores y control de actuadores.
 */
object Esp32VirtualSimulator {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    // Estados internos del microcontrolador emulado
    var simulatedDistance: Long = 120L
    var simulatedLight: Int = 1850
    var simulatedTemp: Float = 22.5f
    var simulatedHumidity: Float = 48.0f
    var isAlarmActive: Boolean = false
    var servoAngle: Int = 90
    var isButtonPressed: Boolean = false
    var isPerimeterArmed: Boolean = true

    private const val UMBRAL_DIST = 50L
    private const val UMBRAL_LUZ = 800

    private val _telemetryFlow = MutableStateFlow(createTelemetry())
    val telemetryFlow: StateFlow<TelemetryResponse> = _telemetryFlow.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                // Leves variaciones aleatorias para realismo de sensor analógico y termohigrométrico DHT11
                if (simulatedDistance > 55) {
                    val jitter = Random.nextLong(-2, 3)
                    simulatedDistance = (simulatedDistance + jitter).coerceIn(52L, 250L)
                }
                val lightJitter = Random.nextInt(-15, 16)
                simulatedLight = (simulatedLight + lightJitter).coerceIn(100, 4000)

                val tempJitter = (Random.nextFloat() - 0.5f) * 0.2f
                simulatedTemp = (simulatedTemp + tempJitter).coerceIn(15.0f, 42.0f)

                val humJitter = (Random.nextFloat() - 0.5f) * 0.5f
                simulatedHumidity = (simulatedHumidity + humJitter).coerceIn(20.0f, 90.0f)

                // Lógica de detección perimetral (Edge Computing)
                if (isPerimeterArmed && !isAlarmActive && simulatedDistance in 1 until UMBRAL_DIST) {
                    isAlarmActive = true
                    servoAngle = 0 // Bloqueo físico inmediato
                }

                // Lógica de secuencia de desarme físico: Distancia > 50 && Luz < 800 && Botón presionado
                if (isAlarmActive && simulatedDistance > UMBRAL_DIST && simulatedLight < UMBRAL_LUZ && isButtonPressed) {
                    isAlarmActive = false
                    servoAngle = 90 // Desbloqueo tras secuencia autorizada
                }

                _telemetryFlow.value = createTelemetry()
                delay(1000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun triggerIntrusion(distanceCm: Long = 30L) {
        simulatedDistance = distanceCm
        if (isPerimeterArmed) {
            isAlarmActive = true
            servoAngle = 0
            _telemetryFlow.value = createTelemetry()
        }
    }

    fun triggerSafeDistance(distanceCm: Long = 120L) {
        simulatedDistance = distanceCm
        _telemetryFlow.value = createTelemetry()
    }

    fun updateSimulatedLight(adcValue: Int) {
        simulatedLight = adcValue
        _telemetryFlow.value = createTelemetry()
    }

    fun updateButtonPressed(pressed: Boolean) {
        isButtonPressed = pressed
        // Evaluar desarme
        if (isAlarmActive && simulatedDistance > UMBRAL_DIST && simulatedLight < UMBRAL_LUZ && isButtonPressed) {
            isAlarmActive = false
            servoAngle = 90
        }
        _telemetryFlow.value = createTelemetry()
    }

    fun executeCommand(req: CommandRequest): CommandResponse {
        return when (req.comando.lowercase()) {
            "desbloquear", "unlock" -> {
                servoAngle = 90
                isAlarmActive = false
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Apertura remota de emergencia autorizada (Servo a 90°)")
            }
            "bloquear", "lock" -> {
                servoAngle = 0
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Bloqueo físico forzado (Servo a 0°)")
            }
            "armar", "arm" -> {
                isPerimeterArmed = true
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Perímetro invisible armado")
            }
            "desarmar", "disarm" -> {
                isPerimeterArmed = false
                isAlarmActive = false
                servoAngle = 90
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Perímetro desarmado")
            }
            "silenciar", "silence" -> {
                isAlarmActive = false
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Alarma sonora silenciada")
            }
            "cmd_ping_handshake", "ping" -> {
                CommandResponse("ok", "ACK_PONG_READY: ver=v3.0.4-OT; rssi=-58dBm; heap=218450 bytes")
            }
            "gpio_set" -> {
                val pin = req.pin ?: 2
                val valPin = req.valor ?: 0
                CommandResponse("ok", "GPIO $pin establecido a $valPin")
            }
            else -> {
                CommandResponse("error", "Comando no reconocido: ${req.comando}")
            }
        }
    }

    private fun createTelemetry(): TelemetryResponse {
        return TelemetryResponse(
            distancia = simulatedDistance,
            nivelLuz = simulatedLight,
            temperatura = simulatedTemp,
            humedad = simulatedHumidity,
            alarmaActiva = isAlarmActive,
            pestilloGrados = servoAngle,
            botonPresionado = isButtonPressed,
            ipDispositivo = "192.168.1.100 (Simulador Edge)",
            tiempoActivoSegundos = System.currentTimeMillis() / 1000
        )
    }
}
