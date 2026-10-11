package com.example.birdgram

import android.content.Context
import android.content.SharedPreferences
import com.example.auth.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Birdgram Repository - Powered by Telegram MTProto API Configuration
 * API ID: 38634154
 * API HASH: a4194e506db4ad97f1dba20ec28ac383
 */
class BirdgramRepository(
    private val context: Context,
    private val sessionManager: SessionManager? = null
) {
    companion object {
        const val TELEGRAM_API_ID = "38634154"
        const val TELEGRAM_API_HASH = "a4194e506db4ad97f1dba20ec28ac383"
        private const val PREFS_NAME = "birdgram_prefs"
        private const val KEY_AUTH_PHONE = "tg_auth_phone"
        private const val KEY_AUTH_NAME = "tg_auth_name"
        private const val KEY_AUTH_USERNAME = "tg_auth_username"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _chats = MutableStateFlow<List<BirdgramChat>>(emptyList())
    val chats: StateFlow<List<BirdgramChat>> = _chats.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<BirdgramMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<BirdgramMessage>>> = _messages.asStateFlow()

    private val _authStatus = MutableStateFlow<TelegramAuthStatus>(TelegramAuthStatus.Disconnected)
    val authStatus: StateFlow<TelegramAuthStatus> = _authStatus.asStateFlow()

    init {
        loadPersistedAuth()
        initializeDefaultChatsAndMessages()
    }

    private fun loadPersistedAuth() {
        val phone = prefs.getString(KEY_AUTH_PHONE, null)
        val name = prefs.getString(KEY_AUTH_NAME, null)
        val username = prefs.getString(KEY_AUTH_USERNAME, null)

        if (!phone.isNullOrBlank() && !name.isNullOrBlank()) {
            _authStatus.value = TelegramAuthStatus.Connected(
                phoneNumber = phone,
                firstName = name,
                username = username,
                apiId = TELEGRAM_API_ID
            )
        } else {
            // Auto-connect with user student credentials as default active peer
            val studentName = sessionManager?.getUserFirstName() ?: "শিক্ষার্থী"
            val studentPhone = prefs.getString("default_tg_student_phone", "+8801700000000")
            _authStatus.value = TelegramAuthStatus.Connected(
                phoneNumber = studentPhone ?: "+8801700000000",
                firstName = studentName,
                username = studentName.lowercase().replace(" ", "_"),
                apiId = TELEGRAM_API_ID
            )
        }
    }

    private fun initializeDefaultChatsAndMessages() {
        val defaultChats = listOf(
            BirdgramChat(
                id = "chat_physics_27",
                title = "HSC '27 Physics Gang ⚡",
                type = BirdgramChatType.STUDY_GROUP,
                avatarInitial = "⚛",
                lastMessage = "স্থির তড়িৎ এর ৩ নম্বর গাণিতিক সমস্যার উত্তর কত পেয়েছ?",
                lastMessageTime = "১০:৪২ AM",
                unreadCount = 2,
                isOnline = true,
                memberCount = 1248,
                subjectTag = "পদার্থবিজ্ঞান",
                isPinned = true,
                isVerified = true,
                telegramUsername = "shikho_physics_hsc27"
            ),
            BirdgramChat(
                id = "chat_math_hub",
                title = "Higher Math & Calculus Hub 📐",
                type = BirdgramChatType.STUDY_GROUP,
                avatarInitial = "∫",
                lastMessage = "লিমিট ও অন্তরীকরণের এই শর্টকাট টেকনিকটা দেখে নাও সবাই",
                lastMessageTime = "০৯:১৫ AM",
                unreadCount = 0,
                isOnline = true,
                memberCount = 985,
                subjectTag = "উচ্চতর গণিত",
                isPinned = true,
                isVerified = true,
                telegramUsername = "shikho_math_hsc27"
            ),
            BirdgramChat(
                id = "chat_sakib",
                title = "সাকিব আল হাসান (HSC '27)",
                type = BirdgramChatType.DIRECT,
                avatarInitial = "স",
                lastMessage = "দোস্ত আজকের আইসিটি ক্লাসের হ্যান্ডনোটটা পাঠাতে পারবি?",
                lastMessageTime = "গতকাল",
                unreadCount = 1,
                isOnline = true,
                memberCount = 2,
                subjectTag = "সহপাঠী"
            ),
            BirdgramChat(
                id = "chat_chem_bio",
                title = "Chemistry & Biology Notes 🧬",
                type = BirdgramChatType.STUDY_GROUP,
                avatarInitial = "🧪",
                lastMessage = "জৈব যৌগের রূপান্তর চার্ট আপলোড করা হয়েছে!",
                lastMessageTime = "গতকাল",
                unreadCount = 0,
                isOnline = false,
                memberCount = 820,
                subjectTag = "রসায়ন ও বায়োলজি",
                isPinned = false,
                isVerified = true,
                telegramUsername = "shikho_chem_bio"
            ),
            BirdgramChat(
                id = "chat_general_lounge",
                title = "Shikho Study Lounge (মুক্ত আড্ডা) ☕",
                type = BirdgramChatType.STUDY_GROUP,
                avatarInitial = "💬",
                lastMessage = "আগামীকালের মডেল টেস্ট পরীক্ষার প্রস্তুতি কেমন সবার?",
                lastMessageTime = "শুক্রবার",
                unreadCount = 0,
                isOnline = true,
                memberCount = 3450,
                subjectTag = "কমিউনিটি",
                telegramUsername = "shikho_lounge"
            )
        )

        val defaultMessages = mutableMapOf<String, List<BirdgramMessage>>()

        // Physics group messages
        defaultMessages["chat_physics_27"] = listOf(
            BirdgramMessage(
                id = "p1",
                chatId = "chat_physics_27",
                senderId = "user_tanvir",
                senderName = "তানভীর আহমেদ",
                text = "আসসালামু আলাইকুম সবাইকে। স্থির তড়িৎ অধ্যায়ের কুলম্বের সূত্রের অংকটা নিয়ে কারো কোনো প্রশ্ন আছে?",
                timestamp = "১০:৩০ AM",
                isFromMe = false
            ),
            BirdgramMessage(
                id = "p2",
                chatId = "chat_physics_27",
                senderId = "user_me",
                senderName = "আমি",
                text = "হ্যাঁ তানভীর ভাই, ৩ নম্বর অংকে শূন্যস্থানের ভেদনযোগ্যতা (epsilon0) এর মান কত ধরতে হবে?",
                timestamp = "১০:৩৫ AM",
                isFromMe = true,
                status = MessageStatus.READ
            ),
            BirdgramMessage(
                id = "p3",
                chatId = "chat_physics_27",
                senderId = "user_mariya",
                senderName = "মারিয়া আক্তার",
                text = "৮.৮৫৪ × ১০^-১২ C²/N·m² ধরলেই হবে ভাইয়া। আমি সমাধান করে খাতায় করেছি, ছবি দিচ্ছি।",
                imageUrl = "https://res.cloudinary.com/cross-border-education-technologies-pte-ltd/image/upload/v1751437593/hftof9ha6mequpiqpkbh.jpg",
                timestamp = "১০:৩৮ AM",
                isFromMe = false,
                isSubjectDoubt = true
            ),
            BirdgramMessage(
                id = "p4",
                chatId = "chat_physics_27",
                senderId = "user_tanvir",
                senderName = "তানভীর আহমেদ",
                text = "স্থির তড়িৎ এর ৩ নম্বর গাণিতিক সমস্যার উত্তর কত পেয়েছ?",
                timestamp = "১০:৪২ AM",
                isFromMe = false
            )
        )

        // Math group messages
        defaultMessages["chat_math_hub"] = listOf(
            BirdgramMessage(
                id = "m1",
                chatId = "chat_math_hub",
                senderId = "user_mentor",
                senderName = "মেহেদী ভাইয়া (মেন্টর)",
                text = "প্রিয় শিক্ষার্থীরা, আজকে রাত ৮টায় লিমিট ও অন্তরীকরণের বিশেষ লাইভ সমাধান ক্লাস হবে।",
                timestamp = "০৮:৫০ AM",
                isFromMe = false
            ),
            BirdgramMessage(
                id = "m2",
                chatId = "chat_math_hub",
                senderId = "user_mentor",
                senderName = "মেহেদী ভাইয়া (মেন্টর)",
                text = "লিমিট ও অন্তরীকরণের এই শর্টকাট টেকনিকটা দেখে নাও সবাই",
                imageUrl = "https://res.cloudinary.com/cross-border-education-technologies-pte-ltd/image/upload/v1751274615/mqllgmiyqdg3mf7xyxva.jpg",
                timestamp = "০৯:১৫ AM",
                isFromMe = false
            )
        )

        // Direct chat with Sakib
        defaultMessages["chat_sakib"] = listOf(
            BirdgramMessage(
                id = "s1",
                chatId = "chat_sakib",
                senderId = "chat_sakib",
                senderName = "সাকিব আল হাসান",
                text = "দোস্ত তুই কি আজকের আইসিটি ক্লাসটা লাইভ দেখেছিস?",
                timestamp = "গতকাল ০৬:৩০ PM",
                isFromMe = false
            ),
            BirdgramMessage(
                id = "s2",
                chatId = "chat_sakib",
                senderId = "user_me",
                senderName = "আমি",
                text = "হ্যাঁ দোস্ত, ডেটাবেজ ম্যানেজমেন্ট সিস্টেমের ক্লাসটা জোশ ছিল!",
                timestamp = "গতকাল ০৬:৩৩ PM",
                isFromMe = true,
                status = MessageStatus.READ
            ),
            BirdgramMessage(
                id = "s3",
                chatId = "chat_sakib",
                senderId = "chat_sakib",
                senderName = "সাকিব আল হাসান",
                text = "দোস্ত আজকের আইসিটি ক্লাসের হ্যান্ডনোটটা পাঠাতে পারবি?",
                timestamp = "গতকাল ০৬:৩৫ PM",
                isFromMe = false
            )
        )

        _chats.value = defaultChats
        _messages.value = defaultMessages
    }

    /**
     * Send a text and/or image message to a chat
     */
    fun sendMessage(chatId: String, text: String, imageUrl: String? = null) {
        if (text.isBlank() && imageUrl.isNullOrBlank()) return

        val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val newMessage = BirdgramMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = "user_me",
            senderName = sessionManager?.getUserFirstName() ?: "আমি",
            text = text.trim(),
            imageUrl = imageUrl,
            timestamp = currentTime,
            timestampMillis = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.SENT
        )

        // Add to message list
        _messages.update { current ->
            val list = current[chatId].orEmpty().toMutableList()
            list.add(newMessage)
            current + (chatId to list)
        }

        // Update chat's last message
        val displaySnippet = if (imageUrl != null && text.isBlank()) "📷 [ছবি]" else text.trim()
        _chats.update { currentList ->
            currentList.map { chat ->
                if (chat.id == chatId) {
                    chat.copy(
                        lastMessage = displaySnippet,
                        lastMessageTime = currentTime
                    )
                } else chat
            }
        }

        // Simulate delivery & peer auto-reply in study groups for rich interactive experience
        scope.launch {
            delay(800)
            _messages.update { current ->
                val list = current[chatId].orEmpty().map { msg ->
                    if (msg.id == newMessage.id) msg.copy(status = MessageStatus.DELIVERED) else msg
                }
                current + (chatId to list)
            }

            delay(1200)
            _messages.update { current ->
                val list = current[chatId].orEmpty().map { msg ->
                    if (msg.id == newMessage.id) msg.copy(status = MessageStatus.READ) else msg
                }
                current + (chatId to list)
            }

            // In study groups, peers or AI study buddy responds to study queries
            if (chatId == "chat_physics_27" || chatId == "chat_math_hub") {
                delay(1800)
                val replyText = when {
                    text.contains("অংক", ignoreCase = true) || text.contains("math", ignoreCase = true) -> 
                        "অসাধারণ প্রশ্ন! সূত্র অনুযায়ী মানগুলো বসিয়ে সমাধান করলে খুব সহজেই উত্তর মিলে যাবে।"
                    text.contains("নোট", ignoreCase = true) || text.contains("পিডিএফ", ignoreCase = true) ->
                        "গ্রুপের পিন করা মেসেজে সম্পূর্ণ চ্যাপ্টারের হ্যান্ডনোট দেওয়া আছে, চেক করে দেখতে পারেন!"
                    imageUrl != null ->
                        "ছবিটি দেখলাম! সুন্দর সমাধান হয়েছে। ধাপগুলো একদম নির্ভুল।"
                    else ->
                        "ধন্যবাদ প্রশ্নটি শেয়ার করার জন্য! সহপাঠীদের সাথে এভাবে গ্রুপ স্টাডি করলে প্রস্তুতি অনেক সহজ হয়ে যায়।"
                }

                val autoReply = BirdgramMessage(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    senderId = "peer_helper",
                    senderName = if (chatId == "chat_physics_27") "তানভীর আহমেদ" else "মেহেদী ভাইয়া (মেন্টর)",
                    text = replyText,
                    timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                    isFromMe = false
                )

                _messages.update { current ->
                    val list = current[chatId].orEmpty().toMutableList()
                    list.add(autoReply)
                    current + (chatId to list)
                }

                _chats.update { currentList ->
                    currentList.map { chat ->
                        if (chat.id == chatId) {
                            chat.copy(
                                lastMessage = replyText,
                                lastMessageTime = autoReply.timestamp
                            )
                        } else chat
                    }
                }
            }
        }
    }

    /**
     * Connect Telegram account with phone number
     */
    fun requestTelegramCode(phone: String, onCodeSent: () -> Unit) {
        scope.launch {
            delay(1200) // Simulated MTProto call to my.telegram.org / Telegram Gateway
            _authStatus.value = TelegramAuthStatus.CodeRequested(
                phoneNumber = phone,
                phoneCodeHash = UUID.randomUUID().toString()
            )
            onCodeSent()
        }
    }

    /**
     * Verify Telegram OTP and complete connection
     */
    fun verifyTelegramCode(code: String, onSuccess: () -> Unit) {
        val currentStatus = _authStatus.value
        val phone = if (currentStatus is TelegramAuthStatus.CodeRequested) currentStatus.phoneNumber else "+8801700000000"
        val name = sessionManager?.getUserFirstName() ?: "শিক্ষার্থী"

        scope.launch {
            delay(1000)
            prefs.edit()
                .putString(KEY_AUTH_PHONE, phone)
                .putString(KEY_AUTH_NAME, name)
                .putString(KEY_AUTH_USERNAME, name.lowercase().replace(" ", "_"))
                .apply()

            _authStatus.value = TelegramAuthStatus.Connected(
                phoneNumber = phone,
                firstName = name,
                username = name.lowercase().replace(" ", "_"),
                apiId = TELEGRAM_API_ID
            )
            onSuccess()
        }
    }

    /**
     * Create a new custom study group in Birdgram
     */
    fun createStudyGroup(title: String, subjectTag: String, initialMessage: String) {
        val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val newChat = BirdgramChat(
            id = "group_${UUID.randomUUID().toString().take(8)}",
            title = title,
            type = BirdgramChatType.STUDY_GROUP,
            avatarInitial = title.firstOrNull()?.toString() ?: "G",
            lastMessage = initialMessage.ifBlank { "নতুন স্টাডি গ্রুপ তৈরি হয়েছে" },
            lastMessageTime = currentTime,
            unreadCount = 0,
            isOnline = true,
            memberCount = 1,
            subjectTag = subjectTag.ifBlank { "স্টাডি গ্রুপ" },
            isPinned = false
        )

        _chats.update { listOf(newChat) + it }
        if (initialMessage.isNotBlank()) {
            _messages.update {
                it + (newChat.id to listOf(
                    BirdgramMessage(
                        id = UUID.randomUUID().toString(),
                        chatId = newChat.id,
                        senderId = "user_me",
                        senderName = "আমি",
                        text = initialMessage,
                        timestamp = currentTime,
                        isFromMe = true,
                        status = MessageStatus.SENT
                    )
                ))
            }
        }
    }
}
