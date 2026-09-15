package com.example.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.AiChatMessage
import com.example.api.AiChatSession
import com.example.api.AiSubjectOption
import com.example.api.AiSubscriptionItem
import com.example.api.ConversationContinueRequest
import com.example.api.CreateSessionRequest
import com.example.auth.SessionManager
import com.example.api.ShikhoApiService
import com.example.api.SignedUrlRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

data class AiUiState(
    val userName: String = "শিক্ষার্থী",
    val userPhone: String = "",
    val userAvatar: String? = null,
    val userClassName: String = "HSC",
    val userGroup: String = "",
    val isSubscribed: Boolean = true,
    val isUnlimited: Boolean = true,
    val subscription: AiSubscriptionItem? = null,
    val usedQuestionsCount: Int = 0,
    val totalDailyLimit: Int = 9999,
    
    val selectedSubjectCode: String = "",
    val subjectsList: List<AiSubjectOption> = emptyList(),
    val isLoadingSubjects: Boolean = false,
    
    val inputText: String = "",
    val attachedImageUri: Uri? = null,
    val isGenerating: Boolean = false,
    
    val currentSessionId: String? = null,
    val currentMessages: List<AiChatMessage> = emptyList(),
    val chatSessions: List<AiChatSession> = emptyList(),
    
    val toastMessage: String? = null,
    val isWebMode: Boolean = false
)

class AiViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    init {
        loadUserData()
        fetchSubjectList()
        fetchSubscriptionStatus()
    }

    fun loadUserData() {
        val name = sessionManager.getUserFullName() ?: sessionManager.getUserFirstName() ?: "শিক্ষার্থী"
        val phone = sessionManager.getUserPhone() ?: ""
        val avatar = sessionManager.getUserAvatar()
        val classDisplay = sessionManager.getUserClassDisplay() ?: sessionManager.getUserClassName() ?: "HSC"
        val group = sessionManager.getUserGroup() ?: ""

        _uiState.update {
            it.copy(
                userName = name,
                userPhone = phone,
                userAvatar = avatar,
                userClassName = classDisplay,
                userGroup = group
            )
        }
    }

    fun fetchSubjectList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSubjects = true) }
            try {
                val response = apiService.getAiSubjectListRaw()
                val rawString = if (response.isSuccessful) response.body()?.string() ?: "" else response.errorBody()?.string() ?: ""
                Log.d("AiViewModel", "Subject List API raw response: $rawString")
                val subjects = parseSubjectsFromRawJson(rawString)
                if (subjects.isNotEmpty()) {
                    val currentSelected = _uiState.value.selectedSubjectCode
                    val selected = if (subjects.any { it.code.equals(currentSelected, ignoreCase = true) }) {
                        currentSelected
                    } else {
                        subjects.first().code
                    }
                    _uiState.update {
                        it.copy(
                            subjectsList = subjects,
                            selectedSubjectCode = selected,
                            isLoadingSubjects = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            subjectsList = emptyList(),
                            isLoadingSubjects = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AiViewModel", "Error fetching subject list", e)
                _uiState.update {
                    it.copy(
                        subjectsList = emptyList(),
                        isLoadingSubjects = false
                    )
                }
            }
        }
    }

    private fun parseSubjectsFromRawJson(rawJson: String): List<AiSubjectOption> {
        val result = mutableListOf<AiSubjectOption>()
        try {
            val root = org.json.JSONObject(rawJson)
            val dataArray = root.optJSONArray("data")
            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val code = item.optString("subject_code", item.optString("code", ""))
                    val name = item.optString("name", item.optString("title", code))
                    if (code.isNotBlank()) {
                        result.add(
                            AiSubjectOption(
                                code = code,
                                titleBn = name,
                                titleEn = name,
                                iconName = getIconForCode(code),
                                samplePrompts = emptyList()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AiViewModel", "Error parsing subject list JSON", e)
        }
        return result
    }

    private fun getIconForCode(code: String): String {
        val codeUpper = code.uppercase()
        return when {
            codeUpper.contains("MATH") -> "Calculate"
            codeUpper.contains("ICT") -> "Computer"
            codeUpper.contains("BANGLA") -> "MenuBook"
            codeUpper.contains("ECONOMICS") -> "TrendingUp"
            codeUpper.contains("PHYSICS") -> "ElectricBolt"
            codeUpper.contains("CHEMISTRY") -> "Science"
            codeUpper.contains("BIOLOGY") -> "Biotech"
            codeUpper.contains("ENGLISH") -> "Translate"
            else -> "AutoAwesome"
        }
    }

    fun fetchSubscriptionStatus() {
        viewModelScope.launch {
            try {
                val response = apiService.getAiSubscriptionList()
                val activeItem = response.data?.firstOrNull()
                _uiState.update {
                    it.copy(
                        subscription = activeItem,
                        isSubscribed = activeItem != null,
                        isUnlimited = true
                    )
                }
            } catch (e: Exception) {
                Log.e("AiViewModel", "Error fetching subscription status", e)
            }
        }
    }

    fun onSubjectSelected(subjectCode: String) {
        _uiState.update { it.copy(selectedSubjectCode = subjectCode) }
    }

    fun onInputTextChanged(text: String) {
        if (text.length <= 500) {
            _uiState.update { it.copy(inputText = text) }
        }
    }

    fun onImageSelected(uri: Uri?) {
        _uiState.update { it.copy(attachedImageUri = uri) }
    }

    fun clearAttachedImage() {
        _uiState.update { it.copy(attachedImageUri = null) }
    }

    fun setSamplePrompt(prompt: String) {
        _uiState.update { it.copy(inputText = prompt) }
    }

    fun toggleWebMode() {
        _uiState.update { it.copy(isWebMode = !it.isWebMode) }
    }

    fun startNewChat() {
        val currentMsgs = _uiState.value.currentMessages
        val currentSessId = _uiState.value.currentSessionId
        
        if (currentMsgs.isNotEmpty() && !currentSessId.isNullOrBlank()) {
            val firstUserMsg = currentMsgs.firstOrNull { it.isUser }?.text ?: "নতুন চ্যাট"
            val title = firstUserMsg.take(30)
            val newSession = AiChatSession(
                id = currentSessId,
                title = title,
                subject = _uiState.value.selectedSubjectCode,
                timestamp = System.currentTimeMillis(),
                messages = currentMsgs
            )
            _uiState.update {
                it.copy(
                    chatSessions = listOf(newSession) + it.chatSessions.filter { s -> s.id != currentSessId },
                    currentSessionId = null,
                    currentMessages = emptyList(),
                    inputText = "",
                    attachedImageUri = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    currentSessionId = null,
                    currentMessages = emptyList(),
                    inputText = "",
                    attachedImageUri = null
                )
            }
        }
    }

    fun loadSession(session: AiChatSession) {
        _uiState.update {
            it.copy(
                currentSessionId = session.id,
                currentMessages = session.messages,
                selectedSubjectCode = session.subject.ifBlank { it.selectedSubjectCode }
            )
        }
    }

    fun deleteSession(sessionId: String) {
        _uiState.update {
            val updated = it.chatSessions.filter { s -> s.id != sessionId }
            val newCurrId = if (it.currentSessionId == sessionId) null else it.currentSessionId
            val newMsgs = if (it.currentSessionId == sessionId) emptyList() else it.currentMessages
            it.copy(
                chatSessions = updated,
                currentSessionId = newCurrId,
                currentMessages = newMsgs
            )
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private suspend fun createSessionWithApi(subject: String, title: String): String {
        try {
            val response = apiService.createAiSessionRaw(CreateSessionRequest(subject_code = subject, title = title))
            val rawString = if (response.isSuccessful) response.body()?.string() ?: "" else response.errorBody()?.string() ?: ""
            Log.d("AiViewModel", "Create Session API raw response ($subject): $rawString")
            if (rawString.isNotBlank()) {
                val root = org.json.JSONObject(rawString)
                val sessionId = root.optString("session_id", "")
                    .ifBlank { root.optJSONObject("data")?.optString("session_id", "") ?: "" }
                if (sessionId.isNotBlank()) {
                    return sessionId
                }
            }
        } catch (e: Exception) {
            Log.e("AiViewModel", "Error creating session via API", e)
        }
        return "session_${System.currentTimeMillis()}"
    }

    private fun parseAiReplyFromResponse(rawBody: String, httpCode: Int): String {
        if (rawBody.isBlank()) {
            return if (httpCode in 200..299) {
                "Shikho AI থেকে কোনো উত্তর পাওয়া যায়নি।"
            } else {
                "⚠️ Shikho AI সার্ভার থেকে এরর কোড $httpCode পাওয়া গিয়েছে।"
            }
        }

        try {
            val root = org.json.JSONObject(rawBody)

            fun extractFromObj(obj: org.json.JSONObject): String? {
                val keys = listOf("text", "reply", "response", "answer", "content", "message", "prompt_response")
                for (k in keys) {
                    val valStr = obj.optString(k, "")
                    if (valStr.isNotBlank()) return valStr
                }
                return null
            }

            val topExtract = extractFromObj(root)
            if (topExtract != null) return topExtract

            if (root.has("data")) {
                val dataVal = root.get("data")
                if (dataVal is String && dataVal.isNotBlank()) {
                    return dataVal
                } else if (dataVal is org.json.JSONObject) {
                    val innerExtract = extractFromObj(dataVal)
                    if (innerExtract != null) return innerExtract
                } else if (dataVal is org.json.JSONArray && dataVal.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until dataVal.length()) {
                        val item = dataVal.get(i)
                        if (item is String) {
                            sb.append(item).append("\n")
                        } else if (item is org.json.JSONObject) {
                            val itemText = extractFromObj(item)
                            if (itemText != null) sb.append(itemText).append("\n")
                        }
                    }
                    if (sb.isNotBlank()) return sb.toString().trim()
                }
            }
        } catch (_: Exception) {
            if (rawBody.contains("data:")) {
                val lines = rawBody.lines()
                val sb = StringBuilder()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("data:")) {
                        val content = trimmed.removePrefix("data:").trim()
                        if (content != "[DONE]" && content.isNotBlank()) {
                            try {
                                val json = org.json.JSONObject(content)
                                val chunk = json.optString("text", json.optString("content", json.optString("delta", "")))
                                if (chunk.isNotBlank()) sb.append(chunk)
                            } catch (_: Exception) {
                                sb.append(content)
                            }
                        }
                    }
                }
                if (sb.isNotBlank()) return sb.toString().trim()
            }
        }

        return if (httpCode in 200..299) {
            rawBody
        } else {
            "⚠️ Shikho AI API Error ($httpCode):\n$rawBody"
        }
    }

    fun submitFeedback(msgId: String, feedbackType: String) {
        _uiState.update {
            it.copy(toastMessage = "ফিডব্যাক গ্রহণ করা হয়েছে: $feedbackType")
        }
    }

    fun sendQuestion(customPrompt: String? = null, context: Context? = null) {
        val question = (customPrompt ?: _uiState.value.inputText).trim()
        val imageUri = if (customPrompt == null) _uiState.value.attachedImageUri else null
        val subject = _uiState.value.selectedSubjectCode.ifBlank { "MATH" }

        if (question.isBlank() && imageUri == null) return

        val userMessage = AiChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            text = question.ifBlank { "[ছবি সংযুক্ত করা হয়েছে]" },
            isUser = true,
            imageUri = imageUri,
            subject = subject,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                currentMessages = it.currentMessages + userMessage,
                inputText = "",
                attachedImageUri = null,
                isGenerating = true
            )
        }

        viewModelScope.launch {
            try {
                // Step 1: Create Session
                var activeSessionId = _uiState.value.currentSessionId
                if (activeSessionId.isNullOrBlank()) {
                    val sessionTitle = question.ifBlank { "Shikho AI Query" }.take(35)
                    activeSessionId = createSessionWithApi(subject, sessionTitle)
                    _uiState.update { it.copy(currentSessionId = activeSessionId) }
                }

                // Step 2: Image Upload
                var imageIdentifier: String? = null
                if (imageUri != null && context != null) {
                    try {
                        val signedRes = apiService.getAiSignedUrlRaw(SignedUrlRequest(file_extension = "jpg"))
                        val rawSigned = if (signedRes.isSuccessful) signedRes.body()?.string() ?: "" else ""
                        if (rawSigned.isNotBlank()) {
                            val obj = org.json.JSONObject(rawSigned)
                            val uploadUrl = obj.optString("upload_url", "")
                                .ifBlank { obj.optJSONObject("data")?.optString("upload_url", "") ?: "" }
                            imageIdentifier = obj.optString("image_identifier", "")
                                .ifBlank { obj.optJSONObject("data")?.optString("image_identifier", "") ?: "" }

                            if (uploadUrl.isNotBlank()) {
                                val inputStream = context.contentResolver.openInputStream(imageUri)
                                val bytes = inputStream?.readBytes()
                                inputStream?.close()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    val reqBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                                    apiService.uploadImageBinary(uploadUrl, reqBody)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AiViewModel", "Image upload failed", e)
                    }
                }

                // Step 3: Main Conversation API
                val response = apiService.continueAiConversationRaw(
                    ConversationContinueRequest(
                        prompt = question.ifBlank { "এই ছবির সঠিক সমাধান ও ব্যাখ্যা দাও" },
                        session_id = activeSessionId,
                        image_identifier = imageIdentifier
                    )
                )

                val responseCode = response.code()
                val rawBodyString = if (response.isSuccessful) {
                    response.body()?.string() ?: ""
                } else {
                    response.errorBody()?.string() ?: ""
                }

                Log.d("AiViewModel", "Shikho AI Conversation Response Code: $responseCode, Body: $rawBodyString")

                val replyText = parseAiReplyFromResponse(rawBodyString, responseCode)

                val aiMessage = AiChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    text = replyText,
                    isUser = false,
                    subject = subject,
                    timestamp = System.currentTimeMillis()
                )

                _uiState.update {
                    it.copy(
                        currentMessages = it.currentMessages + aiMessage,
                        isGenerating = false
                    )
                }

            } catch (e: Exception) {
                Log.e("AiViewModel", "Error in sendQuestion API call", e)
                val errorMessage = "⚠️ Shikho AI API কানেকশনে সমস্যা হয়েছে:\n${e.localizedMessage ?: "Network/Server Connection Error"}"
                val aiMessage = AiChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    text = errorMessage,
                    isUser = false,
                    subject = subject,
                    timestamp = System.currentTimeMillis()
                )
                _uiState.update {
                    it.copy(
                        currentMessages = it.currentMessages + aiMessage,
                        isGenerating = false
                    )
                }
            }
        }
    }
}

class AiViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AiViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
