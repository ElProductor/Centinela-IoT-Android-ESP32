package com.inacap.centinelaiot.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.inacap.centinelaiot.data.database.model.AuditLog
import com.inacap.centinelaiot.data.database.model.Esp32Profile
import com.inacap.centinelaiot.data.database.model.GenericWidget
import com.inacap.centinelaiot.data.database.model.User
import com.inacap.centinelaiot.data.security.SecurityManager

/**
 * Gestor SQLite nativo según el estándar Android y la norma ISO 27400
 * para almacenamiento persistente y trazabilidad de eventos OT.
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "centinela_iot.db"
        private const val DATABASE_VERSION = 1

        // Tabla Usuarios
        const val TABLE_USERS = "users"
        const val COL_USER_ID = "id"
        const val COL_USERNAME = "username"
        const val COL_PASSWORD_HASH = "password_hash"
        const val COL_SALT = "salt"
        const val COL_ROLE = "role"
        const val COL_FULL_NAME = "full_name"
        const val COL_CREATED_AT = "created_at"

        // Tabla Auditoría (Audit Trail ISO 27400)
        const val TABLE_AUDIT_LOGS = "audit_logs"
        const val COL_LOG_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_EVENT_TYPE = "event_type"
        const val COL_LOG_USER = "username"
        const val COL_DETAILS = "details"
        const val COL_SEVERITY = "severity"

        // Tabla Perfiles ESP32 (Universal Hub)
        const val TABLE_PROFILES = "esp32_profiles"
        const val COL_PROFILE_ID = "id"
        const val COL_PROFILE_NAME = "name"
        const val COL_PROFILE_HOST = "host"
        const val COL_PROFILE_PORT = "port"
        const val COL_PROFILE_PROTO = "protocol"
        const val COL_PROFILE_TOKEN = "auth_token"
        const val COL_PROFILE_ACTIVE = "is_active"

        // Tabla Widgets Genéricos
        const val TABLE_WIDGETS = "generic_widgets"
        const val COL_WIDGET_ID = "id"
        const val COL_WIDGET_PROFILE_ID = "profile_id"
        const val COL_WIDGET_NAME = "name"
        const val COL_WIDGET_TYPE = "widget_type"
        const val COL_WIDGET_PIN = "gpio_pin"
        const val COL_WIDGET_VALUE = "current_value"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Tabla de Usuarios con roles y hashes seguros
        db.execSQL("""
            CREATE TABLE $TABLE_USERS (
                $COL_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_USERNAME TEXT UNIQUE NOT NULL,
                $COL_PASSWORD_HASH TEXT NOT NULL,
                $COL_SALT TEXT NOT NULL,
                $COL_ROLE TEXT NOT NULL,
                $COL_FULL_NAME TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent())

        // 2. Tabla de Auditoría Inmutable (ISO 27400)
        db.execSQL("""
            CREATE TABLE $TABLE_AUDIT_LOGS (
                $COL_LOG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_EVENT_TYPE TEXT NOT NULL,
                $COL_LOG_USER TEXT NOT NULL,
                $COL_DETAILS TEXT NOT NULL,
                $COL_SEVERITY TEXT NOT NULL
            )
        """.trimIndent())

        // 3. Tabla de Perfiles ESP32 Universal
        db.execSQL("""
            CREATE TABLE $TABLE_PROFILES (
                $COL_PROFILE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PROFILE_NAME TEXT NOT NULL,
                $COL_PROFILE_HOST TEXT NOT NULL,
                $COL_PROFILE_PORT INTEGER NOT NULL,
                $COL_PROFILE_PROTO TEXT NOT NULL,
                $COL_PROFILE_TOKEN TEXT,
                $COL_PROFILE_ACTIVE INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // 4. Tabla de Widgets de control genérico
        db.execSQL("""
            CREATE TABLE $TABLE_WIDGETS (
                $COL_WIDGET_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_WIDGET_PROFILE_ID INTEGER NOT NULL,
                $COL_WIDGET_NAME TEXT NOT NULL,
                $COL_WIDGET_TYPE TEXT NOT NULL,
                $COL_WIDGET_PIN INTEGER NOT NULL,
                $COL_WIDGET_VALUE INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // Pre-poblar usuarios de demostración
        prepopulateDefaultData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WIDGETS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PROFILES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_AUDIT_LOGS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    private fun prepopulateDefaultData(db: SQLiteDatabase) {
        // 1. Usuario Administrador (admin / admin123)
        insertUserWithDb(db, "admin", "admin123", "ADMIN", "Juan Pablo (Administrador OT)")

        // 2. Usuario Operador (operador / inacap2026)
        insertUserWithDb(db, "operador", "inacap2026", "OPERATOR", "Operador Centinela INACAP")

        // 3. Usuario Auditor (auditor / auditor123)
        insertUserWithDb(db, "auditor", "auditor123", "MONITOR", "Auditor de Seguridad ISO 27400")

        // Perfil por defecto: Centinela-IoT
        val profileValues = ContentValues().apply {
            put(COL_PROFILE_NAME, "Centinela-IoT Seguridad Edge")
            put(COL_PROFILE_HOST, "192.168.1.100")
            put(COL_PROFILE_PORT, 3000)
            put(COL_PROFILE_PROTO, "HTTP")
            put(COL_PROFILE_TOKEN, "OT-CENTINELA-2026")
            put(COL_PROFILE_ACTIVE, 1)
        }
        val profileId = db.insert(TABLE_PROFILES, null, profileValues)

        // Widgets de prueba para el perfil genérico
        val w1 = ContentValues().apply {
            put(COL_WIDGET_PROFILE_ID, profileId)
            put(COL_WIDGET_NAME, "Luz Indicadora Perímetro")
            put(COL_WIDGET_TYPE, "SWITCH")
            put(COL_WIDGET_PIN, 2)
            put(COL_WIDGET_VALUE, 0)
        }
        db.insert(TABLE_WIDGETS, null, w1)

        val w2 = ContentValues().apply {
            put(COL_WIDGET_PROFILE_ID, profileId)
            put(COL_WIDGET_NAME, "Posición Servomotor Pestillo")
            put(COL_WIDGET_TYPE, "SLIDER")
            put(COL_WIDGET_PIN, 13)
            put(COL_WIDGET_VALUE, 90)
        }
        db.insert(TABLE_WIDGETS, null, w2)

        val w3 = ContentValues().apply {
            put(COL_WIDGET_PROFILE_ID, profileId)
            put(COL_WIDGET_NAME, "Disparo Buzzer Alarma")
            put(COL_WIDGET_TYPE, "BUTTON")
            put(COL_WIDGET_PIN, 14)
            put(COL_WIDGET_VALUE, 0)
        }
        db.insert(TABLE_WIDGETS, null, w3)

        // Primer registro de auditoría de inicio
        val logValues = ContentValues().apply {
            put(COL_TIMESTAMP, System.currentTimeMillis())
            put(COL_EVENT_TYPE, "SYSTEM_INIT")
            put(COL_LOG_USER, "SYSTEM")
            put(COL_DETAILS, "Base de datos inicializada bajo estándar ISO/IEC 27400:2022")
            put(COL_SEVERITY, "INFO")
        }
        db.insert(TABLE_AUDIT_LOGS, null, logValues)
    }

    private fun insertUserWithDb(db: SQLiteDatabase, user: String, pass: String, role: String, fullName: String) {
        val salt = SecurityManager.generateSalt()
        val hash = SecurityManager.hashPassword(pass, salt)
        val values = ContentValues().apply {
            put(COL_USERNAME, user)
            put(COL_PASSWORD_HASH, hash)
            put(COL_SALT, salt)
            put(COL_ROLE, role)
            put(COL_FULL_NAME, fullName)
            put(COL_CREATED_AT, System.currentTimeMillis())
        }
        db.insert(TABLE_USERS, null, values)
    }

    // ==========================================
    // OPERACIONES DE USUARIOS Y AUTENTICACIÓN
    // ==========================================

    fun authenticateUser(username: String, pass: String): User? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            null,
            "$COL_USERNAME = ?",
            arrayOf(username.trim()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val storedHash = it.getString(it.getColumnIndexOrThrow(COL_PASSWORD_HASH))
                val salt = it.getString(it.getColumnIndexOrThrow(COL_SALT))
                if (SecurityManager.verifyPassword(pass, storedHash, salt)) {
                    return User(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_USER_ID)),
                        username = it.getString(it.getColumnIndexOrThrow(COL_USERNAME)),
                        passwordHash = storedHash,
                        salt = salt,
                        role = it.getString(it.getColumnIndexOrThrow(COL_ROLE)),
                        fullName = it.getString(it.getColumnIndexOrThrow(COL_FULL_NAME)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COL_CREATED_AT))
                    )
                }
            }
        }
        return null
    }

    // ==========================================
    // OPERACIONES DE AUDITORÍA (ISO 27400)
    // ==========================================

    fun logEvent(eventType: String, username: String, details: String, severity: String = "INFO"): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, System.currentTimeMillis())
            put(COL_EVENT_TYPE, eventType)
            put(COL_LOG_USER, username)
            put(COL_DETAILS, details)
            put(COL_SEVERITY, severity)
        }
        return db.insert(TABLE_AUDIT_LOGS, null, values)
    }

    fun getAllAuditLogs(): List<AuditLog> {
        val list = mutableListOf<AuditLog>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_AUDIT_LOGS,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    AuditLog(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_LOG_ID)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                        eventType = it.getString(it.getColumnIndexOrThrow(COL_EVENT_TYPE)),
                        username = it.getString(it.getColumnIndexOrThrow(COL_LOG_USER)),
                        details = it.getString(it.getColumnIndexOrThrow(COL_DETAILS)),
                        severity = it.getString(it.getColumnIndexOrThrow(COL_SEVERITY))
                    )
                )
            }
        }
        return list
    }

    fun clearAuditLogs() {
        writableDatabase.delete(TABLE_AUDIT_LOGS, null, null)
    }

    // ==========================================
    // OPERACIONES DE PERFILES Y WIDGETS GENÉRICOS
    // ==========================================

    fun getAllProfiles(): List<Esp32Profile> {
        val list = mutableListOf<Esp32Profile>()
        val cursor = readableDatabase.query(TABLE_PROFILES, null, null, null, null, null, "$COL_PROFILE_NAME ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Esp32Profile(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_PROFILE_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(COL_PROFILE_NAME)),
                        host = it.getString(it.getColumnIndexOrThrow(COL_PROFILE_HOST)),
                        port = it.getInt(it.getColumnIndexOrThrow(COL_PROFILE_PORT)),
                        protocol = it.getString(it.getColumnIndexOrThrow(COL_PROFILE_PROTO)),
                        authToken = it.getString(it.getColumnIndexOrThrow(COL_PROFILE_TOKEN)) ?: "",
                        isActive = it.getInt(it.getColumnIndexOrThrow(COL_PROFILE_ACTIVE)) == 1
                    )
                )
            }
        }
        return list
    }

    fun insertProfile(profile: Esp32Profile): Long {
        val values = ContentValues().apply {
            put(COL_PROFILE_NAME, profile.name)
            put(COL_PROFILE_HOST, profile.host)
            put(COL_PROFILE_PORT, profile.port)
            put(COL_PROFILE_PROTO, profile.protocol)
            put(COL_PROFILE_TOKEN, profile.authToken)
            put(COL_PROFILE_ACTIVE, if (profile.isActive) 1 else 0)
        }
        return writableDatabase.insert(TABLE_PROFILES, null, values)
    }

    fun getWidgets(): List<GenericWidget> {
        val list = mutableListOf<GenericWidget>()
        val cursor = readableDatabase.query(TABLE_WIDGETS, null, null, null, null, null, "$COL_WIDGET_NAME ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    GenericWidget(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_WIDGET_ID)),
                        profileId = it.getLong(it.getColumnIndexOrThrow(COL_WIDGET_PROFILE_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(COL_WIDGET_NAME)),
                        widgetType = it.getString(it.getColumnIndexOrThrow(COL_WIDGET_TYPE)),
                        gpioPin = it.getInt(it.getColumnIndexOrThrow(COL_WIDGET_PIN)),
                        currentValue = it.getInt(it.getColumnIndexOrThrow(COL_WIDGET_VALUE))
                    )
                )
            }
        }
        return list
    }

    fun insertWidget(widget: GenericWidget): Long {
        val values = ContentValues().apply {
            put(COL_WIDGET_PROFILE_ID, widget.profileId)
            put(COL_WIDGET_NAME, widget.name)
            put(COL_WIDGET_TYPE, widget.widgetType)
            put(COL_WIDGET_PIN, widget.gpioPin)
            put(COL_WIDGET_VALUE, widget.currentValue)
        }
        return writableDatabase.insert(TABLE_WIDGETS, null, values)
    }

    fun updateWidgetValue(widgetId: Long, value: Int) {
        val values = ContentValues().apply {
            put(COL_WIDGET_VALUE, value)
        }
        writableDatabase.update(TABLE_WIDGETS, values, "$COL_WIDGET_ID = ?", arrayOf(widgetId.toString()))
    }
}
