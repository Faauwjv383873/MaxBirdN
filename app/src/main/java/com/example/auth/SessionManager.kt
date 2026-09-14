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

    fun updateAuthTokens(accessToken: String, refreshToken: String?, idToken: String? = null) {
        val editor = sharedPreferences.edit()
            .putString("access_token", accessToken)
        if (!refreshToken.isNullOrBlank()) {
            editor.putString("refresh_token", refreshToken)
        }
        if (!idToken.isNullOrBlank()) {
            editor.putString("id_token", idToken)
        }
        editor.apply()
    }

    fun getAccessToken(): String? = sharedPreferences.getString("access_token", null)
    fun getUserId(): String? = sharedPreferences.getString("user_id", null)

    fun saveActiveProgram(
        programId: String,
        titleBn: String?,
        batchId: String? = null,
        classCode: String? = null
    ) {
        val editor = sharedPreferences.edit()
            .putString("active_program_id", programId)
            .putString("active_program_title_bn", titleBn)
        if (!batchId.isNullOrBlank()) {
            editor.putString("active_program_batch_id", batchId)
        }
        if (!classCode.isNullOrBlank()) {
            editor.putString("active_program_class_code", classCode)
        }
        editor.apply()
    }

    fun getActiveProgramId(): String? = sharedPreferences.getString("active_program_id", null)
    fun getActiveProgramTitleBn(): String? = sharedPreferences.getString("active_program_title_bn", null)
    fun getActiveProgramBatchId(): String? = sharedPreferences.getString("active_program_batch_id", null)
    fun getActiveProgramClassCode(): String? = sharedPreferences.getString("active_program_class_code", null)

    fun clearActiveProgram() {
        sharedPreferences.edit()
            .remove("active_program_id")
            .remove("active_program_title_bn")
            .remove("active_program_batch_id")
            .remove("active_program_class_code")
            .apply()
    }

    fun saveUserAcademicInfo(batchId: String?, className: String?, group: String?, vendor: String? = "BD") {
        sharedPreferences.edit()
            .putString("academic_batch_id", batchId)
            .putString("academic_class_name", className)
            .putString("academic_group", group)
            .putString("academic_vendor", vendor ?: "BD")
            .apply()
    }

    fun getUserBatchId(): String? = sharedPreferences.getString("academic_batch_id", null)
    fun getUserClassName(): String? = sharedPreferences.getString("academic_class_name", "C11")
    fun getUserGroup(): String? = sharedPreferences.getString("academic_group", "Humanities")
    fun getUserVendor(): String? = sharedPreferences.getString("academic_vendor", "BD")

    fun saveUserProfile(
        firstName: String?,
        lastName: String?,
        avatar: String?,
        schoolName: String? = null,
        classDisplay: String? = null
    ) {
        sharedPreferences.edit()
            .putString("user_first_name", firstName)
            .putString("user_last_name", lastName)
            .putString("user_avatar", avatar)
            .putString("user_school_name", schoolName)
            .putString("user_class_display", classDisplay)
            .apply()
    }

    fun getUserFirstName(): String? = sharedPreferences.getString("user_first_name", null)
    fun getUserSchoolName(): String? = sharedPreferences.getString("user_school_name", null)
    fun getUserClassDisplay(): String? = sharedPreferences.getString("user_class_display", null)

    fun getUserFullName(): String? {
        val first = sharedPreferences.getString("user_first_name", null)
        val last = sharedPreferences.getString("user_last_name", null)
        return when {
            !first.isNullOrBlank() && !last.isNullOrBlank() -> "$first $last"
            !first.isNullOrBlank() -> first
            !last.isNullOrBlank() -> last
            else -> null
        }
    }

    fun getUserAvatar(): String? = sharedPreferences.getString("user_avatar", null)

    fun saveSelectedSubjectCodes(programId: String, subjectCodes: Set<String>) {
        sharedPreferences.edit()
            .putStringSet("priority_subjects_$programId", subjectCodes)
            .apply()
    }

    fun getSelectedSubjectCodes(programId: String): Set<String>? {
        return sharedPreferences.getStringSet("priority_subjects_$programId", null)
    }

    // Theme Mode Management ("system", "light", "dark")
    private val _themeModeFlow = kotlinx.coroutines.flow.MutableStateFlow(getThemeMode())
    val themeModeFlow: kotlinx.coroutines.flow.StateFlow<String> = _themeModeFlow

    fun getThemeMode(): String {
        return sharedPreferences.getString("app_theme_mode", "system") ?: "system"
    }

    fun setThemeMode(mode: String) {
        sharedPreferences.edit().putString("app_theme_mode", mode).apply()
        _themeModeFlow.value = mode
    }

    // Account Completion Status
    fun setAccountComplete(completed: Boolean) {
        sharedPreferences.edit().putBoolean("is_account_completed", completed).apply()
    }

    fun isAccountComplete(): Boolean {
        if (sharedPreferences.contains("is_account_completed")) {
            return sharedPreferences.getBoolean("is_account_completed", true)
        }
        val firstName = getUserFirstName()?.trim()
        return !firstName.isNullOrBlank()
    }

    fun isAccountIncomplete(): Boolean = !isAccountComplete()

    fun setJustSignedUp(isNew: Boolean) {
        sharedPreferences.edit().putBoolean("just_signed_up", isNew).apply()
    }

    // Watched / Completed Lessons Tracking
    fun markLessonCompleted(lessonId: String) {
        if (lessonId.isBlank()) return
        val current = sharedPreferences.getStringSet("completed_lessons", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(lessonId)
        sharedPreferences.edit().putStringSet("completed_lessons", current).apply()
    }

    fun isLessonCompleted(lessonId: String): Boolean {
        if (lessonId.isBlank()) return false
        val set = sharedPreferences.getStringSet("completed_lessons", emptySet()) ?: emptySet()
        return set.contains(lessonId)
    }

    fun getJustSignedUp(): Boolean {
        return sharedPreferences.getBoolean("just_signed_up", false)
    }

    fun clearSession() {
        sharedPreferences.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("user_id")
            .remove("active_program_id")
            .remove("active_program_title_bn")
            .remove("academic_batch_id")
            .remove("academic_class_name")
            .remove("academic_group")
            .remove("academic_vendor")
            .remove("user_first_name")
            .remove("user_last_name")
            .remove("user_avatar")
            .remove("just_signed_up")
            .apply()
    }
}
