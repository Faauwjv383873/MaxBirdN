package com.example.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.GraphQlQuery
import com.example.api.LoginRequest
import com.example.api.ProfileDevice
import com.example.api.ShikhoApiService
import com.example.api.UserCheckRequest
import com.example.api.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class UserCheckSuccess(val phone: String, val pinExists: Boolean) : AuthState()
    object LoginSuccess : AuthState()
    data class ProfileLoaded(val profile: UserProfile) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun formatPhone(input: String): String {
        var clean = input.filter { it.isDigit() }
        if (clean.startsWith("01")) {
            clean = "88$clean"
        }
        return clean
    }

    fun checkUser(phoneInput: String) {
        val phone = formatPhone(phoneInput)
        if (phone.length != 13 || !phone.startsWith("8801")) {
            _authState.value = AuthState.Error("Please enter a valid phone number (e.g., 017...)")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val response = apiService.checkUser(UserCheckRequest(phone = phone))
                if (response.code == 200) {
                    _authState.value = AuthState.UserCheckSuccess(phone, response.pin_exist)
                } else {
                    _authState.value = AuthState.Error(response.message)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Network error occurred")
            }
        }
    }

    fun login(phone: String, otp: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val deviceId = sessionManager.getDeviceId()
                val adsId = sessionManager.getGoogleAdsId()
                
                val request = LoginRequest(
                    phone = phone,
                    otp = otp,
                    profile = ProfileDevice(device_id = deviceId),
                    google_ads_id = adsId
                )
                
                val response = apiService.login(request)
                
                // Store tokens
                sessionManager.saveTokens(
                    accessToken = response.tokens.access_token,
                    refreshToken = response.tokens.refresh_token,
                    userId = response.tokens.user_id
                )
                
                _authState.value = AuthState.LoginSuccess
                fetchProfile()
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Failed to login")
            }
        }
    }

    fun fetchProfile() {
        val accessToken = sessionManager.getAccessToken()
        val userId = sessionManager.getUserId()
        
        if (accessToken.isNullOrBlank() || userId.isNullOrBlank()) {
            _authState.value = AuthState.Error("No active session found. Please login again.")
            return
        }

        viewModelScope.launch {
            try {
                val queryBody = GraphQlQuery(
                    operationName = "GetProfile",
                    query = "query GetProfile(\$user_id: String,\$type: String!) { profile(user_id: \$user_id, type:\$type) { id first_name last_name avatar gender dob study_group class { code display } school { id name } user { phone email } } }",
                    variables = mapOf(
                        "user_id" to userId,
                        "type" to "student"
                    )
                )
                
                val response = apiService.getProfile("Bearer $accessToken", queryBody)
                val profile = response.data?.profile
                if (profile != null) {
                    _authState.value = AuthState.ProfileLoaded(profile)
                } else {
                    _authState.value = AuthState.Error("Failed to parse profile data.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Failed to fetch profile")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun logout() {
        sessionManager.clearSession()
        _authState.value = AuthState.Idle
    }
}

class AuthViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
