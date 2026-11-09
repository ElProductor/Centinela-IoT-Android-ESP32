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
    private var courtesyJob: Job? = null

    // Estados internos del microcontrolador emulado
    var simulatedDistance: Long = 120L
    var simulatedLight: Int = 1850
    var simulatedTemp: Float = 22.5f
    var simulatedHumidity: Float = 48.0f
    var isAlarmActive: Boolean = false
    var servoAngle: Int = 90
    var isButtonPressed: Boolean = false
    var isPerimeterArmed: Boolean = true
    var isBeaconActive: Boolean = false
    var isHvacActive: Boolean = false
    var courtesyCountdown: Int = 0

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

                // Efecto de climatización HVAC si está encendido
                val tempTarget = if (isHvacActive) 19.5f else 23.0f
                if (simulatedTemp > tempTarget) {
                    simulatedTemp -= 0.1f
                } else {
                    val tempJitter = (Random.nextFloat() - 0.5f) * 0.2f
                    simulatedTemp = (simulatedTemp + tempJitter).coerceIn(15.0f, 42.0f)
                }

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
        courtesyJob?.cancel()
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
        val cmd = req.comando.lowercase().trim()
        return when {
            cmd in listOf("desbloquear", "unlock") -> {
                servoAngle = 90
                isAlarmActive = false
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Apertura remota de emergencia autorizada (Servo a 90°)")
            }
            cmd in listOf("bloquear", "lock") -> {
                servoAngle = 0
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Bloqueo físico forzado (Servo a 0°)")
            }
            cmd in listOf("armar", "arm") -> {
                isPerimeterArmed = true
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Perímetro invisible armado")
            }
            cmd in listOf("desarmar", "disarm") -> {
                isPerimeterArmed = false
                isAlarmActive = false
                servoAngle = 90
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Perímetro desarmado")
            }
            cmd in listOf("silenciar", "silence") -> {
                isAlarmActive = false
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Alarma sonora silenciada")
            }
            cmd in listOf("panico", "panic", "alarma_panico") -> {
                isAlarmActive = true
                servoAngle = 0
                isBeaconActive = true
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "🚨 ALERTA DE PÁNICO ACTIVADA: Sirena continua, baliza estroboscópica y cerrojo asegurado")
            }
            cmd in listOf("cortesia", "courtesy", "apertura_cortesia") -> {
                servoAngle = 90
                isAlarmActive = false
                courtesyCountdown = 10
                _telemetryFlow.value = createTelemetry()

                courtesyJob?.cancel()
                courtesyJob = scope.launch {
                    while (courtesyCountdown > 0) {
                        delay(1000)
                        courtesyCountdown--
                    }
                    // Auto-cierre
                    servoAngle = 0
                    isPerimeterArmed = true
                    _telemetryFlow.value = createTelemetry()
                }

                CommandResponse("ok", "🔓 APERTURA TEMPORAL: Cerrojo abierto por 10s con auto-cierre programado")
            }
            cmd in listOf("bist", "autodiagnostico", "hardware_bist") -> {
                CommandResponse(
                    "ok",
                    "🩺 AUTODIAGNÓSTICO BIST (100% OPERATIVO):\n• Servo SG90: OK (Test 0°-90°-0°)\n• Buzzer Alarma: OK (2.4 kHz)\n• LEDs Baliza: OK (Verde/Amarillo/Rojo)\n• HC-SR04: ${simulatedDistance} cm (Normal)\n• LDR GL5528: ${simulatedLight} ADC\n• DHT11: ${String.format(java.util.Locale.US, "%.1f", simulatedTemp)}°C | ${String.format(java.util.Locale.US, "%.1f", simulatedHumidity)}%\n• RAM Heap: 218,450 Bytes libres\n• Wi-Fi RSSI: -54 dBm (Excelente)"
                )
            }
            cmd in listOf("baliza", "strobe", "beacon") -> {
                isBeaconActive = !isBeaconActive
                val estado = if (isBeaconActive) "ACTIVADA (Parpadeo continuo)" else "DESACTIVADA"
                CommandResponse("ok", "💡 Baliza Estroboscópica: $estado")
            }
            cmd in listOf("hvac", "clima", "ventilacion") -> {
                isHvacActive = !isHvacActive
                val estado = if (isHvacActive) "ENCENDIDO (Refrigerando a 19.5°C)" else "APAGADO"
                CommandResponse("ok", "❄️ Climatización / Extractor OT: $estado")
            }
            cmd in listOf("reiniciar", "reboot", "restart") -> {
                simulatedDistance = 120L
                isAlarmActive = false
                isBeaconActive = false
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "🔄 ESP32 reiniciado exitosamente (Soft Reset completado)")
            }
            cmd in listOf("cmd_ping_handshake", "ping") -> {
                CommandResponse("ok", "ACK_PONG_READY: ver=v3.0.4-OT; rssi=-58dBm; heap=218450 bytes")
            }
            cmd == "servo_angle" -> {
                val angle = (req.valor ?: 90).coerceIn(0, 180)
                servoAngle = angle
                _telemetryFlow.value = createTelemetry()
                CommandResponse("ok", "Pestillo servo posicionado a $angle°")
            }
            cmd in listOf("gpio_set", "gpio_pulse") -> {
                val pin = req.pin ?: 2
                val valPin = req.valor ?: 0
                CommandResponse("ok", "Acción GPIO $pin establecida a $valPin")
            }
            else -> {
                CommandResponse("ok", "Instrucción '$cmd' ejecutada exitosamente en el nodo ESP32")
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
