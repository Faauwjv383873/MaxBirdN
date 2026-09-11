package com.example.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class NavigateToPin(val phone: String) : AuthState()
    data class NavigateToOtp(val phone: String, val authType: String) : AuthState()
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
                if (response.code == 200 && response.pin_exist == true) {
                    _authState.value = AuthState.NavigateToPin(phone)
                } else {
                    // Profile exists but no pin, or treated as signup
                    sendSmsInternal(phone, "signup")
                }
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    // Profile not found -> New user signup flow
                    sendSmsInternal(phone, "signup")
                } else {
                    _authState.value = AuthState.Error("API Error: ${e.code()}")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Network error occurred")
            }
        }
    }

    fun triggerForgotPassword(phone: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            sendSmsInternal(phone, "login")
        }
    }

    fun triggerResendOtp(phone: String, authType: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val adsId = sessionManager.getGoogleAdsId()
                val req = SendSmsRequest(phone = phone, auth_type = authType, google_ads_id = adsId)
                val response = apiService.sendSms(req)
                if (response.code == 200) {
                    _authState.value = AuthState.Idle // keep it on same screen naturally
                } else {
                    _authState.value = AuthState.Error(response.message)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Failed to resend OTP")
            }
        }
    }

    private suspend fun sendSmsInternal(phone: String, authType: String) {
        try {
            val adsId = sessionManager.getGoogleAdsId()
            val req = SendSmsRequest(phone = phone, auth_type = authType, google_ads_id = adsId)
            val response = apiService.sendSms(req)
            if (response.code == 200) {
                _authState.value = AuthState.NavigateToOtp(phone, authType)
            } else {
                _authState.value = AuthState.Error(response.message)
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Failed to send SMS: ${e.localizedMessage}")
        }
    }

    fun submitOtp(phone: String, otp: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val response = apiService.verifyOtp(VerifyOtpRequest(phone = phone, otp = otp))
                if (response.code == 200 || response.code == 201) {
                    loginInternal(phone, otp)
                } else {
                    _authState.value = AuthState.Error(response.message)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Invalid OTP. Verification failed.")
            }
        }
    }

    fun submitPin(phone: String, pin: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            loginInternal(phone, pin)
        }
    }

    private suspend fun loginInternal(phone: String, otpOrPin: String) {
        try {
            val deviceId = sessionManager.getDeviceId()
            val adsId = sessionManager.getGoogleAdsId()
            
            val request = LoginRequest(
                phone = phone,
                otp = otpOrPin,
                profile = ProfileDevice(device_id = deviceId),
                google_ads_id = adsId
            )
            
            val response = apiService.login(request)
            
            sessionManager.saveTokens(
                accessToken = response.tokens.access_token,
                refreshToken = response.tokens.refresh_token,
                userId = response.tokens.user_id
            )
            
            _authState.value = AuthState.LoginSuccess
            fetchProfile()
        } catch (e: HttpException) {
            _authState.value = AuthState.Error("Incorrect PIN or OTP.")
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.localizedMessage ?: "Failed to login")
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
