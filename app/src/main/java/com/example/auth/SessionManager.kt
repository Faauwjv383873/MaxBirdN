package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

class SessionManager(context: Context) {
    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "shikho_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        android.util.Log.e("SessionManager", "Failed to initialize EncryptedSharedPreferences, falling back to standard SharedPreferences: ${e.message}", e)
        context.getSharedPreferences("shikho_prefs_fallback", Context.MODE_PRIVATE)
    }

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

    @Volatile
    private var isUnauthorizedNotified = false

    fun saveTokens(accessToken: String, refreshToken: String?, userId: String) {
        isUnauthorizedNotified = false
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

    fun setFcmToken(token: String) {
        sharedPreferences.edit().putString("fcm_token", token).apply()
    }

    fun getFcmToken(): String? = sharedPreferences.getString("fcm_token", null)

    fun saveActiveProgram(
        programId: String,
        titleBn: String?,
        batchId: String? = null,
        classCode: String? = null,
        phaseId: String? = null
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
        if (!phaseId.isNullOrBlank()) {
            editor.putString("active_program_phase_id", phaseId)
        }
        editor.apply()
    }

    fun getActiveProgramId(): String? = sharedPreferences.getString("active_program_id", null)
    fun getActiveProgramTitleBn(): String? = sharedPreferences.getString("active_program_title_bn", null)
    fun getActiveProgramBatchId(): String? = sharedPreferences.getString("active_program_batch_id", null)
    fun getActiveProgramClassCode(): String? = sharedPreferences.getString("active_program_class_code", null)
    fun getActiveProgramPhaseId(): String? = sharedPreferences.getString("active_program_phase_id", null)

    fun clearActiveProgram() {
        sharedPreferences.edit()
            .remove("active_program_id")
            .remove("active_program_title_bn")
            .remove("active_program_batch_id")
            .remove("active_program_class_code")
            .remove("active_program_phase_id")
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
        classDisplay: String? = null,
        phone: String? = null
    ) {
        val editor = sharedPreferences.edit()
            .putString("user_first_name", firstName)
            .putString("user_last_name", lastName)
            .putString("user_avatar", avatar)
            .putString("user_school_name", schoolName)
            .putString("user_class_display", classDisplay)
        if (!phone.isNullOrBlank()) {
            editor.putString("user_phone", phone)
        }
        editor.apply()
    }

    fun saveUserPhone(phone: String?) {
        if (!phone.isNullOrBlank()) {
            sharedPreferences.edit().putString("user_phone", phone).apply()
        }
    }

    fun getUserPhone(): String? = sharedPreferences.getString("user_phone", null)

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
        return sharedPreferences.getBoolean("is_account_completed", true)
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
        sharedPreferences.edit().putStringSet("completed_lessons", HashSet(current)).apply()
    }

    fun isLessonCompleted(lessonId: String): Boolean {
        if (lessonId.isBlank()) return false
        val set = sharedPreferences.getStringSet("completed_lessons", emptySet()) ?: emptySet()
        return set.contains(lessonId)
    }

    fun getCompletedLessonIds(): Set<String> {
        return sharedPreferences.getStringSet("completed_lessons", emptySet()) ?: emptySet()
    }

    fun getJustSignedUp(): Boolean {
        return sharedPreferences.getBoolean("just_signed_up", false)
    }

    // Unauthorized / Session Expiry Event
    private val _unauthorizedEvent = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val unauthorizedEvent: SharedFlow<Unit> = _unauthorizedEvent.asSharedFlow()

    fun notifyUnauthorized() {
        if (isUnauthorizedNotified) return
        val activeToken = getAccessToken()
        if (activeToken.isNullOrBlank()) return
        isUnauthorizedNotified = true
        clearSession()
        _unauthorizedEvent.tryEmit(Unit)
    }

    fun resetUnauthorizedNotified() {
        isUnauthorizedNotified = false
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
