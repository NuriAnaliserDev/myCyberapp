package com.example.cyberapp.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // 10.0.2.2 is the localhost of the host machine from the Android Emulator
    // For physical device, use your machine's local IP (e.g., 192.168.x.x) or the Render URL
    // private const val BASE_URL = "http://10.0.2.2:8000/" 
    private const val BASE_URL = "https://nuri-safe-guard.onrender.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val api: PhishGuardApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PhishGuardApi::class.java)
    }
}
