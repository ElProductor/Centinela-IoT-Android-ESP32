package com.inacap.centinelaiot.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.inacap.centinelaiot.CentinelaApp
import com.inacap.centinelaiot.R
import com.inacap.centinelaiot.data.simulator.Esp32VirtualSimulator
import com.inacap.centinelaiot.data.supabase.SupabaseClient
import com.inacap.centinelaiot.databinding.FragmentSettingsBinding
import com.inacap.centinelaiot.ui.admin.AdminManagementActivity
import com.inacap.centinelaiot.ui.auth.LoginActivity
import com.inacap.centinelaiot.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val sessionManager by lazy { CentinelaApp.instance.sessionManager }
    private val dbHelper by lazy { CentinelaApp.instance.databaseHelper }

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

        // Configuración Supabase
        binding.switchSupabaseSync.isChecked = sessionManager.isSupabaseEnabled
        binding.etSupabaseUrl.setText(sessionManager.supabaseUrl)
        binding.etSupabaseKey.setText(sessionManager.supabaseKey)

        if (sessionManager.lastSyncTimestamp > 0) {
            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(sessionManager.lastSyncTimestamp))
            binding.tvSupabaseStatus.text = "Última Sincronización: $dateStr"
        } else {
            binding.tvSupabaseStatus.text = "Estado: No sincronizado aún"
        }

        // Visibilidad Panel Administrador (Solo si tiene permisos de gestión de usuarios)
        binding.cardAdminPanel.visibility = if (sessionManager.canManageUsers(dbHelper)) {
            View.VISIBLE
        } else {
            View.GONE
        }
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

        binding.switchSupabaseSync.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.isSupabaseEnabled = isChecked
            val statusMsg = if (isChecked) "Sincronización con Supabase Activada" else "Sincronización con Supabase Desactivada"
            Toast.makeText(requireContext(), statusMsg, Toast.LENGTH_SHORT).show()
        }

        binding.btnTestSupabase.setOnClickListener {
            val url = binding.etSupabaseUrl.text.toString().trim()
            val key = binding.etSupabaseKey.text.toString().trim()

            if (url.isBlank() || key.isBlank()) {
                Toast.makeText(requireContext(), "Ingresa la URL y Anon Key de tu proyecto Supabase", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.tvSupabaseStatus.text = "Probando conexión con Supabase..."
            binding.tvSupabaseStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))

            lifecycleScope.launch {
                val (success, message) = SupabaseClient.testConnection(url, key)
                binding.tvSupabaseStatus.text = message
                val color = if (success) {
                    ContextCompat.getColor(requireContext(), R.color.status_safe)
                } else {
                    ContextCompat.getColor(requireContext(), R.color.status_alarm)
                }
                binding.tvSupabaseStatus.setTextColor(color)
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnSyncSupabase.setOnClickListener {
            val url = binding.etSupabaseUrl.text.toString().trim()
            val key = binding.etSupabaseKey.text.toString().trim()

            sessionManager.supabaseUrl = url
            sessionManager.supabaseKey = key
            sessionManager.isSupabaseEnabled = true
            binding.switchSupabaseSync.isChecked = true

            binding.tvSupabaseStatus.text = "Sincronizando con Supabase Cloud..."
            binding.tvSupabaseStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))

            lifecycleScope.launch {
                val (success, message) = SupabaseClient.syncAll(dbHelper, sessionManager)
                binding.tvSupabaseStatus.text = message
                val color = if (success) {
                    ContextCompat.getColor(requireContext(), R.color.status_safe)
                } else {
                    ContextCompat.getColor(requireContext(), R.color.status_alarm)
                }
                binding.tvSupabaseStatus.setTextColor(color)
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnCopySqlScript.setOnClickListener {
            val script = SupabaseClient.getSqlSchemaScript()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Script SQL para Supabase")
                .setMessage("Copia este script y ejecútalo en el SQL Editor de tu proyecto Supabase para habilitar las tablas de roles, usuarios, auditoría y telemetría.")
                .setPositiveButton("Copiar al Portapapeles") { _, _ ->
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Supabase Centinela-IoT Schema", script)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Script SQL copiado al portapapeles", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }

        binding.btnOpenAdminPanel.setOnClickListener {
            val intent = Intent(requireContext(), AdminManagementActivity::class.java)
            startActivity(intent)
        }

        binding.btnSaveSettings.setOnClickListener {
            val host = binding.etEsp32Host.text.toString().trim()
            val port = binding.etEsp32Port.text.toString().toIntOrNull() ?: 3000
            val dist = binding.etUmbralDist.text.toString().toIntOrNull() ?: 50
            val luz = binding.etUmbralLuz.text.toString().toIntOrNull() ?: 800

            val supaUrl = binding.etSupabaseUrl.text.toString().trim()
            val supaKey = binding.etSupabaseKey.text.toString().trim()

            sessionManager.esp32Host = host
            sessionManager.esp32Port = port
            sessionManager.umbralDistancia = dist
            sessionManager.umbralLuz = luz

            sessionManager.supabaseUrl = supaUrl
            sessionManager.supabaseKey = supaKey

            viewModel.recordAudit(
                eventType = "CONFIG_UPDATED",
                details = "Parámetros actualizados: Host=$host:$port, Dist=$dist cm, Luz=$luz ADC, Supabase=${if (sessionManager.isSupabaseEnabled) "ON" else "OFF"}",
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
