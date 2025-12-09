package com.example.cyberapp.network

import android.content.Context
import android.util.Log
import com.example.cyberapp.EncryptedPrefsManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val TAG = "RetrofitClient"
    // 10.0.2.2 is the localhost of the host machine from the Android Emulator
    // For physical device, use your machine's local IP (e.g., 192.168.x.x) or the Render URL
    // private const val BASE_URL = "http://10.0.2.2:8000/" 
    private const val BASE_URL = "https://nuri-safe-guard.onrender.com/"

    private var context: Context? = null

    fun init(context: Context) {
        RetrofitClient.context = context.applicationContext
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        
        // If we get 401, try to refresh token
        if (response.code == 401 && context != null) {
            Log.d(TAG, "401 Unauthorized - Attempting token refresh")
            
            val prefs = EncryptedPrefsManager(context!!)
            val refreshResult = runBlocking {
                AuthManager.refreshTokenIfNeeded(context!!)
            }
            
            if (refreshResult != null && refreshResult.isSuccess) {
                // Retry request with new token
                val newToken = AuthManager.getAuthToken(prefs)
                if (!newToken.isNullOrEmpty()) {
                    val newRequest = request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    return@Interceptor chain.proceed(newRequest)
                }
            }
        }
        
        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
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
