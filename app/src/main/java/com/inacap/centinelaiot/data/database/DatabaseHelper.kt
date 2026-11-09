package com.inacap.centinelaiot.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.inacap.centinelaiot.data.database.model.AuditLog
import com.inacap.centinelaiot.data.database.model.Esp32Profile
import com.inacap.centinelaiot.data.database.model.GenericWidget
import com.inacap.centinelaiot.data.database.model.Role
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
        private const val DATABASE_VERSION = 2

        // Tabla Roles
        const val TABLE_ROLES = "roles"
        const val COL_ROLE_NAME = "name"
        const val COL_ROLE_DESC = "description"
        const val COL_CAN_DISARM = "can_disarm"
        const val COL_CAN_REMOTE_OPEN = "can_remote_open"
        const val COL_CAN_UNIVERSAL_GPIO = "can_universal_gpio"
        const val COL_CAN_MANAGE_USERS = "can_manage_users"
        const val COL_CAN_VIEW_AUDIT = "can_view_audit"
        const val COL_IS_SYSTEM = "is_system"
        const val COL_ROLE_CREATED_AT = "created_at"

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
        // 0. Tabla de Roles Dinámicos (RBAC ISO 27400)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_ROLES (
                $COL_ROLE_NAME TEXT PRIMARY KEY,
                $COL_ROLE_DESC TEXT,
                $COL_CAN_DISARM INTEGER NOT NULL DEFAULT 0,
                $COL_CAN_REMOTE_OPEN INTEGER NOT NULL DEFAULT 0,
                $COL_CAN_UNIVERSAL_GPIO INTEGER NOT NULL DEFAULT 0,
                $COL_CAN_MANAGE_USERS INTEGER NOT NULL DEFAULT 0,
                $COL_CAN_VIEW_AUDIT INTEGER NOT NULL DEFAULT 1,
                $COL_IS_SYSTEM INTEGER NOT NULL DEFAULT 0,
                $COL_ROLE_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent())

        // 1. Tabla de Usuarios con roles y hashes seguros
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_USERS (
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
            CREATE TABLE IF NOT EXISTS $TABLE_AUDIT_LOGS (
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
            CREATE TABLE IF NOT EXISTS $TABLE_PROFILES (
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
            CREATE TABLE IF NOT EXISTS $TABLE_WIDGETS (
                $COL_WIDGET_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_WIDGET_PROFILE_ID INTEGER NOT NULL,
                $COL_WIDGET_NAME TEXT NOT NULL,
                $COL_WIDGET_TYPE TEXT NOT NULL,
                $COL_WIDGET_PIN INTEGER NOT NULL,
                $COL_WIDGET_VALUE INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // Pre-poblar datos de fábrica
        prepopulateDefaultData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Migración no destructiva a V2: Crear tabla de roles si no existe
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_ROLES (
                    $COL_ROLE_NAME TEXT PRIMARY KEY,
                    $COL_ROLE_DESC TEXT,
                    $COL_CAN_DISARM INTEGER NOT NULL DEFAULT 0,
                    $COL_CAN_REMOTE_OPEN INTEGER NOT NULL DEFAULT 0,
                    $COL_CAN_UNIVERSAL_GPIO INTEGER NOT NULL DEFAULT 0,
                    $COL_CAN_MANAGE_USERS INTEGER NOT NULL DEFAULT 0,
                    $COL_CAN_VIEW_AUDIT INTEGER NOT NULL DEFAULT 1,
                    $COL_IS_SYSTEM INTEGER NOT NULL DEFAULT 0,
                    $COL_ROLE_CREATED_AT INTEGER NOT NULL
                )
            """.trimIndent())
            seedDefaultRoles(db)
        }
    }

    private fun prepopulateDefaultData(db: SQLiteDatabase) {
        // Roles de fábrica
        seedDefaultRoles(db)

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

    private fun seedDefaultRoles(db: SQLiteDatabase) {
        insertRoleWithDb(db, Role(
            name = "ADMIN",
            description = "Administrador total del sistema: gestión de roles, usuarios, parámetros y anulación remota",
            canDisarm = true,
            canRemoteOpen = true,
            canUniversalGpio = true,
            canManageUsers = true,
            canViewAudit = true,
            isSystem = true
        ))

        insertRoleWithDb(db, Role(
            name = "OPERATOR",
            description = "Operador de planta: supervisión, desarme perimetral, apertura de pestillo y control GPIO",
            canDisarm = true,
            canRemoteOpen = true,
            canUniversalGpio = true,
            canManageUsers = false,
            canViewAudit = true,
            isSystem = true
        ))

        insertRoleWithDb(db, Role(
            name = "MONITOR",
            description = "Auditor/Monitor de seguridad: solo lectura de telemetría y registros de auditoría",
            canDisarm = false,
            canRemoteOpen = false,
            canUniversalGpio = false,
            canManageUsers = false,
            canViewAudit = true,
            isSystem = true
        ))
    }

    private fun insertRoleWithDb(db: SQLiteDatabase, role: Role): Long {
        val values = ContentValues().apply {
            put(COL_ROLE_NAME, role.name.uppercase())
            put(COL_ROLE_DESC, role.description)
            put(COL_CAN_DISARM, if (role.canDisarm) 1 else 0)
            put(COL_CAN_REMOTE_OPEN, if (role.canRemoteOpen) 1 else 0)
            put(COL_CAN_UNIVERSAL_GPIO, if (role.canUniversalGpio) 1 else 0)
            put(COL_CAN_MANAGE_USERS, if (role.canManageUsers) 1 else 0)
            put(COL_CAN_VIEW_AUDIT, if (role.canViewAudit) 1 else 0)
            put(COL_IS_SYSTEM, if (role.isSystem) 1 else 0)
            put(COL_ROLE_CREATED_AT, role.createdAt)
        }
        return db.insertWithOnConflict(TABLE_ROLES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
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
    // OPERACIONES DE ROLES (RBAC DINÁMICO)
    // ==========================================

    fun getAllRoles(): List<Role> {
        val list = mutableListOf<Role>()
        val db = readableDatabase
        val cursor = db.query(TABLE_ROLES, null, null, null, null, null, "$COL_ROLE_NAME ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Role(
                        name = it.getString(it.getColumnIndexOrThrow(COL_ROLE_NAME)),
                        description = it.getString(it.getColumnIndexOrThrow(COL_ROLE_DESC)) ?: "",
                        canDisarm = it.getInt(it.getColumnIndexOrThrow(COL_CAN_DISARM)) == 1,
                        canRemoteOpen = it.getInt(it.getColumnIndexOrThrow(COL_CAN_REMOTE_OPEN)) == 1,
                        canUniversalGpio = it.getInt(it.getColumnIndexOrThrow(COL_CAN_UNIVERSAL_GPIO)) == 1,
                        canManageUsers = it.getInt(it.getColumnIndexOrThrow(COL_CAN_MANAGE_USERS)) == 1,
                        canViewAudit = it.getInt(it.getColumnIndexOrThrow(COL_CAN_VIEW_AUDIT)) == 1,
                        isSystem = it.getInt(it.getColumnIndexOrThrow(COL_IS_SYSTEM)) == 1,
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COL_ROLE_CREATED_AT))
                    )
                )
            }
        }
        return list
    }

    fun getRole(roleName: String): Role? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ROLES,
            null,
            "$COL_ROLE_NAME = ?",
            arrayOf(roleName.uppercase().trim()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return Role(
                    name = it.getString(it.getColumnIndexOrThrow(COL_ROLE_NAME)),
                    description = it.getString(it.getColumnIndexOrThrow(COL_ROLE_DESC)) ?: "",
                    canDisarm = it.getInt(it.getColumnIndexOrThrow(COL_CAN_DISARM)) == 1,
                    canRemoteOpen = it.getInt(it.getColumnIndexOrThrow(COL_CAN_REMOTE_OPEN)) == 1,
                    canUniversalGpio = it.getInt(it.getColumnIndexOrThrow(COL_CAN_UNIVERSAL_GPIO)) == 1,
                    canManageUsers = it.getInt(it.getColumnIndexOrThrow(COL_CAN_MANAGE_USERS)) == 1,
                    canViewAudit = it.getInt(it.getColumnIndexOrThrow(COL_CAN_VIEW_AUDIT)) == 1,
                    isSystem = it.getInt(it.getColumnIndexOrThrow(COL_IS_SYSTEM)) == 1,
                    createdAt = it.getLong(it.getColumnIndexOrThrow(COL_ROLE_CREATED_AT))
                )
            }
        }
        return null
    }

    fun insertRole(role: Role): Long {
        return insertRoleWithDb(writableDatabase, role)
    }

    fun deleteRole(roleName: String): Boolean {
        val upper = roleName.uppercase().trim()
        val r = getRole(upper)
        if (r == null || r.isSystem) return false
        val count = writableDatabase.delete(TABLE_ROLES, "$COL_ROLE_NAME = ?", arrayOf(upper))
        return count > 0
    }

    // ==========================================
    // OPERACIONES DE USUARIOS Y AUTENTICACIÓN
    // ==========================================

    fun getAllUsers(): List<User> {
        val list = mutableListOf<User>()
        val db = readableDatabase
        val cursor = db.query(TABLE_USERS, null, null, null, null, null, "$COL_USERNAME ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    User(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_USER_ID)),
                        username = it.getString(it.getColumnIndexOrThrow(COL_USERNAME)),
                        passwordHash = it.getString(it.getColumnIndexOrThrow(COL_PASSWORD_HASH)),
                        salt = it.getString(it.getColumnIndexOrThrow(COL_SALT)),
                        role = it.getString(it.getColumnIndexOrThrow(COL_ROLE)),
                        fullName = it.getString(it.getColumnIndexOrThrow(COL_FULL_NAME)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COL_CREATED_AT))
                    )
                )
            }
        }
        return list
    }

    fun insertUser(user: String, pass: String, role: String, fullName: String): Long {
        return try {
            val salt = SecurityManager.generateSalt()
            val hash = SecurityManager.hashPassword(pass, salt)
            val values = ContentValues().apply {
                put(COL_USERNAME, user.trim())
                put(COL_PASSWORD_HASH, hash)
                put(COL_SALT, salt)
                put(COL_ROLE, role.uppercase().trim())
                put(COL_FULL_NAME, fullName.trim())
                put(COL_CREATED_AT, System.currentTimeMillis())
            }
            writableDatabase.insertOrThrow(TABLE_USERS, null, values)
        } catch (e: Exception) {
            -1L
        }
    }

    fun deleteUser(username: String): Boolean {
        if (username.equals("admin", ignoreCase = true)) return false
        val count = writableDatabase.delete(TABLE_USERS, "$COL_USERNAME = ?", arrayOf(username.trim()))
        return count > 0
    }

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
