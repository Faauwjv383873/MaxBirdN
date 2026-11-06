package com.example.agent

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.api.EnrolledProgram
import com.example.api.ShikhoApiService
import com.example.api.StudentLessonItem
import com.example.auth.SessionManager
import com.example.course.CourseRepository
import com.example.security.DeviceActivationRepository
import com.example.security.DeviceSecurityManager
import com.example.security.applyAntiCaptureSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class AiAgentRepository(
    private val context: Context,
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val courseRepository: CourseRepository = CourseRepository(apiService)
) {

    companion object {
        private const val TAG = "AiAgentRepo"
        private const val PREFS_NAME = "maxbird_ai_agent_prefs"

        private const val KEY_GH_TOKEN = "gh_personal_access_token"
        private const val KEY_GH_OWNER = "gh_owner"
        private const val KEY_GH_REPO = "gh_repo"
        private const val KEY_GH_BRANCH = "gh_branch"
        private const val KEY_GH_USERNAME = "gh_username"
        private const val KEY_GH_AVATAR = "gh_avatar"
        private const val KEY_GH_VERIFIED = "gh_verified"
        private const val KEY_GH_WORKFLOW_DEPLOYED = "gh_workflow_deployed"

        private const val KEY_TG_API_ID = "tg_api_id"
        private const val KEY_TG_API_HASH = "tg_api_hash"
        private const val KEY_TG_PHONE = "tg_phone"
        private const val KEY_TG_SESSION = "tg_session_string"
        private const val KEY_TG_BOT_TOKEN = "tg_bot_token"
        private const val KEY_TG_DEFAULT_CHAT = "tg_default_chat_id"
        private const val KEY_TG_DISPLAY_NAME = "tg_display_name"
        private const val KEY_TG_AUTH_STATE = "tg_auth_state"
        private const val KEY_TG_AUTH_MSG = "tg_auth_msg"
        private const val KEY_TG_CONNECTED = "tg_connected"

        private const val KEY_LOCAL_TASKS_JSON = "local_tasks_json"
        private const val KEY_LOCAL_SCHEDULES_JSON = "local_schedules_json"
        private const val KEY_NOTIFIED_COMPLETED_IDS = "notified_completed_task_ids"
    }

    private val prefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .applyAntiCaptureSecurity()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun getCurrentDeviceHash(): String {
        return runCatching { DeviceSecurityManager.getDeviceHardwareHash(context) }
            .getOrDefault("GLOBAL_DEFAULT")
            .ifBlank { "GLOBAL_DEFAULT" }
    }

    // =========================================================================
    // 1. EXTENSION CONFIG PERSISTENCE & DEVICE-BOUND SUPABASE CLOUD VAULT
    // =========================================================================

    fun getGitHubConfig(): GitHubExtensionConfig {
        return GitHubExtensionConfig(
            personalAccessToken = prefs.getString(KEY_GH_TOKEN, "") ?: "",
            owner = prefs.getString(KEY_GH_OWNER, "") ?: "",
            repo = prefs.getString(KEY_GH_REPO, "") ?: "",
            branch = prefs.getString(KEY_GH_BRANCH, "main")?.ifBlank { "main" } ?: "main",
            connectedUsername = prefs.getString(KEY_GH_USERNAME, "") ?: "",
            avatarUrl = prefs.getString(KEY_GH_AVATAR, "") ?: "",
            isVerified = prefs.getBoolean(KEY_GH_VERIFIED, false),
            isWorkflowDeployed = prefs.getBoolean(KEY_GH_WORKFLOW_DEPLOYED, false)
        )
    }

    fun saveGitHubConfig(config: GitHubExtensionConfig) {
        prefs.edit()
            .putString(KEY_GH_TOKEN, config.personalAccessToken.trim())
            .putString(KEY_GH_OWNER, config.owner.trim())
            .putString(KEY_GH_REPO, config.repo.trim())
            .putString(KEY_GH_BRANCH, config.branch.trim().ifBlank { "main" })
            .putString(KEY_GH_USERNAME, config.connectedUsername.trim())
            .putString(KEY_GH_AVATAR, config.avatarUrl.trim())
            .putBoolean(KEY_GH_VERIFIED, config.isVerified)
            .putBoolean(KEY_GH_WORKFLOW_DEPLOYED, config.isWorkflowDeployed)
            .apply()
    }

    fun getTelegramConfig(): TelegramExtensionConfig {
        val savedApiId = prefs.getString(KEY_TG_API_ID, "")?.trim().orEmpty()
        val savedApiHash = prefs.getString(KEY_TG_API_HASH, "")?.trim().orEmpty()
        val sessionStr = prefs.getString(KEY_TG_SESSION, "")?.trim().orEmpty()
        val botTok = prefs.getString(KEY_TG_BOT_TOKEN, "")?.trim().orEmpty()
        val connected = sessionStr.isNotBlank() || botTok.isNotBlank() || prefs.getBoolean(KEY_TG_CONNECTED, false)
        val rawState = prefs.getString(KEY_TG_AUTH_STATE, "IDLE").orEmpty()
        val effectiveState = if (sessionStr.isNotBlank()) "CONNECTED" else rawState.ifBlank { "IDLE" }

        return TelegramExtensionConfig(
            apiId = savedApiId.ifBlank { DEFAULT_TELEGRAM_API_ID },
            apiHash = savedApiHash.ifBlank { DEFAULT_TELEGRAM_API_HASH },
            phone = prefs.getString(KEY_TG_PHONE, "") ?: "",
            sessionString = sessionStr,
            botToken = botTok,
            defaultChatId = prefs.getString(KEY_TG_DEFAULT_CHAT, "") ?: "",
            accountDisplayName = prefs.getString(KEY_TG_DISPLAY_NAME, "") ?: "",
            authState = effectiveState,
            authMessage = prefs.getString(KEY_TG_AUTH_MSG, "") ?: "",
            isConnected = connected
        )
    }

    fun saveTelegramConfig(config: TelegramExtensionConfig) {
        val cleanApiId = config.apiId.trim().ifBlank { DEFAULT_TELEGRAM_API_ID }
        val cleanApiHash = config.apiHash.trim().ifBlank { DEFAULT_TELEGRAM_API_HASH }
        val cleanSession = config.sessionString.trim()
        val cleanBot = config.botToken.trim()
        val isReady = (cleanApiId.isNotBlank() && cleanApiHash.isNotBlank() && cleanSession.isNotBlank()) ||
            cleanBot.isNotBlank()
        val state = if (cleanSession.isNotBlank()) "CONNECTED" else config.authState.ifBlank { "IDLE" }

        prefs.edit()
            .putString(KEY_TG_API_ID, cleanApiId)
            .putString(KEY_TG_API_HASH, cleanApiHash)
            .putString(KEY_TG_PHONE, config.phone.trim())
            .putString(KEY_TG_SESSION, cleanSession)
            .putString(KEY_TG_BOT_TOKEN, cleanBot)
            .putString(KEY_TG_DEFAULT_CHAT, config.defaultChatId.trim())
            .putString(KEY_TG_DISPLAY_NAME, config.accountDisplayName.trim())
            .putString(KEY_TG_AUTH_STATE, state)
            .putString(KEY_TG_AUTH_MSG, config.authMessage.trim())
            .putBoolean(KEY_TG_CONNECTED, isReady)
            .apply()
    }

    private fun sealSecret(plain: String): String {
        if (plain.isBlank()) return ""
        if (plain.startsWith("ENC1:")) return plain
        val key = MessageDigest.getInstance("SHA-256")
            .digest(DEFAULT_TELEGRAM_API_HASH.toByteArray(Charsets.UTF_8))
        val raw = plain.toByteArray(Charsets.UTF_8)
        val xored = ByteArray(raw.size) { i ->
            (raw[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return "ENC1:" + Base64.encodeToString(xored, Base64.NO_WRAP)
    }

    private fun unsealSecret(value: String): String {
        if (value.isBlank()) return ""
        if (!value.startsWith("ENC1:")) return value
        return try {
            val key = MessageDigest.getInstance("SHA-256")
                .digest(DEFAULT_TELEGRAM_API_HASH.toByteArray(Charsets.UTF_8))
            val raw = Base64.decode(value.removePrefix("ENC1:"), Base64.DEFAULT)
            val plain = ByteArray(raw.size) { i ->
                (raw[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun readGitHubAuthState(gh: GitHubExtensionConfig = getGitHubConfig()): JSONObject? {
        if (gh.personalAccessToken.isBlank() || gh.owner.isBlank() || gh.repo.isBlank()) return null
        val branch = gh.branch.ifBlank { "main" }
        val apiUrl = "https://api.github.com/repos/${gh.owner}/${gh.repo}/contents/scripts/agent_auth_state.json?ref=$branch&_t=${System.currentTimeMillis()}"
        return try {
            val req = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer ${gh.personalAccessToken}")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("Cache-Control", "no-cache")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                if (!body.trim().startsWith("{")) return null
                val wrapper = JSONObject(body)
                val b64 = wrapper.optString("content", "").replace("\n", "").replace("\r", "")
                if (b64.isBlank()) return null
                val rawJson = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                val obj = JSONObject(rawJson)
                for (k in listOf("telegram_session_string", "telegram_partial_session", "telegram_2fa_password")) {
                    val v = obj.optString(k, "")
                    if (v.isNotBlank()) {
                        obj.put(k, unsealSecret(v))
                    }
                }
                obj
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeGitHubAuthState(gh: GitHubExtensionConfig, patch: JSONObject): Boolean {
        if (gh.personalAccessToken.isBlank() || gh.owner.isBlank() || gh.repo.isBlank()) return false
        val branch = gh.branch.ifBlank { "main" }
        val apiUrl = "https://api.github.com/repos/${gh.owner}/${gh.repo}/contents/scripts/agent_auth_state.json"
        var existingSha: String? = null
        var merged = JSONObject()

        try {
            val getReq = Request.Builder()
                .url("$apiUrl?ref=$branch&_t=${System.currentTimeMillis()}")
                .addHeader("Authorization", "Bearer ${gh.personalAccessToken}")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("Cache-Control", "no-cache")
                .get()
                .build()
            httpClient.newCall(getReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.trim().startsWith("{")) {
                        val wrapper = JSONObject(body)
                        existingSha = wrapper.optString("sha", "").takeIf { it.isNotBlank() }
                        val b64 = wrapper.optString("content", "").replace("\n", "").replace("\r", "")
                        if (b64.isNotBlank()) {
                            merged = JSONObject(String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8))
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val keys = patch.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = patch.opt(k)
            if (k in listOf("telegram_session_string", "telegram_partial_session", "telegram_2fa_password") &&
                v is String && v.isNotBlank()
            ) {
                merged.put(k, sealSecret(v))
            } else {
                merged.put(k, v)
            }
        }

        val b64Content = Base64.encodeToString(merged.toString(2).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val putBody = JSONObject().apply {
            put("message", "chore(ai-agent): sync telegram otp/session state")
            put("content", b64Content)
            put("branch", branch)
            if (!existingSha.isNullOrBlank()) {
                put("sha", existingSha)
            }
        }

        return try {
            val putReq = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer ${gh.personalAccessToken}")
                .addHeader("Accept", "application/vnd.github+json")
                .put(putBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(putReq).execute().use { resp ->
                resp.isSuccessful || resp.code == 200 || resp.code == 201
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchRemoteConfigObjectFromSupabase(
        supabaseUrl: String,
        anonKey: String,
        deviceHash: String
    ): JSONObject? {
        if (supabaseUrl.isBlank() || anonKey.isBlank()) return null
        val urlsToTry = listOf(
            "${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_configs?device_hash=eq.$deviceHash&order=updated_at.desc&limit=1",
            "${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_configs?user_phone=eq.$deviceHash&order=updated_at.desc&limit=1",
            "${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_configs?order=updated_at.desc&limit=1"
        )
        for (url in urlsToTry) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()?.trim().orEmpty()
                        if (body.startsWith("[")) {
                            val arr = JSONArray(body)
                            if (arr.length() > 0) {
                                val obj = arr.getJSONObject(0)
                                for (k in listOf("telegram_session_string", "telegram_partial_session", "telegram_2fa_password")) {
                                    val v = obj.optString(k, "")
                                    if (v.isNotBlank()) {
                                        obj.put(k, unsealSecret(v))
                                    }
                                }
                                return obj
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Synchronizes both GitHub and Telegram credentials with the Device-Bound Cloud Vault in Supabase (`ai_agent_configs`)
     * AND `scripts/agent_auth_state.json` on GitHub.
     * If the user deletes/uninstalls the app and reinstalls it on the same device, all saved tokens, repos,
     * API_ID, API_HASH, and STRING_SESSION are automatically restored!
     */
    suspend fun syncAgentConfigWithSupabase() = withContext(Dispatchers.IO) {
        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        val gh = getGitHubConfig()
        val tg = getTelegramConfig()
        val deviceHash = getCurrentDeviceHash()

        try {
            // 1. Pull remote config from Supabase OR GitHub auth state to restore any missing fields after app reinstall
            val remoteObj = fetchRemoteConfigObjectFromSupabase(supabaseUrl, anonKey, deviceHash)
                ?: readGitHubAuthState(gh)
            if (remoteObj != null) {
                val remotePat = remoteObj.optString("github_pat", "")
                val remoteOwner = remoteObj.optString("github_owner", "")
                val remoteRepo = remoteObj.optString("github_repo", "")
                val remoteBranch = remoteObj.optString("github_branch", "main")
                val remoteUser = remoteObj.optString("github_username", "")
                val remoteAvatar = remoteObj.optString("github_avatar_url", "")

                if ((gh.personalAccessToken.isBlank() && remotePat.isNotBlank()) ||
                    (gh.owner.isBlank() && remoteOwner.isNotBlank())
                ) {
                    saveGitHubConfig(
                        gh.copy(
                            personalAccessToken = gh.personalAccessToken.ifBlank { remotePat },
                            owner = gh.owner.ifBlank { remoteOwner },
                            repo = gh.repo.ifBlank { remoteRepo },
                            branch = gh.branch.ifBlank { remoteBranch.ifBlank { "main" } },
                            connectedUsername = gh.connectedUsername.ifBlank { remoteUser },
                            avatarUrl = gh.avatarUrl.ifBlank { remoteAvatar },
                            isVerified = (gh.personalAccessToken.ifBlank { remotePat }).isNotBlank() &&
                                (gh.owner.ifBlank { remoteOwner }).isNotBlank()
                        )
                    )
                }

                val remoteApiId = remoteObj.optString("telegram_api_id", "").ifBlank { DEFAULT_TELEGRAM_API_ID }
                val remoteApiHash = remoteObj.optString("telegram_api_hash", "").ifBlank { DEFAULT_TELEGRAM_API_HASH }
                val remotePhone = remoteObj.optString("telegram_phone", "")
                val remoteSession = unsealSecret(remoteObj.optString("telegram_session_string", ""))
                val remoteAccountName = remoteObj.optString("telegram_account_name", "")
                val remoteAuthState = remoteObj.optString("telegram_auth_state", "IDLE")
                val remoteAuthMsg = remoteObj.optString("telegram_auth_message", "")
                val remoteBot = remoteObj.optString("telegram_bot_token", "")
                val remoteChat = remoteObj.optString("telegram_default_chat_id", "")
                    .ifBlank { remoteObj.optString("default_telegram_chat_id", "") }

                if (remoteSession.isNotBlank() || remoteBot.isNotBlank() || tg.sessionString.isBlank()) {
                    val mergedSession = remoteSession.ifBlank { tg.sessionString }
                    saveTelegramConfig(
                        tg.copy(
                            apiId = tg.apiId.ifBlank { remoteApiId },
                            apiHash = tg.apiHash.ifBlank { remoteApiHash },
                            phone = tg.phone.ifBlank { remotePhone },
                            sessionString = mergedSession,
                            botToken = tg.botToken.ifBlank { remoteBot },
                            defaultChatId = tg.defaultChatId.ifBlank { remoteChat },
                            accountDisplayName = remoteAccountName.ifBlank { tg.accountDisplayName },
                            authState = if (mergedSession.isNotBlank()) "CONNECTED" else remoteAuthState.ifBlank { tg.authState },
                            authMessage = remoteAuthMsg.ifBlank { tg.authMessage },
                            isConnected = mergedSession.isNotBlank() || tg.botToken.ifBlank { remoteBot }.isNotBlank()
                        )
                    )
                }
            }

            // 2. Push latest non-blank config to Supabase under this device_hash
            val latestGh = getGitHubConfig()
            val latestTg = getTelegramConfig()
            val fullPayload = JSONObject().apply {
                put("device_hash", deviceHash)
                if (latestGh.personalAccessToken.isNotBlank()) put("github_pat", latestGh.personalAccessToken)
                if (latestGh.owner.isNotBlank()) put("github_owner", latestGh.owner)
                if (latestGh.repo.isNotBlank()) put("github_repo", latestGh.repo)
                if (latestGh.branch.isNotBlank()) put("github_branch", latestGh.branch)
                if (latestGh.connectedUsername.isNotBlank()) put("github_username", latestGh.connectedUsername)
                if (latestGh.avatarUrl.isNotBlank()) put("github_avatar_url", latestGh.avatarUrl)
                put("telegram_api_id", latestTg.apiId.ifBlank { DEFAULT_TELEGRAM_API_ID })
                put("telegram_api_hash", latestTg.apiHash.ifBlank { DEFAULT_TELEGRAM_API_HASH })
                if (latestTg.phone.isNotBlank()) put("telegram_phone", latestTg.phone)
                if (latestTg.sessionString.isNotBlank()) put("telegram_session_string", latestTg.sessionString)
                if (latestTg.accountDisplayName.isNotBlank()) put("telegram_account_name", latestTg.accountDisplayName)
                if (latestTg.botToken.isNotBlank()) put("telegram_bot_token", latestTg.botToken)
                if (latestTg.defaultChatId.isNotBlank()) put("telegram_default_chat_id", latestTg.defaultChatId)
            }

            if (supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
                upsertAgentConfigToSupabase(supabaseUrl, anonKey, deviceHash, fullPayload)
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncAgentConfigWithSupabase note: ${e.message}")
        }
    }

    private fun upsertAgentConfigToSupabase(
        supabaseUrl: String,
        anonKey: String,
        deviceHash: String,
        payload: JSONObject
    ) {
        if (supabaseUrl.isBlank() || anonKey.isBlank()) return
        val baseUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_configs"

        // Filter out any unknown columns so PostgREST never returns 400
        val allowedCols = setOf(
            "user_phone",
            "device_hash",
            "github_pat",
            "github_owner",
            "github_repo",
            "github_branch",
            "github_username",
            "github_avatar_url",
            "telegram_api_id",
            "telegram_api_hash",
            "telegram_phone",
            "telegram_session_string",
            "telegram_bot_token",
            "telegram_default_chat_id",
            "telegram_account_name",
            "telegram_auth_state",
            "telegram_auth_message",
            "telegram_phone_code_hash",
            "telegram_partial_session",
            "telegram_otp_code",
            "telegram_2fa_password"
        )
        val safePatch = JSONObject().apply {
            put("device_hash", deviceHash)
        }
        val keys = payload.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k == "default_telegram_chat_id") {
                safePatch.put("telegram_default_chat_id", payload.opt(k))
            } else if (k in allowedCols) {
                safePatch.put(k, payload.opt(k))
            }
        }

        // 1. Try PATCH on existing device_hash row
        try {
            val patchReq = Request.Builder()
                .url("$baseUrl?device_hash=eq.$deviceHash")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .patch(safePatch.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(patchReq).execute().use { resp ->
                val body = resp.body?.string()?.trim().orEmpty()
                if (resp.isSuccessful && body.startsWith("[") && JSONArray(body).length() > 0) {
                    return
                }
            }
        } catch (_: Exception) {
        }

        // 2. Try PATCH on user_phone=deviceHash row
        try {
            val patchReq2 = Request.Builder()
                .url("$baseUrl?user_phone=eq.$deviceHash")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .patch(safePatch.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(patchReq2).execute().use { resp ->
                val body = resp.body?.string()?.trim().orEmpty()
                if (resp.isSuccessful && body.startsWith("[") && JSONArray(body).length() > 0) {
                    return
                }
            }
        } catch (_: Exception) {
        }

        // 3. Try POST with user_phone = deviceHash (satisfies NOT NULL UNIQUE on user_phone)
        val insertPayload = JSONObject(safePatch.toString()).apply {
            put("user_phone", deviceHash)
            put("device_hash", deviceHash)
        }
        try {
            val postReq = Request.Builder()
                .url("$baseUrl?on_conflict=user_phone")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(insertPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(postReq).execute().use { resp ->
                if (resp.isSuccessful) return
            }
        } catch (_: Exception) {
        }

        // 4. Fallback POST on_conflict=device_hash
        try {
            val postReq2 = Request.Builder()
                .url("$baseUrl?on_conflict=device_hash")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(safePatch.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(postReq2).execute().close()
        } catch (_: Exception) {
        }
    }

    // =========================================================================
    // 2. REAL GITHUB REST API v3 INTEGRATION (VERIFY, LIST REPOS, AUTO-DEPLOY, TRIGGER)
    // =========================================================================

    suspend fun verifyGitHubTokenAndFetchRepos(token: String): Result<Pair<GitHubExtensionConfig, List<GitHubRepoSummary>>> =
        withContext(Dispatchers.IO) {
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) {
                return@withContext Result.failure(Exception("অনুগ্রহ করে আপনার GitHub Personal Access Token (PAT) দিন।"))
            }
            try {
                val userReq = Request.Builder()
                    .url("https://api.github.com/user")
                    .addHeader("Authorization", "Bearer $cleanToken")
                    .addHeader("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                val username: String
                val avatarUrl: String
                httpClient.newCall(userReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("GitHub টোকেন যাচাই ব্যর্থ হয়েছে (HTTP ${resp.code})। টোকেনে repo এবং workflow পারমিশন আছে কিনা দেখুন।")
                        )
                    }
                    val json = JSONObject(body)
                    username = json.optString("login", "")
                    avatarUrl = json.optString("avatar_url", "")
                }

                val reposReq = Request.Builder()
                    .url("https://api.github.com/user/repos?per_page=50&sort=updated&affiliation=owner,collaborator")
                    .addHeader("Authorization", "Bearer $cleanToken")
                    .addHeader("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                val repos = mutableListOf<GitHubRepoSummary>()
                httpClient.newCall(reposReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && body.trim().startsWith("[")) {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val ownerObj = item.optJSONObject("owner")
                            val ownerLogin = ownerObj?.optString("login", username) ?: username
                            val repoName = item.optString("name", "")
                            if (repoName.isNotBlank()) {
                                repos.add(
                                    GitHubRepoSummary(
                                        fullName = item.optString("full_name", "$ownerLogin/$repoName"),
                                        owner = ownerLogin,
                                        name = repoName,
                                        defaultBranch = item.optString("default_branch", "main").ifBlank { "main" },
                                        isPrivate = item.optBoolean("private", false)
                                    )
                                )
                            }
                        }
                    }
                }

                val current = getGitHubConfig()
                val preferredRepo = repos.firstOrNull {
                    it.name.contains("maxbird", ignoreCase = true) ||
                        it.name.contains("shikho", ignoreCase = true)
                } ?: repos.firstOrNull()

                val updatedConfig = current.copy(
                    personalAccessToken = cleanToken,
                    owner = current.owner.ifBlank { preferredRepo?.owner ?: username },
                    repo = current.repo.ifBlank { preferredRepo?.name ?: "" },
                    branch = current.branch.ifBlank { preferredRepo?.defaultBranch ?: "main" },
                    connectedUsername = username,
                    avatarUrl = avatarUrl,
                    isVerified = true
                )
                saveGitHubConfig(updatedConfig)
                syncAgentConfigWithSupabase()

                // Auto-deploy the workflow & worker script to the selected repository
                if (updatedConfig.owner.isNotBlank() && updatedConfig.repo.isNotBlank()) {
                    deployAgentWorkflowToGitHub(updatedConfig)
                }

                Result.success(Pair(getGitHubConfig(), repos))
            } catch (e: Exception) {
                Result.failure(Exception("GitHub সংযোগে ত্রুটি: ${e.localizedMessage ?: e.message}"))
            }
        }

    /**
     * Automatically installs/updates `.github/workflows/maxbird-ai-agent.yml`,
     * `scripts/maxbird_ai_agent_worker.py`, and `scripts/agent_runtime_bridge.json`
     * into the user's selected GitHub repository via GitHub Contents API so the user NEVER
     * has to manually add scripts or GitHub Secrets!
     */
    suspend fun deployAgentWorkflowToGitHub(
        configOverride: GitHubExtensionConfig? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val gh = configOverride ?: getGitHubConfig()
        if (gh.personalAccessToken.isBlank() || gh.owner.isBlank() || gh.repo.isBlank()) {
            return@withContext Result.failure(Exception("GitHub Token ও Repository সিলেক্ট করা নেই।"))
        }
        val (supabaseUrl, supabaseKey) = DeviceActivationRepository.getSupabaseConfig()
        val deviceHash = getCurrentDeviceHash()

        try {
            val workflowContent = context.assets.open("agent/maxbird-ai-agent.yml")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val workerContent = context.assets.open("agent/maxbird_ai_agent_worker.py")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val bridgeJson = JSONObject().apply {
                put("supabase_url", supabaseUrl)
                put("supabase_anon_key", supabaseKey)
                put("device_hash", deviceHash)
            }.toString(2)

            val branch = gh.branch.ifBlank { "main" }
            val s1 = putFileToGitHubRepo(
                gh = gh,
                path = "scripts/agent_runtime_bridge.json",
                content = bridgeJson,
                commitMessage = "chore(ai-agent): configure Supabase runtime bridge for 24/7 worker",
                branch = branch
            )
            val s2 = putFileToGitHubRepo(
                gh = gh,
                path = "scripts/maxbird_ai_agent_worker.py",
                content = workerContent,
                commitMessage = "feat(ai-agent): install/update MaxBird 24/7 autonomous cloud worker",
                branch = branch
            )
            val s3 = putFileToGitHubRepo(
                gh = gh,
                path = ".github/workflows/maxbird-ai-agent.yml",
                content = workflowContent,
                commitMessage = "ci(ai-agent): install/update 24/7 autonomous agent GitHub workflow",
                branch = branch
            )

            if (s1 && s2 && s3) {
                val marked = gh.copy(isWorkflowDeployed = true)
                saveGitHubConfig(marked)
                Result.success("✅ আপনার ${gh.owner}/${gh.repo} রিপোজিটরিতে ২৪/৭ AI Agent স্ক্রিপ্ট স্বয়ংক্রিয়ভাবে ইনস্টল হয়েছে!")
            } else {
                Result.failure(Exception("রিপোজিটরিতে কিছু ফাইল আপলোড করা যায়নি। টোকেনে 'repo' ও 'workflow' পারমিশন আছে কিনা যাচাই করুন।"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("GitHub-এ স্ক্রিপ্ট অটো-ইনস্টল ত্রুটি: ${e.localizedMessage ?: e.message}"))
        }
    }

    private fun putFileToGitHubRepo(
        gh: GitHubExtensionConfig,
        path: String,
        content: String,
        commitMessage: String,
        branch: String
    ): Boolean {
        val apiUrl = "https://api.github.com/repos/${gh.owner}/${gh.repo}/contents/$path"
        var existingSha: String? = null
        try {
            val getReq = Request.Builder()
                .url("$apiUrl?ref=$branch")
                .addHeader("Authorization", "Bearer ${gh.personalAccessToken}")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()
            httpClient.newCall(getReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.trim().startsWith("{")) {
                        existingSha = JSONObject(body).optString("sha", "").takeIf { it.isNotBlank() }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val b64Content = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val putBody = JSONObject().apply {
            put("message", commitMessage)
            put("content", b64Content)
            put("branch", branch)
            if (!existingSha.isNullOrBlank()) {
                put("sha", existingSha)
            }
        }

        val putReq = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer ${gh.personalAccessToken}")
            .addHeader("Accept", "application/vnd.github+json")
            .put(putBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            httpClient.newCall(putReq).execute().use { resp ->
                resp.isSuccessful || resp.code == 200 || resp.code == 201
            }
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // 2.5 AUTOMATIC IN-APP TELEGRAM STRING_SESSION GENERATOR (VIA GITHUB WORKER)
    // =========================================================================

    suspend fun requestTelegramOtpCode(
        apiId: String,
        apiHash: String,
        phone: String,
        twoFaPassword: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanApiId = apiId.trim().ifBlank { DEFAULT_TELEGRAM_API_ID }
        val cleanApiHash = apiHash.trim().ifBlank { DEFAULT_TELEGRAM_API_HASH }
        val cleanPhone = phone.trim().replace(" ", "").replace("-", "")
            .let { if (!it.startsWith("+") && it.isNotBlank()) "+$it" else it }
        val clean2Fa = twoFaPassword.trim()

        if (cleanPhone.length < 8) {
            return@withContext Result.failure(Exception("অনুগ্রহ করে কান্ট্রি কোডসহ আপনার টেলিগ্রাম নাম্বার দিন (যেমন: +88017XXXXXXXX)।"))
        }

        val gh = getGitHubConfig()
        if (gh.personalAccessToken.isBlank() || gh.owner.isBlank() || gh.repo.isBlank()) {
            return@withContext Result.failure(
                Exception("টেলিগ্রাম কানেক্ট করার জন্য আগে উপরের ১ নং কার্ডে আপনার GitHub Token দিয়ে রিপোজিটরি সিলেক্ট করুন।")
            )
        }

        val (supabaseUrl, supabaseKey) = DeviceActivationRepository.getSupabaseConfig()
        val deviceHash = getCurrentDeviceHash()

        val updatedTg = getTelegramConfig().copy(
            apiId = cleanApiId,
            apiHash = cleanApiHash,
            phone = cleanPhone,
            authState = "SENDING_CODE",
            authMessage = "📲 আপনার টেলিগ্রাম নাম্বারে ($cleanPhone) ওটিপি কোড পাঠানো হচ্ছে (১০-১৫ সেকেন্ড অপেক্ষা করুন)..."
        )
        saveTelegramConfig(updatedTg)

        // Update both GitHubAuthBridge (zero-SQL) and Supabase state
        val initPatch = JSONObject().apply {
            put("device_hash", deviceHash)
            put("telegram_api_id", cleanApiId)
            put("telegram_api_hash", cleanApiHash)
            put("telegram_phone", cleanPhone)
            put("telegram_otp_code", "")
            put("telegram_2fa_password", clean2Fa)
            put("telegram_auth_state", "SENDING_CODE")
            put("telegram_auth_message", updatedTg.authMessage)
        }
        writeGitHubAuthState(gh, initPatch)
        upsertAgentConfigToSupabase(supabaseUrl, supabaseKey, deviceHash, initPatch)

        // Always push the latest fast-mode workflow & live-session worker before sending OTP
        deployAgentWorkflowToGitHub(gh)
        kotlinx.coroutines.delay(1200L)

        val payloadJson = JSONObject().apply {
            put("supabase_url", supabaseUrl)
            put("supabase_anon_key", supabaseKey)
            put("device_hash", deviceHash)
            put("github_pat", gh.personalAccessToken)
            put("github_repository", "${gh.owner}/${gh.repo}")
            put("github_branch", gh.branch.ifBlank { "main" })
            put("auth_action", "TELEGRAM_SEND_CODE")
            put(
                "auth_payload",
                JSONObject().apply {
                    put("device_hash", deviceHash)
                    put("telegram_api_id", cleanApiId)
                    put("telegram_api_hash", cleanApiHash)
                    put("telegram_phone", cleanPhone)
                    put("telegram_2fa_password", clean2Fa)
                }
            )
        }

        return@withContext dispatchRawPayloadToGitHub(
            gh = gh,
            taskId = "tg_send_code_${System.currentTimeMillis()}",
            payloadJson = payloadJson,
            successMessage = "📲 টেলিগ্রামে ওটিপি রিকোয়েস্ট পাঠানো হয়েছে! ১০-১৫ সেকেন্ডের মধ্যে আপনার টেলিগ্রাম অ্যাপে কোড যাবে।"
        )
    }

    suspend fun verifyTelegramOtpAndCreateSession(
        otpCode: String,
        twoFaPassword: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanOtp = otpCode.trim().replace(" ", "").replace("-", "")
        val clean2Fa = twoFaPassword.trim()
        val tg = getTelegramConfig()

        if (cleanOtp.isBlank() && clean2Fa.isBlank()) {
            return@withContext Result.failure(Exception("অনুগ্রহ করে আপনার টেলিগ্রাম অ্যাপে আসা ৫ সংখ্যার ওটিপি (OTP) কোডটি লিখুন।"))
        }

        val gh = getGitHubConfig()
        if (gh.personalAccessToken.isBlank() || gh.owner.isBlank() || gh.repo.isBlank()) {
            return@withContext Result.failure(Exception("GitHub একাউন্ট কানেক্ট করা নেই।"))
        }

        val (supabaseUrl, supabaseKey) = DeviceActivationRepository.getSupabaseConfig()
        val deviceHash = getCurrentDeviceHash()

        val verifyingTg = tg.copy(
            authState = "VERIFYING_OTP",
            authMessage = "🔐 ওটিপি ভেরিফাই হচ্ছে... (মাত্র ৫-১০ সেকেন্ড অপেক্ষা করুন)"
        )
        saveTelegramConfig(verifyingTg)

        // 1. Write OTP and 2FA password IMMEDIATELY to both GitHubAuthBridge and Supabase.
        //    The already-connected live TELEGRAM_SEND_CODE worker polls these every 2.5s and signs in on the live socket!
        val otpPatch = JSONObject().apply {
            put("device_hash", deviceHash)
            if (cleanOtp.isNotBlank()) put("telegram_otp_code", cleanOtp)
            if (clean2Fa.isNotBlank()) put("telegram_2fa_password", clean2Fa)
            put("telegram_auth_state", "VERIFYING_OTP")
            put("telegram_auth_message", verifyingTg.authMessage)
        }
        writeGitHubAuthState(gh, otpPatch)
        upsertAgentConfigToSupabase(supabaseUrl, supabaseKey, deviceHash, otpPatch)

        // 2. Wait up to 12 seconds for the live-connected worker to finish sign-in on the active socket!
        for (attempt in 1..5) {
            kotlinx.coroutines.delay(2400L)
            val polled = pollTelegramAuthStatusFromSupabase()
            if (polled.isConnected && polled.sessionString.isNotBlank()) {
                return@withContext Result.success(
                    "🎉 অভিনন্দন! আপনার টেলিগ্রাম একাউন্ট (${polled.accountDisplayName}) সফলভাবে কানেক্ট হয়েছে এবং এই ডিভাইসে স্থায়ীভাবে সেভ হয়েছে!"
                )
            }
            if (polled.authState == "NEED_2FA") {
                return@withContext Result.failure(Exception(polled.authMessage))
            }
            if (polled.authState == "ERROR") {
                return@withContext Result.failure(Exception(polled.authMessage))
            }
        }

        // 3. Fallback: if the live worker was still starting up or had already exited, also dispatch TELEGRAM_SIGN_IN
        val ghState = readGitHubAuthState(gh)
        val supaState = fetchRemoteConfigObjectFromSupabase(supabaseUrl, supabaseKey, deviceHash)
        val phoneCodeHash = ghState?.optString("telegram_phone_code_hash", "").orEmpty()
            .ifBlank { supaState?.optString("telegram_phone_code_hash", "").orEmpty() }
        val partialSession = ghState?.optString("telegram_partial_session", "").orEmpty()
            .ifBlank { supaState?.optString("telegram_partial_session", "").orEmpty() }

        val payloadJson = JSONObject().apply {
            put("supabase_url", supabaseUrl)
            put("supabase_anon_key", supabaseKey)
            put("device_hash", deviceHash)
            put("github_pat", gh.personalAccessToken)
            put("github_repository", "${gh.owner}/${gh.repo}")
            put("github_branch", gh.branch.ifBlank { "main" })
            put("auth_action", "TELEGRAM_SIGN_IN")
            put(
                "auth_payload",
                JSONObject().apply {
                    put("device_hash", deviceHash)
                    put("telegram_api_id", tg.apiId.ifBlank { DEFAULT_TELEGRAM_API_ID })
                    put("telegram_api_hash", tg.apiHash.ifBlank { DEFAULT_TELEGRAM_API_HASH })
                    put("telegram_phone", tg.phone)
                    put("telegram_otp_code", cleanOtp)
                    put("telegram_2fa_password", clean2Fa)
                    put("telegram_phone_code_hash", phoneCodeHash)
                    put("telegram_partial_session", partialSession)
                }
            )
        }

        return@withContext dispatchRawPayloadToGitHub(
            gh = gh,
            taskId = "tg_verify_otp_${System.currentTimeMillis()}",
            payloadJson = payloadJson,
            successMessage = "🔐 ওটিপি ভেরিফাই চলছে! কয়েক সেকেন্ডের মধ্যে অটোমেটিক কানেক্ট হয়ে যাবে..."
        )
    }

    suspend fun pollTelegramAuthStatusFromSupabase(): TelegramExtensionConfig = withContext(Dispatchers.IO) {
        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        val current = getTelegramConfig()
        val gh = getGitHubConfig()
        val deviceHash = getCurrentDeviceHash()

        // Check both GitHubAuthBridge (`scripts/agent_auth_state.json`) and Supabase
        val ghObj = readGitHubAuthState(gh)
        val supaObj = fetchRemoteConfigObjectFromSupabase(supabaseUrl, anonKey, deviceHash)

        if (ghObj == null && supaObj == null) return@withContext current

        val remoteSession = unsealSecret(ghObj?.optString("telegram_session_string", "").orEmpty().trim())
            .ifBlank { unsealSecret(supaObj?.optString("telegram_session_string", "").orEmpty().trim()) }

        val remoteState = ghObj?.optString("telegram_auth_state", "").orEmpty().trim()
            .ifBlank { supaObj?.optString("telegram_auth_state", current.authState).orEmpty().trim() }

        val remoteMsg = ghObj?.optString("telegram_auth_message", "").orEmpty().trim()
            .ifBlank { supaObj?.optString("telegram_auth_message", current.authMessage).orEmpty().trim() }

        val remoteAccount = ghObj?.optString("telegram_account_name", "").orEmpty().trim()
            .ifBlank { supaObj?.optString("telegram_account_name", current.accountDisplayName).orEmpty().trim() }

        val remotePhone = ghObj?.optString("telegram_phone", "").orEmpty().trim()
            .ifBlank { supaObj?.optString("telegram_phone", current.phone).orEmpty().trim() }

        val remoteApiId = ghObj?.optString("telegram_api_id", "").orEmpty().trim()
            .ifBlank { supaObj?.optString("telegram_api_id", current.apiId).orEmpty().trim() }
            .ifBlank { DEFAULT_TELEGRAM_API_ID }

        val remoteApiHash = ghObj?.optString("telegram_api_hash", "").orEmpty().trim()
            .ifBlank { supaObj?.optString("telegram_api_hash", current.apiHash).orEmpty().trim() }
            .ifBlank { DEFAULT_TELEGRAM_API_HASH }

        val updated = current.copy(
            apiId = remoteApiId,
            apiHash = remoteApiHash,
            phone = remotePhone.ifBlank { current.phone },
            sessionString = remoteSession.ifBlank { current.sessionString },
            accountDisplayName = remoteAccount.ifBlank { current.accountDisplayName },
            authState = if (remoteSession.isNotBlank()) "CONNECTED" else remoteState.ifBlank { current.authState },
            authMessage = remoteMsg.ifBlank { current.authMessage },
            isConnected = remoteSession.isNotBlank() || current.sessionString.isNotBlank() || current.botToken.isNotBlank()
        )
        saveTelegramConfig(updated)
        updated
    }

    private suspend fun dispatchRawPayloadToGitHub(
        gh: GitHubExtensionConfig,
        taskId: String,
        payloadJson: JSONObject,
        successMessage: String
    ): Result<String> {
        val payloadB64 = Base64.encodeToString(
            payloadJson.toString().toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        val dispatchBody = JSONObject().apply {
            put("ref", gh.branch.ifBlank { "main" })
            put(
                "inputs",
                JSONObject().apply {
                    put("task_id", taskId)
                    put("task_payload_b64", payloadB64)
                }
            )
        }
        val url = "https://api.github.com/repos/${gh.owner}/${gh.repo}/actions/workflows/maxbird-ai-agent.yml/dispatches"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${gh.personalAccessToken}")
            .addHeader("Accept", "application/vnd.github+json")
            .post(dispatchBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful || resp.code == 204) {
                return Result.success(successMessage)
            }
            if (resp.code == 404) {
                // Workflow not in repo yet -> auto-deploy and retry once!
                deployAgentWorkflowToGitHub(gh)
                kotlinx.coroutines.delay(2500L)
                httpClient.newCall(req).execute().use { retryResp ->
                    if (retryResp.isSuccessful || retryResp.code == 204) {
                        return Result.success(successMessage)
                    }
                }
            }
            val errBody = resp.body?.string().orEmpty()
            return Result.failure(
                Exception("GitHub Action ট্রিগার ব্যর্থ (HTTP ${resp.code}): ${errBody.take(150)}")
            )
        }
    }

    /**
     * Dispatches the `maxbird-ai-agent.yml` GitHub Actions workflow with a base64-encoded JSON payload
     * containing the task, active course lessons (with resolved video & slide URLs!), and credentials.
     */
    suspend fun dispatchTaskToGitHubActions(
        task: AiAgentTask,
        bundledLessonsJson: JSONArray? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val gh = getGitHubConfig()
        val tg = getTelegramConfig()
        val (supabaseUrl, supabaseKey) = DeviceActivationRepository.getSupabaseConfig()
        val deviceHash = getCurrentDeviceHash()

        // 1. Always save task to local & Supabase first
        val extraObj = JSONObject().apply {
            if (bundledLessonsJson != null && bundledLessonsJson.length() > 0) {
                put("lessons", bundledLessonsJson)
            }
        }
        saveTaskLocallyAndSupabase(task, extraObj)
        syncAgentConfigWithSupabase()

        if (gh.personalAccessToken.isBlank() || gh.owner.isBlank() || gh.repo.isBlank()) {
            return@withContext Result.failure(
                Exception("টাস্কটি কিউতে সেভ হয়েছে, কিন্তু GitHub Account (Token ও Repository) কানেক্ট করা নেই। অনুগ্রহ করে '🔌 একাউন্টস' ট্যাব থেকে GitHub কানেক্ট করুন।")
            )
        }

        try {
            if (!gh.isWorkflowDeployed) {
                deployAgentWorkflowToGitHub(gh)
            }

            val payloadJson = JSONObject().apply {
                put("supabase_url", supabaseUrl)
                put("supabase_anon_key", supabaseKey)
                put("device_hash", deviceHash)
                put(
                    "agent_config",
                    JSONObject().apply {
                        put("device_hash", deviceHash)
                        put("github_pat", gh.personalAccessToken)
                        put("github_owner", gh.owner)
                        put("github_repo", gh.repo)
                        put("github_branch", gh.branch.ifBlank { "main" })
                        put("telegram_api_id", tg.apiId.ifBlank { DEFAULT_TELEGRAM_API_ID })
                        put("telegram_api_hash", tg.apiHash.ifBlank { DEFAULT_TELEGRAM_API_HASH })
                        put("telegram_session_string", tg.sessionString)
                        put("telegram_bot_token", tg.botToken)
                        put("default_telegram_chat_id", tg.defaultChatId)
                        put("shikho_access_token", sessionManager.getAccessToken() ?: "")
                    }
                )
                put(
                    "task",
                    JSONObject().apply {
                        put("id", task.id)
                        put("title", task.title)
                        put("task_type", task.taskType)
                        put("program_id", task.programId)
                        put("program_title", task.programTitle)
                        put("phase_id", task.phaseId)
                        put("subject_code", task.subjectCode)
                        put("subject_title", task.subjectTitle)
                        put("chapter_id", task.chapterId)
                        put("chapter_title", task.chapterTitle)
                        put("video_quality", task.videoQuality)
                        put("include_videos", task.includeVideos)
                        put("include_slides", task.includeSlides)
                        put("create_new_channel", task.createNewChannel)
                        put("use_forum_topics", task.useForumTopics)
                        put("target_channel_title", task.targetChannelTitle)
                        put("target_chat_id", task.targetChatId.ifBlank { tg.defaultChatId })
                        put("extra_payload_json", extraObj)
                    }
                )
            }

            val payloadB64 = Base64.encodeToString(
                payloadJson.toString().toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )

            val safePayloadB64 = if (payloadB64.length < 60000) {
                payloadB64
            } else {
                payloadJson.getJSONObject("task").remove("extra_payload_json")
                Base64.encodeToString(
                    payloadJson.toString().toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
            }

            val dispatchBody = JSONObject().apply {
                put("ref", gh.branch.ifBlank { "main" })
                put(
                    "inputs",
                    JSONObject().apply {
                        put("task_id", task.id)
                        put("task_payload_b64", safePayloadB64)
                    }
                )
            }

            val url = "https://api.github.com/repos/${gh.owner}/${gh.repo}/actions/workflows/maxbird-ai-agent.yml/dispatches"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${gh.personalAccessToken}")
                .addHeader("Accept", "application/vnd.github+json")
                .post(dispatchBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 204) {
                    val runningTask = task.copy(
                        status = "RUNNING",
                        statusMessage = "🚀 GitHub Actions ক্লাউড ওয়ার্কারে টাস্ক চালু হয়েছে!"
                    )
                    saveTaskLocallyAndSupabase(runningTask, extraObj)
                    return@withContext Result.success("GitHub Actions-এ টাস্ক সফলভাবে চালু হয়েছে!")
                } else {
                    val errBody = resp.body?.string().orEmpty()
                    return@withContext Result.failure(
                        Exception("GitHub Workflow ট্রিগার ব্যর্থ (HTTP ${resp.code}): ${errBody.take(160)}")
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("GitHub Actions সংযোগ ত্রুটি: ${e.localizedMessage ?: e.message}"))
        }
    }

    suspend fun fetchRecentGitHubWorkflowRuns(): List<GitHubWorkflowRunInfo> = withContext(Dispatchers.IO) {
        val gh = getGitHubConfig()
        if (gh.personalAccessToken.isBlank() || gh.owner.isBlank() || gh.repo.isBlank()) {
            return@withContext emptyList()
        }
        try {
            val url = "https://api.github.com/repos/${gh.owner}/${gh.repo}/actions/runs?per_page=8"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${gh.personalAccessToken}")
                .addHeader("Accept", "application/vnd.github+json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || !body.trim().startsWith("{")) return@withContext emptyList()
                val arr = JSONObject(body).optJSONArray("workflow_runs") ?: return@withContext emptyList()
                val result = mutableListOf<GitHubWorkflowRunInfo>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    result.add(
                        GitHubWorkflowRunInfo(
                            id = item.optLong("id", 0L),
                            name = item.optString("name", "AI Agent Run"),
                            status = item.optString("status", "queued"),
                            conclusion = if (item.isNull("conclusion")) null else item.optString("conclusion"),
                            htmlUrl = item.optString("html_url", ""),
                            createdAt = item.optString("created_at", "")
                        )
                    )
                }
                result
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun cancelGitHubWorkflowRun(runId: Long): Boolean = withContext(Dispatchers.IO) {
        val gh = getGitHubConfig()
        if (gh.personalAccessToken.isBlank() || gh.owner.isBlank() || gh.repo.isBlank()) return@withContext false
        try {
            val url = "https://api.github.com/repos/${gh.owner}/${gh.repo}/actions/runs/$runId/cancel"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${gh.personalAccessToken}")
                .addHeader("Accept", "application/vnd.github+json")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful || resp.code == 202
            }
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // 3. COURSE & LESSON INSPECTION (REAL SHIKHO DATA FOR AI BRAIN & WORKER)
    // =========================================================================

    suspend fun fetchEnrolledProgramsForAgent(): List<EnrolledProgram> = withContext(Dispatchers.IO) {
        try {
            val className = sessionManager.getUserClassName()?.takeIf { it.isNotBlank() } ?: "C11"
            val group = sessionManager.getUserGroup()
            val response = courseRepository.getAcademicProgramByEnrollment(
                batchId = null,
                className = className,
                group = group
            )
            response.data?.listAcademicProgramByEnrollment?.enrolled_programs.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "fetchEnrolledProgramsForAgent warning: ${e.message}")
            emptyList()
        }
    }

    suspend fun inspectProgramContent(
        programId: String,
        programTitle: String,
        phaseId: String? = null
    ): CourseInspectionSummary = withContext(Dispatchers.IO) {
        val cleanProgramId = programId.ifBlank { sessionManager.getActiveProgramId().orEmpty() }
        val cleanPhaseId = phaseId ?: sessionManager.getActiveProgramPhaseId().orEmpty()
        val lessons = try {
            courseRepository.fetchAllLessonsForProgram(cleanProgramId, cleanPhaseId.ifBlank { null })
        } catch (e: Exception) {
            emptyList()
        }

        val bySubject = lessons.groupBy {
            (it.subject_code ?: "GEN") to (it.subject_name ?: "সাধারণ বিষয়")
        }

        var totalVideos = 0
        var totalSlides = 0
        val breakdown = bySubject.map { (subjPair, subjLessons) ->
            val vCount = subjLessons.count {
                it.type.equals("RECORDED", ignoreCase = true) ||
                    it.type.equals("LIVE", ignoreCase = true) ||
                    it.candidateStreamUrls.isNotEmpty()
            }
            val sCount = subjLessons.sumOf { lesson ->
                val hasPrimary = if (!lesson.resolvedSlideUrl.isNullOrBlank()) 1 else 0
                val extra = lesson.allAttachments.count { !it.downloadUrl.isNullOrBlank() }
                maxOf(hasPrimary, extra)
            }
            totalVideos += vCount
            totalSlides += sCount
            SubjectInspectionItem(
                subjectCode = subjPair.first,
                subjectTitle = subjPair.second,
                lessonsCount = subjLessons.size,
                videosCount = vCount,
                slidesCount = sCount
            )
        }

        CourseInspectionSummary(
            programId = cleanProgramId,
            programTitle = programTitle.ifBlank { sessionManager.getActiveProgramTitleBn() ?: "সক্রিয় কোর্স" },
            phaseId = cleanPhaseId,
            subjectsCount = breakdown.size,
            totalLessonsCount = lessons.size,
            videoLessonsCount = totalVideos,
            slideAttachmentsCount = totalSlides,
            subjectBreakdown = breakdown
        )
    }

    /**
     * Builds a serialized JSONArray of lessons (with resolved video stream URLs and slide PDF URLs)
     * so the GitHub Actions worker can immediately download & upload without extra round-trips.
     */
    suspend fun buildBundledLessonsJson(
        programId: String,
        phaseId: String?,
        subjectCode: String = "",
        chapterId: String = ""
    ): Pair<Int, JSONArray> = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        val allLessons: List<StudentLessonItem> = try {
            courseRepository.fetchAllLessonsForProgram(programId, phaseId?.ifBlank { null })
        } catch (_: Exception) {
            emptyList()
        }

        val filtered = allLessons.filter { lesson ->
            val matchesSubject = subjectCode.isBlank() ||
                lesson.subject_code.equals(subjectCode, ignoreCase = true) ||
                (lesson.subject_name?.contains(subjectCode, ignoreCase = true) == true)
            val matchesChapter = chapterId.isBlank() ||
                lesson.chapter_id.equals(chapterId, ignoreCase = true) ||
                (lesson.chapter_name?.contains(chapterId, ignoreCase = true) == true)
            matchesSubject && matchesChapter
        }

        for (lesson in filtered) {
            val streamUrl = lesson.candidateStreamUrls.firstOrNull().orEmpty()
            val slideUrl = lesson.resolvedSlideUrl.orEmpty()
            val attachmentsArr = JSONArray()
            lesson.allAttachments.forEach { att ->
                val dl = att.downloadUrl
                if (!dl.isNullOrBlank()) {
                    attachmentsArr.put(
                        JSONObject().apply {
                            put("id", att.id ?: "")
                            put("title", att.displayTitle)
                            put("url", dl)
                        }
                    )
                }
            }
            arr.put(
                JSONObject().apply {
                    put("id", lesson.id)
                    put("title", lesson.title ?: "লেকচার")
                    put("type", lesson.type ?: "RECORDED")
                    put("subject_code", lesson.subject_code ?: "")
                    put("subject_name", lesson.subject_name ?: "")
                    put("chapter_id", lesson.chapter_id ?: "")
                    put("chapter_name", lesson.chapter_name ?: "")
                    put("teacher_name", lesson.live_class?.teacher?.name ?: "")
                    put("video_url", streamUrl)
                    put("slide_url", slideUrl)
                    put("attachments", attachmentsArr)
                }
            )
        }
        Pair(filtered.size, arr)
    }

    // =========================================================================
    // 4. TASKS & 24/7 SCHEDULES STORAGE (SUPABASE + LOCAL FALLBACK)
    // =========================================================================

    suspend fun fetchTasks(): List<AiAgentTask> = withContext(Dispatchers.IO) {
        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        if (supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
            try {
                val req = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_tasks?order=created_at.desc&limit=30")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()?.trim().orEmpty()
                    if (resp.isSuccessful && body.startsWith("[")) {
                        val arr = JSONArray(body)
                        val tasks = parseTasksArray(arr)
                        if (tasks.isNotEmpty()) {
                            saveTasksListLocally(tasks)
                            return@withContext tasks
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchTasks Supabase fallback: ${e.message}")
            }
        }
        getLocalTasks()
    }

    suspend fun saveTaskLocallyAndSupabase(
        task: AiAgentTask,
        extraPayloadJson: JSONObject? = null
    ) = withContext(Dispatchers.IO) {
        val current = getLocalTasks().toMutableList()
        val existingIdx = current.indexOfFirst { it.id == task.id }
        if (existingIdx >= 0) {
            current[existingIdx] = task
        } else {
            current.add(0, task)
        }
        saveTasksListLocally(current)

        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext

        try {
            val deviceHash = runCatching { DeviceSecurityManager.getDeviceHardwareHash(context) }.getOrDefault("")
            val json = JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("task_type", task.taskType)
                put("status", task.status)
                put("program_id", task.programId)
                put("program_title", task.programTitle)
                put("phase_id", task.phaseId)
                put("subject_code", task.subjectCode)
                put("subject_title", task.subjectTitle)
                put("chapter_id", task.chapterId)
                put("chapter_title", task.chapterTitle)
                put("video_quality", task.videoQuality)
                put("include_videos", task.includeVideos)
                put("include_slides", task.includeSlides)
                put("create_new_channel", task.createNewChannel)
                put("use_forum_topics", task.useForumTopics)
                put("target_channel_title", task.targetChannelTitle)
                put("target_chat_id", task.targetChatId)
                put("telegram_invite_link", task.telegramInviteLink)
                put("total_items", task.totalItems)
                put("completed_items", task.completedItems)
                put("current_item_title", task.currentItemTitle)
                put("status_message", task.statusMessage)
                put("github_run_id", task.githubRunId)
                put("created_by_device_hash", deviceHash)
                if (extraPayloadJson != null) {
                    put("extra_payload_json", extraPayloadJson)
                }
            }

            val req = Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_tasks")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "saveTaskLocallyAndSupabase note: ${e.message}")
        }
    }

    suspend fun fetchSchedules(): List<AiAgentSchedule> = withContext(Dispatchers.IO) {
        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        if (supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
            try {
                val req = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_schedules?order=created_at.desc")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()?.trim().orEmpty()
                    if (resp.isSuccessful && body.startsWith("[")) {
                        val arr = JSONArray(body)
                        val list = parseSchedulesArray(arr)
                        if (list.isNotEmpty()) {
                            saveSchedulesListLocally(list)
                            return@withContext list
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchSchedules Supabase fallback: ${e.message}")
            }
        }
        getLocalSchedules()
    }

    suspend fun saveSchedule(schedule: AiAgentSchedule) = withContext(Dispatchers.IO) {
        val current = getLocalSchedules().toMutableList()
        val idx = current.indexOfFirst { it.id == schedule.id }
        if (idx >= 0) {
            current[idx] = schedule
        } else {
            current.add(0, schedule)
        }
        saveSchedulesListLocally(current)

        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext

        try {
            val json = JSONObject().apply {
                put("id", schedule.id)
                put("title", schedule.title)
                put("schedule_type", schedule.scheduleType)
                put("cron_expression", schedule.cronExpression)
                put("time_display_bn", schedule.timeDisplayBn)
                put("program_id", schedule.programId)
                put("program_title", schedule.programTitle)
                put("phase_id", schedule.phaseId)
                put("subject_code", schedule.subjectCode)
                put("target_chat_id", schedule.targetChatId)
                put("target_channel_title", schedule.targetChannelTitle)
                put("include_videos", schedule.includeVideos)
                put("include_slides", schedule.includeSlides)
                put("include_daily_routine", schedule.includeDailyRoutine)
                put("is_enabled", schedule.isEnabled)
                put("last_run_status", schedule.lastRunStatus)
            }

            val req = Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_schedules")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "saveSchedule note: ${e.message}")
        }
    }

    suspend fun deleteSchedule(scheduleId: String) = withContext(Dispatchers.IO) {
        val updated = getLocalSchedules().filterNot { it.id == scheduleId }
        saveSchedulesListLocally(updated)

        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        if (supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext
        try {
            val req = Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_schedules?id=eq.$scheduleId")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .delete()
                .build()
            httpClient.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    fun isTaskCompletionAlreadyNotified(taskId: String): Boolean {
        val set = prefs.getStringSet(KEY_NOTIFIED_COMPLETED_IDS, emptySet()).orEmpty()
        return taskId in set
    }

    fun markTaskCompletionNotified(taskId: String) {
        val set = prefs.getStringSet(KEY_NOTIFIED_COMPLETED_IDS, emptySet()).orEmpty().toMutableSet()
        set.add(taskId)
        prefs.edit().putStringSet(KEY_NOTIFIED_COMPLETED_IDS, set).apply()
    }

    private fun getLocalTasks(): List<AiAgentTask> {
        val raw = prefs.getString(KEY_LOCAL_TASKS_JSON, "[]") ?: "[]"
        return try {
            parseTasksArray(JSONArray(raw))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveTasksListLocally(tasks: List<AiAgentTask>) {
        val arr = JSONArray()
        tasks.take(30).forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("task_type", t.taskType)
                    put("status", t.status)
                    put("program_id", t.programId)
                    put("program_title", t.programTitle)
                    put("phase_id", t.phaseId)
                    put("subject_code", t.subjectCode)
                    put("subject_title", t.subjectTitle)
                    put("chapter_id", t.chapterId)
                    put("chapter_title", t.chapterTitle)
                    put("video_quality", t.videoQuality)
                    put("include_videos", t.includeVideos)
                    put("include_slides", t.includeSlides)
                    put("create_new_channel", t.createNewChannel)
                    put("use_forum_topics", t.useForumTopics)
                    put("target_channel_title", t.targetChannelTitle)
                    put("target_chat_id", t.targetChatId)
                    put("telegram_invite_link", t.telegramInviteLink)
                    put("total_items", t.totalItems)
                    put("completed_items", t.completedItems)
                    put("current_item_title", t.currentItemTitle)
                    put("status_message", t.statusMessage)
                    put("github_run_id", t.githubRunId)
                    put("created_at", t.createdAt)
                    put("updated_at", t.updatedAt)
                }
            )
        }
        prefs.edit().putString(KEY_LOCAL_TASKS_JSON, arr.toString()).apply()
    }

    private fun parseTasksArray(arr: JSONArray): List<AiAgentTask> {
        val list = mutableListOf<AiAgentTask>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                AiAgentTask(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", "কোর্স আপলোড টাস্ক"),
                    taskType = obj.optString("task_type", "COURSE_TELEGRAM_UPLOAD"),
                    status = obj.optString("status", "QUEUED"),
                    programId = obj.optString("program_id", ""),
                    programTitle = obj.optString("program_title", ""),
                    phaseId = obj.optString("phase_id", ""),
                    subjectCode = obj.optString("subject_code", ""),
                    subjectTitle = obj.optString("subject_title", ""),
                    chapterId = obj.optString("chapter_id", ""),
                    chapterTitle = obj.optString("chapter_title", ""),
                    videoQuality = obj.optString("video_quality", "720p"),
                    includeVideos = obj.optBoolean("include_videos", true),
                    includeSlides = obj.optBoolean("include_slides", true),
                    createNewChannel = obj.optBoolean("create_new_channel", true),
                    useForumTopics = obj.optBoolean("use_forum_topics", false),
                    targetChannelTitle = obj.optString("target_channel_title", ""),
                    targetChatId = obj.optString("target_chat_id", ""),
                    telegramInviteLink = obj.optString("telegram_invite_link", ""),
                    totalItems = obj.optInt("total_items", 0),
                    completedItems = obj.optInt("completed_items", 0),
                    currentItemTitle = obj.optString("current_item_title", ""),
                    statusMessage = obj.optString("status_message", ""),
                    githubRunId = obj.optString("github_run_id", ""),
                    createdAt = obj.optString("created_at", ""),
                    updatedAt = obj.optString("updated_at", "")
                )
            )
        }
        return list
    }

    private fun getLocalSchedules(): List<AiAgentSchedule> {
        val raw = prefs.getString(KEY_LOCAL_SCHEDULES_JSON, "[]") ?: "[]"
        return try {
            parseSchedulesArray(JSONArray(raw))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSchedulesListLocally(schedules: List<AiAgentSchedule>) {
        val arr = JSONArray()
        schedules.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("schedule_type", s.scheduleType)
                    put("cron_expression", s.cronExpression)
                    put("time_display_bn", s.timeDisplayBn)
                    put("program_id", s.programId)
                    put("program_title", s.programTitle)
                    put("phase_id", s.phaseId)
                    put("subject_code", s.subjectCode)
                    put("target_chat_id", s.targetChatId)
                    put("target_channel_title", s.targetChannelTitle)
                    put("include_videos", s.includeVideos)
                    put("include_slides", s.includeSlides)
                    put("include_daily_routine", s.includeDailyRoutine)
                    put("is_enabled", s.isEnabled)
                    put("last_run_at", s.lastRunAt)
                    put("last_run_status", s.lastRunStatus)
                }
            )
        }
        prefs.edit().putString(KEY_LOCAL_SCHEDULES_JSON, arr.toString()).apply()
    }

    private fun parseSchedulesArray(arr: JSONArray): List<AiAgentSchedule> {
        val list = mutableListOf<AiAgentSchedule>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                AiAgentSchedule(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", "দৈনিক রুটিন সিঙ্ক"),
                    scheduleType = obj.optString("schedule_type", "DAILY_COURSE_SYNC"),
                    cronExpression = obj.optString("cron_expression", "0 2 * * *"),
                    timeDisplayBn = obj.optString("time_display_bn", "প্রতিদিন সকাল ০৮:০০"),
                    programId = obj.optString("program_id", ""),
                    programTitle = obj.optString("program_title", ""),
                    phaseId = obj.optString("phase_id", ""),
                    subjectCode = obj.optString("subject_code", ""),
                    targetChatId = obj.optString("target_chat_id", ""),
                    targetChannelTitle = obj.optString("target_channel_title", ""),
                    includeVideos = obj.optBoolean("include_videos", true),
                    includeSlides = obj.optBoolean("include_slides", true),
                    includeDailyRoutine = obj.optBoolean("include_daily_routine", true),
                    isEnabled = obj.optBoolean("is_enabled", true),
                    lastRunAt = obj.optString("last_run_at", ""),
                    lastRunStatus = obj.optString("last_run_status", "অপেক্ষমাণ")
                )
            )
        }
        return list
    }
}
