package com.inacap.centinelaiot.data.supabase

import com.inacap.centinelaiot.data.database.DatabaseHelper
import com.inacap.centinelaiot.data.database.model.AuditLog
import com.inacap.centinelaiot.data.database.model.Role
import com.inacap.centinelaiot.data.database.model.User
import com.inacap.centinelaiot.data.network.model.SupabaseSensorRecord
import com.inacap.centinelaiot.data.network.model.TelemetryResponse
import com.inacap.centinelaiot.data.security.SessionManager
import com.inacap.centinelaiot.data.simulator.Esp32VirtualSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Cliente y Gestor de Sincronización en la Nube con Supabase (PostgreSQL BaaS).
 * Provee conexión offline-first: sincroniza roles, usuarios y logs cuando hay red,
 * y permite funcionamiento 100% autónomo y local mediante SQLite.
 */
object SupabaseClient {

    private var currentService: SupabaseApiService? = null
    private var lastUrl: String = ""
    private var lastKey: String = ""

    fun sanitizeUrl(rawUrl: String): String {
        var clean = rawUrl.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        clean = clean.removeSuffix("/")
        if (clean.endsWith("/rest/v1")) {
            clean = clean.removeSuffix("/rest/v1")
        }
        return "${clean.removeSuffix("/")}/"
    }

