package com.inacap.centinelaiot.data.supabase

import com.inacap.centinelaiot.data.network.model.SupabaseSensorRecord
import retrofit2.Response
import retrofit2.http.*

/**
 * Contrato Retrofit para la API REST de Supabase (PostgREST /rest/v1/).
 */
interface SupabaseApiService {

    @GET("rest/v1/roles?select=*")
    suspend fun getRoles(): Response<List<SupabaseRoleDto>>

    @Headers("Prefer: return=representation, resolution=merge-duplicates")
    @POST("rest/v1/roles?on_conflict=name")
    suspend fun upsertRole(@Body role: SupabaseRoleDto): Response<List<SupabaseRoleDto>>

    @DELETE("rest/v1/roles")
    suspend fun deleteRole(@Query("name") eqName: String): Response<Unit>

    @GET("rest/v1/app_users?select=*")
    suspend fun getUsers(): Response<List<SupabaseUserDto>>

    @Headers("Prefer: return=representation, resolution=merge-duplicates")
    @POST("rest/v1/app_users?on_conflict=username")
    suspend fun upsertUser(@Body user: SupabaseUserDto): Response<List<SupabaseUserDto>>

    @DELETE("rest/v1/app_users")
    suspend fun deleteUser(@Query("username") eqUsername: String): Response<Unit>

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/audit_logs")
    suspend fun insertAuditLogs(@Body logs: List<SupabaseAuditLogDto>): Response<Unit>

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/telemetry")
    suspend fun insertTelemetry(@Body telemetry: SupabaseTelemetryDto): Response<Unit>

    @Headers("Prefer: return=minimal")
    @POST("rest/v1/sensores")
    suspend fun insertSensorRecord(@Body sensor: SupabaseSensorRecord): Response<Unit>

    @GET("rest/v1/")
    suspend fun pingRoot(): Response<Unit>
}
