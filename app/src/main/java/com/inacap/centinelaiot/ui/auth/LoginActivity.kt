package com.inacap.centinelaiot.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.inacap.centinelaiot.CentinelaApp
import com.inacap.centinelaiot.R
import com.inacap.centinelaiot.data.security.SecurityManager
import com.inacap.centinelaiot.data.supabase.SupabaseClient
import com.inacap.centinelaiot.databinding.ActivityLoginBinding
import com.inacap.centinelaiot.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val sessionManager by lazy { CentinelaApp.instance.sessionManager }
    private val dbHelper by lazy { CentinelaApp.instance.database }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si ya hay una sesión activa, pasar directo al Dashboard
        if (sessionManager.isLoggedIn) {
            navigateToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        // Botón principal de Login
        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        // Atajos demo para la evaluación de clase
        binding.btnQuickAdmin.setOnClickListener {
            binding.etUsername.setText("admin")
            binding.etPassword.setText("admin123")
        }

        binding.btnQuickOperator.setOnClickListener {
            binding.etUsername.setText("operador")
            binding.etPassword.setText("inacap2026")
        }

        binding.btnQuickAuditor.setOnClickListener {
            binding.etUsername.setText("auditor")
            binding.etPassword.setText("auditor123")
        }
    }

    private fun performLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor ingrese usuario y contraseña", Toast.LENGTH_SHORT).show()
            return
        }

        val user = dbHelper.authenticateUser(username, password)
        if (user != null) {
            val token = SecurityManager.generateSessionToken(user.username)
            sessionManager.saveSession(user.username, user.fullName, user.role, token)

            // Registro en Auditoría ISO 27400
            val ts = System.currentTimeMillis()
            val details = "Inicio de sesión exitoso. Rol otorgado: ${user.role}"
            dbHelper.logEvent(
                eventType = "AUTH_LOGIN_SUCCESS",
                username = user.username,
                details = details,
                severity = "INFO",
                timestamp = ts
            )
            if (sessionManager.isSupabaseEnabled) {
                CoroutineScope(Dispatchers.IO).launch {
                    SupabaseClient.pushAuditLog("AUTH_LOGIN_SUCCESS", user.username, details, "INFO", ts, sessionManager)
                }
            }

            Toast.makeText(this, "Bienvenido, ${user.fullName}", Toast.LENGTH_SHORT).show()
            navigateToMain()
        } else {
            val ts = System.currentTimeMillis()
            val details = "Intento fallido de autenticación desde la aplicación móvil"
            dbHelper.logEvent(
                eventType = "AUTH_LOGIN_FAILED",
                username = username,
                details = details,
                severity = "WARNING",
                timestamp = ts
            )
            if (sessionManager.isSupabaseEnabled) {
                CoroutineScope(Dispatchers.IO).launch {
                    SupabaseClient.pushAuditLog("AUTH_LOGIN_FAILED", username, details, "WARNING", ts, sessionManager)
                }
            }
            Toast.makeText(this, getString(R.string.error_invalid_credentials), Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
