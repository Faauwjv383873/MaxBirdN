package com.example.player

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages real-time WebSocket communication for 100ms Live Class rooms using JSON-RPC 2.0.
 *
 * Provides:
 * 1. Live viewer count tracking ("session-info")
 * 2. Pinned message notices ("on-metadata-change")
 * 3. Real-time chat messages broadcast & receive ("broadcast", "on-message")
 * 4. Hand raise status toggle and sync ("group-join", "group-leave", "peer-update", "on-peer-update")
 * 5. Live Poll / Quiz listener ("on-poll-start", "poll-start")
 */
class HmsLiveSocketManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for WebSocket
        .build()
) {
    companion object {
        private const val TAG = "HmsLiveSocketManager"
        private const val BASE_WS_URL = "wss://prod-in3.100ms.live/v2/ws"
    }

    private val managerScope = CoroutineScope(Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null

    private var currentPeerUuid: String = UUID.randomUUID().toString()
    private var currentRoomId: String? = null
    private var currentUserName: String = "Student"

    // 1. Live Viewer Count (default 1)
    private val _viewerCount = MutableStateFlow(1)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    // 2. Pinned Message
    private val _pinnedMessage = MutableStateFlow<PinnedMessageData?>(null)
    val pinnedMessage: StateFlow<PinnedMessageData?> = _pinnedMessage.asStateFlow()

    // 3. Chat Messages
    private val _chatMessages = MutableStateFlow<List<ChatMessageItem>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageItem>> = _chatMessages.asStateFlow()

    // 4. Hand Raise Status
    private val _isHandRaised = MutableStateFlow(false)
    val isHandRaised: StateFlow<Boolean> = _isHandRaised.asStateFlow()

    // 5. Active Poll / Quiz
    private val _activePoll = MutableStateFlow<PollData?>(null)
    val activePoll: StateFlow<PollData?> = _activePoll.asStateFlow()

    // Connection Status
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // 6. Live HLS Master Stream URL
    private val _hlsStreamUrl = MutableStateFlow<String?>(null)
    val hlsStreamUrl: StateFlow<String?> = _hlsStreamUrl.asStateFlow()

    /**
     * Recursively searches for the stream master URL under nested "variants" array in JSON response.
     */
    private fun recursivelyFindHlsUrl(json: Any) {
        if (json is JSONObject) {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "variants") {
                    val arr = json.optJSONArray("variants")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i)
                            val url = obj?.optString("url", "") ?: ""
                            if (url.isNotBlank() && url.startsWith("http") && url.contains(".m3u8")) {
                                Log.d(TAG, "Recursively discovered HLS master URL from 100ms WebSocket: $url")
                                _hlsStreamUrl.value = url
                                return
                            }
                        }
                    }
                } else {
                    try {
                        val value = json.get(key)
                        recursivelyFindHlsUrl(value)
                    } catch (_: Exception) {}
                }
            }
        } else if (json is JSONArray) {
            for (i in 0 until json.length()) {
                try {
                    recursivelyFindHlsUrl(json.get(i))
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Connect to 100ms Live Class WebSocket stream.
     */
    fun connect(token: String, roomId: String?, userName: String? = null) {
        if (token.isBlank()) {
            Log.w(TAG, "Cannot connect: HMS Token is blank")
            return
        }

        // If already connected with same token/room, avoid re-opening
        if (_isConnected.value && currentRoomId == roomId && webSocket != null) {
            return
        }

        disconnect()
        _hlsStreamUrl.value = null

        currentPeerUuid = UUID.randomUUID().toString()
        currentRoomId = roomId
        currentUserName = if (!userName.isNullOrBlank()) userName.trim() else "Student"

        val wsUrl = "$BASE_WS_URL?peer=$currentPeerUuid&token=$token&user_agent_v2=os:android,sdk:android,env:prod&protocol_version=2.5&protocol_spec=20240720"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully to 100ms live room")
                _isConnected.value = true
                startPingLoop()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                _isConnected.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                _isConnected.value = false
                stopPingLoop()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                _isConnected.value = false
                stopPingLoop()
            }
        })
    }

    /**
     * Parse incoming JSON-RPC 2.0 messages from 100ms server.
     */
    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            
            // Extract HLS stream URL from any nested structure in room state
            recursivelyFindHlsUrl(json)

            val method = json.optString("method", "")
            val params = json.optJSONObject("params")

            when (method) {
                // 1. Session Info (Viewer Count)
                "session-info" -> {
                    if (params != null) {
                        var count = params.optInt("peer_count", 0)
                        if (count <= 0) {
                            val sessionObj = params.optJSONObject("session")
                            count = sessionObj?.optInt("peer_count", 0) ?: 0
                        }
                        if (count > 0) {
                            _viewerCount.value = count
                        }
                    }
                }

                // 2. Metadata Change (Pinned Notice)
                "on-metadata-change" -> {
                    parseMetadataChange(params)
                }

                // 3. Chat Messages (Broadcast / on-message)
                "broadcast", "on-message" -> {
                    parseChatMessage(params)
                }

                // 4. Peer Update (Hand raise sync)
                "on-peer-update" -> {
                    parsePeerUpdate(params)
                }

                // 5. Poll / Quiz Start
                "poll-start", "on-poll-start" -> {
                    parsePollStart(params)
                }

                // Generic pong or response
                "pong" -> {
                    // Keepalive response
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message: ${e.message}", e)
        }
    }

    /**
     * Parses "on-metadata-change" for pinned message updates.
     */
    private fun parseMetadataChange(params: JSONObject?) {
        if (params == null) return
        try {
            // Check in values dictionary or array
            val valuesObj = params.optJSONObject("values")
            var pinnedRaw: Any? = valuesObj?.opt("pinnedMessages")

            if (pinnedRaw == null) {
                val valuesArr = params.optJSONArray("values")
                if (valuesArr != null) {
                    for (i in 0 until valuesArr.length()) {
                        val item = valuesArr.optJSONObject(i)
                        if (item?.optString("key") == "pinnedMessages") {
                            pinnedRaw = item.opt("data") ?: item.opt("value")
                            break
                        }
                    }
                }
            }

            if (pinnedRaw != null) {
                var text = ""
                var pinnedBy = "শিক্ষক"

                when (pinnedRaw) {
                    is JSONObject -> {
                        text = pinnedRaw.optString("text", "")
                        pinnedBy = pinnedRaw.optString("pinnedBy", "শিক্ষক")
                    }
                    is String -> {
                        if (pinnedRaw.isNotBlank()) {
                            try {
                                val parsed = JSONObject(pinnedRaw)
                                text = parsed.optString("text", "")
                                pinnedBy = parsed.optString("pinnedBy", "শিক্ষক")
                            } catch (_: Exception) {
                                text = pinnedRaw
                            }
                        }
                    }
                }

                if (text.isNotBlank()) {
                    _pinnedMessage.value = PinnedMessageData(
                        text = text,
                        pinnedBy = pinnedBy,
                        pinnedAt = System.currentTimeMillis()
                    )
                } else {
                    _pinnedMessage.value = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing metadata change: ${e.message}", e)
        }
    }

    /**
     * Parses "broadcast" or "on-message" frames for chat messages.
     */
    private fun parseChatMessage(params: JSONObject?) {
        if (params == null) return
        try {
            val info = params.optJSONObject("info")
            val messageText = info?.optString("message")
                ?: params.optString("message", "")

            if (messageText.isBlank()) return

            val peer = params.optJSONObject("peer")
            val senderName = peer?.optString("name")
                ?: params.optString("sender_name")
                ?: info?.optString("sender")
                ?: "সহপাঠী / শিক্ষক"

            val time = params.optLong("time", System.currentTimeMillis())

            val isMe = peer?.optString("id") == currentPeerUuid ||
                    peer?.optString("name") == currentUserName

            val chatItem = ChatMessageItem(
                senderName = senderName,
                message = messageText,
                timestamp = if (time > 0) time else System.currentTimeMillis(),
                isFromMe = isMe
            )

            _chatMessages.update { list ->
                list + chatItem
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing chat message: ${e.message}", e)
        }
    }

    /**
     * Parses "on-peer-update" to detect hand-raise groups.
     */
    private fun parsePeerUpdate(params: JSONObject?) {
        if (params == null) return
        try {
            val peer = params.optJSONObject("peer") ?: params
            val peerId = peer.optString("id")
            val peerName = peer.optString("name")

            val isMyPeer = (peerId.isNotBlank() && peerId == currentPeerUuid) ||
                    (peerName.isNotBlank() && peerName == currentUserName)

            if (isMyPeer) {
                val groups = peer.optJSONArray("groups")
                var hasHandraise = false
                if (groups != null) {
                    for (i in 0 until groups.length()) {
                        if (groups.optString(i) == "_handraise") {
                            hasHandraise = true
                            break
                        }
                    }
                }
                _isHandRaised.value = hasHandraise
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing peer update: ${e.message}", e)
        }
    }

    /**
     * Parses live polls/quizzes.
     */
    private fun parsePollStart(params: JSONObject?) {
        if (params == null) return
        try {
            val id = params.optString("id", UUID.randomUUID().toString())
            val title = params.optString("title", "লাইভ পোল / কুইজ")
            val question = params.optString("question", "")
            val optionsArr = params.optJSONArray("options") ?: JSONArray()
            val duration = params.optInt("duration", 30)

            val optionsList = mutableListOf<PollOption>()
            for (i in 0 until optionsArr.length()) {
                val opt = optionsArr.optJSONObject(i)
                if (opt != null) {
                    optionsList.add(
                        PollOption(
                            id = opt.optString("id", "$i"),
                            text = opt.optString("text", "অপশন ${i + 1}"),
                            voteCount = opt.optInt("votes", 0)
                        )
                    )
                } else {
                    val text = optionsArr.optString(i)
                    optionsList.add(PollOption(id = "$i", text = text))
                }
            }

            if (question.isNotBlank() || optionsList.isNotEmpty()) {
                _activePoll.value = PollData(
                    id = id,
                    title = title,
                    question = question,
                    options = optionsList,
                    durationSeconds = duration
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing poll start: ${e.message}", e)
        }
    }

    /**
     * Send a real-time chat message to the teacher or the entire room.
     */
    fun sendMessage(messageText: String, recipientRole: String = "teacher") {
        if (messageText.isBlank()) return

        val msgTrimmed = messageText.trim()
        val frameId = UUID.randomUUID().toString()

        try {
            val rolesArray = JSONArray()
            rolesArray.put(recipientRole)

            val infoObj = JSONObject()
            infoObj.put("message", msgTrimmed)
            infoObj.put("type", "chat")

            val paramsObj = JSONObject()
            paramsObj.put("info", infoObj)
            paramsObj.put("roles", rolesArray)

            val frame = JSONObject()
            frame.put("id", frameId)
            frame.put("method", "broadcast")
            frame.put("params", paramsObj)
            frame.put("jsonrpc", "2.0")

            webSocket?.send(frame.toString())

            // Immediately add to local chat stream for optimistic responsiveness
            val optimisticItem = ChatMessageItem(
                id = frameId,
                senderName = currentUserName,
                message = msgTrimmed,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                recipientRole = recipientRole
            )
            _chatMessages.update { it + optimisticItem }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send chat message: ${e.message}", e)
        }
    }

    /**
     * Toggles hand raise status with 100ms group join/leave protocol.
     */
    fun toggleHandRaise() {
        val currentlyRaised = _isHandRaised.value
        val nextState = !currentlyRaised

        try {
            if (nextState) {
                // 1. Send group-join frame
                val joinFrame = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("method", "group-join")
                    put("params", JSONObject().apply { put("name", "_handraise") })
                    put("jsonrpc", "2.0")
                }
                webSocket?.send(joinFrame.toString())

                // 2. Send peer-update frame
                val peerUpdateFrame = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("method", "peer-update")
                    val dataJson = JSONObject().apply {
                        put("handRaisedAt", System.currentTimeMillis())
                        put("name", currentUserName)
                    }
                    put("params", JSONObject().apply { put("data", dataJson.toString()) })
                    put("jsonrpc", "2.0")
                }
                webSocket?.send(peerUpdateFrame.toString())
                _isHandRaised.value = true
            } else {
                // 1. Send group-leave frame
                val leaveFrame = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("method", "group-leave")
                    put("params", JSONObject().apply { put("name", "_handraise") })
                    put("jsonrpc", "2.0")
                }
                webSocket?.send(leaveFrame.toString())

                // 2. Send peer-update frame
                val peerUpdateFrame = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("method", "peer-update")
                    val dataJson = JSONObject().apply {
                        put("name", currentUserName)
                    }
                    put("params", JSONObject().apply { put("data", dataJson.toString()) })
                    put("jsonrpc", "2.0")
                }
                webSocket?.send(peerUpdateFrame.toString())
                _isHandRaised.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle hand raise: ${e.message}", e)
        }
    }

    /**
     * Submit an answer to the active live poll/quiz.
     */
    fun submitPollAnswer(pollId: String, optionId: String) {
        val currentPoll = _activePoll.value ?: return
        if (currentPoll.id == pollId) {
            _activePoll.value = currentPoll.copy(selectedOptionId = optionId)

            try {
                val frame = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("method", "poll-response")
                    put("params", JSONObject().apply {
                        put("poll_id", pollId)
                        put("option_id", optionId)
                        put("peer_id", currentPeerUuid)
                    })
                    put("jsonrpc", "2.0")
                }
                webSocket?.send(frame.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit poll answer: ${e.message}", e)
            }
        }
    }

    /**
     * Dismiss active poll.
     */
    fun dismissPoll() {
        _activePoll.value = null
    }

    /**
     * Start periodic ping to maintain WebSocket connection alive.
     */
    private fun startPingLoop() {
        stopPingLoop()
        pingJob = managerScope.launch {
            while (isActive) {
                delay(20000L)
                try {
                    val pingFrame = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("method", "ping")
                        put("jsonrpc", "2.0")
                    }
                    webSocket?.send(pingFrame.toString())
                } catch (_: Exception) {}
            }
        }
    }

    private fun stopPingLoop() {
        pingJob?.cancel()
        pingJob = null
    }

    /**
     * Disconnect and cleanup WebSocket and timers.
     */
    fun disconnect() {
        stopPingLoop()
        try {
            webSocket?.close(1000, "Normal Closure")
        } catch (_: Exception) {}
        webSocket = null
        _isConnected.value = false
        _isHandRaised.value = false
        _pinnedMessage.value = null
        _activePoll.value = null
        _viewerCount.value = 1
        _hlsStreamUrl.value = null
    }
}
