package com.example.api

import com.example.auth.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
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

    @POST("/auth/v2/logout")
    suspend fun logout(): LogoutResponse

    @POST("/graphql")
    suspend fun getProfile(@Body query: GraphQlQuery): ProfileResponse

    @POST("/graphql")
    suspend fun setPin(@Body query: GraphQlQuery): SetPinResponse

    @POST("/graphql")
    suspend fun getAcademicProgram(@Body query: GraphQlQuery): AcademicProgramResponse

    @POST("/graphql")
    suspend fun getProgramPhases(@Body query: GraphQlQuery): ProgramPhasesResponse

    @POST("/graphql")
    suspend fun getStudentLessons(@Body query: GraphQlQuery): StudentSpecificLessonsResponse

    @POST("/graphql")
    suspend fun getPracticeQuizAccess(@Body query: GraphQlQuery): PracticeQuizAccessResponse

    @POST("/graphql")
    suspend fun getVideoList(@Body query: GraphQlQuery): VideoListResponse

    @GET("https://analytics.shikho.com/api/v1/results/quarterly/quarter/{programId}/{phaseId}")
    suspend fun getQuarterlyResults(
        @Path("programId") programId: String,
        @Path("phaseId") phaseId: String
    ): QuarterlyResultResponse

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

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ShikhoApiService::class.java)
        }
    }
}
