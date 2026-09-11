package com.example.api

import com.example.auth.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ShikhoApiService {

    @POST("/auth/v2/user/check")
    suspend fun checkUser(@Body request: UserCheckRequest): UserCheckResponse

    @POST("/auth/v2/send/sms")
    suspend fun sendSms(@Body request: SendSmsRequest): SendSmsResponse

    @POST("/auth/v2/verify/otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): VerifyOtpResponse

    @POST("/auth/v2/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("/graphql")
    suspend fun getProfile(@Body query: GraphQlQuery): ProfileResponse

    companion object {
        private const val BASE_URL = "https://api.shikho.com"

        fun create(sessionManager: SessionManager): ShikhoApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY 
            }

            val headerInterceptor = Interceptor { chain ->
                val token = sessionManager.getAccessToken()
                val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else "Bearer "
                
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-User-Timezone", "Asia/Dhaka")
                    .header("Build-Version", "(605) 6.0.5")
                    .header("User-Agent", "Shikho/(605) 6.0.5 (Android 12; V2029; vivo 2027; en; WIFI; )")
                    .header("Authorization", authHeader)
                    .build()
                
                chain.proceed(request)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(headerInterceptor)
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(ShikhoApiService::class.java)
        }
    }
}
