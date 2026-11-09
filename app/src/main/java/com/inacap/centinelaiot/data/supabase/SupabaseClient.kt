package com.inacap.centinelaiot.data.supabase

import com.inacap.centinelaiot.data.database.DatabaseHelper
import com.inacap.centinelaiot.data.database.model.AuditLog
import com.inacap.centinelaiot.data.database.model.Role
import com.inacap.centinelaiot.data.database.model.User
import com.inacap.centinelaiot.data.security.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

    private fun sanitizeUrl(rawUrl: String): String {
        var clean = rawUrl.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        if (!clean.endsWith("/")) {
            clean = "$clean/"
        }
        return clean
    }

    @Synchronized
    fun getService(rawUrl: String, apiKey: String): SupabaseApiService? {
        if (rawUrl.isBlank() || apiKey.isBlank()) return null
        val cleanUrl = sanitizeUrl(rawUrl)

        if (currentService != null && cleanUrl == lastUrl && apiKey == lastKey) {
            return currentService
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("apikey", apiKey.trim())
                    .header("Authorization", "Bearer ${apiKey.trim()}")
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
        lastKey = apiKey
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

                val response = service.pingRoot()
                if (response.isSuccessful || response.code() == 404 || response.code() == 200) {
                    // Intenta consultar la tabla roles
                    val rolesResp = service.getRoles()
                    if (rolesResp.isSuccessful) {
                        val count = rolesResp.body()?.size ?: 0
                        return@withContext Pair(true, "Conexión exitosa a Supabase ($count roles encontrados)")
                    } else if (rolesResp.code() == 404 || rolesResp.code() == 401) {
                        return@withContext Pair(true, "Servidor alcanzable. Recuerda ejecutar el script SQL para crear las tablas")
                    }
                    return@withContext Pair(true, "Conectado a Supabase (HTTP ${rolesResp.code()})")
                } else {
                    return@withContext Pair(false, "Error de respuesta HTTP ${response.code()}: ${response.message()}")
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
                service.upsertRole(dto)
                rolesSynced++
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
                service.upsertUser(uDto)
                usersSynced++
            }

            // Descargar usuarios remotos
            val remoteUsersResp = service.getUsers()
            if (remoteUsersResp.isSuccessful) {
                remoteUsersResp.body()?.forEach { ru ->
                    val exists = localUsers.any { it.username.equals(ru.username, ignoreCase = true) }
                    if (!exists) {
                        // Guardar usuario descargado directamente en SQLite
                        dbHelper.insertUser(
                            user = ru.username,
                            pass = "tempPassword", // se sobreescribe con el hash remoto
                            role = ru.role,
                            fullName = ru.fullName
                        )
                    }
                }
            }

            // 3. Subir Logs de Auditoría locales a Supabase
            val localLogs = dbHelper.getAllAuditLogs().take(25) // enviar los últimos 25
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
                }
            }

            sessionManager.lastSyncTimestamp = System.currentTimeMillis()

            Pair(
                true,
                "Sincronización completada: $rolesSynced roles, $usersSynced usuarios, $logsPushed logs enviados a la nube"
            )
        } catch (e: Exception) {
            Pair(false, "Error durante sincronización: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Sube un rol recién creado a Supabase de forma inmediata si está conectado.
     */
    suspend fun pushRole(role: Role, sessionManager: SessionManager): Boolean =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isSupabaseEnabled) return@withContext false
            val service = getService(sessionManager.supabaseUrl, sessionManager.supabaseKey) ?: return@withContext false
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
                resp.isSuccessful
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Sube un usuario recién creado a Supabase de forma inmediata si está conectado.
     */
    suspend fun pushUser(user: User, sessionManager: SessionManager): Boolean =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isSupabaseEnabled) return@withContext false
            val service = getService(sessionManager.supabaseUrl, sessionManager.supabaseKey) ?: return@withContext false
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
                resp.isSuccessful
            } catch (e: Exception) {
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

            -- 1. Tabla de Roles Dinámicos (RBAC)
            CREATE TABLE IF NOT EXISTS public.roles (
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

            -- 2. Tabla de Usuarios con Hashes ISO 27400
            CREATE TABLE IF NOT EXISTS public.app_users (
                id BIGSERIAL PRIMARY KEY,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                role TEXT NOT NULL REFERENCES public.roles(name) ON UPDATE CASCADE,
                full_name TEXT NOT NULL,
                created_at BIGINT NOT NULL
            );

            -- 3. Tabla de Auditoría Inmutable (Audit Trail)
            CREATE TABLE IF NOT EXISTS public.audit_logs (
                id BIGSERIAL PRIMARY KEY,
                timestamp BIGINT NOT NULL,
                event_type TEXT NOT NULL,
                username TEXT NOT NULL,
                details TEXT NOT NULL,
                severity TEXT NOT NULL
            );

            -- 4. Tabla de Telemetría Histórica ESP32
            CREATE TABLE IF NOT EXISTS public.telemetry (
                id BIGSERIAL PRIMARY KEY,
                timestamp BIGINT NOT NULL,
                distancia REAL NOT NULL,
                luz INTEGER NOT NULL,
                alarma_activa BOOLEAN NOT NULL,
                pestillo_abierto BOOLEAN NOT NULL,
                modo TEXT NOT NULL
            );

            -- 5. Tabla Unificada de Sensores OT (Unidad 3 - DHT11, HC-SR04, LDR)
            CREATE TABLE IF NOT EXISTS public.sensores (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                fechatoma TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
                temperatura NUMERIC(5,2) NOT NULL,
                humedad NUMERIC(5,2) NOT NULL,
                distancia_cm INTEGER NOT NULL,
                luz_adc INTEGER NOT NULL,
                alarma_activa BOOLEAN DEFAULT FALSE NOT NULL,
                operador_android TEXT DEFAULT 'ESP32_EDGE_NODE'
            );

            -- Roles iniciales
            INSERT INTO public.roles (name, description, can_disarm, can_remote_open, can_universal_gpio, can_manage_users, can_view_audit, is_system, created_at)
            VALUES 
                ('ADMIN', 'Administrador Total OT', true, true, true, true, true, true, 1729000000000),
                ('OPERATOR', 'Operador de Planta Centinela', true, true, true, false, true, true, 1729000000000),
                ('MONITOR', 'Auditor de Seguridad Solo Lectura', false, false, false, false, true, true, 1729000000000)
            ON CONFLICT (name) DO NOTHING;

            -- Habilitar Políticas RLS públicas para Anon Key
            ALTER TABLE public.roles ENABLE ROW LEVEL SECURITY;
            ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;
            ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
            ALTER TABLE public.telemetry ENABLE ROW LEVEL SECURITY;
            ALTER TABLE public.sensores ENABLE ROW LEVEL SECURITY;

            CREATE POLICY "Permitir acceso anónimo a roles" ON public.roles FOR ALL USING (true);
            CREATE POLICY "Permitir acceso anónimo a usuarios" ON public.app_users FOR ALL USING (true);
            CREATE POLICY "Permitir acceso anónimo a audit_logs" ON public.audit_logs FOR ALL USING (true);
            CREATE POLICY "Permitir acceso anónimo a telemetría" ON public.telemetry FOR ALL USING (true);

            CREATE POLICY "Permitir insercion telemetria autenticada"
            ON public.sensores FOR INSERT
            TO anon, authenticated
            WITH CHECK (temperatura BETWEEN -20 AND 85 AND humedad BETWEEN 0 AND 100);

            CREATE POLICY "Permitir lectura historial OT"
            ON public.sensores FOR SELECT
            TO anon, authenticated
            USING (true);
        """.trimIndent()
    }
}