    @Synchronized
    fun getService(rawUrl: String, apiKey: String): SupabaseApiService? {
        if (rawUrl.isBlank() || apiKey.isBlank()) return null
        val cleanUrl = sanitizeUrl(rawUrl)
        val cleanKey = apiKey.trim()

        if (currentService != null && cleanUrl == lastUrl && cleanKey == lastKey) {
            return currentService
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("apikey", cleanKey)
                    .header("Authorization", "Bearer $cleanKey")
                    .header("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        lastUrl = cleanUrl
        lastKey = cleanKey
        currentService = retrofit.create(SupabaseApiService::class.java)
        return currentService
    }

    /**
     * Prueba la conexión hacia la instancia de Supabase.
     */
    suspend fun testConnection(rawUrl: String, apiKey: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val service = getService(rawUrl, apiKey)
                    ?: return@withContext Pair(false, "URL o API Key vacíos")

                val rolesResp = service.getRoles()
                if (rolesResp.isSuccessful) {
                    val count = rolesResp.body()?.size ?: 0
                    Pair(true, "Conexión exitosa a Supabase ($count roles encontrados en la nube)")
                } else when (rolesResp.code()) {
                    404 -> Pair(false, "Servidor alcanzado, pero la tabla 'roles' no existe. Ejecuta el script SQL en Supabase.")
                    401, 403 -> {
                        val err = rolesResp.errorBody()?.string() ?: ""
                        Pair(false, "Error de permisos (HTTP ${rolesResp.code()}): Verifica tu Publishable Key o RLS ($err)")
                    }
                    else -> {
                        val err = rolesResp.errorBody()?.string() ?: rolesResp.message()
                        Pair(false, "Error Supabase (HTTP ${rolesResp.code()}): $err")
                    }
                }
            } catch (e: Exception) {
                Pair(false, "Fallo de conexión: ${e.localizedMessage ?: e.message}")
            }
        }

    /**
     * Sincroniza bidireccionalmente Roles, Usuarios y Logs de Auditoría.
     */
    suspend fun syncAll(
        dbHelper: DatabaseHelper,
        sessionManager: SessionManager
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!sessionManager.isSupabaseEnabled) {
            return@withContext Pair(false, "Sincronización con Supabase deshabilitada")
        }

        val service = getService(sessionManager.supabaseUrl, sessionManager.supabaseKey)
            ?: return@withContext Pair(false, "Configuración de Supabase incompleta")

        var rolesSynced = 0
        var usersSynced = 0
        var logsPushed = 0

        try {
            // 1. Sincronizar Roles: Subir locales a Supabase
            val localRoles = dbHelper.getAllRoles()
            for (lr in localRoles) {
                val dto = SupabaseRoleDto(
                    name = lr.name,
                    description = lr.description,
                    canDisarm = lr.canDisarm,
                    canRemoteOpen = lr.canRemoteOpen,
                    canUniversalGpio = lr.canUniversalGpio,
                    canManageUsers = lr.canManageUsers,
                    canViewAudit = lr.canViewAudit,
                    isSystem = lr.isSystem,
                    createdAt = lr.createdAt
                )
                val resp = service.upsertRole(dto)
                if (resp.isSuccessful) {
                    rolesSynced++
                } else {
                    val err = resp.errorBody()?.string() ?: resp.message()
                    return@withContext Pair(false, "Error al subir rol '${lr.name}' (HTTP ${resp.code()}): $err")
                }
            }

            // Descargar roles de Supabase que no estén locales
            val remoteRolesResp = service.getRoles()
            if (remoteRolesResp.isSuccessful) {
                remoteRolesResp.body()?.forEach { rr ->
                    if (dbHelper.getRole(rr.name) == null) {
                        dbHelper.insertRole(
                            Role(
                                name = rr.name,
                                description = rr.description,
                                canDisarm = rr.canDisarm,
                                canRemoteOpen = rr.canRemoteOpen,
                                canUniversalGpio = rr.canUniversalGpio,
                                canManageUsers = rr.canManageUsers,
                                canViewAudit = rr.canViewAudit,
                                isSystem = rr.isSystem,
                                createdAt = rr.createdAt
                            )
                        )
                    }
                }
            }

            // 2. Sincronizar Usuarios: Subir locales
            val localUsers = dbHelper.getAllUsers()
            for (lu in localUsers) {
                val uDto = SupabaseUserDto(
                    username = lu.username,
                    passwordHash = lu.passwordHash,
                    salt = lu.salt,
                    role = lu.role,
                    fullName = lu.fullName,
                    createdAt = lu.createdAt
                )
                val resp = service.upsertUser(uDto)
                if (resp.isSuccessful) {
                    usersSynced++
                } else {
                    val err = resp.errorBody()?.string() ?: resp.message()
                    return@withContext Pair(false, "Error al subir usuario '@${lu.username}' (HTTP ${resp.code()}): $err")
                }
            }

            // Descargar usuarios remotos
            val remoteUsersResp = service.getUsers()
            if (remoteUsersResp.isSuccessful) {
                remoteUsersResp.body()?.forEach { ru ->
                    val exists = localUsers.any { it.username.equals(ru.username, ignoreCase = true) }
                    if (!exists) {
                        dbHelper.insertUser(
                            user = ru.username,
                            pass = "tempPassword",
                            role = ru.role,
                            fullName = ru.fullName
                        )
                    }
                }
            }

            // 3. Subir Logs de Auditoría locales pendientes a Supabase (sin duplicar previos)
            val lastPushed = sessionManager.lastAuditPushTimestamp
            val localLogs = dbHelper.getAllAuditLogs()
                .filter { it.timestamp > lastPushed }
                .take(50)
            if (localLogs.isNotEmpty()) {
                val dtoList = localLogs.map {
                    SupabaseAuditLogDto(
                        timestamp = it.timestamp,
                        eventType = it.eventType,
                        username = it.username,
                        details = it.details,
                        severity = it.severity
                    )
                }
                val logResp = service.insertAuditLogs(dtoList)
                if (logResp.isSuccessful || logResp.code() in 200..299) {
                    logsPushed = dtoList.size
                    val maxTs = localLogs.maxOf { it.timestamp }
                    if (maxTs > sessionManager.lastAuditPushTimestamp) {
                        sessionManager.lastAuditPushTimestamp = maxTs
                    }
                }
            }

            // 4. Subir snapshot actual de telemetría a 'telemetry' y 'sensores'
            val snap = Esp32VirtualSimulator.telemetryFlow.value
            val snapOk = pushTelemetrySnapshot(
                data = snap,
                operatorTag = sessionManager.username.ifBlank { "ANDROID_SYNC" },
                isSimulator = sessionManager.isSimulatorActive,
                sessionManager = sessionManager
            )

            sessionManager.lastSyncTimestamp = System.currentTimeMillis()
            val telMsg = if (snapOk) ", telemetría actualizada" else ""

            Pair(
                true,
                "Sincronización exitosa: $rolesSynced roles, $usersSynced usuarios, $logsPushed logs nuevos$telMsg"
            )
        } catch (e: Exception) {
            Pair(false, "Error de red durante sincronización: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Sube un rol recién creado a Supabase de forma inmediata si está conectado.
     */
    suspend fun pushRole(role: Role, sessionManager: SessionManager): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isSupabaseEnabled) {
                return@withContext Pair(false, "Sincronización con Supabase no activada en Ajustes")
            }
            if (sessionManager.supabaseUrl.isBlank() || sessionManager.supabaseKey.isBlank()) {
                return@withContext Pair(false, "Faltan credenciales de Supabase en Ajustes")
            }
            val service = getService(sessionManager.supabaseUrl, sessionManager.supabaseKey)
                ?: return@withContext Pair(false, "No se pudo conectar con Supabase")

            return@withContext try {
                val dto = SupabaseRoleDto(
                    name = role.name,
                    description = role.description,
                    canDisarm = role.canDisarm,
                    canRemoteOpen = role.canRemoteOpen,
                    canUniversalGpio = role.canUniversalGpio,
                    canManageUsers = role.canManageUsers,
                    canViewAudit = role.canViewAudit,
                    isSystem = role.isSystem,
                    createdAt = role.createdAt
                )
                val resp = service.upsertRole(dto)
                if (resp.isSuccessful) {
                    Pair(true, "Rol '${role.name}' sincronizado con Supabase")
                } else {
                    val err = resp.errorBody()?.string() ?: resp.message()
                    Pair(false, "Error Supabase (HTTP ${resp.code()}): $err")
                }
            } catch (e: Exception) {
                Pair(false, "Error de red al sincronizar rol: ${e.localizedMessage ?: e.message}")
            }
        }

    /**
     * Sube un usuario recién creado a Supabase de forma inmediata si está conectado.
     */
    suspend fun pushUser(user: User, sessionManager: SessionManager): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isSupabaseEnabled) {
                return@withContext Pair(false, "Sincronización con Supabase no activada en Ajustes")
            }
            if (sessionManager.supabaseUrl.isBlank() || sessionManager.supabaseKey.isBlank()) {
                return@withContext Pair(false, "Faltan credenciales de Supabase en Ajustes")
            }
            val service = getService(sessionManager.supabaseUrl, sessionManager.supabaseKey)
                ?: return@withContext Pair(false, "No se pudo conectar con Supabase")

