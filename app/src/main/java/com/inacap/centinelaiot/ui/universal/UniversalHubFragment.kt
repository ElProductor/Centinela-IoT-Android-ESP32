package com.inacap.centinelaiot.ui.universal

import android.app.AlertDialog
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.inacap.centinelaiot.CentinelaApp
import com.inacap.centinelaiot.R
import com.inacap.centinelaiot.data.database.model.GenericWidget
import com.inacap.centinelaiot.databinding.FragmentUniversalHubBinding
import com.inacap.centinelaiot.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UniversalHubFragment : Fragment() {

    private var _binding: FragmentUniversalHubBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val sessionManager by lazy { CentinelaApp.instance.sessionManager }
    private val dbHelper by lazy { CentinelaApp.instance.databaseHelper }
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUniversalHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTerminalOutput.movementMethod = ScrollingMovementMethod()
        binding.tvActiveProfileInfo.text = "Destino: ${sessionManager.baseUrl} • Protocolo: HTTP REST"

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.genericWidgets.observe(viewLifecycleOwner) { widgets ->
            renderWidgets(widgets)
        }

        viewModel.telemetry.observe(viewLifecycleOwner) { data ->
            val timestamp = timeFormat.format(Date())
            val logLine = "[$timestamp] RX: {\"dist\":${data.distancia}, \"luz\":${data.nivelLuz}, \"temp\":${String.format(Locale.US, "%.1f", data.temperatura)}, \"alarma\":${data.alarmaActiva}, \"pestillo\":${data.pestilloGrados}}\n"
            appendTerminal(logLine)
        }
    }

    private fun setupListeners() {
        // --- 1. ACCIONES RÁPIDAS DEL ESP32 ---
        binding.btnQuickPanic.setOnClickListener {
            if (!sessionManager.canDisarm(dbHelper)) {
                Toast.makeText(requireContext(), "Acceso denegado: Se requiere rol de Operador u Administrador", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            appendTerminal("[${timeFormat.format(Date())}] TX: ALERTA DE PÁNICO INMEDIATA\n")
            viewModel.triggerPanic()
            Toast.makeText(requireContext(), "🚨 Alarma de Pánico enviada al ESP32", Toast.LENGTH_SHORT).show()
        }

        binding.btnQuickCourtesy.setOnClickListener {
            if (!sessionManager.canRemoteOpen(dbHelper)) {
                Toast.makeText(requireContext(), "Acceso denegado: Se requiere rol con permiso de Apertura", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            appendTerminal("[${timeFormat.format(Date())}] TX: APERTURA DE CORTESÍA (10s)\n")
            viewModel.triggerCourtesyUnlock()
            Toast.makeText(requireContext(), "🔓 Apertura de Cortesía iniciada (Auto-cierre en 10s)", Toast.LENGTH_SHORT).show()
        }

        binding.btnQuickBist.setOnClickListener {
            appendTerminal("[${timeFormat.format(Date())}] TX: INICIANDO TEST BIST DE HARDWARE...\n")
            Toast.makeText(requireContext(), "Ejecutando autodiagnóstico BIST en el ESP32...", Toast.LENGTH_SHORT).show()
            viewModel.triggerBist { report ->
                appendTerminal("[${timeFormat.format(Date())}] RX BIST REPORT:\n$report\n")
                showBistReportDialog(report)
            }
        }

        binding.btnQuickBeacon.setOnClickListener {
            appendTerminal("[${timeFormat.format(Date())}] TX: TOGGLE BALIZA ESTROBOSCÓPICA\n")
            viewModel.triggerBeacon()
            Toast.makeText(requireContext(), "💡 Estado de Baliza Estroboscópica actualizado", Toast.LENGTH_SHORT).show()
        }

        binding.btnQuickHvac.setOnClickListener {
            appendTerminal("[${timeFormat.format(Date())}] TX: TOGGLE EXTRACTOR / CLIMA OT\n")
            viewModel.triggerHvac()
            Toast.makeText(requireContext(), "❄️ Estado de Climatización/Ventilación actualizado", Toast.LENGTH_SHORT).show()
        }

        binding.btnQuickReboot.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reinicio Remoto del Nodo ESP32")
                .setMessage("¿Deseas enviar un comando de reinicio (Soft Reset) al ESP32? El dispositivo restablecerá sus periféricos y reiniciará su ciclo de inicio.")
                .setPositiveButton("REINICIAR") { _, _ ->
                    appendTerminal("[${timeFormat.format(Date())}] TX: COMANDO SOFT-REBOOT\n")
                    viewModel.triggerReboot()
                    Toast.makeText(requireContext(), "🔄 Comando de reinicio transmitido", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("CANCELAR", null)
                .show()
        }

        // --- 2. ENTRADA DE COMANDOS MANUALES RAW ---
        binding.btnSendRawCommand.setOnClickListener {
            if (!sessionManager.canUniversalGpio(dbHelper)) {
                Toast.makeText(requireContext(), "Acceso denegado: Su rol no cuenta con privilegios de comandos GPIO", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val cmd = binding.etRawCommand.text.toString().trim()
            if (cmd.isNotEmpty()) {
                val timestamp = timeFormat.format(Date())
                appendTerminal("[$timestamp] TX: $cmd\n")
                viewModel.sendCommand(cmd)
                binding.etRawCommand.text?.clear()
            }
        }

        binding.btnClearTerminal.setOnClickListener {
            binding.tvTerminalOutput.text = "[${timeFormat.format(Date())}] Terminal limpiada.\n"
        }

        binding.btnAddWidget.setOnClickListener {
            if (!sessionManager.canUniversalGpio(dbHelper)) {
                Toast.makeText(requireContext(), "Acceso denegado: Se requiere permiso para configurar widgets GPIO", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showAddWidgetDialog()
        }
    }

    private fun showBistReportDialog(report: String) {
        activity?.runOnUiThread {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("🩺 Informe de Diagnóstico BIST (ESP32)")
                .setMessage(report)
                .setPositiveButton("ENTENDIDO", null)
                .show()
        }
    }

    private fun renderWidgets(widgets: List<GenericWidget>) {
        binding.layoutWidgetsContainer.removeAllViews()

        if (widgets.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = "No hay acciones o widgets configurados. Añade uno con el botón '+ Nueva Acción'."
                setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                textSize = 12f
                setPadding(0, 16, 0, 16)
            }
            binding.layoutWidgetsContainer.addView(emptyText)
            return
        }

        for (w in widgets) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 14) }
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface))
                radius = 20f
                strokeColor = ContextCompat.getColor(context, R.color.card_stroke)
                strokeWidth = 2
            }

            val contentLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 18, 20, 18)
            }

            // Fila superior con Nombre, Pin y Botón Eliminar
            val headerRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val label = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${w.name} (GPIO ${w.gpioPin})"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val btnDelete = MaterialButton(requireContext(), null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "✕"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.status_alarm))
                setOnClickListener {
                    confirmDeleteWidget(w)
                }
            }

            headerRow.addView(label)
            headerRow.addView(btnDelete)
            contentLayout.addView(headerRow)

            when (w.widgetType.uppercase()) {
                "SWITCH" -> {
                    val toggle = MaterialSwitch(requireContext()).apply {
                        isChecked = w.currentValue == 1
                        text = if (isChecked) "Estado: ENCENDIDO (HIGH)" else "Estado: APAGADO (LOW)"
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        setOnCheckedChangeListener { _, isChecked ->
                            val newVal = if (isChecked) 1 else 0
                            text = if (isChecked) "Estado: ENCENDIDO (HIGH)" else "Estado: APAGADO (LOW)"
                            viewModel.updateWidgetValue(w.id, newVal, w.gpioPin)
                            appendTerminal("[${timeFormat.format(Date())}] TX: GPIO ${w.gpioPin} -> $newVal\n")
                        }
                    }
                    contentLayout.addView(toggle)
                }

                "SLIDER" -> {
                    val statusText = TextView(requireContext()).apply {
                        text = "Valor: ${w.currentValue}° / PWM"
                        setTextColor(ContextCompat.getColor(context, R.color.primary))
                        textSize = 12f
                    }
                    val seek = SeekBar(requireContext()).apply {
                        max = 180
                        progress = w.currentValue
                        setPadding(0, 12, 0, 8)
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                if (fromUser) {
                                    statusText.text = "Valor: $progress° / PWM"
                                }
                            }
                            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                                val value = seekBar?.progress ?: 0
                                viewModel.updateWidgetValue(w.id, value, w.gpioPin)
                                appendTerminal("[${timeFormat.format(Date())}] TX: GPIO ${w.gpioPin} PWM -> $value\n")
                            }
                        })
                    }
                    contentLayout.addView(statusText)
                    contentLayout.addView(seek)
                }

                "TIMER_OPEN" -> {
                    val btn = MaterialButton(requireContext()).apply {
                        text = "⏱️ Activar Pulso Temporizado (GPIO ${w.gpioPin})"
                        setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                        setTextColor(ContextCompat.getColor(context, R.color.on_primary))
                        setOnClickListener {
                            viewModel.sendCommand("gpio_pulse", pin = w.gpioPin, valor = 1)
                            appendTerminal("[${timeFormat.format(Date())}] TX: Pulso temporizado GPIO ${w.gpioPin}\n")
                            Toast.makeText(context, "Pulso temporizado en GPIO ${w.gpioPin}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    contentLayout.addView(btn)
                }

                else -> { // BUTTON / ACCIÓN RÁPIDA
                    val btn = MaterialButton(requireContext()).apply {
                        text = "▶ Ejecutar: ${w.name} (Pin ${w.gpioPin})"
                        setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                        setTextColor(ContextCompat.getColor(context, R.color.on_primary))
                        setOnClickListener {
                            viewModel.sendCommand("gpio_pulse", pin = w.gpioPin, valor = 1)
                            appendTerminal("[${timeFormat.format(Date())}] TX: Pulso GPIO ${w.gpioPin}\n")
                            Toast.makeText(context, "Acción '${w.name}' enviada", Toast.LENGTH_SHORT).show()
                        }
                    }
                    contentLayout.addView(btn)
                }
            }

            card.addView(contentLayout)
            binding.layoutWidgetsContainer.addView(card)
        }
    }

    private fun confirmDeleteWidget(w: GenericWidget) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Acción")
            .setMessage("¿Deseas eliminar la acción '${w.name}' de tu lista personalizada?")
            .setPositiveButton("ELIMINAR") { _, _ ->
                viewModel.deleteWidget(w.id)
                Toast.makeText(requireContext(), "Acción eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun showAddWidgetDialog() {
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etName = EditText(requireContext()).apply {
            hint = "Nombre de la Acción (ej. Foco Patio, Extractor, Campana)"
        }
        val etPin = EditText(requireContext()).apply {
            hint = "Número de Pin GPIO (ej. 2, 4, 12, 13, 14, 15, 23)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val spType = Spinner(requireContext()).apply {
            adapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf(
                    "Interruptor ON/OFF (Luces / Relés)",
                    "Control Deslizante Servo / PWM (0 a 180)",
                    "Pulsador de Disparo (Pulso rápido)",
                    "Temporizador Programado (Pulso continuo)"
                )
            )
        }

        dialogView.addView(etName)
        dialogView.addView(etPin)
        dialogView.addView(spType)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nueva Acción / Widget ESP32")
            .setMessage("Define un nuevo comando de control para hardware conectado al ESP32:")
            .setView(dialogView)
            .setPositiveButton("AGREGAR") { _, _ ->
                val name = etName.text.toString().trim()
                val pin = etPin.text.toString().toIntOrNull() ?: 2
                val type = when (spType.selectedItemPosition) {
                    0 -> "SWITCH"
                    1 -> "SLIDER"
                    2 -> "BUTTON"
                    else -> "TIMER_OPEN"
                }
                if (name.isNotEmpty()) {
                    viewModel.addWidget(name, type, pin)
                    Toast.makeText(requireContext(), "Acción '$name' agregada exitosamente", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Ingresa un nombre para la acción", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun appendTerminal(line: String) {
        activity?.runOnUiThread {
            if (_binding != null) {
                binding.tvTerminalOutput.append(line)
                if (binding.tvTerminalOutput.text.length > 5000) {
                    val trimmed = binding.tvTerminalOutput.text.substring(2000)
                    binding.tvTerminalOutput.text = trimmed
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
