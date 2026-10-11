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

                    // Broadcast chat messages parsing
                    if (method == "on-broadcast" || method == "broadcast") {
                        val params = msg.optJSONObject("params")
                        val infoStr = params?.optString("info")
                        if (!infoStr.isNullOrBlank()) {
                            try {
                                val infoJson = JSONObject(infoStr)
                                val chatText = infoJson.optString("message")
                                val sender = infoJson.optString("sender", "শিক্ষার্থী")
                                if (chatText.isNotBlank()) {
                                    _incomingChatMessages.value = LiveChatMessage(
                                        senderName = sender,
                                        message = chatText,
                                        isTeacher = sender.contains("Teacher", ignoreCase = true) ||
                                                sender.contains("শিক্ষক") || sender.contains("Teacher")
                                    )
                                }
                            } catch (_: Exception) {}
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

    fun sendChatMessage(text: String, studentName: String) {
        val ws = webSocket ?: return
        try {
            val broadcastMsg = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("method", "broadcast")
                put("params", JSONObject().apply {
                    put("info", JSONObject().apply {
                        put("message", text)
                        put("sender", studentName)
                        put("time", System.currentTimeMillis())
                    }.toString())
                    put("type", "chat")
                })
                put("jsonrpc", "2.0")
            }
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
