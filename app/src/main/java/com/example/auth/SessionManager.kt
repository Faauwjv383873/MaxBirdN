package com.example.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class SessionManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "shikho_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getDeviceId(): String {
        var deviceId = sharedPreferences.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    fun getGoogleAdsId(): String {
        var adsId = sharedPreferences.getString("google_ads_id", null)
        if (adsId == null) {
            adsId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("google_ads_id", adsId).apply()
        }
        return adsId
    }

    fun saveTokens(accessToken: String, refreshToken: String?, userId: String) {
        sharedPreferences.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_id", userId)
            .apply()
    }

    fun getAccessToken(): String? = sharedPreferences.getString("access_token", null)
    fun getUserId(): String? = sharedPreferences.getString("user_id", null)

    fun clearSession() {
        sharedPreferences.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("user_id")
            .apply()
    }
}
