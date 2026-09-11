package com.example.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserCheckRequest(
    val phone: String,
    val type: String = "student"
)

@JsonClass(generateAdapter = true)
data class UserCheckResponse(
    val code: Int,
    val message: String,
    val pin_exist: Boolean
)

@JsonClass(generateAdapter = true)
data class ProfileDevice(
    val device_id: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val phone: String,
    val otp: String,
    val type: String = "student",
    val profile: ProfileDevice,
    val google_ads_id: String
)

@JsonClass(generateAdapter = true)
data class LoginTokens(
    val access_token: String,
    val refresh_token: String?,
    val user_id: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val tokens: LoginTokens
)

// GraphQL request payload
@JsonClass(generateAdapter = true)
data class GraphQlQuery(
    val operationName: String,
    val query: String,
    val variables: Map<String, Any>
)

@JsonClass(generateAdapter = true)
data class ProfileResponse(
    val data: ProfileData?
)

@JsonClass(generateAdapter = true)
data class ProfileData(
    val profile: UserProfile?
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: String?,
    val first_name: String?,
    val last_name: String?,
    val avatar: String?,
    val gender: String?,
    val dob: String?,
    val study_group: String?,
    val `class`: ClassInfo?,
    val school: SchoolInfo?,
    val user: UserInfo?
)

@JsonClass(generateAdapter = true)
data class ClassInfo(
    val code: String?,
    val display: String?
)

@JsonClass(generateAdapter = true)
data class SchoolInfo(
    val id: String?,
    val name: String?
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val phone: String?,
    val email: String?
)
