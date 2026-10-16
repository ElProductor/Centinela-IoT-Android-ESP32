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
            val logLine = "[$timestamp] RX: {\"dist\":${data.distancia}, \"luz\":${data.nivelLuz}, \"alarma\":${data.alarmaActiva}, \"pestillo\":${data.pestilloGrados}}\n"
            appendTerminal(logLine)
        }
    }

    private fun setupListeners() {
        // Enviar comando manual en texto o JSON
        binding.btnSendRawCommand.setOnClickListener {
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
            showAddWidgetDialog()
        }
    }

    private fun renderWidgets(widgets: List<GenericWidget>) {
        binding.layoutWidgetsContainer.removeAllViews()

        if (widgets.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = "No hay widgets configurados. Añade uno con el botón superior."
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
                ).apply { setMargins(0, 0, 0, 16) }
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface))
                radius = 24f
                strokeColor = ContextCompat.getColor(context, R.color.card_stroke)
                strokeWidth = 2
            }

            val contentLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
            }

            when (w.widgetType.uppercase()) {
                "SWITCH" -> {
                    val row = LinearLayout(requireContext()).apply {
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
                    val toggle = MaterialSwitch(requireContext()).apply {
                        isChecked = w.currentValue == 1
                        setOnCheckedChangeListener { _, isChecked ->
                            val newVal = if (isChecked) 1 else 0
                            viewModel.updateWidgetValue(w.id, newVal, w.gpioPin)
                            appendTerminal("[${timeFormat.format(Date())}] TX: GPIO ${w.gpioPin} -> $newVal\n")
                        }
                    }
                    row.addView(label)
                    row.addView(toggle)
                    contentLayout.addView(row)
                }

                "SLIDER" -> {
                    val label = TextView(requireContext()).apply {
                        text = "${w.name} (GPIO ${w.gpioPin}): ${w.currentValue}° / PWM"
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        textSize = 14f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    val seek = SeekBar(requireContext()).apply {
                        max = 180
                        progress = w.currentValue
                        setPadding(0, 16, 0, 8)
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                if (fromUser) {
                                    label.text = "${w.name} (GPIO ${w.gpioPin}): $progress° / PWM"
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
                    contentLayout.addView(label)
                    contentLayout.addView(seek)
                }

                else -> { // BUTTON
                    val btn = MaterialButton(requireContext()).apply {
                        text = "Activar Pulso: ${w.name} (GPIO ${w.gpioPin})"
                        setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                        setTextColor(ContextCompat.getColor(context, R.color.on_primary))
                        setOnClickListener {
                            viewModel.sendCommand("gpio_pulse", pin = w.gpioPin, valor = 1)
                            appendTerminal("[${timeFormat.format(Date())}] TX: Pulso GPIO ${w.gpioPin}\n")
                            Toast.makeText(context, "Pulso enviado a GPIO ${w.gpioPin}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    contentLayout.addView(btn)
                }
            }

            card.addView(contentLayout)
            binding.layoutWidgetsContainer.addView(card)
        }
    }

    private fun showAddWidgetDialog() {
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val etName = EditText(requireContext()).apply {
            hint = "Nombre del Actuador / Sensor"
        }
        val etPin = EditText(requireContext()).apply {
            hint = "Número de Pin GPIO (ej. 2, 12, 13)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val spType = Spinner(requireContext()).apply {
            adapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Interruptor Digital (SWITCH)", "Control Deslizante (SLIDER)", "Pulsador de Disparo (BUTTON)")
            )
        }

        dialogView.addView(etName)
        dialogView.addView(etPin)
        dialogView.addView(spType)

        AlertDialog.Builder(requireContext())
            .setTitle("Nuevo Widget ESP32")
            .setView(dialogView)
            .setPositiveButton("AGREGAR") { _, _ ->
                val name = etName.text.toString().trim()
                val pin = etPin.text.toString().toIntOrNull() ?: 2
                val type = when (spType.selectedItemPosition) {
                    0 -> "SWITCH"
                    1 -> "SLIDER"
                    else -> "BUTTON"
                }
                if (name.isNotEmpty()) {
                    viewModel.addWidget(name, type, pin)
                    Toast.makeText(requireContext(), "Widget añadido con éxito", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun appendTerminal(line: String) {
        activity?.runOnUiThread {
            if (_binding != null) {
                binding.tvTerminalOutput.append(line)
                // Mantener los últimos 4000 caracteres
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
