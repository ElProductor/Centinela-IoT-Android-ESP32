package com.inacap.centinelaiot.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.inacap.centinelaiot.CentinelaApp
import com.inacap.centinelaiot.data.simulator.Esp32VirtualSimulator
import com.inacap.centinelaiot.databinding.FragmentSettingsBinding
import com.inacap.centinelaiot.ui.auth.LoginActivity
import com.inacap.centinelaiot.ui.viewmodel.MainViewModel

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val sessionManager by lazy { CentinelaApp.instance.sessionManager }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        binding.tvCurrentUserName.text = sessionManager.fullName
        binding.tvCurrentUserRole.text = "Rol Activo: ${sessionManager.role} • Estándar ISO/IEC 27400:2022"
        val tokenPreview = if (sessionManager.token.length > 16) {
            sessionManager.token.take(8) + "..." + sessionManager.token.takeLast(8)
        } else {
            sessionManager.token
        }
        binding.tvSessionToken.text = "Token de Sesión OT: $tokenPreview"

        binding.switchSimulatorMode.isChecked = sessionManager.isSimulatorActive
        binding.etEsp32Host.setText(sessionManager.esp32Host)
        binding.etEsp32Port.setText(sessionManager.esp32Port.toString())
        binding.etUmbralDist.setText(sessionManager.umbralDistancia.toString())
        binding.etUmbralLuz.setText(sessionManager.umbralLuz.toString())
    }

    private fun setupListeners() {
        binding.switchSimulatorMode.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.isSimulatorActive = isChecked
            if (isChecked) {
                Esp32VirtualSimulator.start()
                Toast.makeText(requireContext(), "Simulador Virtual ESP32 Activado", Toast.LENGTH_SHORT).show()
            } else {
                Esp32VirtualSimulator.stop()
                Toast.makeText(requireContext(), "Modo Hardware Real Activado (Conectando a Wi-Fi)", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSaveSettings.setOnClickListener {
            val host = binding.etEsp32Host.text.toString().trim()
            val port = binding.etEsp32Port.text.toString().toIntOrNull() ?: 3000
            val dist = binding.etUmbralDist.text.toString().toIntOrNull() ?: 50
            val luz = binding.etUmbralLuz.text.toString().toIntOrNull() ?: 800

            sessionManager.esp32Host = host
            sessionManager.esp32Port = port
            sessionManager.umbralDistancia = dist
            sessionManager.umbralLuz = luz

            viewModel.recordAudit(
                eventType = "CONFIG_UPDATED",
                details = "Parámetros actualizados: Host=$host:$port, UmbralDist=$dist cm, UmbralLuz=$luz ADC",
                severity = "INFO"
            )

            Toast.makeText(requireContext(), "Configuración guardada correctamente", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            viewModel.recordAudit(
                eventType = "AUTH_LOGOUT",
                details = "Cierre de sesión manual",
                severity = "INFO"
            )
            sessionManager.clearSession()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
