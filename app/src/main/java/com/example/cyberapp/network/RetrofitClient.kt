package com.example.cyberapp.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 10.0.2.2 is localhost for Android Emulator
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val api: PhishGuardApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PhishGuardApi::class.java)
    }
}
