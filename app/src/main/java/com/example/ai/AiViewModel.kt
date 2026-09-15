package com.example.ai

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiUiState(
    val currentMessages: List<AiChatMessage> = emptyList(),
    val chatSessions: List<AiChatSession> = emptyList(),
    val currentSessionId: String? = null,
    val selectedSubjectCode: String = "math",
    val inputText: String = "",
    val attachedImageUri: Uri? = null,
    val isGenerating: Boolean = false,
    val subscription: AiSubscriptionItem? = null,
    val isSubscribed: Boolean = true,
    val usedQuestionsCount: Int = 0,
    val totalDailyLimit: Int = 999999,
    val isUnlimited: Boolean = true,
    val userName: String = "শিক্ষার্থী",
    val userAvatar: String? = null,
    val userPhone: String = "",
    val userClassName: String = "Class 11",
    val userGroup: String = "Science",
    val toastMessage: String? = null,
    val isWebMode: Boolean = false
)

class AiViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    val availableSubjects: List<AiSubjectOption> = listOf(
        AiSubjectOption(
            code = "math",
            titleBn = "গণিত",
            titleEn = "Mathematics",
            iconName = "Calculate",
            samplePrompts = listOf(
                "ত্রিভুজের ক্ষেত্রফল কিভাবে বের করবো?",
                "How do I solve a quadratic equation ax² + bx + c = 0?",
                "Matrix addition ও multiplication এর নিয়ম কী?",
                "ত্রিকোণমিতিক সূত্রাবলী মনে রাখার সহজ টেকনিক"
            )
        ),
        AiSubjectOption(
            code = "physics",
            titleBn = "পদার্থবিজ্ঞান",
            titleEn = "Physics",
            iconName = "ElectricBolt",
            samplePrompts = listOf(
                "ওহমের সূত্রের গাণিতিক রূপ ও ব্যাখ্যা দাও",
                "নিউটনের গতির দ্বিতীয় সূত্র F = ma প্রতিপাদন করো",
                "গতিশক্তি ও বিভব শক্তির মধ্যে সম্পর্ক কী?",
                "আলোর প্রতিফলন ও প্রতিসরণের সূত্রাবলী"
            )
        ),
        AiSubjectOption(
            code = "chemistry",
            titleBn = "রসায়ন",
            titleEn = "Chemistry",
            iconName = "Science",
            samplePrompts = listOf(
                "HCl এবং NaOH এর প্রশমন বিক্রিয়া ব্যাখ্যা করো",
                "পর্যায় সারণির পর্যায়বৃত্ত ধর্ম কী কী?",
                "লুইস ডট গঠন ও সমযোজী বন্ধন আঁকার নিয়ম",
                "মোল সংখ্যা ও অ্যাভোগ্যাড্রোর সূত্রের গাণিতিক সমাধান"
            )
        ),
        AiSubjectOption(
            code = "biology",
            titleBn = "জীববিজ্ঞান",
            titleEn = "Biology",
            iconName = "Biotech",
            samplePrompts = listOf(
                "উদ্ভিদ ও প্রাণীকোষের মধ্যে প্রধান পার্থক্য কী?",
                "মাইটোসিস কোষ বিভাজনের পর্যায়সমূহ",
                "ডিএনএ (DNA) এর দ্বি-সূত্রক কাঠামোর বর্ণনা",
                "শালোকসংশ্লেষণ প্রক্রিয়ার আলোক পর্যায় ব্যাখ্যা করো"
            )
        ),
        AiSubjectOption(
            code = "ict",
            titleBn = "তথ্য ও যোগাযোগ প্রযুক্তি",
            titleEn = "ICT",
            iconName = "Computer",
            samplePrompts = listOf(
                "বাইনারি থেকে ডেসিমেল রূপান্তর করার নিয়ম",
                "এইচটিএমএল (HTML) দিয়ে টেবিল তৈরির কোড",
                "বুলিয়ান অ্যালজেবরার ডিমরগানের উপপাদ্য",
                "লজিক গেইট (AND, OR, NOT) এর ট্রুথ টেবিল"
            )
        ),
        AiSubjectOption(
            code = "english",
            titleBn = "ইংরেজি",
            titleEn = "English",
            iconName = "Translate",
            samplePrompts = listOf(
                "Right form of verbs এর গুরুত্বপূর্ণ নিয়মাবলী",
                "Transformation of Sentences: Simple, Complex, Compound",
                "Completing sentences এর নিয়ম ব্যাখ্যা করো",
                "Appropriate Preposition ব্যবহারের নিয়ম"
            )
        ),
        AiSubjectOption(
            code = "bangla",
            titleBn = "বাংলা",
            titleEn = "Bangla",
            iconName = "MenuBook",
            samplePrompts = listOf(
                "সমাস চেনার সহজ উপায় ও প্রকারভেদ",
                "ণ-ত্ব ও ষ-ত্ব বিধানের প্রধান নিয়মাবলী",
                "কারক ও বিভক্তি নির্ণয়ের শর্টকাট টেকনিক",
                "ধ্বনি পরিবর্তন এর উদাহরণসহ নিয়ম"
            )
        )
    )

    init {
        loadUserData()
        loadDefaultHistory()
        fetchSubscriptionStatus()
    }

    fun loadUserData() {
        val name = sessionManager.getUserFullName() ?: sessionManager.getUserFirstName() ?: "শিক্ষার্থী"
        val phone = sessionManager.getUserPhone() ?: "8801700000000"
        val avatar = sessionManager.getUserAvatar()
        val classDisplay = sessionManager.getUserClassDisplay() ?: sessionManager.getUserClassName() ?: "Class 11"
        val group = sessionManager.getUserGroup() ?: "বিজ্ঞান"

        _uiState.value = _uiState.value.copy(
            userName = name,
            userPhone = phone,
            userAvatar = avatar,
            userClassName = classDisplay,
            userGroup = group
        )
    }

    private fun loadDefaultHistory() {
        val sampleSession = AiChatSession(
            id = "session_math_demo",
            title = "ত্রিভুজের ক্ষেত্রফল নির্ণয়",
            subject = "math",
            timestamp = System.currentTimeMillis() - 3600000L * 2,
            messages = listOf(
                AiChatMessage(
                    text = "ত্রিভুজের ক্ষেত্রফল কিভাবে বের করবো?",
                    isUser = true,
                    subject = "math"
                ),
                generateAiAnswer(
                    question = "ত্রিভুজের ক্ষেত্রফল কিভাবে বের করবো?",
                    subject = "math",
                    imageAttached = false
                )
            )
        )

        val historyList = listOf(
            sampleSession,
            AiChatSession(
                id = "session_chem_demo",
                title = "HCl ও NaOH এর প্রশমন বিক্রিয়া",
                subject = "chemistry",
                timestamp = System.currentTimeMillis() - 3600000L * 24,
                messages = emptyList()
            ),
            AiChatSession(
                id = "session_phy_demo",
                title = "ওহমের সূত্রের গাণিতিক প্রতিপাদন",
                subject = "physics",
                timestamp = System.currentTimeMillis() - 3600000L * 48,
                messages = emptyList()
            )
        )

        _uiState.value = _uiState.value.copy(
            chatSessions = historyList
        )
    }

    fun fetchSubscriptionStatus() {
        viewModelScope.launch {
            try {
                val response = apiService.getAiSubscriptionList()
                val activeItem = response.data?.firstOrNull() ?: createDefaultActiveSubscription()
                _uiState.value = _uiState.value.copy(
                    subscription = activeItem,
                    isSubscribed = true,
                    isUnlimited = true
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    subscription = createDefaultActiveSubscription(),
                    isSubscribed = true,
                    isUnlimited = true
                )
            }
        }
    }

    private fun createDefaultActiveSubscription(): AiSubscriptionItem {
        return AiSubscriptionItem(
            id = "sub_learning_pass_active",
            package_id = "pkg_learning_pass_01",
            subscription_metadata = SubscriptionMetadata(
                package_title = "লার্নিং পাস (Learning Pass)",
                package_price = 299,
                has_image_support = true,
                usage_limit = UsageLimit(
                    conversation_depth = 50,
                    question_limit = 9999,
                    daily_limit = 9999,
                    used_count = 1
                )
            ),
            start_date = "2026-09-01T00:00:00Z",
            end_date = "2026-11-30T23:59:59Z",
            status = "ACTIVE"
        )
    }

    fun onSubjectSelected(subjectCode: String) {
        _uiState.value = _uiState.value.copy(selectedSubjectCode = subjectCode)
    }

    fun onInputTextChanged(text: String) {
        if (text.length <= 500) {
            _uiState.value = _uiState.value.copy(inputText = text)
        }
    }

    fun onImageSelected(uri: Uri?) {
        _uiState.value = _uiState.value.copy(attachedImageUri = uri)
    }

    fun clearAttachedImage() {
        _uiState.value = _uiState.value.copy(attachedImageUri = null)
    }

    fun setSamplePrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(inputText = prompt)
    }

    fun toggleWebMode() {
        _uiState.value = _uiState.value.copy(isWebMode = !_uiState.value.isWebMode)
    }

    fun startNewChat() {
        val currentMsgs = _uiState.value.currentMessages
        if (currentMsgs.isNotEmpty()) {
            val firstQuestion = currentMsgs.firstOrNull { it.isUser }?.text?.take(35) ?: "নতুন কথোপকথন"
            val newSession = AiChatSession(
                id = java.util.UUID.randomUUID().toString(),
                title = firstQuestion,
                subject = _uiState.value.selectedSubjectCode,
                timestamp = System.currentTimeMillis(),
                messages = currentMsgs
            )
            val updatedHistory = listOf(newSession) + _uiState.value.chatSessions.filter { it.id != newSession.id }
            _uiState.value = _uiState.value.copy(
                chatSessions = updatedHistory,
                currentMessages = emptyList(),
                inputText = "",
                attachedImageUri = null,
                currentSessionId = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                currentMessages = emptyList(),
                inputText = "",
                attachedImageUri = null,
                currentSessionId = null
            )
        }
    }

    fun loadSession(session: AiChatSession) {
        _uiState.value = _uiState.value.copy(
            currentMessages = if (session.messages.isNotEmpty()) session.messages else listOf(
                AiChatMessage(text = session.title, isUser = true, subject = session.subject),
                generateAiAnswer(session.title, session.subject, false)
            ),
            selectedSubjectCode = session.subject,
            currentSessionId = session.id
        )
    }

    fun deleteSession(sessionId: String) {
        val updated = _uiState.value.chatSessions.filter { it.id != sessionId }
        _uiState.value = _uiState.value.copy(chatSessions = updated)
    }

    fun submitFeedback(messageId: String, feedbackType: String) {
        val updated = _uiState.value.currentMessages.map { msg ->
            if (msg.id == messageId) {
                msg.copy(feedback = feedbackType)
            } else msg
        }
        _uiState.value = _uiState.value.copy(
            currentMessages = updated,
            toastMessage = if (feedbackType == "like") "ধন্যবাদ! আপনার ফিডব্যাক গ্রহণ করা হয়েছে 👍" else "ফিডব্যাকের জন্য ধন্যবাদ, আমরা উন্নতির চেষ্টা করছি 👎"
        )
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    fun sendQuestion(customPrompt: String? = null) {
        val question = (customPrompt ?: _uiState.value.inputText).trim()
        val imageUri = if (customPrompt == null) _uiState.value.attachedImageUri else null
        val subject = _uiState.value.selectedSubjectCode

        if (question.isBlank() && imageUri == null) return

        val userMessage = AiChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            text = question.ifBlank { "📸 [আপলোড করা ছবির সমাধান]" },
            imageUri = imageUri,
            isUser = true,
            subject = subject,
            timestamp = System.currentTimeMillis()
        )

        val updatedMessages = _uiState.value.currentMessages + userMessage

        _uiState.value = _uiState.value.copy(
            currentMessages = updatedMessages,
            inputText = "",
            attachedImageUri = null,
            isGenerating = true,
            usedQuestionsCount = _uiState.value.usedQuestionsCount + 1
        )

        viewModelScope.launch {
            // Simulated AI thinking and streaming latency
            delay(1200)

            val aiMessage = generateAiAnswer(
                question = question,
                subject = subject,
                imageAttached = imageUri != null
            )

            _uiState.value = _uiState.value.copy(
                currentMessages = _uiState.value.currentMessages + aiMessage,
                isGenerating = false
            )
        }
    }

    private fun generateAiAnswer(
        question: String,
        subject: String,
        imageAttached: Boolean
    ): AiChatMessage {
        val qLower = question.lowercase()

        val steps = mutableListOf<String>()
        val formulas = mutableListOf<String>()
        var finalAnswer: String
        var tips: String
        val formattedExplanation: StringBuilder = StringBuilder()

        if (imageAttached) {
            formattedExplanation.append("📸 **আপলোড করা ছবির প্রশ্নটি সফলভাবে বিশ্লেষণ করা হয়েছে:**\n\n")
        }

        when {
            // 1. Math / Triangle Area
            qLower.contains("ত্রিভুজ") || qLower.contains("triangle") || qLower.contains("ক্ষেত্রফল") -> {
                formattedExplanation.append("ত্রিভুজের ক্ষেত্রফল নির্ণয়ের সাধারণ ও বিশেষ সূত্রসমূহ নিচে ধাপে ধাপে আলোচনা করা হলো:\n\n")
                steps.add("১. সাধারণ ত্রিভুজের ক্ষেত্রে: ক্ষেত্রফল = ½ × ভূমি × উচ্চতা")
                steps.add("২. সমবাহু ত্রিভুজের ক্ষেত্রে: ক্ষেত্রফল = (√3 / 4) × a² (যেখানে a হলো প্রতি বাহুর দৈর্ঘ্য)")
                steps.add("৩. বিষমবাহু ত্রিভুজের ক্ষেত্রে (হেরনের সূত্র): s = (a + b + c)/2 হলে, ক্ষেত্রফল = √[s(s - a)(s - b)(s - c)]")
                steps.add("৪. স্থানাঙ্ক জ্যামিতির মাধ্যমে: শীর্ষবিন্দু (x₁, y₁), (x₂, y₂), (x₃, y₃) দেওয়া থাকলে নির্ণায়কের মাধ্যমে মান বের করা হয়।")
                
                formulas.add("A = \\frac{1}{2} \\times \\text{base} \\times \\text{height}")
                formulas.add("A = \\frac{\\sqrt{3}}{4} a^2 \\quad (\\text{Equilateral Triangle})")
                formulas.add("A = \\sqrt{s(s-a)(s-b)(s-c)} \\quad \\text{where } s = \\frac{a+b+c}{2}")

                finalAnswer = "ত্রিভুজের ক্ষেত্রফল = ½ × ভূমি × উচ্চতা বর্গ একক।"
                tips = "💡 পরীক্ষার টিপস: যদি ৩টি বাহুর মান দেওয়া থাকে তবে সবসময় হেরনের সূত্র ব্যবহার করবে।"
            }

            // 2. Quadratic Equation
            qLower.contains("quadratic") || qLower.contains("দ্বিঘাত") || qLower.contains("ax^2") || qLower.contains("ax²") -> {
                formattedExplanation.append("দ্বিঘাত সমীকরণ ax² + bx + c = 0 এর সমাধান ও মূল নির্ণয়ের প্রক্রিয়া:\n\n")
                steps.add("১. সমীকরণটিকে আদর্শ রূপ ax² + bx + c = 0 তে সাজাও।")
                steps.add("২. নিশ্চয়ক (Discriminant) নির্ণয় করো: D = b² - 4ac")
                steps.add("৩. সূত্র প্রয়োগ করো: x = (-b ± √(b² - 4ac)) / (2a)")
                steps.add("৪. মূলদ্বয়ের প্রকৃতি: D > 0 হলে বাস্তব ও অসমান, D = 0 হলে বাস্তব ও সমান, D < 0 হলে অবাস্তব/জটিল সংখ্যা।")

                formulas.add("x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}")
                formulas.add("D = b^2 - 4ac")

                finalAnswer = "সমীকরণের মূলদ্বয়: x₁ = (-b + √D) / 2a এবং x₂ = (-b - √D) / 2a"
                tips = "💡 শর্টকাট: মূলদ্বয়ের যোগফল = -b/a এবং গুণফল = c/a।"
            }

            // 3. Matrix
            qLower.contains("matrix") || qLower.contains("ম্যাট্রিক্স") || qLower.contains("নির্ণায়ক") -> {
                formattedExplanation.append("ম্যাট্রিক্স যোগ ও গুণ করার মৌলিক শর্ত ও নিয়ম:\n\n")
                steps.add("১. ম্যাট্রিক্স যোগ/বিয়োগ: দুটি ম্যাট্রিক্সের মাত্রা (Order) অবিকল এক হতে হবে (m × n)। অনুরূপ ভুক্তিগুলো যোগ বা বিয়োগ হবে।")
                steps.add("২. ম্যাট্রিক্স গুণ: প্রথম ম্যাট্রিক্সের কলাম সংখ্যা = দ্বিতীয় ম্যাট্রিক্সের সারি সংখ্যা হতে হবে (A_{m×p} × B_{p×n} = C_{m×n})।")
                steps.add("৩. সারি দিয়ে কলামের প্রতিটি উপাদানের সাথে গুণ করে যোগ করতে হয়।")

                formulas.add("[A]_{m \\times p} \\times [B]_{p \\times n} = [C]_{m \\times n}")
                formulas.add("c_{ij} = \\sum_{k=1}^{p} a_{ik} b_{kj}")

                finalAnswer = "ম্যাট্রিক্স গুণের ক্রম পরিবর্তনশীল নয় (AB ≠ BA সাধারণ ক্ষেত্রে)।"
                tips = "💡 অ্যাডমিশন শর্টকাট: ২×২ ম্যাট্রিক্সের বিপরীত ম্যাট্রিক্স নির্ণয়ে মুখ্য কর্ণের ভুক্তি অদলবদল এবং গৌণ কর্ণের চিহ্ন পরিবর্তন করতে হয়।"
            }

            // 4. Ohm's Law / Physics
            qLower.contains("ohm") || qLower.contains("ওহম") || qLower.contains("বিদ্যুৎ") || qLower.contains("বর্তনী") -> {
                formattedExplanation.append("ওহমের সূত্র (Ohm's Law) এর সংজ্ঞা, সমীকরণ ও প্রতিপাদন:\n\n")
                steps.add("১. সূত্র: তাপমাত্রা স্থির থাকলে কোনো পরিবাহীর মধ্য দিয়ে প্রবাহিত তড়িৎ প্রবাহ (I) পরিবাহীর দুই প্রান্তের বিভব পার্থক্যের (V) সমানুপাতিক।")
                steps.add("২. গাণিতিক রূপ: I ∝ V ⟹ V = IR")
                steps.add("৩. এখানে R হলো পরিবাহীর রোধ (Resistance), যার একক ওহম (Ω)।")
                steps.add("৪. গ্রাফ: V বনাম I লেখচিত্র মূলবিন্দুগামী একটি সরলরেখা।")

                formulas.add("V = I \\times R")
                formulas.add("I = \\frac{V}{R}, \\quad R = \\frac{V}{I}")
                formulas.add("P = V \\times I = I^2 R = \\frac{V^2}{R}")

                finalAnswer = "বিভব পার্থক্য V = IR এবং তড়িৎ প্রবাহ I = V / R"
                tips = "💡 মনে রেখো: তাপমাত্রা বাড়লে ধাতব পরিবাহীর রোধ বৃদ্ধি পায় এবং তড়িৎ প্রবাহ হ্রাস পায়।"
            }

            // 5. Newton's 2nd Law
            qLower.contains("newton") || qLower.contains("নিউটন") || qLower.contains("গতি") || qLower.contains("বল") -> {
                formattedExplanation.append("নিউটনের গতির দ্বিতীয় সূত্রের গাণিতিক প্রতিপাদন:\n\n")
                steps.add("১. বিবৃতি: বস্তুর ভরবেগের পরিবর্তনের হার তার উপর প্রযুক্ত বলের সমানুপাতিক এবং বল যেদিকে ক্রিয়া করে বস্তুর ভরবেগের পরিবর্তনও সেদিকে ঘটে।")
                steps.add("২. ভরবেগ p = mv, ভরবেগের পরিবর্তন Δp = m(v - u)")
                steps.add("৩. পরিবর্তনের হার = m(v - u) / t = ma (যেহেতু ত্বরণ a = (v - u)/t)")
                steps.add("৪. বল F = k × ma; SI এককে k = 1 ধরে পাওয়া যায়: F = ma")

                formulas.add("F = m \\times a")
                formulas.add("p = m \\times v")
                formulas.add("F = \\frac{dp}{dt} = m \\frac{dv}{dt}")

                finalAnswer = "প্রযুক্ত বল F = ma (একক: নিউটন / N)"
                tips = "💡 টিপস: যদি বল শূন্য হয় (F = 0), তবে ত্বরণও শূন্য হবে (a = 0), যা নিউটনের প্রথম সূত্র প্রমাণ করে।"
            }

            // 6. Chemistry - Neutralization / Acid Base
            qLower.contains("hcl") || qLower.contains("naoh") || qLower.contains("প্রশমন") || qLower.contains("acid") || qLower.contains("এসিড") -> {
                formattedExplanation.append("অ্যাসিড ও ক্ষারের প্রশমন বিক্রিয়া (Neutralization Reaction):\n\n")
                steps.add("১. অ্যাসিড (হাইড্রোক্লোরিক অ্যাসিড - HCl) এবং ক্ষার (সোডিয়াম হাইড্রোক্সাইড - NaOH) বিক্রিয়া করে লবণ (NaCl) ও পানি (H₂O) উৎপন্ন করে।")
                steps.add("২. সম্পূর্ণ বিক্রিয়া: HCl + NaOH → NaCl + H₂O + তাপ")
                steps.add("৩. আয়নীয় সমীকরণ: H⁺(aq) + OH⁻(aq) → H₂O(l)")
                steps.add("৪. এই বিক্রিয়ায় ৫৩.৪ কিলোজুল/মোল বা ৫৭.৩৪ কিলোজুল/মোল তাপ উৎপন্ন হয়, তাই এটি একটি তাপোৎপাদী বিক্রিয়া।")

                formulas.add("\\text{HCl}_{(aq)} + \\text{NaOH}_{(aq)} \\longrightarrow \\text{NaCl}_{(aq)} + \\text{H}_2\\text{O}_{(l)}")
                formulas.add("\\Delta H = -57.34 \\text{ kJ/mol}")

                finalAnswer = "উৎপন্ন যৌগ: সোডিয়াম ক্লোরাইড (লবণ) এবং পানি। এটি প্রশমন তাপ উৎপন্ন করে।"
                tips = "💡 যেকোনো তীব্র অ্যাসিড ও তীব্র ক্ষারের প্রশমন তাপের মান সর্বদা ধ্রুবক (প্রায় -৫৭.৩৪ kJ/mol)।"
            }

            // 7. ICT / Binary
            qLower.contains("binary") || qLower.contains("বাইনারি") || qLower.contains("decimal") || qLower.contains("ডেসিমেল") || qLower.contains("ict") -> {
                formattedExplanation.append("তথ্য ও যোগাযোগ প্রযুক্তি: ডেসিমেল থেকে বাইনারি রূপান্তরের সহজ নিয়ম:\n\n")
                steps.add("১. দশমিক সংখ্যাটিকে ক্রমাগত ২ দিয়ে ভাগ করতে হবে এবং ভাগশেষগুলো পাশে লিখে রাখতে হবে।")
                steps.add("২. ভাগফল ০ না হওয়া পর্যন্ত ভাগ প্রক্রিয়া চলবে।")
                steps.add("৩. ভাগশেষগুলোকে নিচ থেকে উপরের দিকে (MSB to LSB) সাজালে কাঙ্ক্ষিত বাইনারি মান পাওয়া যায়।")
                steps.add("৪. যেমন: (25)₁₀ = (11001)₂")

                formulas.add("(25)_{10} = (11001)_2")
                formulas.add("2^4 + 2^3 + 0 + 0 + 2^0 = 16 + 8 + 1 = 25")

                finalAnswer = "দশমিক মানকে ২ দ্বারা ভাগ করে অবশিষ্ট উল্টো সাজিয়ে বাইনারি নির্ণীত হয়।"
                tips = "💡 শর্টকাট টেকনিক: 1, 2, 4, 8, 16, 32... সিরিজের সংখ্যাগুলো যোগ করে দ্রুত বাইনারি মান বের করা যায়।"
            }

            // Default fallback solver
            else -> {
                formattedExplanation.append("তোমার প্রশ্নের বিষয়ভিত্তিক বিশ্লেষণ ও সমাধান:\n\n")
                steps.add("১. প্রশ্নের মূল ধারণা: বিষয়ভিত্তিক মূল সূত্র ও তত্ত্ব পর্যালোচনা করা হয়েছে।")
                steps.add("২. সমাধান প্রণালী: ধাপে ধাপে যুক্তি ও সমীকরণ প্রয়োগ করে কাঙ্ক্ষিত ফলাফল নির্ণয় করা সম্ভব।")
                steps.add("৩. গাণিতিক বা তাত্ত্বিক সামঞ্জস্য: উত্তরটি বোর্ড বই ও পাঠ্যক্রম অনুসারে ভেরিফাই করা হয়েছে।")

                formulas.add("\\text{Result} = f(\\text{Concept, Formula, Logic})")

                finalAnswer = "প্রশ্নটির পূর্ণাঙ্গ সমাধান নির্ভুলভাবে প্রস্তুত করা হয়েছে।"
                tips = "💡 আরও বিস্তারিত জানতে নিচের 'আরো এক্সপ্লেইন করো' বাটনে ট্যাপ করো।"
            }
        }

        return AiChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            text = formattedExplanation.toString(),
            isUser = false,
            subject = subject,
            isStepByStep = true,
            steps = steps,
            formulas = formulas,
            finalAnswer = finalAnswer,
            tips = tips,
            timestamp = System.currentTimeMillis()
        )
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
