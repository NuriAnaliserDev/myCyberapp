package com.example.cyberapp.network

import retrofit2.http.Body
import retrofit2.http.POST

data class UrlCheckRequest(val url: String)
data class ApkCheckRequest(val hash: String)

data class CheckResponse(
    val score: Int,
    val verdict: String,
    val reasons: List<String>
)

interface PhishGuardApi {
    @POST("/check/url")
    suspend fun checkUrl(@Body request: UrlCheckRequest): CheckResponse

    @POST("/check/apk")
    suspend fun checkApk(@Body request: ApkCheckRequest): CheckResponse
}
