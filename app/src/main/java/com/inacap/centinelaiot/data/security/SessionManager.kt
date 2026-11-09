package com.inacap.centinelaiot.data.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor de Sesión y Preferencias persistentes en el dispositivo.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "centinela_iot_session"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USERNAME = "username"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_ROLE = "role"
        private const val KEY_TOKEN = "session_token"

        // Configuración de Conexión ESP32
        private const val KEY_ESP32_HOST = "esp32_host"
        private const val KEY_ESP32_PORT = "esp32_port"
        private const val KEY_ESP32_USE_SIMULATOR = "esp32_use_simulator"
        private const val KEY_UMBRAL_DIST = "umbral_distancia"
        private const val KEY_UMBRAL_LUZ = "umbral_luz"

        // Configuración de Conexión Supabase BaaS (Nube PostgreSQL)
        private const val KEY_SUPABASE_URL = "supabase_url"
        private const val KEY_SUPABASE_KEY = "supabase_anon_key"
        private const val KEY_SUPABASE_ENABLED = "supabase_sync_enabled"
        private const val KEY_SUPABASE_LAST_SYNC = "supabase_last_sync"

        // Valores por defecto
        const val DEFAULT_HOST = "192.168.1.100"
        const val DEFAULT_PORT = 3000
        const val DEFAULT_UMBRAL_DIST = 50 // cm
        const val DEFAULT_UMBRAL_LUZ = 800  // ADC 12 bits
    }

    fun saveSession(username: String, fullName: String, role: String, token: String) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USERNAME, username)
            putString(KEY_FULL_NAME, fullName)
            putString(KEY_ROLE, role)
            putString(KEY_TOKEN, token)
            apply()
        }
    }

    fun clearSession() {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USERNAME)
            remove(KEY_FULL_NAME)
            remove(KEY_ROLE)
            remove(KEY_TOKEN)
            apply()
        }
    }

    val isLoggedIn: Boolean get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    val username: String get() = prefs.getString(KEY_USERNAME, "") ?: ""
    val fullName: String get() = prefs.getString(KEY_FULL_NAME, "Operador") ?: "Operador"
    val role: String get() = prefs.getString(KEY_ROLE, "OPERATOR") ?: "OPERATOR"
    val token: String get() = prefs.getString(KEY_TOKEN, "") ?: ""

    // Roles permitidos
    val isAdmin: Boolean get() = role.equals("ADMIN", ignoreCase = true)
    val isOperator: Boolean get() = isAdmin || role.equals("OPERATOR", ignoreCase = true)

    // ESP32 Settings
    var esp32Host: String
        get() = prefs.getString(KEY_ESP32_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) = prefs.edit().putString(KEY_ESP32_HOST, value).apply()

    var esp32Port: Int
        get() = prefs.getInt(KEY_ESP32_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_ESP32_PORT, value).apply()

    var isSimulatorActive: Boolean
        get() = prefs.getBoolean(KEY_ESP32_USE_SIMULATOR, true) // Activo por defecto para que funcione de inmediato
        set(value) = prefs.edit().putBoolean(KEY_ESP32_USE_SIMULATOR, value).apply()

    var umbralDistancia: Int
        get() = prefs.getInt(KEY_UMBRAL_DIST, DEFAULT_UMBRAL_DIST)
        set(value) = prefs.edit().putInt(KEY_UMBRAL_DIST, value).apply()

    var umbralLuz: Int
        get() = prefs.getInt(KEY_UMBRAL_LUZ, DEFAULT_UMBRAL_LUZ)
        set(value) = prefs.edit().putInt(KEY_UMBRAL_LUZ, value).apply()

    val baseUrl: String
        get() = "http://$esp32Host:$esp32Port/"

    // Supabase Settings
    var supabaseUrl: String
        get() = prefs.getString(KEY_SUPABASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUPABASE_URL, value.trim()).apply()

    var supabaseKey: String
        get() = prefs.getString(KEY_SUPABASE_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUPABASE_KEY, value.trim()).apply()

    var isSupabaseEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUPABASE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SUPABASE_ENABLED, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_SUPABASE_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_SUPABASE_LAST_SYNC, value).apply()

    // Verificación de Permisos Dinámicos (RBAC)
    fun canManageUsers(dbHelper: com.inacap.centinelaiot.data.database.DatabaseHelper): Boolean {
        if (isAdmin) return true
        val r = dbHelper.getRole(role)
        return r?.canManageUsers == true
    }

    fun canDisarm(dbHelper: com.inacap.centinelaiot.data.database.DatabaseHelper): Boolean {
        if (isAdmin || isOperator) return true
        val r = dbHelper.getRole(role)
        return r?.canDisarm == true
    }

    fun canRemoteOpen(dbHelper: com.inacap.centinelaiot.data.database.DatabaseHelper): Boolean {
        if (isAdmin || isOperator) return true
        val r = dbHelper.getRole(role)
        return r?.canRemoteOpen == true
    }

    fun canUniversalGpio(dbHelper: com.inacap.centinelaiot.data.database.DatabaseHelper): Boolean {
        if (isAdmin || isOperator) return true
        val r = dbHelper.getRole(role)
        return r?.canUniversalGpio == true
    }
}
