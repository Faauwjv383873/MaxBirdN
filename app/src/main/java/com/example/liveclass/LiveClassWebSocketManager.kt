package com.example.liveclass

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class LiveClassWebSocketManager {

    companion object {
        private const val TAG = "LiveClassWS"
        private const val BASE_WS_URL = "wss://prod-in3.100ms.live/v2/ws"
    }

    // 15s ping interval ensures carrier NATs and 100ms servers do not drop the connection during live playback
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _masterUrl = MutableStateFlow<String?>(null)
    val masterUrl: StateFlow<String?> = _masterUrl.asStateFlow()

    private val _candidateUrls = MutableStateFlow<List<String>>(emptyList())
    val candidateUrls: StateFlow<List<String>> = _candidateUrls.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingChatMessages = MutableStateFlow<LiveChatMessage?>(null)
    val incomingChatMessages: StateFlow<LiveChatMessage?> = _incomingChatMessages.asStateFlow()

    fun setMasterUrl(url: String, viewers: Int = 1) {
        _masterUrl.value = url
        _viewerCount.value = viewers.coerceAtLeast(1)
        _isConnected.value = true
        _error.value = null
    }

    fun addCandidateUrls(urls: List<String>) {
        val current = _candidateUrls.value.toMutableList()
        current.addAll(urls.filter { it.isNotBlank() && it != "null" })
        _candidateUrls.value = current.distinct()
    }

    fun connect(
        token: String,
        studentName: String = "Student"
    ) {
        disconnect()

        val peerUuid = UUID.randomUUID().toString()
        val wsUrl = "$BASE_WS_URL?peer=$peerUuid&token=$token&protocol_version=2.5&protocol_spec=20240720"

        Log.d(TAG, "Connecting to WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Opened successfully")
                _isConnected.value = true
                _error.value = null

                // Send join message
                val joinMessage = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("method", "join")
                    put("params", JSONObject().apply {
                        put("name", studentName.ifBlank { "Student" })
                        put("disableVidAutoSub", true)
                        put("simulcast", true)
                    })
                    put("jsonrpc", "2.0")
                }

                Log.d(TAG, "Sending join message: ${joinMessage.toString()}")
                ws.send(joinMessage.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    val method = msg.optString("method")

                    if (method == "peer-list") {
                        Log.d(TAG, "Received peer-list message")
                        val params = msg.optJSONObject("params")
                        val room = params?.optJSONObject("room")
                        val streaming = room?.optJSONObject("streaming")
                        val hls = streaming?.optJSONObject("hls")
                        val variants = hls?.optJSONArray("variants")

                        if (variants != null && variants.length() > 0) {
                            val variant = variants.optJSONObject(0)
                            val url = variant?.optString("url")
                            if (!url.isNullOrBlank()) {
                                Log.d(TAG, "Found Master Link: $url")
                                _masterUrl.value = url
                            }
                        }

                        // Calculate initial peer/viewer count
                        val peers = params?.optJSONObject("peers")
                        val initialCount = peers?.length() ?: 0
                        if (initialCount > 0) {
                            _viewerCount.value = initialCount
                        }
                    }

                    if (method == "session-info") {
                        val params = msg.optJSONObject("params")
                        val peerCount = params?.optInt("peer_count", 0) ?: 0
                        if (peerCount > 0) {
                            _viewerCount.value = peerCount
                        }
                    }

                    // Check result object (JSON-RPC reply to 'join')
                    val result = msg.optJSONObject("result")
                    if (result != null) {
                        val room = result.optJSONObject("room")
                        val streaming = room?.optJSONObject("streaming") ?: result.optJSONObject("streaming")
                        val hls = streaming?.optJSONObject("hls")
                        val variants = hls?.optJSONArray("variants")
                        if (variants != null && variants.length() > 0) {
                            val url = variants.optJSONObject(0)?.optString("url")
                            if (!url.isNullOrBlank()) {
                                Log.d(TAG, "Found Master Link in result: $url")
                                _masterUrl.value = url
                            }
                        }
                    }

                    // Universal regex fallback for any HLS m3u8 stream link in websocket frame
                    if (_masterUrl.value.isNullOrBlank() && text.contains(".m3u8", ignoreCase = true)) {
                        val regex = Regex("""https?://[^\s"'\\]+\.m3u8[^\s"'\\]*""")
                        val match = regex.find(text)?.value
                        if (!match.isNullOrBlank()) {
                            val cleanUrl = match.replace("\\/", "/")
                            Log.d(TAG, "Regex extracted Master HLS Link: $cleanUrl")
                            _masterUrl.value = cleanUrl
                        }
                    }

                    // Broadcast & Direct chat messages parsing (matching 100ms / Shikho real HAR file protocol)
                    if (method == "on-broadcast" || method == "broadcast" || method == "on-message" || method == "message") {
                        val params = msg.optJSONObject("params")
                        var chatText: String? = null
                        var sender: String? = null
                        var role: String? = null
                        var timestampMillis = System.currentTimeMillis()

                        // 1. Try reading info parameter (which in 100ms HAR can be a JSON string, a JSONObject, or plain text)
                        val infoRaw = params?.opt("info")
                        when (infoRaw) {
                            is JSONObject -> {
                                chatText = infoRaw.optString("message").takeIf { it.isNotBlank() }
                                    ?: infoRaw.optString("msg").takeIf { it.isNotBlank() }
                                    ?: infoRaw.optString("text").takeIf { it.isNotBlank() }
                                sender = infoRaw.optString("sender").takeIf { it.isNotBlank() }
                                    ?: infoRaw.optString("name").takeIf { it.isNotBlank() }
                                role = infoRaw.optString("role").takeIf { it.isNotBlank() }
                                if (infoRaw.has("time")) timestampMillis = infoRaw.optLong("time", timestampMillis)
                            }
                            is String -> {
                                if (infoRaw.trim().startsWith("{") && infoRaw.trim().endsWith("}")) {
                                    try {
                                        val infoJson = JSONObject(infoRaw)
                                        chatText = infoJson.optString("message").takeIf { it.isNotBlank() }
                                            ?: infoJson.optString("msg").takeIf { it.isNotBlank() }
                                            ?: infoJson.optString("text").takeIf { it.isNotBlank() }
                                        sender = infoJson.optString("sender").takeIf { it.isNotBlank() }
                                            ?: infoJson.optString("name").takeIf { it.isNotBlank() }
                                        role = infoJson.optString("role").takeIf { it.isNotBlank() }
                                        if (infoJson.has("time")) timestampMillis = infoJson.optLong("time", timestampMillis)
                                    } catch (_: Exception) {
                                        chatText = infoRaw
                                    }
                                } else {
                                    chatText = infoRaw
                                }
                            }
                        }

                        // 2. Fallbacks directly from params if not extracted from info
                        if (chatText.isNullOrBlank()) {
                            chatText = params?.optString("message")?.takeIf { it.isNotBlank() }
                                ?: params?.optString("msg")?.takeIf { it.isNotBlank() }
                                ?: params?.optString("text")?.takeIf { it.isNotBlank() }
                        }

                        if (sender.isNullOrBlank()) {
                            val peer = params?.optJSONObject("peer")
                            sender = peer?.optString("name")?.takeIf { it.isNotBlank() }
                                ?: params?.optString("sender")?.takeIf { it.isNotBlank() }
                                ?: params?.optString("from")?.takeIf { it.isNotBlank() }
                                ?: "শিক্ষার্থী"
                            if (role.isNullOrBlank()) {
                                role = peer?.optString("role")?.takeIf { it.isNotBlank() }
                            }
                        }

                        if (role.isNullOrBlank()) {
                            val rolesArr = params?.optJSONArray("roles")
                            if (rolesArr != null && rolesArr.length() > 0) {
                                role = rolesArr.optString(0)
                            } else {
                                role = params?.optString("role")?.takeIf { it.isNotBlank() }
                            }
                        }

                        // Determine normalized role: teacher, student, all
                        val normalizedRole = when {
                            role?.contains("teacher", ignoreCase = true) == true ||
                            sender?.contains("Teacher", ignoreCase = true) == true ||
                            sender?.contains("শিক্ষক") == true -> "teacher"
                            role?.equals("*") == true || role?.contains("all", ignoreCase = true) == true -> "all"
                            else -> "student"
                        }

                        val isTeacher = normalizedRole == "teacher"

                        if (!chatText.isNullOrBlank()) {
                            val formattedTime = try {
                                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                sdf.format(java.util.Date(timestampMillis))
                            } catch (_: Exception) {
                                "এখন"
                            }

                            _incomingChatMessages.value = LiveChatMessage(
                                id = UUID.randomUUID().toString(),
                                senderName = sender ?: "শিক্ষার্থী",
                                message = chatText,
                                timestamp = formattedTime,
                                isTeacher = isTeacher,
                                role = normalizedRole
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WebSocket message: ${e.message}", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}", t)
                _isConnected.value = false
                // CRITICAL FIX: If masterUrl is ALREADY ACQUIRED, the user's video player is actively
                // streaming over HLS via ExoPlayer. The WebSocket is only a signaling/chat layer.
                // A dropped WebSocket must NEVER kill active video playback or crash to fatal error!
                if (_masterUrl.value.isNullOrBlank()) {
                    _error.value = t.message ?: "WebSocket connection failed"
                } else {
                    Log.d(TAG, "WebSocket dropped while stream is already active; keeping playback uninterrupted.")
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $code / $reason")
                _isConnected.value = false
            }
        })
    }

    /**
     * Send broadcast chat message conforming exactly to 100ms / Shikho real HAR file protocol.
     * Roles supported: "teacher", "student", or "*" / "all"
     */
    fun sendChatMessage(
        text: String,
        studentName: String,
        role: String = "student",
        targetRole: String = "*"
    ) {
        val ws = webSocket ?: return
        try {
            val normalizedRole = when (role.lowercase()) {
                "teacher" -> "teacher"
                "student", "students" -> "student"
                else -> "all"
            }

            val targetRolesArray = org.json.JSONArray().apply {
                when (targetRole.lowercase()) {
                    "teacher" -> put("teacher")
                    "student", "students" -> {
                        put("student")
                        put("students")
                    }
                    else -> put("*")
                }
            }

            val broadcastMsg = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("method", "broadcast")
                put("params", JSONObject().apply {
                    put("info", JSONObject().apply {
                        put("message", text)
                        put("sender", studentName)
                        put("role", normalizedRole)
                        put("time", System.currentTimeMillis())
                    }.toString())
                    put("type", "chat")
                    put("roles", targetRolesArray)
                })
                put("jsonrpc", "2.0")
            }
            Log.d(TAG, "Sending broadcast message (role: $normalizedRole, targets: $targetRolesArray): ${broadcastMsg.toString()}")
            ws.send(broadcastMsg.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send chat message over WebSocket: ${e.message}")
        }
    }

    fun setDirectMasterUrl(url: String) {
        if (url.isNotBlank()) {
            Log.d(TAG, "Setting direct Master URL: $url")
            _masterUrl.value = url
            _isConnected.value = true
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Leaving Live Class")
            webSocket = null
            _isConnected.value = false
            _masterUrl.value = null
            _candidateUrls.value = emptyList()
            _viewerCount.value = 0
            _error.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WebSocket: ${e.message}", e)
        }
    }
}
