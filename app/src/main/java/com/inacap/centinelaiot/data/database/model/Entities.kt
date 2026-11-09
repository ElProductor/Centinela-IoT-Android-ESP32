package com.inacap.centinelaiot.data.database.model

data class Role(
    val name: String,
    val description: String = "",
    val canDisarm: Boolean = false,
    val canRemoteOpen: Boolean = false,
    val canUniversalGpio: Boolean = false,
    val canManageUsers: Boolean = false,
    val canViewAudit: Boolean = true,
    val isSystem: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class User(
    val id: Long = 0,
    val username: String,
    val passwordHash: String,
    val salt: String,
    val role: String,
    val fullName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

data class AuditLog(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val username: String,
    val details: String,
    val severity: String // INFO, WARNING, CRITICAL
)

data class Esp32Profile(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: String = "HTTP",
    val authToken: String = "",
    val isActive: Boolean = false
)

data class GenericWidget(
    val id: Long = 0,
    val profileId: Long,
    val name: String,
    val widgetType: String, // SWITCH, SLIDER, SENSOR, BUTTON
    val gpioPin: Int,
    val currentValue: Int = 0
)
