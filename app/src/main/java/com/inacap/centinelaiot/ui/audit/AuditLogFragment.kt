package com.inacap.centinelaiot.ui.audit

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.inacap.centinelaiot.CentinelaApp
import com.inacap.centinelaiot.databinding.FragmentAuditLogBinding
import com.inacap.centinelaiot.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditLogFragment : Fragment() {

    private var _binding: FragmentAuditLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val sessionManager by lazy { CentinelaApp.instance.sessionManager }
    private val adapter = AuditLogAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuditLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvAuditLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAuditLogs.adapter = adapter

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.auditLogs.observe(viewLifecycleOwner) { logs ->
            adapter.updateLogs(logs)
            binding.tvLogsCount.text = "Eventos Registrados en SQLite: ${logs.size} registros"
        }
    }

    private fun setupListeners() {
        binding.btnClearAudit.setOnClickListener {
            if (!sessionManager.isAdmin) {
                Toast.makeText(requireContext(), "Acceso denegado: Solo el ADMIN puede purgar registros (ISO 27400)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Confirmar Purga de Auditoría")
                .setMessage("¿Está seguro de que desea eliminar todos los registros de auditoría? Esta acción dejará constancia.")
                .setPositiveButton("PURGAR") { _, _ ->
                    viewModel.clearAuditLogs()
                    Toast.makeText(requireContext(), "Registros eliminados", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("CANCELAR", null)
                .show()
        }

        binding.btnExportAudit.setOnClickListener {
            val logs = viewModel.auditLogs.value ?: emptyList()
            if (logs.isEmpty()) {
                Toast.makeText(requireContext(), "No hay registros para exportar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val sb = StringBuilder()
            sb.append("=== INFORME DE AUDITORÍA OT - CENTINELA IOT (ISO/IEC 27400) ===\n")
            sb.append("Generado: ${dateFormat.format(Date())}\n")
            sb.append("Usuario Solicitante: ${sessionManager.username} (${sessionManager.role})\n\n")

            for (log in logs) {
                sb.append("[${dateFormat.format(Date(log.timestamp))}] [${log.severity}] ${log.eventType}\n")
                sb.append("  Usuario: ${log.username} | Detalle: ${log.details}\n\n")
            }

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, sb.toString())
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Exportar Registro de Auditoría"))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAuditLogs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
