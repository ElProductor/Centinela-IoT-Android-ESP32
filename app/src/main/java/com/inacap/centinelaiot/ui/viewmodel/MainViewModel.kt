package com.inacap.centinelaiot.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inacap.centinelaiot.CentinelaApp
import com.inacap.centinelaiot.data.database.model.AuditLog
import com.inacap.centinelaiot.data.database.model.GenericWidget
import com.inacap.centinelaiot.data.network.NetworkClient
import com.inacap.centinelaiot.data.network.model.CommandRequest
import com.inacap.centinelaiot.data.network.model.TelemetryResponse
import com.inacap.centinelaiot.data.simulator.Esp32VirtualSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    private val db = CentinelaApp.instance.database
    private val session = CentinelaApp.instance.sessionManager

    private val _telemetry = MutableLiveData<TelemetryResponse>()
    val telemetry: LiveData<TelemetryResponse> = _telemetry

    private val _auditLogs = MutableLiveData<List<AuditLog>>()
    val auditLogs: LiveData<List<AuditLog>> = _auditLogs

    private val _genericWidgets = MutableLiveData<List<GenericWidget>>()
    val genericWidgets: LiveData<List<GenericWidget>> = _genericWidgets

    private val _connectionStatus = MutableLiveData<String>()
    val connectionStatus: LiveData<String> = _connectionStatus

    private val _lastMessage = MutableLiveData<String>()
    val lastMessage: LiveData<String> = _lastMessage

    private var previousAlarmState = false

    init {
        loadAuditLogs()
        loadWidgets()
        startTelemetryPolling()
    }

    fun startTelemetryPolling() {
        viewModelScope.launch {
            while (isActive) {
                if (session.isSimulatorActive) {
                    _connectionStatus.postValue("SIMULADOR ESP32 ACTIVO")
                    val current = Esp32VirtualSimulator.telemetryFlow.value
                    _telemetry.postValue(current)
                    checkAlarmTransition(current)
                } else {
                    _connectionStatus.postValue("CONECTANDO A ${session.baseUrl}...")
                    fetchRealHardwareTelemetry()
                }
                delay(1200)
            }
        }
    }

    private suspend fun fetchRealHardwareTelemetry() {
        try {
            val service = NetworkClient.getService(session.baseUrl)
            val response = service.getTelemetria(token = "Bearer ${session.token}")
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                _telemetry.postValue(data)
                _connectionStatus.postValue("ONLINE: ${session.baseUrl}")
                checkAlarmTransition(data)
            } else {
                _connectionStatus.postValue("ERROR HTTP: ${response.code()}")
            }
        } catch (e: Exception) {
            _connectionStatus.postValue("SIN CONEXIÓN ESP32 (${e.message ?: "Timeout"})")
        }
    }

    private fun checkAlarmTransition(data: TelemetryResponse) {
        if (data.alarmaActiva && !previousAlarmState) {
            // Se acaba de disparar una intrusión
            recordAudit(
                eventType = "INTRUSION_DETECTED",
                details = "¡Alarma activada! Distancia: ${data.distancia} cm (Umbral < ${session.umbralDistancia} cm)",
                severity = "CRITICAL"
            )
        } else if (!data.alarmaActiva && previousAlarmState) {
            // Se desarmó el sistema
            recordAudit(
                eventType = "PERIMETER_DISARMED",
                details = "Sistema desarmado: Distancia ${data.distancia} cm, Luz ${data.nivelLuz} ADC, Pestillo a ${data.pestilloGrados}°",
                severity = "INFO"
            )
        }
        previousAlarmState = data.alarmaActiva
    }

    fun sendCommand(command: String, pin: Int? = null, valor: Int? = null) {
        viewModelScope.launch {
            val req = CommandRequest(
                comando = command,
                pin = pin,
                valor = valor,
                token = session.token,
                usuario = session.username
            )

            recordAudit(
                eventType = "REMOTE_COMMAND",
                details = "Comando '$command' ejecutado por ${session.username} (Rol: ${session.role})",
                severity = if (command.contains("desbloquear", true)) "WARNING" else "INFO"
            )

            if (session.isSimulatorActive) {
                val res = Esp32VirtualSimulator.executeCommand(req)
                _lastMessage.postValue(res.mensaje)
            } else {
                try {
                    val service = NetworkClient.getService(session.baseUrl)
                    val response = service.sendComando(req, "Bearer ${session.token}")
                    if (response.isSuccessful && response.body() != null) {
                        _lastMessage.postValue(response.body()!!.mensaje)
                    } else {
                        _lastMessage.postValue("Fallo comando: HTTP ${response.code()}")
                    }
                } catch (e: Exception) {
                    _lastMessage.postValue("Error de red: ${e.message}")
                }
            }
            loadAuditLogs()
        }
    }

    fun recordAudit(eventType: String, details: String, severity: String = "INFO") {
        viewModelScope.launch(Dispatchers.IO) {
            db.logEvent(eventType, session.username.ifEmpty { "SISTEMA" }, details, severity)
            loadAuditLogs()
        }
    }

    fun loadAuditLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val logs = db.getAllAuditLogs()
            _auditLogs.postValue(logs)
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearAuditLogs()
            recordAudit("LOGS_CLEARED", "Registros de auditoría purgados por ${session.username}", "WARNING")
            loadAuditLogs()
        }
    }

    fun loadWidgets() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = db.getWidgets()
            _genericWidgets.postValue(list)
        }
    }

    fun addWidget(name: String, type: String, pin: Int, initialValue: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val widget = GenericWidget(
                profileId = 1,
                name = name,
                widgetType = type,
                gpioPin = pin,
                currentValue = initialValue
            )
            db.insertWidget(widget)
            loadWidgets()
        }
    }

    fun updateWidgetValue(widgetId: Long, value: Int, pin: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateWidgetValue(widgetId, value)
            sendCommand("gpio_set", pin = pin, valor = value)
            loadWidgets()
        }
    }
}
