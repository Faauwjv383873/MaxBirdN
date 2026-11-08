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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
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
        private const val KEY_TG_DIALOGS_JSON = "tg_dialogs_json"

        private const val KEY_LOCAL_TASKS_JSON = "local_tasks_json"
        private const val KEY_LOCAL_SCHEDULES_JSON = "local_schedules_json"
        private const val KEY_LOCAL_UPLOADED_KEYS_JSON = "local_uploaded_keys_json"
        private const val KEY_NOTIFIED_COMPLETED_IDS = "notified_completed_task_ids"
        private const val KEY_CHAT_SESSIONS_JSON = "ai_chat_sessions_v2_json"
        private const val KEY_ACTIVE_CHAT_SESSION_ID = "ai_active_chat_session_id"
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
        val dialogsRaw = prefs.getString(KEY_TG_DIALOGS_JSON, "[]") ?: "[]"

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
            isConnected = connected,
            dialogs = parseDialogsJson(dialogsRaw)
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

        val editor = prefs.edit()
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
        if (config.dialogs.isNotEmpty()) {
            editor.putString(KEY_TG_DIALOGS_JSON, serializeDialogsJson(config.dialogs))
        }
        editor.apply()
    }

    private fun parseDialogsJson(raw: String): List<TelegramDialogDestination> {
        if (raw.isBlank()) return emptyList()
        return try {
            parseDialogsArray(JSONArray(raw))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseDialogsArray(arr: JSONArray): List<TelegramDialogDestination> {
        val list = mutableListOf<TelegramDialogDestination>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            val title = obj.optString("title", "").trim()
            if (id.isBlank() || title.isBlank()) continue
            val topicsArr = obj.optJSONArray("topics")
            val topics = mutableListOf<TelegramForumTopic>()
            if (topicsArr != null) {
                for (j in 0 until topicsArr.length()) {
                    val tObj = topicsArr.optJSONObject(j) ?: continue
                    val tId = tObj.optInt("id", 0)
                    val tTitle = tObj.optString("title", "").trim()
                    if (tId > 0 && tTitle.isNotBlank()) {
                        topics.add(TelegramForumTopic(id = tId, title = tTitle))
                    }
                }
            }
            list.add(
                TelegramDialogDestination(
                    id = id,
                    title = title,
                    username = obj.optString("username", ""),
                    isChannel = obj.optBoolean("is_channel", !obj.optBoolean("is_megagroup", false)),
                    isMegagroup = obj.optBoolean("is_megagroup", false),
                    isForum = obj.optBoolean("is_forum", topics.isNotEmpty()),
                    canPost = obj.optBoolean("can_post", true),
                    topics = topics
                )
            )
        }
        return list
    }

    private fun serializeDialogsJson(dialogs: List<TelegramDialogDestination>): String {
        val arr = JSONArray()
        dialogs.forEach { d ->
            val topicsArr = JSONArray()
            d.topics.forEach { t ->
                topicsArr.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                    }
                )
            }
            arr.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("title", d.title)
                    put("username", d.username)
                    put("is_channel", d.isChannel)
                    put("is_megagroup", d.isMegagroup)
                    put("is_forum", d.isForum)
                    put("can_post", d.canPost)
                    put("topics", topicsArr)
                }
            )
        }
        return arr.toString()
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
                for (k in listOf("telegram_session_string", "telegram_partial_session", "telegram_2fa_password", "shikho_access_token")) {
                    val v = obj.optString(k, "")
                    if (v.isNotBlank()) {
                        obj.put(k, unsealSecret(v))
                    }
                }
                obj.optJSONArray("uploaded_keys")?.let { mergeLocalUploadedKeys(it) }
                obj
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getLocalUploadedKeysArray(): JSONArray {
        val raw = prefs.getString(KEY_LOCAL_UPLOADED_KEYS_JSON, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun mergeLocalUploadedKeys(remoteArr: JSONArray) {
        if (remoteArr.length() == 0) return
        try {
            val mergedSet = linkedSetOf<String>()
            val existing = getLocalUploadedKeysArray()
            for (i in 0 until existing.length()) {
                val item = existing.optString(i, "").trim()
                if (item.isNotBlank()) mergedSet.add(item)
            }
            for (i in 0 until remoteArr.length()) {
                val item = remoteArr.optString(i, "").trim()
                if (item.isNotBlank()) mergedSet.add(item)
            }
            val finalArr = JSONArray()
            mergedSet.toList().takeLast(5000).forEach { finalArr.put(it) }
            prefs.edit().putString(KEY_LOCAL_UPLOADED_KEYS_JSON, finalArr.toString()).apply()
        } catch (_: Exception) {
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
            if (k in listOf("telegram_session_string", "telegram_partial_session", "telegram_2fa_password", "shikho_access_token") &&
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

                val ghStateObj = readGitHubAuthState(getGitHubConfig())
                val dialogsArr = ghStateObj?.optJSONArray("telegram_dialogs") ?: remoteObj.optJSONArray("telegram_dialogs")
                val syncedDialogs = if (dialogsArr != null) parseDialogsArray(dialogsArr) else tg.dialogs

                val effectiveRemoteState = ghStateObj?.optString("telegram_auth_state", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: remoteAuthState
                val effectiveRemoteMsg = ghStateObj?.optString("telegram_auth_message", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: remoteAuthMsg

                if (effectiveRemoteState == "SESSION_EXPIRED") {
                    saveTelegramConfig(
                        tg.copy(
                            sessionString = "",
                            authState = "SESSION_EXPIRED",
                            authMessage = effectiveRemoteMsg.ifBlank {
                                "⚠️ টেলিগ্রাম সেশন রিসেট প্রয়োজন। অনুগ্রহ করে ১ বার পুনরায় OTP দিয়ে কানেক্ট করুন।"
                            },
                            isConnected = false,
                            dialogs = syncedDialogs
                        )
                    )
                } else if (remoteSession.isNotBlank() || remoteBot.isNotBlank() || tg.sessionString.isBlank() || syncedDialogs.isNotEmpty()) {
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
                            authState = if (mergedSession.isNotBlank()) "CONNECTED" else effectiveRemoteState.ifBlank { tg.authState },
                            authMessage = effectiveRemoteMsg.ifBlank { tg.authMessage },
                            isConnected = mergedSession.isNotBlank() || tg.botToken.ifBlank { remoteBot }.isNotBlank(),
                            dialogs = syncedDialogs.ifEmpty { tg.dialogs }
                        )
                    )
                }
            }

            // 2. Push latest non-blank config to Supabase & GitHub Auth State under this device_hash
            val latestGh = getGitHubConfig()
            val latestTg = getTelegramConfig()
            val currentShikhoToken = sessionManager.getAccessToken()?.trim().orEmpty()
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

            if (currentShikhoToken.isNotBlank() && latestGh.personalAccessToken.isNotBlank()) {
                runCatching {
                    writeGitHubAuthState(
                        latestGh,
                        JSONObject().apply {
                            put("device_hash", deviceHash)
                            put("shikho_access_token", currentShikhoToken)
                        }
                    )
                }
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

                // Prefer a PRIVATE repository so GitHub's public-repo free-tier bot scanner never flags the account!
                var preferredRepo = repos.firstOrNull {
                    it.isPrivate && (
                        it.name.contains("sync", ignoreCase = true) ||
                            it.name.contains("course", ignoreCase = true) ||
                            it.name.contains("maxbird", ignoreCase = true) ||
                            it.name.contains("shikho", ignoreCase = true)
                        )
                } ?: repos.firstOrNull { it.isPrivate }

                // If the user has no private repository yet, automatically create a clean Private repository (`course-content-sync`)
                if (preferredRepo == null) {
                    try {
                        val createBody = JSONObject().apply {
                            put("name", "course-content-sync")
                            put("description", "Automated course study notes and archive sync")
                            put("private", true)
                            put("auto_init", true)
                        }
                        val createReq = Request.Builder()
                            .url("https://api.github.com/user/repos")
                            .addHeader("Authorization", "Bearer $cleanToken")
                            .addHeader("Accept", "application/vnd.github+json")
                            .post(createBody.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                        httpClient.newCall(createReq).execute().use { createResp ->
                            val cBody = createResp.body?.string().orEmpty()
                            if (createResp.isSuccessful && cBody.trim().startsWith("{")) {
                                val cJson = JSONObject(cBody)
                                val createdSummary = GitHubRepoSummary(
                                    fullName = cJson.optString("full_name", "$username/course-content-sync"),
                                    owner = username,
                                    name = cJson.optString("name", "course-content-sync"),
                                    defaultBranch = cJson.optString("default_branch", "main").ifBlank { "main" },
                                    isPrivate = true
                                )
                                repos.add(0, createdSummary)
                                preferredRepo = createdSummary
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                if (preferredRepo == null) {
                    preferredRepo = repos.firstOrNull {
                        it.name.contains("maxbird", ignoreCase = true) ||
                            it.name.contains("shikho", ignoreCase = true)
                    } ?: repos.firstOrNull()
                }

                val current = getGitHubConfig()
                // Before switching to the new GitHub account, pull any uploaded_keys, dialogs & schedules
                // from the old GitHub repository so 100% of previously uploaded items are preserved!
                if (current.personalAccessToken.isNotBlank() && current.owner.isNotBlank() && current.repo.isNotBlank()) {
                    runCatching {
                        val oldState = readGitHubAuthState(current)
                        oldState?.optJSONArray("uploaded_keys")?.let { mergeLocalUploadedKeys(it) }
                        oldState?.optJSONArray("schedules")?.let { schedArr ->
                            val parsed = parseSchedulesArray(schedArr)
                            if (parsed.isNotEmpty() && getLocalSchedules().isEmpty()) {
                                saveSchedulesListLocally(parsed)
                            }
                        }
                        oldState?.optJSONArray("telegram_dialogs")?.let { diagArr ->
                            val parsedDialogs = parseDialogsArray(diagArr)
                            if (parsedDialogs.isNotEmpty()) {
                                val tgCurr = getTelegramConfig()
                                saveTelegramConfig(tgCurr.copy(dialogs = parsedDialogs))
                            }
                        }
                    }
                }

                val updatedConfig = current.copy(
                    personalAccessToken = cleanToken,
                    owner = preferredRepo?.owner ?: current.owner.ifBlank { username },
                    repo = preferredRepo?.name ?: current.repo,
                    branch = preferredRepo?.defaultBranch ?: current.branch.ifBlank { "main" },
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

                // Automatically carry over existing connected Telegram session, uploaded_keys, dialogs & schedules
                // to the new repository so switching GitHub accounts NEVER re-uploads already uploaded videos/slides!
                try {
                    val tg = getTelegramConfig()
                    val localSchedules = getLocalSchedules()
                    val localUploadedKeys = getLocalUploadedKeysArray()
                    val currentShikhoToken = sessionManager.getAccessToken()?.trim().orEmpty()
                    val statePatch = JSONObject().apply {
                        put("device_hash", deviceHash)
                        if (currentShikhoToken.isNotBlank()) {
                            put("shikho_access_token", currentShikhoToken)
                        }
                        if (tg.sessionString.isNotBlank()) {
                            put("telegram_api_id", tg.apiId.ifBlank { DEFAULT_TELEGRAM_API_ID })
                            put("telegram_api_hash", tg.apiHash.ifBlank { DEFAULT_TELEGRAM_API_HASH })
                            put("telegram_phone", tg.phone)
                            put("telegram_session_string", sealSecret(tg.sessionString))
                            put("telegram_account_name", tg.accountDisplayName)
                            put("telegram_auth_state", "CONNECTED")
                            put("telegram_auth_message", "Connected (${tg.accountDisplayName})")
                        }
                        if (tg.dialogs.isNotEmpty()) {
                            put("telegram_dialogs", JSONArray(serializeDialogsJson(tg.dialogs)))
                        }
                        if (localSchedules.isNotEmpty()) {
                            put("schedules", serializeSchedulesArray(localSchedules))
                        }
                        if (localUploadedKeys.length() > 0) {
                            put("uploaded_keys", localUploadedKeys)
                        }
                    }
                    writeGitHubAuthState(marked, statePatch)
                } catch (_: Exception) {
                }

                Result.success("✅ আপনার ${gh.owner}/${gh.repo} প্রাইভেট রিপোজিটরিতে স্ক্রিপ্ট, টেলিগ্রাম সেশন ও পূর্বের আপলোড হিস্ট্রি স্বয়ংক্রিয়ভাবে সিঙ্ক হয়েছে!")
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
        for (attempt in 1..2) {
            var existingSha: String? = null
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

            try {
                httpClient.newCall(putReq).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 200 || resp.code == 201) {
                        return true
                    }
                    if (resp.code != 409 && resp.code != 422) {
                        return false
                    }
                }
            } catch (_: Exception) {
                if (attempt == 2) return false
            }
        }
        return false
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

        val remoteDialogsArr = ghObj?.optJSONArray("telegram_dialogs")
            ?: supaObj?.optJSONArray("telegram_dialogs")
        val remoteDialogs = if (remoteDialogsArr != null) {
            parseDialogsArray(remoteDialogsArr)
        } else {
            emptyList()
        }

        val updated = current.copy(
            apiId = remoteApiId,
            apiHash = remoteApiHash,
            phone = remotePhone.ifBlank { current.phone },
            sessionString = remoteSession.ifBlank { current.sessionString },
            accountDisplayName = remoteAccount.ifBlank { current.accountDisplayName },
            authState = if (remoteSession.isNotBlank()) "CONNECTED" else remoteState.ifBlank { current.authState },
            authMessage = remoteMsg.ifBlank { current.authMessage },
            isConnected = remoteSession.isNotBlank() || current.sessionString.isNotBlank() || current.botToken.isNotBlank(),
            dialogs = if (remoteDialogs.isNotEmpty()) remoteDialogs else current.dialogs
        )
        saveTelegramConfig(updated)
        updated
    }

    /**
     * Fetches the user's synced Telegram Channels, Supergroups, and Forum Topics from cloud state,
     * and optionally dispatches a fast `TELEGRAM_FETCH_DIALOGS` run on GitHub Actions to refresh them live.
     */
    suspend fun fetchTelegramDialogs(triggerRemoteRefresh: Boolean = false): List<TelegramDialogDestination> =
        withContext(Dispatchers.IO) {
            val polled = pollTelegramAuthStatusFromSupabase()
            if (!triggerRemoteRefresh && polled.dialogs.isNotEmpty()) {
                return@withContext polled.dialogs
            }
            val gh = getGitHubConfig()
            val tg = getTelegramConfig()
            if (gh.personalAccessToken.isBlank() || gh.owner.isBlank() || gh.repo.isBlank() || tg.sessionString.isBlank()) {
                return@withContext polled.dialogs
            }
            val (supabaseUrl, supabaseKey) = DeviceActivationRepository.getSupabaseConfig()
            val deviceHash = getCurrentDeviceHash()

            runCatching { deployAgentWorkflowToGitHub(gh) }
            val payloadJson = JSONObject().apply {
                put("supabase_url", supabaseUrl)
                put("supabase_anon_key", supabaseKey)
                put("device_hash", deviceHash)
                put("github_pat", gh.personalAccessToken)
                put("github_repository", "${gh.owner}/${gh.repo}")
                put("github_branch", gh.branch.ifBlank { "main" })
                put("auth_action", "TELEGRAM_FETCH_DIALOGS")
                put(
                    "auth_payload",
                    JSONObject().apply {
                        put("device_hash", deviceHash)
                        put("telegram_api_id", tg.apiId.ifBlank { DEFAULT_TELEGRAM_API_ID })
                        put("telegram_api_hash", tg.apiHash.ifBlank { DEFAULT_TELEGRAM_API_HASH })
                        put("telegram_session_string", tg.sessionString)
                    }
                )
            }
            dispatchRawPayloadToGitHub(
                gh = gh,
                taskId = "tg_fetch_dialogs_${System.currentTimeMillis()}",
                payloadJson = payloadJson,
                successMessage = "টেলিগ্রাম চ্যানেল ও গ্রুপ তালিকা সিঙ্ক হচ্ছে..."
            )
            // Poll for up to 16 seconds to get the freshly synced dialogs
            for (i in 1..6) {
                kotlinx.coroutines.delay(2600L)
                val latest = pollTelegramAuthStatusFromSupabase()
                if (latest.dialogs.isNotEmpty()) {
                    return@withContext latest.dialogs
                }
            }
            getTelegramConfig().dialogs
        }

    /**
     * Real Cloud LLM Inference Engine (GitHub Models GPT-4o-mini + Pollinations OpenAI)
     * so AI Agent Chat is 100% genuine, conversational, and intelligent even on custom-signed APKs
     * where Firebase App Check blocks `Firebase.ai`.
     */
    suspend fun generateCloudLlmAgentResponse(
        systemPrompt: String,
        userPrompt: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        val gh = getGitHubConfig()
        val messagesArr = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                }
            )
            put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                }
            )
        }

        // 1. Try GitHub Models API (gpt-4o-mini) using the user's connected GitHub token
        if (gh.personalAccessToken.isNotBlank()) {
            try {
                val ghBody = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", messagesArr)
                    put("temperature", 0.4)
                    put("response_format", JSONObject().put("type", "json_object"))
                }
                val ghReq = Request.Builder()
                    .url("https://models.inference.ai.azure.com/chat/completions")
                    .addHeader("Authorization", "Bearer ${gh.personalAccessToken}")
                    .addHeader("Content-Type", "application/json")
                    .post(ghBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(ghReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        val content = JSONObject(bodyStr)
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content", "")
                            .orEmpty()
                            .trim()
                        val parsed = extractJsonObjectFromText(content)
                        if (parsed != null && parsed.optString("reply_bn", "").isNotBlank()) {
                            return@withContext parsed
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GitHub Models LLM note: ${e.message}")
            }
        }

        // 2. Try Pollinations OpenAI-compatible Cloud Endpoint (Zero-Key Real GPT-4o-mini Inference)
        try {
            val pollBody = JSONObject().apply {
                put("model", "openai")
                put("messages", messagesArr)
                put("temperature", 0.4)
                put("jsonMode", true)
            }
            val pollReq = Request.Builder()
                .url("https://text.pollinations.ai/openai")
                .addHeader("Content-Type", "application/json")
                .post(pollBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(pollReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val content = JSONObject(bodyStr)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", "")
                        .orEmpty()
                        .trim()
                    val parsed = extractJsonObjectFromText(content)
                    if (parsed != null && parsed.optString("reply_bn", "").isNotBlank()) {
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pollinations OpenAI LLM note: ${e.message}")
        }

        null
    }

    private fun extractJsonObjectFromText(raw: String): JSONObject? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(cleaned.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
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
            // Cancel any stuck/queued background runs before dispatching a new task so two runs never collide
            runCatching {
                val activeRuns = fetchRecentGitHubWorkflowRuns()
                activeRuns.filter { it.status in listOf("in_progress", "queued", "waiting") }.forEach { r ->
                    cancelGitHubWorkflowRun(r.id)
                }
            }

            // Always ensure the latest worker script & workflow are pushed before dispatching
            runCatching { deployAgentWorkflowToGitHub(gh) }

            // Write full lessons payload to `scripts/active_task_payload.json` in the repo so even 200+ lessons
            // never exceed GitHub's workflow_dispatch input size limit (HTTP 422) and never get stripped!
            if (bundledLessonsJson != null && bundledLessonsJson.length() > 0) {
                runCatching {
                    val activePayloadFileJson = JSONObject().apply {
                        put("task_id", task.id)
                        put("updated_at", System.currentTimeMillis())
                        put("lessons", bundledLessonsJson)
                    }
                    putFileToGitHubRepo(
                        gh = gh,
                        path = "scripts/active_task_payload.json",
                        content = activePayloadFileJson.toString(2),
                        commitMessage = "chore(ai-agent): stage active task lessons payload (${bundledLessonsJson.length()} items)",
                        branch = gh.branch.ifBlank { "main" }
                    )
                }
            }

            val payloadJson = JSONObject().apply {
                put("supabase_url", supabaseUrl)
                put("supabase_anon_key", supabaseKey)
                put("device_hash", deviceHash)
                put("github_pat", gh.personalAccessToken)
                put("github_repository", "${gh.owner}/${gh.repo}")
                put("github_branch", gh.branch.ifBlank { "main" })
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
                        put("target_topic_id", task.targetTopicId)
                        put("target_topic_title", task.targetTopicTitle)
                        put("extra_payload_json", extraObj)
                    }
                )
            }

            val payloadB64 = Base64.encodeToString(
                payloadJson.toString().toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )

            // Keep workflow_dispatch input under 10KB so GitHub API never rejects with HTTP 422;
            // the full `lessons` array is already committed in `scripts/active_task_payload.json` above!
            val safePayloadB64 = if (payloadB64.length < 10000) {
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

    /**
     * Fetches ALL lessons across ALL Quarters (`getProgramPhases`) of a course in parallel,
     * tagging each lesson with its Quarter ID (`phase_id`) and Quarter Title (`phase_title`).
     */
    suspend fun fetchAllLessonsAcrossQuarters(
        programId: String,
        specificPhaseId: String? = null
    ): Pair<List<QuarterInspectionItem>, List<StudentLessonItem>> = withContext(Dispatchers.IO) {
        val cleanProgramId = programId.ifBlank { sessionManager.getActiveProgramId().orEmpty() }
        if (cleanProgramId.isBlank()) return@withContext Pair(emptyList(), emptyList())

        val phases = try {
            courseRepository.getProgramPhases(cleanProgramId)
        } catch (_: Exception) {
            emptyList()
        }

        if (phases.isNotEmpty()) {
            val phaseResults = coroutineScope {
                phases.mapIndexed { index, phase ->
                    async {
                        val pId = phase.id
                        val pTitle = phase.title?.takeIf { it.isNotBlank() } ?: "কোয়ার্টার ${index + 1}"
                        val rawLessons = try {
                            courseRepository.fetchAllLessonsForProgram(cleanProgramId, pId)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val tagged = rawLessons.map { l ->
                            l.copy(
                                phase_id = l.phase_id?.takeIf { it.isNotBlank() } ?: pId
                            )
                        }
                        val classLessons = tagged.filter {
                            !it.isExam && !it.isModelTest && !it.isLiveExam &&
                                (!it.isUpcoming || it.candidateStreamUrls.isNotEmpty() || !it.resolvedSlideUrl.isNullOrBlank())
                        }
                        val vCount = classLessons.size
                        val directSlides = classLessons.sumOf { lesson ->
                            val hasPrimary = if (!lesson.resolvedSlideUrl.isNullOrBlank()) 1 else 0
                            val extra = lesson.allAttachments.count { !it.downloadUrl.isNullOrBlank() }
                            maxOf(hasPrimary, extra)
                        }
                        val qItem = QuarterInspectionItem(
                            phaseId = pId,
                            phaseTitle = pTitle,
                            isCurrent = phase.is_current == true,
                            lessonsCount = classLessons.size,
                            videosCount = vCount,
                            slidesCount = maxOf(directSlides, vCount)
                        )
                        qItem to tagged
                    }
                }.map { it.await() }
            }

            val quarterItems = phaseResults.map { it.first }
            val phaseTitleMap = quarterItems.associate { it.phaseId to it.phaseTitle }
            val combinedLessons = if (!specificPhaseId.isNullOrBlank()) {
                phaseResults
                    .filter { it.first.phaseId == specificPhaseId }
                    .flatMap { it.second }
                    .ifEmpty { phaseResults.flatMap { it.second } }
            } else {
                phaseResults.flatMap { it.second }
            }.distinctBy { it.id.ifBlank { "${it.content_id}_${it.title}" } }
                .map { l ->
                    val pTitle = phaseTitleMap[l.phase_id] ?: ""
                    if (pTitle.isNotBlank() && l.chapter_name.isNullOrBlank()) {
                        l.copy(chapter_name = pTitle)
                    } else l
                }

            if (combinedLessons.isNotEmpty()) {
                return@withContext Pair(quarterItems, combinedLessons)
            }
        }

        val fallbackPhase = specificPhaseId?.ifBlank { null }
        val fallbackLessons = try {
            courseRepository.fetchAllLessonsForProgram(cleanProgramId, fallbackPhase)
        } catch (_: Exception) {
            emptyList()
        }
        Pair(emptyList(), fallbackLessons)
    }

    suspend fun inspectProgramContent(
        programId: String,
        programTitle: String,
        phaseId: String? = null
    ): CourseInspectionSummary = withContext(Dispatchers.IO) {
        val cleanProgramId = programId.ifBlank { sessionManager.getActiveProgramId().orEmpty() }
        // Pass null by default so ALL 5+ Quarters are inspected together unless user filtered a specific quarter!
        val cleanPhaseId = phaseId?.trim().orEmpty()
        val (quarters, lessons) = fetchAllLessonsAcrossQuarters(
            programId = cleanProgramId,
            specificPhaseId = cleanPhaseId.ifBlank { null }
        )
        val phaseTitleMap = quarters.associate { it.phaseId to it.phaseTitle }

        val bySubject = lessons.groupBy {
            val codeKey = it.subject_code?.takeIf { c -> c.isNotBlank() }
                ?: it.subject_id?.takeIf { c -> c.isNotBlank() }
                ?: it.subject_name?.takeIf { c -> c.isNotBlank() }
                ?: "GEN"
            val nameKey = it.subject_name?.takeIf { n -> n.isNotBlank() } ?: "সাধারণ বিষয়"
            codeKey to nameKey
        }

        var totalVideos = 0
        var totalSlides = 0
        val breakdown = bySubject.map { (subjPair, subjLessons) ->
            val classLessons = subjLessons.filter {
                !it.isExam && !it.isModelTest && !it.isLiveExam &&
                    (!it.isUpcoming || it.candidateStreamUrls.isNotEmpty() || !it.resolvedSlideUrl.isNullOrBlank())
            }
            val vCount = classLessons.size
            val directSlides = classLessons.sumOf { lesson ->
                val hasPrimary = if (!lesson.resolvedSlideUrl.isNullOrBlank()) 1 else 0
                val extra = lesson.allAttachments.count { !it.downloadUrl.isNullOrBlank() }
                maxOf(hasPrimary, extra)
            }
            val sCount = maxOf(directSlides, vCount)
            totalVideos += vCount
            totalSlides += sCount
            SubjectInspectionItem(
                subjectCode = subjPair.first,
                subjectTitle = subjPair.second,
                lessonsCount = classLessons.size,
                videosCount = vCount,
                slidesCount = sCount
            )
        }

        val catalog = lessons
            .filter { !it.isExam && !it.isModelTest && !it.isLiveExam }
            .map { l ->
                val pId = l.phase_id.orEmpty()
                val pTitle = phaseTitleMap[pId] ?: ""
                AgentPlayableLesson(
                    id = l.id,
                    contentId = l.content_id?.takeIf { it.isNotBlank() }
                        ?: l.live_class?.id?.takeIf { it.isNotBlank() }
                        ?: l.id,
                    title = l.title ?: "লেকচার",
                    subjectCode = l.subject_code ?: l.subject_id ?: "",
                    subjectTitle = l.subject_name ?: "বিষয়",
                    chapterId = l.chapter_id ?: "",
                    chapterTitle = l.chapter_name ?: pTitle,
                    phaseId = pId,
                    phaseTitle = pTitle,
                    teacherName = l.live_class?.teacher?.name ?: "",
                    videoUrl = l.candidateStreamUrls.firstOrNull().orEmpty(),
                    slideUrl = l.resolvedSlideUrl.orEmpty(),
                    isAnimated = l.content_type.equals("AnimatedLesson", ignoreCase = true)
                )
            }

        CourseInspectionSummary(
            programId = cleanProgramId,
            programTitle = programTitle.ifBlank { sessionManager.getActiveProgramTitleBn() ?: "সক্রিয় কোর্স" },
            phaseId = cleanPhaseId,
            subjectsCount = breakdown.size,
            totalLessonsCount = totalVideos,
            videoLessonsCount = totalVideos,
            slideAttachmentsCount = totalSlides,
            subjectBreakdown = breakdown,
            quarters = quarters,
            allLessonsCatalog = catalog
        )
    }

    /**
     * Resolves the real HLS/MP4 playback URL and PDF slide URL for a single `AgentPlayableLesson`
     * so the user can play it right from the AI Chat!
     */
    suspend fun resolvePlayableLessonUrls(item: AgentPlayableLesson): AgentPlayableLesson = withContext(Dispatchers.IO) {
        if (item.videoUrl.isNotBlank() && item.slideUrl.isNotBlank()) return@withContext item
        var vUrl = item.videoUrl
        var sUrl = item.slideUrl
        var tName = item.teacherName
        var cTitle = item.chapterTitle
        val candidates = listOf(item.contentId, item.id).filter { it.isNotBlank() }.distinct()
        for (cid in candidates) {
            try {
                val detail = courseRepository.getLiveClassDetails(cid)
                if (detail != null) {
                    if (vUrl.isBlank() && !detail.playback_url.isNullOrBlank()) {
                        vUrl = detail.playback_url
                    }
                    if (sUrl.isBlank()) {
                        sUrl = detail.study_materials?.firstOrNull { !it.file_url.isNullOrBlank() }?.file_url.orEmpty()
                    }
                    if (tName.isBlank() && !detail.teacher?.name.isNullOrBlank()) {
                        tName = detail.teacher?.name.orEmpty()
                    }
                    if (cTitle.isBlank() && !detail.chapter?.name.isNullOrBlank()) {
                        cTitle = detail.chapter?.name.orEmpty()
                    }
                    if (vUrl.isNotBlank() || sUrl.isNotBlank()) break
                }
            } catch (_: Exception) {
            }
        }
        item.copy(
            videoUrl = vUrl,
            slideUrl = sUrl,
            teacherName = tName,
            chapterTitle = cTitle
        )
    }

    /**
     * Builds a serialized JSONArray of lessons (with pre-resolved `playback_url` and `study_materials` PDFs!)
     * from an explicit list of `StudentLessonItem`s (used when sending a single class, a chapter, or a full course).
     * Uses a 10-worker coroutine Semaphore so even 100 lessons resolve their live-class URLs in ~2 seconds!
     */
    suspend fun buildBundledLessonsFromItems(
        items: List<StudentLessonItem>
    ): Pair<Int, JSONArray> = withContext(Dispatchers.IO) {
        val nonExamItems = items.filter { !it.isExam && !it.isModelTest && !it.isLiveExam }
        val recordedOrActiveItems = nonExamItems.filter {
            !it.isUpcoming || it.candidateStreamUrls.isNotEmpty() || !it.resolvedSlideUrl.isNullOrBlank()
        }.ifEmpty { nonExamItems }

        val shouldPreResolve = recordedOrActiveItems.size <= 140
        val semaphore = Semaphore(10)

        val resolvedObjects = coroutineScope {
            recordedOrActiveItems.map { lesson ->
                async {
                    var streamUrl = lesson.candidateStreamUrls.firstOrNull().orEmpty()
                    var teacherName = lesson.live_class?.teacher?.name ?: ""
                    val slidesArr = JSONArray()

                    val initialSlide = lesson.resolvedSlideUrl.orEmpty()
                    if (initialSlide.isNotBlank() && initialSlide != "null") {
                        slidesArr.put(
                            JSONObject().apply {
                                put("title", "${lesson.title ?: "লেকচার"} - স্লাইড")
                                put("url", initialSlide)
                            }
                        )
                    }
                    lesson.allAttachments.forEach { att ->
                        val u = att.downloadUrl.orEmpty().trim()
                        if (u.isNotBlank() && u != "null" && u != initialSlide) {
                            slidesArr.put(
                                JSONObject().apply {
                                    put("title", att.displayTitle.ifBlank { "লেকচার স্লাইড" })
                                    put("url", u)
                                }
                            )
                        }
                    }

                    val candidateIds = listOfNotNull(
                        lesson.content_id?.takeIf { it.isNotBlank() },
                        lesson.live_class?.id?.takeIf { it.isNotBlank() },
                        lesson.id.takeIf { it.isNotBlank() },
                        lesson.session_id?.takeIf { it.isNotBlank() }
                    ).distinct()

                    if (shouldPreResolve && (streamUrl.isBlank() || slidesArr.length() == 0)) {
                        semaphore.acquire()
                        try {
                            for (cid in candidateIds) {
                                try {
                                    val detail = courseRepository.getLiveClassDetails(cid)
                                    if (detail != null) {
                                        if (streamUrl.isBlank() && !detail.playback_url.isNullOrBlank()) {
                                            streamUrl = detail.playback_url
                                        }
                                        if (teacherName.isBlank() && !detail.teacher?.name.isNullOrBlank()) {
                                            teacherName = detail.teacher?.name.orEmpty()
                                        }
                                        detail.study_materials?.forEach { mat ->
                                            val fUrl = mat.file_url?.trim().orEmpty()
                                            if (fUrl.isNotBlank() && fUrl != "null") {
                                                var exists = false
                                                for (k in 0 until slidesArr.length()) {
                                                    if (slidesArr.optJSONObject(k)?.optString("url") == fUrl) {
                                                        exists = true
                                                        break
                                                    }
                                                }
                                                if (!exists) {
                                                    slidesArr.put(
                                                        JSONObject().apply {
                                                            put(
                                                                "title",
                                                                mat.name?.takeIf { it.isNotBlank() }
                                                                    ?: "${lesson.title ?: "লেকচার"} - স্লাইড"
                                                            )
                                                            put("url", fUrl)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        if (streamUrl.isNotBlank() || slidesArr.length() > 0) break
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        } finally {
                            semaphore.release()
                        }
                    }

                    val primaryContentId = candidateIds.firstOrNull() ?: lesson.id
                    val primarySlideUrl = if (slidesArr.length() > 0) {
                        slidesArr.optJSONObject(0)?.optString("url", "").orEmpty()
                    } else ""

                    val candJsonArr = JSONArray().apply {
                        candidateIds.forEach { put(it) }
                    }

                    JSONObject().apply {
                        put("id", lesson.id)
                        put("content_id", primaryContentId)
                        put("live_class_id", lesson.live_class?.id ?: "")
                        put("session_id", lesson.session_id ?: "")
                        put("candidate_ids", candJsonArr)
                        put("title", lesson.title ?: "লেকচার")
                        put("content_type", lesson.content_type ?: "LiveClass")
                        put("subject_code", lesson.subject_code ?: lesson.subject_id ?: "")
                        put("subject_name", lesson.subject_name ?: "")
                        put("chapter_id", lesson.chapter_id ?: "")
                        put("chapter_name", lesson.chapter_name ?: "")
                        put("teacher_name", teacherName)
                        if (streamUrl.isNotBlank()) put("video_url", streamUrl)
                        if (primarySlideUrl.isNotBlank()) put("slide_url", primarySlideUrl)
                        if (slidesArr.length() > 0) put("slides", slidesArr)
                    }
                }
            }.map { it.await() }
        }

        val arr = JSONArray()
        // Always include ALL lessons in chronological order! Never drop lessons whose stream URLs
        // were not pre-resolved yet — the Python cloud worker resolves them directly via Shikho GraphQL.
        resolvedObjects.forEach { arr.put(it) }
        Pair(resolvedObjects.size, arr)
    }

    /**
     * Builds a serialized JSONArray of lessons (with live_class/content_id and direct playback URLs)
     * across ALL Quarters (or a specific Quarter/Subject/Chapter/Lesson ID list)
     * so the GitHub Actions worker can download & upload every video and slide accurately.
     */
    suspend fun buildBundledLessonsJson(
        programId: String,
        phaseId: String?,
        subjectCode: String = "",
        chapterId: String = "",
        specificLessonIds: List<String> = emptyList(),
        subjectTitle: String = ""
    ): Pair<Int, JSONArray> = withContext(Dispatchers.IO) {
        val (_, allLessons) = fetchAllLessonsAcrossQuarters(
            programId = programId,
            specificPhaseId = phaseId?.ifBlank { null }
        )

        val specificSet = specificLessonIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()

        val filtered = allLessons.filter { lesson ->
            val isClassLesson = !lesson.isExam && !lesson.isModelTest && !lesson.isLiveExam
            if (!isClassLesson) return@filter false
            if (specificSet.isNotEmpty()) {
                return@filter lesson.id in specificSet || (lesson.content_id != null && lesson.content_id in specificSet)
            }
            val matchesSubject = (subjectCode.isBlank() && subjectTitle.isBlank()) ||
                (subjectCode.isNotBlank() && (
                    lesson.subject_code.equals(subjectCode, ignoreCase = true) ||
                        lesson.subject_id.equals(subjectCode, ignoreCase = true) ||
                        (lesson.subject_name?.contains(subjectCode, ignoreCase = true) == true)
                    )) ||
                (subjectTitle.isNotBlank() && (
                    lesson.subject_name?.equals(subjectTitle, ignoreCase = true) == true ||
                        lesson.subject_name?.contains(subjectTitle, ignoreCase = true) == true ||
                        subjectTitle.contains(lesson.subject_name.orEmpty().ifBlank { "___" }, ignoreCase = true)
                    ))
            val matchesChapter = chapterId.isBlank() ||
                lesson.chapter_id.equals(chapterId, ignoreCase = true) ||
                (lesson.chapter_name?.contains(chapterId, ignoreCase = true) == true)
            matchesSubject && matchesChapter
        }

        val (count, bundledArr) = buildBundledLessonsFromItems(filtered)
        if (count > 0) {
            return@withContext Pair(count, bundledArr)
        }

        // Fallback: if a chapterId was requested and had 0 live classes, check Animated Lessons (GetTopics)!
        if (chapterId.isNotBlank()) {
            try {
                val topics = courseRepository.getTopics(chapterId = chapterId)
                val animatedItems = topics.mapIndexedNotNull { idx, topic ->
                    val pbUrl = topic.videos?.data?.firstOrNull { !it.playback_url.isNullOrBlank() }?.playback_url
                        ?: return@mapIndexedNotNull null
                    StudentLessonItem(
                        id = topic.id ?: "anim_${chapterId}_$idx",
                        title = topic.name ?: "অ্যানিমেটেড লেসন ${idx + 1}",
                        content_type = "AnimatedLesson",
                        chapter_id = chapterId,
                        video_url = pbUrl
                    )
                }
                if (animatedItems.isNotEmpty()) {
                    return@withContext buildBundledLessonsFromItems(animatedItems)
                }
            } catch (_: Exception) {
            }
        }

        Pair(count, bundledArr)
    }

    // =========================================================================
    // 4. TASKS & 24/7 SCHEDULES STORAGE (SUPABASE + GITHUB BRIDGE + LOCAL)
    // =========================================================================

    suspend fun fetchTasks(): List<AiAgentTask> = withContext(Dispatchers.IO) {
        val localTasks = getLocalTasks().toMutableList()

        // 1. Check GitHub Auth/Task State Bridge (scripts/agent_auth_state.json) for real-time task progress
        try {
            val ghState = readGitHubAuthState(getGitHubConfig())
            val activeTaskObj = ghState?.optJSONObject("active_task")
            if (activeTaskObj != null) {
                val tId = activeTaskObj.optString("id", "")
                if (tId.isNotBlank()) {
                    val idx = localTasks.indexOfFirst { it.id == tId }
                    if (idx >= 0) {
                        val old = localTasks[idx]
                        localTasks[idx] = old.copy(
                            status = activeTaskObj.optString("status", old.status),
                            completedItems = activeTaskObj.optInt("completed_items", old.completedItems),
                            totalItems = activeTaskObj.optInt("total_items", old.totalItems),
                            currentItemTitle = activeTaskObj.optString("current_item_title", old.currentItemTitle),
                            statusMessage = activeTaskObj.optString("status_message", old.statusMessage),
                            targetChatId = activeTaskObj.optString("target_chat_id", old.targetChatId),
                            telegramInviteLink = activeTaskObj.optString("telegram_invite_link", old.telegramInviteLink),
                            githubRunId = activeTaskObj.optString("github_run_id", old.githubRunId)
                        )
                        saveTasksListLocally(localTasks)
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Check Supabase ai_agent_tasks table if available
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
        localTasks
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
        val localList = getLocalSchedules().toMutableList()
        try {
            val ghState = readGitHubAuthState(getGitHubConfig())
            val ghSchedArr = ghState?.optJSONArray("schedules")
            if (ghSchedArr != null && ghSchedArr.length() > 0) {
                val parsedGh = parseSchedulesArray(ghSchedArr)
                if (parsedGh.isNotEmpty() && localList.isEmpty()) {
                    saveSchedulesListLocally(parsedGh)
                    return@withContext parsedGh
                }
            }
        } catch (_: Exception) {
        }

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

    private fun serializeSchedulesArray(schedules: List<AiAgentSchedule>): JSONArray {
        val arr = JSONArray()
        val deviceHash = runCatching { getCurrentDeviceHash() }.getOrDefault("")
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
                    put("telegram_invite_link", s.telegramInviteLink)
                    put("use_forum_topics", s.useForumTopics)
                    put("video_quality", s.videoQuality)
                    put("include_videos", s.includeVideos)
                    put("include_slides", s.includeSlides)
                    put("include_daily_routine", s.includeDailyRoutine)
                    put("is_enabled", s.isEnabled)
                    put("last_run_at", s.lastRunAt)
                    put("last_run_status", s.lastRunStatus)
                    put("created_by_device_hash", deviceHash)
                }
            )
        }
        return arr
    }

    suspend fun saveSchedule(schedule: AiAgentSchedule) = withContext(Dispatchers.IO) {
        val current = getLocalSchedules().toMutableList()
        val idx = current.indexOfFirst { it.id == schedule.id || (schedule.programId.isNotBlank() && it.programId == schedule.programId && it.subjectCode == schedule.subjectCode) }
        if (idx >= 0) {
            current[idx] = schedule
        } else {
            current.add(0, schedule)
        }
        saveSchedulesListLocally(current)

        val gh = getGitHubConfig()
        val currentShikhoToken = sessionManager.getAccessToken()?.trim().orEmpty()
        if (gh.personalAccessToken.isNotBlank()) {
            runCatching {
                writeGitHubAuthState(
                    gh,
                    JSONObject().apply {
                        put("schedules", serializeSchedulesArray(current))
                        if (currentShikhoToken.isNotBlank()) {
                            put("shikho_access_token", currentShikhoToken)
                        }
                    }
                )
            }
        }

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

        val gh = getGitHubConfig()
        if (gh.personalAccessToken.isNotBlank()) {
            runCatching {
                writeGitHubAuthState(
                    gh,
                    JSONObject().apply {
                        put("schedules", serializeSchedulesArray(updated))
                    }
                )
            }
        }

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

    suspend fun deleteTask(taskId: String) = withContext(Dispatchers.IO) {
        val updated = getLocalTasks().filterNot { it.id == taskId }
        saveTasksListLocally(updated)

        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        if (supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
            try {
                val req = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_tasks?id=eq.$taskId")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {}
        }
    }

    suspend fun clearCompletedTasks() = withContext(Dispatchers.IO) {
        val activeOnly = getLocalTasks().filter { it.status.uppercase() in setOf("RUNNING", "QUEUED", "RESUMING") }
        saveTasksListLocally(activeOnly)

        val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
        if (supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
            try {
                val req = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/ai_agent_tasks?status=in.(COMPLETED,FAILED,CANCELLED)")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (_: Exception) {}
        }
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
                    put("target_topic_id", t.targetTopicId)
                    put("target_topic_title", t.targetTopicTitle)
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
                    targetTopicId = obj.optInt("target_topic_id", 0),
                    targetTopicTitle = obj.optString("target_topic_title", ""),
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
        val arr = serializeSchedulesArray(schedules)
        prefs.edit().putString(KEY_LOCAL_SCHEDULES_JSON, arr.toString()).apply()
    }

    private fun parseSchedulesArray(arr: JSONArray): List<AiAgentSchedule> {
        val list = mutableListOf<AiAgentSchedule>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                AiAgentSchedule(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", "দৈনিক ২৪/৭ রুটিন সিঙ্ক"),
                    scheduleType = obj.optString("schedule_type", "DAILY_COURSE_SYNC"),
                    cronExpression = obj.optString("cron_expression", "0 19 * * *"),
                    timeDisplayBn = obj.optString("time_display_bn", "প্রতিদিন রাত ০১:০০"),
                    programId = obj.optString("program_id", ""),
                    programTitle = obj.optString("program_title", ""),
                    phaseId = obj.optString("phase_id", ""),
                    subjectCode = obj.optString("subject_code", ""),
                    targetChatId = obj.optString("target_chat_id", ""),
                    targetChannelTitle = obj.optString("target_channel_title", ""),
                    telegramInviteLink = obj.optString("telegram_invite_link", ""),
                    useForumTopics = obj.optBoolean("use_forum_topics", true),
                    videoQuality = obj.optString("video_quality", "720p"),
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

    // =========================================================================
    // 5. GEMINI-STYLE MULTI-SESSION CHAT HISTORY PERSISTENCE
    // =========================================================================

    fun getActiveChatSessionId(): String {
        return prefs.getString(KEY_ACTIVE_CHAT_SESSION_ID, "")?.trim().orEmpty()
    }

    fun setActiveChatSessionId(sessionId: String) {
        prefs.edit().putString(KEY_ACTIVE_CHAT_SESSION_ID, sessionId.trim()).apply()
    }

    fun getChatSessions(): List<AiChatSession> {
        val raw = prefs.getString(KEY_CHAT_SESSIONS_JSON, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<AiChatSession>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val msgsArr = obj.optJSONArray("messages") ?: JSONArray()
                val msgs = mutableListOf<AiAgentChatMessage>()
                for (j in 0 until msgsArr.length()) {
                    val mObj = msgsArr.optJSONObject(j) ?: continue
                    val planObj = mObj.optJSONObject("proposed_plan")
                    val plan = if (planObj != null) {
                        val specIdsArr = planObj.optJSONArray("specific_lesson_ids")
                        val specIds = mutableListOf<String>()
                        if (specIdsArr != null) {
                            for (k in 0 until specIdsArr.length()) {
                                val sid = specIdsArr.optString(k, "")
                                if (sid.isNotBlank()) specIds.add(sid)
                            }
                        }
                        ProposedAgentPlan(
                            actionType = planObj.optString("action_type", "PROPOSE_PLAN"),
                            taskTitle = planObj.optString("task_title", ""),
                            programId = planObj.optString("program_id", ""),
                            programTitle = planObj.optString("program_title", ""),
                            phaseId = planObj.optString("phase_id", ""),
                            phaseTitle = planObj.optString("phase_title", ""),
                            subjectCode = planObj.optString("subject_code", ""),
                            subjectTitle = planObj.optString("subject_title", ""),
                            chapterId = planObj.optString("chapter_id", ""),
                            chapterTitle = planObj.optString("chapter_title", ""),
                            targetChannelTitle = planObj.optString("target_channel_title", ""),
                            targetChatId = planObj.optString("target_chat_id", ""),
                            targetTopicId = planObj.optInt("target_topic_id", 0),
                            targetTopicTitle = planObj.optString("target_topic_title", ""),
                            videoQuality = planObj.optString("video_quality", "720p"),
                            includeVideos = planObj.optBoolean("include_videos", true),
                            includeSlides = planObj.optBoolean("include_slides", true),
                            createNewChannel = planObj.optBoolean("create_new_channel", true),
                            useForumTopics = planObj.optBoolean("use_forum_topics", false),
                            enableLifetimeSync = planObj.optBoolean("enable_lifetime_sync", false),
                            scheduleTimeBn = planObj.optString("schedule_time_bn", "প্রতিদিন রাত ০১:০০"),
                            cronExpression = planObj.optString("cron_expression", "0 19 * * *"),
                            estimatedVideos = planObj.optInt("estimated_videos", 0),
                            estimatedSlides = planObj.optInt("estimated_slides", 0),
                            specificLessonIds = specIds
                        )
                    } else null

                    val playArr = mObj.optJSONArray("playable_lessons")
                    val playList = mutableListOf<AgentPlayableLesson>()
                    if (playArr != null) {
                        for (k in 0 until playArr.length()) {
                            val pObj = playArr.optJSONObject(k) ?: continue
                            playList.add(
                                AgentPlayableLesson(
                                    id = pObj.optString("id", ""),
                                    contentId = pObj.optString("content_id", ""),
                                    title = pObj.optString("title", "লেকচার"),
                                    subjectCode = pObj.optString("subject_code", ""),
                                    subjectTitle = pObj.optString("subject_title", ""),
                                    chapterId = pObj.optString("chapter_id", ""),
                                    chapterTitle = pObj.optString("chapter_name", ""),
                                    phaseId = pObj.optString("phase_id", ""),
                                    phaseTitle = pObj.optString("phase_title", ""),
                                    teacherName = pObj.optString("teacher_name", ""),
                                    videoUrl = pObj.optString("video_url", ""),
                                    slideUrl = pObj.optString("slide_url", ""),
                                    isAnimated = pObj.optBoolean("is_animated", false)
                                )
                            )
                        }
                    }

                    msgs.add(
                        AiAgentChatMessage(
                            id = mObj.optString("id", UUID.randomUUID().toString()),
                            role = mObj.optString("role", "model"),
                            text = mObj.optString("text", ""),
                            timestamp = mObj.optLong("timestamp", System.currentTimeMillis()),
                            proposedPlan = plan,
                            playableLessons = playList,
                            autoPlayFirstLesson = false,
                            showChannelCatalog = mObj.optBoolean("show_channel_catalog", false),
                            isStreaming = false,
                            isError = mObj.optBoolean("is_error", false)
                        )
                    )
                }
                list.add(
                    AiChatSession(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "নতুন চ্যাট"),
                        createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updated_at", System.currentTimeMillis()),
                        programId = obj.optString("program_id", ""),
                        programTitle = obj.optString("program_title", ""),
                        messages = msgs
                    )
                )
            }
            list.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveChatSession(session: AiChatSession) {
        val current = getChatSessions().toMutableList()
        val idx = current.indexOfFirst { it.id == session.id }
        if (idx >= 0) {
            current[idx] = session
        } else {
            current.add(0, session)
        }
        saveAllChatSessions(current.sortedByDescending { it.updatedAt }.take(35))
        setActiveChatSessionId(session.id)
    }

    fun deleteChatSession(sessionId: String): List<AiChatSession> {
        val updated = getChatSessions().filterNot { it.id == sessionId }
        saveAllChatSessions(updated)
        if (getActiveChatSessionId() == sessionId) {
            setActiveChatSessionId(updated.firstOrNull()?.id.orEmpty())
        }
        return updated
    }

    private fun saveAllChatSessions(sessions: List<AiChatSession>) {
        val arr = JSONArray()
        sessions.forEach { s ->
            val msgsArr = JSONArray()
            s.messages.filterNot { it.isStreaming }.takeLast(60).forEach { m ->
                val mObj = JSONObject().apply {
                    put("id", m.id)
                    put("role", m.role)
                    put("text", m.text)
                    put("timestamp", m.timestamp)
                    put("show_channel_catalog", m.showChannelCatalog)
                    put("is_error", m.isError)
                    m.proposedPlan?.let { p ->
                        val specArr = JSONArray()
                        p.specificLessonIds.forEach { specArr.put(it) }
                        put(
                            "proposed_plan",
                            JSONObject().apply {
                                put("action_type", p.actionType)
                                put("task_title", p.taskTitle)
                                put("program_id", p.programId)
                                put("program_title", p.programTitle)
                                put("phase_id", p.phaseId)
                                put("phase_title", p.phaseTitle)
                                put("subject_code", p.subjectCode)
                                put("subject_title", p.subjectTitle)
                                put("chapter_id", p.chapterId)
                                put("chapter_title", p.chapterTitle)
                                put("target_channel_title", p.targetChannelTitle)
                                put("target_chat_id", p.targetChatId)
                                put("target_topic_id", p.targetTopicId)
                                put("target_topic_title", p.targetTopicTitle)
                                put("video_quality", p.videoQuality)
                                put("include_videos", p.includeVideos)
                                put("include_slides", p.includeSlides)
                                put("create_new_channel", p.createNewChannel)
                                put("use_forum_topics", p.useForumTopics)
                                put("enable_lifetime_sync", p.enableLifetimeSync)
                                put("schedule_time_bn", p.scheduleTimeBn)
                                put("cron_expression", p.cronExpression)
                                put("estimated_videos", p.estimatedVideos)
                                put("estimated_slides", p.estimatedSlides)
                                put("specific_lesson_ids", specArr)
                            }
                        )
                    }
                    if (m.playableLessons.isNotEmpty()) {
                        val playArr = JSONArray()
                        m.playableLessons.take(10).forEach { pl ->
                            playArr.put(
                                JSONObject().apply {
                                    put("id", pl.id)
                                    put("content_id", pl.contentId)
                                    put("title", pl.title)
                                    put("subject_code", pl.subjectCode)
                                    put("subject_title", pl.subjectTitle)
                                    put("chapter_id", pl.chapterId)
                                    put("chapter_name", pl.chapterTitle)
                                    put("phase_id", pl.phaseId)
                                    put("phase_title", pl.phaseTitle)
                                    put("teacher_name", pl.teacherName)
                                    put("video_url", pl.videoUrl)
                                    put("slide_url", pl.slideUrl)
                                    put("is_animated", pl.isAnimated)
                                }
                            )
                        }
                        put("playable_lessons", playArr)
                    }
                }
                msgsArr.put(mObj)
            }
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("created_at", s.createdAt)
                    put("updated_at", s.updatedAt)
                    put("program_id", s.programId)
                    put("program_title", s.programTitle)
                    put("messages", msgsArr)
                }
            )
        }
        prefs.edit().putString(KEY_CHAT_SESSIONS_JSON, arr.toString()).apply()
    }
}
