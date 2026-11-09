package com.inacap.centinelaiot.ui.centinela

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.inacap.centinelaiot.CentinelaApp
import com.inacap.centinelaiot.R
import com.inacap.centinelaiot.data.network.model.TelemetryResponse
import com.inacap.centinelaiot.data.simulator.Esp32VirtualSimulator
import com.inacap.centinelaiot.databinding.FragmentCentinelaBinding
import com.inacap.centinelaiot.ui.viewmodel.MainViewModel

class CentinelaFragment : Fragment() {

    private var _binding: FragmentCentinelaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val sessionManager by lazy { CentinelaApp.instance.sessionManager }
    private val dbHelper by lazy { CentinelaApp.instance.databaseHelper }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCentinelaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.telemetry.observe(viewLifecycleOwner) { data ->
            updateUI(data)
        }

        viewModel.lastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg.isNotEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(data: TelemetryResponse) {
        val safeColor = ContextCompat.getColor(requireContext(), R.color.status_safe)
        val alarmColor = ContextCompat.getColor(requireContext(), R.color.status_alarm)
        val warningColor = ContextCompat.getColor(requireContext(), R.color.status_warning)
        val textSecondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val safeOverlayColor = ContextCompat.getColor(requireContext(), R.color.safe_overlay)
        val alarmOverlayColor = ContextCompat.getColor(requireContext(), R.color.alarm_overlay)

        // 1. Estado del Perímetro General
        if (data.alarmaActiva) {
            binding.cardPerimeterStatus.setCardBackgroundColor(alarmOverlayColor)
            binding.cardPerimeterStatus.strokeColor = alarmColor
            binding.ivPerimeterStatusIcon.setImageResource(R.drawable.ic_warning)
            binding.ivPerimeterStatusIcon.imageTintList = ColorStateList.valueOf(alarmColor)
            binding.tvPerimeterStatusText.text = getString(R.string.state_breached)
            binding.tvPerimeterStatusText.setTextColor(alarmColor)
            binding.tvPerimeterDetails.text = "¡Distancia menor a ${sessionManager.umbralDistancia} cm! Pestillo Bloqueado"
        } else {
            binding.cardPerimeterStatus.setCardBackgroundColor(safeOverlayColor)
            binding.cardPerimeterStatus.strokeColor = safeColor
            binding.ivPerimeterStatusIcon.setImageResource(R.drawable.ic_shield)
            binding.ivPerimeterStatusIcon.imageTintList = ColorStateList.valueOf(safeColor)
            binding.tvPerimeterStatusText.text = getString(R.string.state_safe)
            binding.tvPerimeterStatusText.setTextColor(safeColor)
            binding.tvPerimeterDetails.text = "Perímetro monitoreado continuamente en el Edge (ESP32)"
        }

        // 2. Sensor Ultrasónico HC-SR04
        binding.tvDistanceValue.text = "${data.distancia} cm"
        binding.pbDistance.progress = data.distancia.toInt().coerceIn(0, 200)
        if (data.distancia < sessionManager.umbralDistancia && data.distancia > 0) {
            binding.tvDistanceValue.setTextColor(alarmColor)
            binding.pbDistance.progressTintList = ColorStateList.valueOf(alarmColor)
        } else {
            binding.tvDistanceValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            binding.pbDistance.progressTintList = ColorStateList.valueOf(safeColor)
        }

        // 3. Sensor LDR GL5528
        binding.tvLightValue.text = "${data.nivelLuz} ADC"
        val isDarkness = data.nivelLuz < sessionManager.umbralLuz
        if (isDarkness) {
            binding.tvLightCondition.text = "🌙 Modo Oscuridad (Desarme habilitado)"
            binding.tvLightCondition.setTextColor(safeColor)
        } else {
            binding.tvLightCondition.text = "☀️ Luz Ambiental (> ${sessionManager.umbralLuz} ADC)"
            binding.tvLightCondition.setTextColor(textSecondaryColor)
        }

        // 3.1 Sensor Termohigrométrico DHT11 (Unidad 3)
        binding.tvTempValue.text = String.format(java.util.Locale.getDefault(), "%.1f °C", data.temperatura)
        binding.tvHumidityValue.text = String.format(java.util.Locale.getDefault(), "%.1f %%", data.humedad)

        // 4. Pestillo Servomotor SG90
        if (data.pestilloGrados == 0 || data.alarmaActiva) {
            binding.ivServoLockIcon.setImageResource(R.drawable.ic_lock_closed)
            binding.ivServoLockIcon.imageTintList = ColorStateList.valueOf(alarmColor)
            binding.tvServoState.text = getString(R.string.state_locked)
            binding.tvServoState.setTextColor(alarmColor)
        } else {
            binding.ivServoLockIcon.setImageResource(R.drawable.ic_lock_open)
            binding.ivServoLockIcon.imageTintList = ColorStateList.valueOf(safeColor)
            binding.tvServoState.text = getString(R.string.state_unlocked)
            binding.tvServoState.setTextColor(safeColor)
        }

        // 5. Zumbador Pasivo (Buzzer)
        if (data.alarmaActiva) {
            binding.ivBuzzerIcon.imageTintList = ColorStateList.valueOf(alarmColor)
            binding.tvBuzzerState.text = getString(R.string.state_sounding)
            binding.tvBuzzerState.setTextColor(alarmColor)
        } else {
            binding.ivBuzzerIcon.imageTintList = ColorStateList.valueOf(textSecondaryColor)
            binding.tvBuzzerState.text = getString(R.string.state_silent)
            binding.tvBuzzerState.setTextColor(textSecondaryColor)
        }

        // 6. Fusión de Condiciones de Desarme Físico
        val condDist = data.distancia > sessionManager.umbralDistancia
        val condLight = data.nivelLuz < sessionManager.umbralLuz
        val condBtn = data.botonPresionado

        binding.tvCondDistance.text = if (condDist) "✔ Distancia despejada (> ${sessionManager.umbralDistancia} cm): CUMPLIDA" else "✖ Distancia despejada (> ${sessionManager.umbralDistancia} cm): BLOQUEADA"
        binding.tvCondDistance.setTextColor(if (condDist) safeColor else alarmColor)

        binding.tvCondLight.text = if (condLight) "✔ Nivel de oscuridad (< ${sessionManager.umbralLuz} ADC): CUMPLIDA" else "✖ Nivel de oscuridad (< ${sessionManager.umbralLuz} ADC): PENDIENTE"
        binding.tvCondLight.setTextColor(if (condLight) safeColor else textSecondaryColor)

        binding.tvCondButton.text = if (condBtn) "✔ Pulsador físico accionado (GPIO 4): ACTIVO" else "✖ Pulsador físico accionado (GPIO 4): EN REPOSO"
        binding.tvCondButton.setTextColor(if (condBtn) safeColor else textSecondaryColor)

        // Mostrar / ocultar controles del simulador
        binding.cardSimulatorControls.visibility = if (sessionManager.isSimulatorActive) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        // Apertura Remota de Emergencia (ISO 27400 Security Confirmation)
        binding.btnEmergencyUnlock.setOnClickListener {
            if (!sessionManager.canRemoteOpen(dbHelper)) {
                Toast.makeText(requireContext(), "Acceso denegado: Su rol no cuenta con privilegios de Apertura Remota", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Confirmación de Seguridad OT (ISO 27400)")
                .setMessage("¿Confirma la APERTURA REMOTA DE EMERGENCIA del pestillo Centinela? Esta acción quedará registrada en el log de auditoría inmutable.")
                .setPositiveButton("AUTORIZAR APERTURA") { _, _ ->
                    viewModel.sendCommand("desbloquear")
                }
                .setNegativeButton("CANCELAR", null)
                .show()
        }

        // Armar / Desarmar
        binding.btnArmDisarm.setOnClickListener {
            if (!sessionManager.canDisarm(dbHelper)) {
                Toast.makeText(requireContext(), "Acceso denegado: Su rol no cuenta con privilegios de Armar/Desarmar", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val isAlarm = viewModel.telemetry.value?.alarmaActiva ?: false
            if (isAlarm) {
                viewModel.sendCommand("desarmar")
            } else {
                viewModel.sendCommand("armar")
            }
        }

        // Silenciar Alarma
        binding.btnSilenceBuzzer.setOnClickListener {
            if (!sessionManager.canDisarm(dbHelper)) {
                Toast.makeText(requireContext(), "Acceso denegado: Su rol no cuenta con privilegios de Desarme", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            viewModel.sendCommand("silenciar")
        }

        // --- Acciones Rápidas OT del ESP32 ---
        binding.btnQuickPanicMain.setOnClickListener {
            if (!sessionManager.canDisarm(dbHelper)) {
                Toast.makeText(requireContext(), "Acceso denegado: Se requiere rol de Operador u Administrador", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.triggerPanic()
            Toast.makeText(requireContext(), "🚨 Alarma de Pánico Activada en ESP32", Toast.LENGTH_SHORT).show()
        }

        binding.btnQuickCourtesyMain.setOnClickListener {
            if (!sessionManager.canRemoteOpen(dbHelper)) {
                Toast.makeText(requireContext(), "Acceso denegado: Se requiere permiso de Apertura Remota", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.triggerCourtesyUnlock()
            Toast.makeText(requireContext(), "🔓 Apertura de Cortesía iniciada (Auto-cierre en 10s)", Toast.LENGTH_SHORT).show()
        }

        binding.btnQuickBistMain.setOnClickListener {
            Toast.makeText(requireContext(), "Ejecutando prueba BIST de hardware en el ESP32...", Toast.LENGTH_SHORT).show()
            viewModel.triggerBist { report ->
                activity?.runOnUiThread {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("🩺 Diagnóstico BIST de Hardware (ESP32)")
                        .setMessage(report)
                        .setPositiveButton("ACEPTAR", null)
                        .show()
                }
            }
        }

        binding.btnQuickBeaconMain.setOnClickListener {
            viewModel.triggerBeacon()
            Toast.makeText(requireContext(), "💡 Baliza Estroboscópica activada/desactivada", Toast.LENGTH_SHORT).show()
        }

        // Controles de Simulación Interactiva
        binding.btnSimIntrusion.setOnClickListener {
            Esp32VirtualSimulator.triggerIntrusion(28)
            Toast.makeText(requireContext(), "Simulando intrusión a 28 cm", Toast.LENGTH_SHORT).show()
        }

        binding.btnSimSafe.setOnClickListener {
            Esp32VirtualSimulator.triggerSafeDistance(135)
            Toast.makeText(requireContext(), "Zona perimetral despejada (135 cm)", Toast.LENGTH_SHORT).show()
        }

        binding.btnSimDarkness.setOnClickListener {
            val currentLight = Esp32VirtualSimulator.simulatedLight
            if (currentLight > 800) {
                Esp32VirtualSimulator.updateSimulatedLight(350)
                Toast.makeText(requireContext(), "Simulando oscuridad (< 800 ADC)", Toast.LENGTH_SHORT).show()
            } else {
                Esp32VirtualSimulator.updateSimulatedLight(2100)
                Toast.makeText(requireContext(), "Restaurando luz ambiental normal", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSimButtonPress.setOnClickListener {
            Esp32VirtualSimulator.updateButtonPressed(true)
            Toast.makeText(requireContext(), "Pulsador accionado temporalmente", Toast.LENGTH_SHORT).show()
            view?.postDelayed({
                Esp32VirtualSimulator.updateButtonPressed(false)
            }, 2500)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
