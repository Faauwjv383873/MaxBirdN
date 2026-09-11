package com.example.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ShikhoApiService {

    @Headers(
        "User-Agent: Shikho",
        "Accept: application/json",
        "Content-Type: application/json",
        "X-User-Timezone: Asia/Dhaka"
    )
    @POST("/auth/v2/user/check")
    suspend fun checkUser(@Body request: UserCheckRequest): UserCheckResponse

    @Headers(
        "User-Agent: Shikho",
        "Accept: application/json",
        "Content-Type: application/json",
        "X-User-Timezone: Asia/Dhaka"
    )
    @POST("/auth/v2/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @Headers(
        "User-Agent: Shikho",
        "Accept: application/json",
        "Content-Type: application/json",
        "X-User-Timezone: Asia/Dhaka"
    )
    @POST("/graphql")
    suspend fun getProfile(
        @Header("Authorization") authHeader: String,
        @Body query: GraphQlQuery
    ): ProfileResponse

    companion object {
        private const val BASE_URL = "https://api.shikho.com"

        fun create(): ShikhoApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY // We can tone this down in prod, but keeping it for debug
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging) // Adding standard interceptor, not any external tracking SDK
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
