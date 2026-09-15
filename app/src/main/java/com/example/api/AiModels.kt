package com.example.api

import android.net.Uri
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// -------------------------------------------------------------
// 1. AI Subscription & Usage Limits
// -------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class AiSubscriptionRequest(
    val is_history: Boolean = true
)

@JsonClass(generateAdapter = true)
data class AiSubscriptionResponse(
    val data: List<AiSubscriptionItem>? = null,
    val total_count: Int? = null,
    val code: Int? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class AiSubscriptionItem(
    val id: String? = null,
    val package_id: String? = null,
    val subscription_metadata: SubscriptionMetadata? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class SubscriptionMetadata(
    val package_title: String? = null,
    val package_price: Int? = null,
    val has_image_support: Boolean? = true,
    val usage_limit: UsageLimit? = null
)

@JsonClass(generateAdapter = true)
data class UsageLimit(
    val conversation_depth: Int? = null,
    val question_limit: Int? = null,
    val daily_limit: Int? = null,
    val used_count: Int? = null
)

// -------------------------------------------------------------
// 2. Chat Data Models
// -------------------------------------------------------------

data class AiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val imageUri: Uri? = null,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val subject: String? = null,
    val isStepByStep: Boolean = false,
    val steps: List<String> = emptyList(),
    val formulas: List<String> = emptyList(),
    val finalAnswer: String? = null,
    val tips: String? = null,
    val feedback: String? = null, // "like", "dislike", null
    val isGenerating: Boolean = false
)

data class AiChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val subject: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<AiChatMessage> = emptyList()
)

data class AiSubjectOption(
    val code: String,
    val titleBn: String,
    val titleEn: String,
    val iconName: String,
    val samplePrompts: List<String>
)

// -------------------------------------------------------------
// 3. Shikho Gen-AI API Data Models
// -------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class ShikhoSubjectListResponse(
    val data: List<ShikhoSubjectItem>? = null,
    val code: Int? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ShikhoSubjectItem(
    val subject_code: String? = null,
    val name: String? = null,
    val prompt_id: String? = null
)

@JsonClass(generateAdapter = true)
data class SignedUrlRequest(
    val file_extension: String = "jpg"
)

@JsonClass(generateAdapter = true)
data class SignedUrlResponse(
    val upload_url: String? = null,
    val image_identifier: String? = null,
    val data: SignedUrlData? = null
)

@JsonClass(generateAdapter = true)
data class SignedUrlData(
    val upload_url: String? = null,
    val image_identifier: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateSessionRequest(
    val subject_code: String,
    val title: String
)

@JsonClass(generateAdapter = true)
data class CreateSessionResponse(
    val session_id: String? = null,
    val data: CreateSessionData? = null
)

@JsonClass(generateAdapter = true)
data class CreateSessionData(
    val session_id: String? = null
)

@JsonClass(generateAdapter = true)
data class ConversationContinueRequest(
    val prompt: String,
    val session_id: String,
    val image_identifier: String? = null
)

@JsonClass(generateAdapter = true)
data class ConversationContinueResponse(
    val data: ConversationData? = null,
    val text: String? = null,
    val reply: String? = null,
    val response: String? = null,
    val answer: String? = null
)

@JsonClass(generateAdapter = true)
data class ConversationData(
    val text: String? = null,
    val reply: String? = null,
    val response: String? = null,
    val answer: String? = null,
    val prompt: String? = null,
    val session_id: String? = null
)

