package com.example.cyberapp.network

import retrofit2.http.*

data class UrlCheckRequest(val url: String)
data class ApkCheckRequest(val hash: String)

data class CheckResponse(
    val score: Int,
    val verdict: String,
    val reasons: List<String>,
    val method: String? = null,
    val ml_confidence: Double? = null
)

// Authentication Models
data class RegisterRequest(val device_id: String, val password: String? = null)
data class LoginRequest(val device_id: String, val password: String? = null)
data class AuthResponse(
    val user_id: Int,
    val device_id: String,
    val auth_token: String,
    val expires_at: Double? = null,
    val created_at: Double? = null
)

// Session Models
data class SessionCreateRequest(
    val device_name: String,
    val device_info: Map<String, String>,
    val ip_address: String
)
data class Session(
    val session_id: Int,
    val session_token: String,
    val device_name: String,
    val device_info: String? = null,
    val ip_address: String,
    val created_at: Double,
    val last_active: Double,
    val is_active: Boolean
)
data class SessionsResponse(val sessions: List<Session>)

// Statistics Models
data class DailyStatistic(
    val date: String,
    val urls_scanned: Int,
    val threats_detected: Int,
    val apps_scanned: Int,
    val anomalies_found: Int
)
data class TotalStatistics(
    val total_urls_scanned: Int,
    val total_threats_detected: Int,
    val total_apps_scanned: Int,
    val total_anomalies_found: Int
)
data class StatisticsResponse(
    val daily: List<DailyStatistic>,
    val total: TotalStatistics
)

// Push Notification Models
data class PushTokenRequest(val device_token: String, val platform: String = "android")
data class MessageResponse(val message: String)

interface PhishGuardApi {
    // Existing endpoints
    @POST("/check/url")
    suspend fun checkUrl(
        @Body request: UrlCheckRequest,
        @Header("Authorization") token: String? = null
    ): CheckResponse

    @POST("/check/apk")
    suspend fun checkApk(
        @Body request: ApkCheckRequest,
        @Header("Authorization") token: String? = null
    ): CheckResponse

    // Authentication endpoints
    @POST("/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("/auth/verify")
    suspend fun verify(@Header("Authorization") token: String): Map<String, Any>

    @POST("/auth/logout")
    suspend fun logout(@Header("Authorization") token: String): MessageResponse

    // Session Management endpoints
    @POST("/sessions/create")
    suspend fun createSession(
        @Body request: SessionCreateRequest,
        @Header("Authorization") token: String
    ): Session

    @GET("/sessions/list")
    suspend fun listSessions(@Header("Authorization") token: String): SessionsResponse

    @POST("/sessions/{session_id}/terminate")
    suspend fun terminateSession(
        @Path("session_id") sessionId: Int,
        @Header("Authorization") token: String
    ): MessageResponse

    @POST("/sessions/terminate-all")
    suspend fun terminateAllSessions(
        @Query("exclude_session_id") excludeSessionId: Int? = null,
        @Header("Authorization") token: String
    ): MessageResponse

    // Statistics endpoints
    @GET("/statistics")
    suspend fun getStatistics(
        @Query("days") days: Int = 7,
        @Header("Authorization") token: String
    ): StatisticsResponse

    // Push Notifications endpoints
    @POST("/push/register")
    suspend fun registerPushToken(
        @Body request: PushTokenRequest,
        @Header("Authorization") token: String
    ): MessageResponse

    @POST("/push/unregister")
    suspend fun unregisterPushToken(
        @Body request: PushTokenRequest,
        @Header("Authorization") token: String
    ): MessageResponse
}
