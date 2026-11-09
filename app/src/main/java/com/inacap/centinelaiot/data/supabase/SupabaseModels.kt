package com.inacap.centinelaiot.data.supabase

import com.google.gson.annotations.SerializedName

/**
 * Modelos DTO para serialización/deserialización con la API REST de Supabase (PostgREST).
 */

data class SupabaseRoleDto(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("can_disarm") val canDisarm: Boolean = false,
    @SerializedName("can_remote_open") val canRemoteOpen: Boolean = false,
    @SerializedName("can_universal_gpio") val canUniversalGpio: Boolean = false,
    @SerializedName("can_manage_users") val canManageUsers: Boolean = false,
    @SerializedName("can_view_audit") val canViewAudit: Boolean = true,
    @SerializedName("is_system") val isSystem: Boolean = false,
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis()
)

data class SupabaseUserDto(
    @SerializedName("username") val username: String,
    @SerializedName("password_hash") val passwordHash: String,
    @SerializedName("salt") val salt: String,
    @SerializedName("role") val role: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis()
)

data class SupabaseAuditLogDto(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("username") val username: String,
    @SerializedName("details") val details: String,
    @SerializedName("severity") val severity: String
)

data class SupabaseTelemetryDto(
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("distancia") val distancia: Float,
    @SerializedName("luz") val luz: Int,
    @SerializedName("alarma_activa") val alarmaActiva: Boolean,
    @SerializedName("pestillo_abierto") val pestilloAbierto: Boolean,
    @SerializedName("modo") val modo: String
)