            return@withContext try {
                val dto = SupabaseUserDto(
                    username = user.username,
                    passwordHash = user.passwordHash,
                    salt = user.salt,
                    role = user.role,
                    fullName = user.fullName,
                    createdAt = user.createdAt
                )
                val resp = service.upsertUser(dto)
                if (resp.isSuccessful) {
                    Pair(true, "Usuario '@${user.username}' sincronizado con Supabase")
                } else {
                    val err = resp.errorBody()?.string() ?: resp.message()
                    Pair(false, "Error Supabase (HTTP ${resp.code()}): $err")
                }
            } catch (e: Exception) {
                Pair(false, "Error de red al sincronizar usuario: ${e.localizedMessage ?: e.message}")
            }
        }

    /**
     * Elimina un rol en Supabase de forma inmediata si está conectado.
     */
    suspend fun deleteRoleRemote(roleName: String, sessionManager: SessionManager): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isSupabaseEnabled) {
                return@withContext Pair(false, "Sincronización desactivada")
            }
            val service = getService(sessionManager.supabaseUrl, sessionManager.supabaseKey)
                ?: return@withContext Pair(false, "Cliente Supabase no disponible")

            return@withContext try {
                val resp = service.deleteRole("eq.$roleName")
                if (resp.isSuccessful) {
                    Pair(true, "Rol '$roleName' eliminado también en Supabase")
                } else {
                    val err = resp.errorBody()?.string() ?: resp.message()
                    Pair(false, "Error Supabase (HTTP ${resp.code()}): $err")
                }
            } catch (e: Exception) {
                Pair(false, "Error de red al eliminar rol: ${e.localizedMessage ?: e.message}")
            }
        }

    /**
     * Elimina un usuario en Supabase de forma inmediata si está conectado.
     */
    suspend fun deleteUserRemote(username: String, sessionManager: SessionManager): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isSupabaseEnabled) {
                return@withContext Pair(false, "Sincronización desactivada")
            }
            val service = getService(sessionManager.supabaseUrl, sessionManager.supabaseKey)
                ?: return@withContext Pair(false, "Cliente Supabase no disponible")

            return@withContext try {
                val resp = service.deleteUser("eq.$username")
                if (resp.isSuccessful) {
                    Pair(true, "Usuario '@$username' eliminado también en Supabase")
                } else {
                    val err = resp.errorBody()?.string() ?: resp.message()
                    Pair(false, "Error Supabase (HTTP ${resp.code()}): $err")
                }
            } catch (e: Exception) {
                Pair(false, "Error de red al eliminar usuario: ${e.localizedMessage ?: e.message}")
            }
        }

    /**
     * Envía un evento de auditoría (Audit Trail) en tiempo real a Supabase.
     */
    suspend fun pushAuditLog(
        eventType: String,
        username: String,
        details: String,
        severity: String,
        timestamp: Long,
        sessionManager: SessionManager
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isSupabaseEnabled) return@withContext false
        val service = getService(sessionManager.supabaseUrl, sessionManager.supabaseKey)
            ?: return@withContext false

        return@withContext try {
            val dto = SupabaseAuditLogDto(
                timestamp = timestamp,
                eventType = eventType,
                username = username,
                details = details,
                severity = severity
            )
            val resp = service.insertAuditLogs(listOf(dto))
            if (resp.isSuccessful || resp.code() in 200..299) {
                if (timestamp > sessionManager.lastAuditPushTimestamp) {
                    sessionManager.lastAuditPushTimestamp = timestamp
                }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Envía una lectura de telemetría a las tablas 'telemetry' y 'sensores' de Supabase.
     */
    suspend fun pushTelemetrySnapshot(
        data: TelemetryResponse,
        operatorTag: String,
        isSimulator: Boolean,
        sessionManager: SessionManager
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isSupabaseEnabled) return@withContext false
        val service = getService(sessionManager.supabaseUrl, sessionManager.supabaseKey)
            ?: return@withContext false

        return@withContext try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowIso = isoFormat.format(Date())

            val telemetryDto = SupabaseTelemetryDto(
                timestamp = System.currentTimeMillis(),
                distancia = data.distancia.toFloat(),
                luz = data.nivelLuz,
                alarmaActiva = data.alarmaActiva,
                pestilloAbierto = data.pestilloGrados > 0,
                modo = if (isSimulator) "SIMULATOR_EDGE" else "ESP32_REAL"
            )
            val sensorRecord = SupabaseSensorRecord(
                fechatoma = nowIso,
                temperatura = (Math.round(data.temperatura * 100f) / 100f),
                humedad = (Math.round(data.humedad * 100f) / 100f),
                distancia_cm = data.distancia,
                luz_adc = data.nivelLuz,
                alarma_activa = data.alarmaActiva,
                operador_android = if (isSimulator) "SIM_${operatorTag}" else "APP_${operatorTag}"
            )

            val tResp = service.insertTelemetry(telemetryDto)
            val sResp = service.insertSensorRecord(sensorRecord)
            tResp.isSuccessful || sResp.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Retorna el script SQL DDL para crear las tablas en Supabase.
     */
    fun getSqlSchemaScript(): String {
        return """
            -- =========================================================
            -- ESQUEMA POSTGRESQL PARA CENTINELA-IOT EN SUPABASE
            -- =========================================================

            -- 1. Limpieza de tablas previas si se crearon vacías o incompatibles
            DROP TABLE IF EXISTS public.sensores CASCADE;
            DROP TABLE IF EXISTS public.telemetry CASCADE;
            DROP TABLE IF EXISTS public.audit_logs CASCADE;
            DROP TABLE IF EXISTS public.app_users CASCADE;
            DROP TABLE IF EXISTS public.roles CASCADE;

            -- 2. Tabla de Roles Dinámicos (RBAC)
            CREATE TABLE public.roles (
                name TEXT PRIMARY KEY,
                description TEXT DEFAULT '',
                can_disarm BOOLEAN DEFAULT false,
                can_remote_open BOOLEAN DEFAULT false,
                can_universal_gpio BOOLEAN DEFAULT false,
                can_manage_users BOOLEAN DEFAULT false,
                can_view_audit BOOLEAN DEFAULT true,
                is_system BOOLEAN DEFAULT false,
                created_at BIGINT NOT NULL
            );

            -- 3. Tabla de Usuarios con Hashes ISO 27400
            CREATE TABLE public.app_users (
                id BIGSERIAL PRIMARY KEY,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                role TEXT NOT NULL REFERENCES public.roles(name) ON UPDATE CASCADE,
                full_name TEXT NOT NULL,
                created_at BIGINT NOT NULL
            );

            -- 4. Tabla de Auditoría Inmutable (Audit Trail)
            CREATE TABLE public.audit_logs (
                id BIGSERIAL PRIMARY KEY,
                timestamp BIGINT NOT NULL,
                event_type TEXT NOT NULL,
                username TEXT NOT NULL,
                details TEXT NOT NULL,
                severity TEXT NOT NULL
            );

            -- 5. Tabla de Telemetría Histórica ESP32
            CREATE TABLE public.telemetry (
                id BIGSERIAL PRIMARY KEY,
                timestamp BIGINT NOT NULL,
                distancia REAL NOT NULL,
                luz INTEGER NOT NULL,
                alarma_activa BOOLEAN NOT NULL,
                pestillo_abierto BOOLEAN NOT NULL,
                modo TEXT NOT NULL
            );

            -- 6. Tabla Unificada de Sensores OT (Unidad 3 - DHT11, HC-SR04, LDR)
            CREATE TABLE public.sensores (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                fechatoma TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
                temperatura NUMERIC(5,2) NOT NULL,
                humedad NUMERIC(5,2) NOT NULL,
                distancia_cm INTEGER NOT NULL,
                luz_adc INTEGER NOT NULL,
                alarma_activa BOOLEAN DEFAULT FALSE NOT NULL,
                operador_android TEXT DEFAULT 'ESP32_EDGE_NODE'
            );

            -- Roles iniciales requeridos por el sistema
            INSERT INTO public.roles (name, description, can_disarm, can_remote_open, can_universal_gpio, can_manage_users, can_view_audit, is_system, created_at)
            VALUES 
                ('ADMIN', 'Administrador Total OT', true, true, true, true, true, true, 1729000000000),
                ('OPERATOR', 'Operador de Planta Centinela', true, true, true, false, true, true, 1729000000000),
                ('MONITOR', 'Auditor de Seguridad Solo Lectura', false, false, false, false, true, true, 1729000000000)
            ON CONFLICT (name) DO NOTHING;

            -- Habilitar Políticas RLS públicas para Anon / Publishable Key
            ALTER TABLE public.roles ENABLE ROW LEVEL SECURITY;
            ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;
            ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
            ALTER TABLE public.telemetry ENABLE ROW LEVEL SECURITY;
            ALTER TABLE public.sensores ENABLE ROW LEVEL SECURITY;

            DROP POLICY IF EXISTS "Permiso roles" ON public.roles;
            CREATE POLICY "Permiso roles" ON public.roles FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

            DROP POLICY IF EXISTS "Permiso app_users" ON public.app_users;
            CREATE POLICY "Permiso app_users" ON public.app_users FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

            DROP POLICY IF EXISTS "Permiso audit_logs" ON public.audit_logs;
            CREATE POLICY "Permiso audit_logs" ON public.audit_logs FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

            DROP POLICY IF EXISTS "Permiso telemetry" ON public.telemetry;
            CREATE POLICY "Permiso telemetry" ON public.telemetry FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

            DROP POLICY IF EXISTS "Permiso sensores" ON public.sensores;
            CREATE POLICY "Permiso sensores" ON public.sensores FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
        """.trimIndent()
    }
}
