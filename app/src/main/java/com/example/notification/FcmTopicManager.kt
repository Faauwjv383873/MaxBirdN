package com.example.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.auth.SessionManager
import com.example.database.NotificationHistoryEntity
import com.example.database.NotificationHistoryRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resumeWithException

enum class TopicCategory(val labelBn: String) {
    PUBLIC_BROADCAST("পাবলিক ও ক্যাম্পেইন"),
    ACADEMIC_CLASS("সিলেবাস ও শ্রেণি"),
    ACTIVE_COURSE("সক্রিয় কোর্স"),
    USER_ACCOUNT("ইউজার অ্যাকাউন্ট")
}

data class TopicStatus(
    val topicName: String,
    val description: String,
    val category: TopicCategory,
    val isSubscribed: Boolean,
    val error: String? = null,
    val latencyMs: Long = 0L
)

data class FcmDiagnosticState(
    val isChecking: Boolean = false,
    val isFirebaseInitialized: Boolean = false,
    val firebaseProjectId: String? = null,
    val firebaseAppId: String? = null,
    val isNotificationPermissionGranted: Boolean = false,
    val fcmToken: String? = null,
    val tokenError: String? = null,
    val activeCourseTitle: String? = null,
    val activeProgramId: String? = null,
    val activePhaseId: String? = null,
    val academicClass: String? = null,
    val userId: String? = null,
    val topics: List<TopicStatus> = emptyList(),
    val unsubscribedTopics: List<String> = emptyList(),
    val terminalLogs: List<String> = emptyList(),
    val lastCheckedTimestamp: Long = 0L,
    val testNotificationMessage: String? = null
)

object FcmTopicManager {
    private const val TAG = "FcmTopicManager"

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resumeWith(Result.success(result))
            }
            addOnFailureListener { exception ->
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
            addOnCanceledListener {
                if (continuation.isActive) continuation.cancel()
            }
        }

    /**
     * Compute the full set of desired topics based on current session:
     * 1. Public & Marketing Broadcasts (e.g. SHIKHO_ALL, campaign, broadcast)
     * 2. Academic / Syllabus (e.g. CLASS_C11, HSC)
     * 3. Batch / Year (e.g. BATCH_2025)
     * 4. User Account (e.g. userId)
     * 5. Active Course / Program & Phase (e.g. LIVE_ShikhoNotification_UserID_..._Program_...)
     */
    fun getDesiredTopicDefinitions(sessionManager: SessionManager): List<Triple<String, String, TopicCategory>> {
        val list = mutableListOf<Triple<String, String, TopicCategory>>()

        // 1. General Public & Campaign Announcements
        list.add(Triple("SHIKHO_ALL", "সকল সাধারণ ঘোষণা ও ক্যাম্পেইন (SHIKHO_ALL)", TopicCategory.PUBLIC_BROADCAST))
        list.add(Triple("shikho_all", "সাধারণ পুশ নোটিফিকেশন (shikho_all)", TopicCategory.PUBLIC_BROADCAST))
        list.add(Triple("shikho_public", "পাবলিক নোটিফিকেশন ব্রডকাস্ট (shikho_public)", TopicCategory.PUBLIC_BROADCAST))
        list.add(Triple("campaign", "শিখো স্পেশাল ক্যাম্পেইন ও কনটেস্ট (campaign)", TopicCategory.PUBLIC_BROADCAST))
        list.add(Triple("broadcast", "সারাদেশের শিক্ষার্থী ব্রডকাস্ট (broadcast)", TopicCategory.PUBLIC_BROADCAST))
        list.add(Triple("all", "সকল শিক্ষার্থী ব্রডকাস্ট (all)", TopicCategory.PUBLIC_BROADCAST))
        list.add(Triple("general", "জেনারেল নোটিফিকেশন (general)", TopicCategory.PUBLIC_BROADCAST))
        list.add(Triple("student", "স্টুডেন্ট নোটিফিকেশন (student)", TopicCategory.PUBLIC_BROADCAST))
        list.add(Triple("android", "অ্যান্ড্রয়েড অ্যাপ ব্রডকাস্ট (android)", TopicCategory.PUBLIC_BROADCAST))

        // 2. Academic Class & Syllabus Topics
        val rawClass = sessionManager.getUserClassName() ?: sessionManager.getActiveProgramClassCode() ?: "C11"
        val cleanClass = rawClass.replace(" ", "").trim()
        if (cleanClass.isNotBlank()) {
            list.add(Triple("CLASS_$cleanClass", "ক্লাস-ভিত্তিক নোটিফিকেশন (CLASS_$cleanClass)", TopicCategory.ACADEMIC_CLASS))
            list.add(Triple(cleanClass, "ক্লাস টপিক ($cleanClass)", TopicCategory.ACADEMIC_CLASS))

            val upper = cleanClass.uppercase(Locale.ROOT)
            if (upper.contains("11") || upper.contains("12") || upper.contains("HSC")) {
                list.add(Triple("CLASS_HSC", "এইচএসসি শিক্ষার্থী টপিক (CLASS_HSC)", TopicCategory.ACADEMIC_CLASS))
                list.add(Triple("HSC", "এইচএসসি সাধারণ টপিক (HSC)", TopicCategory.ACADEMIC_CLASS))
            }
            if (upper.contains("9") || upper.contains("10") || upper.contains("SSC")) {
                list.add(Triple("CLASS_SSC", "এসএসসি শিক্ষার্থী টপিক (CLASS_SSC)", TopicCategory.ACADEMIC_CLASS))
                list.add(Triple("SSC", "এসএসসি সাধারণ টপিক (SSC)", TopicCategory.ACADEMIC_CLASS))
            }
        }

        // Batch / Year
        val batchId = sessionManager.getUserBatchId() ?: sessionManager.getActiveProgramBatchId()
        if (!batchId.isNullOrBlank()) {
            list.add(Triple("BATCH_$batchId", "ব্যাচ নোটিফিকেশন (BATCH_$batchId)", TopicCategory.ACADEMIC_CLASS))
            list.add(Triple(batchId, "ব্যাচ সাল টপিক ($batchId)", TopicCategory.ACADEMIC_CLASS))
        }

        // 3. User Specific Topics
        val userId = sessionManager.getUserId()
        if (!userId.isNullOrBlank()) {
            list.add(Triple(userId, "ব্যবহারকারী অ্যাকাউন্ট টপিক (User: $userId)", TopicCategory.USER_ACCOUNT))
            list.add(Triple("USER_$userId", "ইউজার প্রোফাইল নোটিফিকেশন (USER_$userId)", TopicCategory.USER_ACCOUNT))
        }

        // 4. Active Course / Program & Phase Topics
        val programId = sessionManager.getActiveProgramId()
        val phaseId = sessionManager.getActiveProgramPhaseId()

        if (!programId.isNullOrBlank()) {
            if (!userId.isNullOrBlank()) {
                val userProgTopic = "LIVE_ShikhoNotification_UserID_${userId}_Program_${programId}"
                list.add(Triple(userProgTopic, "লাইভ ক্লাস নোটিফিকেশন টপিক (User + Program)", TopicCategory.ACTIVE_COURSE))
            }

            if (!phaseId.isNullOrBlank()) {
                val progPhaseTopic = "LIVE_ShikhoNotification_Program_${programId}_Phase_${phaseId}"
                list.add(Triple(progPhaseTopic, "প্রোগ্রাম ফেজ টপিক (Program + Phase)", TopicCategory.ACTIVE_COURSE))
                list.add(Triple("PHASE_$phaseId", "ফেজ টপিক (PHASE_$phaseId)", TopicCategory.ACTIVE_COURSE))
            }

            list.add(Triple("PROGRAM_$programId", "কোর্স প্রোগ্রাম টপিক (PROGRAM_$programId)", TopicCategory.ACTIVE_COURSE))
            list.add(Triple("LIVE_ShikhoNotification_Program_$programId", "কোর্স লাইভ ব্রডকাস্ট ($programId)", TopicCategory.ACTIVE_COURSE))
        }

        return list.distinctBy { it.first }
    }

    /**
     * Automatic synchronization & replacement of FCM topics:
     * - Unsubscribes stale topics (old courses, old syllabus, old users)
     * - Subscribes all new desired topics
     * - Stores updated set in SessionManager
     */
    fun syncAllTopics(sessionManager: SessionManager) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fm = FirebaseMessaging.getInstance()

                // Fetch & store fresh FCM token
                try {
                    val token = fm.token.awaitTask()
                    sessionManager.setFcmToken(token)
                    Log.d(TAG, "🔑 FCM token verified during sync")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fetch FCM token during sync: ${e.message}")
                }

                val desired = getDesiredTopicDefinitions(sessionManager)
                val desiredTopicNames = desired.map { it.first }.toSet()
                val previouslySubscribed = sessionManager.getSubscribedTopics()

                // 1. Unsubscribe topics that are no longer part of current user / active course
                val toUnsubscribe = previouslySubscribed - desiredTopicNames
                for (oldTopic in toUnsubscribe) {
                    try {
                        fm.unsubscribeFromTopic(oldTopic).awaitTask()
                        Log.d(TAG, "🔄 Unsubscribed obsolete topic: $oldTopic")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to unsubscribe obsolete topic '$oldTopic': ${e.message}")
                    }
                }

                // 2. Subscribe all currently desired topics
                for (newTopic in desiredTopicNames) {
                    try {
                        fm.subscribeToTopic(newTopic).awaitTask()
                        Log.d(TAG, "📡 Subscribed topic: $newTopic")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to subscribe topic '$newTopic': ${e.message}")
                    }
                }

                // 3. Save new state
                sessionManager.setSubscribedTopics(desiredTopicNames)
                Log.i(TAG, "✅ Topic sync completed: ${desiredTopicNames.size} active topics (unsubscribed ${toUnsubscribe.size} old)")
            } catch (e: Exception) {
                Log.e(TAG, "Error during FCM topic sync: ${e.message}", e)
            }
        }
    }

    suspend fun runDiagnostics(context: Context, sessionManager: SessionManager): FcmDiagnosticState =
        withContext(Dispatchers.IO) {
            val logs = mutableListOf<String>()
            fun log(msg: String) {
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val entry = "[$time] $msg"
                logs.add(entry)
                Log.d(TAG, entry)
            }

            log("🔍 ফায়ারবেস ও লাইভ নোটিফিকেশন ডায়াগনস্টিক শুরু হচ্ছে...")

            // Ensure channel exists
            NotificationHelper.createNotificationChannel(context)
            log("📢 নোটিফিকেশন চ্যানেল '${NotificationHelper.CHANNEL_ID}' সক্রিয় নিশ্চিত করা হয়েছে")

            // 1. Firebase Initialization Check
            var isFirebaseInit = false
            var projectId: String? = null
            var appId: String? = null
            try {
                val apps = FirebaseApp.getApps(context)
                if (apps.isEmpty()) {
                    log("❌ ত্রুটি: FirebaseApp ইনিশিয়ালাইজ হয়নি!")
                } else {
                    val app = FirebaseApp.getInstance()
                    projectId = app.options.projectId
                    appId = app.options.applicationId
                    isFirebaseInit = true
                    log("✅ FirebaseApp ইনিশিয়ালাইজড | প্রজেক্ট: '$projectId'")
                    log("ℹ️ অ্যাপ্লিকেশন আইডি: $appId")
                    if (projectId == "shikho-tech") {
                        log("🎯 অফিসিয়াল shikho-tech ব্যাকএন্ড প্রজেক্টের সাথে সরাসরি সংযুক্ত")
                    } else {
                        log("⚠️ প্রজেক্ট আইডি '$projectId' (প্রত্যাশিত: shikho-tech)")
                    }
                }
            } catch (e: Exception) {
                log("❌ Firebase ত্রুটি: ${e.message}")
            }

            // 2. Notification Permission Check
            var isPermissionGranted = false
            try {
                val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val postGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    isPermissionGranted = areNotificationsEnabled && postGranted
                    log("📱 Android 13+ POST_NOTIFICATIONS: ${if (postGranted) "GRANTED (অনুমোদিত) ✅" else "DENIED (অনুমতি নেই) ❌"}")
                } else {
                    isPermissionGranted = areNotificationsEnabled
                    log("📱 সিস্টেম নোটিফিকেশন: ${if (areNotificationsEnabled) "অনুমোদিত ✅" else "নিষ্ক্রিয় ❌"}")
                }
            } catch (e: Exception) {
                log("⚠️ পারমিশন চেক ত্রুটি: ${e.message}")
            }

            // 3. FCM Registration Token
            var fcmToken: String? = sessionManager.getFcmToken()
            var tokenError: String? = null
            try {
                log("🔑 গুগল প্লে সার্ভিসেস থেকে লাইভ FCM Token চাওয়া হচ্ছে...")
                val token = FirebaseMessaging.getInstance().token.awaitTask()
                fcmToken = token
                sessionManager.setFcmToken(token)
                val preview = if (token.length > 24) "${token.take(12)}...${token.takeLast(8)}" else token
                log("✅ FCM Token প্রাপ্ত: $preview")
            } catch (e: Exception) {
                tokenError = e.message ?: "Unknown token error"
                log("❌ FCM Token আনতে ব্যর্থ: $tokenError")
            }

            // 4. Topic Replacement & Synchronization
            val desiredDefinitions = getDesiredTopicDefinitions(sessionManager)
            val desiredNames = desiredDefinitions.map { it.first }.toSet()
            val previousSubscribed = sessionManager.getSubscribedTopics()

            val fm = FirebaseMessaging.getInstance()
            val unsubscribedList = mutableListOf<String>()

            // Unsubscribe obsolete topics
            val toUnsubscribe = previousSubscribed - desiredNames
            for (oldTopic in toUnsubscribe) {
                try {
                    fm.unsubscribeFromTopic(oldTopic).awaitTask()
                    unsubscribedList.add(oldTopic)
                    log("🔄 পুরানো টপিক আনসাবস্ক্রাইব করা হয়েছে: $oldTopic")
                } catch (e: Exception) {
                    log("⚠️ আনসাবস্ক্রাইব করতে সমস্যা: $oldTopic (${e.message})")
                }
            }

            // Subscribe and benchmark all desired topics
            val topicList = mutableListOf<TopicStatus>()
            for ((topicName, desc, category) in desiredDefinitions) {
                log("📡 টপিক যাচাই ও সাবস্ক্রিপশন: $topicName...")
                val start = System.currentTimeMillis()
                try {
                    fm.subscribeToTopic(topicName).awaitTask()
                    val latency = System.currentTimeMillis() - start
                    topicList.add(
                        TopicStatus(
                            topicName = topicName,
                            description = desc,
                            category = category,
                            isSubscribed = true,
                            error = null,
                            latencyMs = latency
                        )
                    )
                    log("✅ সক্রিয়: $topicName (${latency}ms) [${category.labelBn}]")
                } catch (e: Exception) {
                    val latency = System.currentTimeMillis() - start
                    val errMsg = e.message ?: "সাবস্ক্রিপশন ব্যর্থ"
                    topicList.add(
                        TopicStatus(
                            topicName = topicName,
                            description = desc,
                            category = category,
                            isSubscribed = false,
                            error = errMsg,
                            latencyMs = latency
                        )
                    )
                    log("❌ ব্যর্থ: $topicName ($errMsg)")
                }
            }

            // Save new active set
            sessionManager.setSubscribedTopics(desiredNames)

            if (isFirebaseInit && isPermissionGranted && !fcmToken.isNullOrBlank()) {
                log("🎉 সম্পূর্ণ সিস্টেম সক্রিয় ও স্বয়ংক্রিয় রিপ্লেসমেন্ট রেডি! পাবলিক ক্যাম্পেইন ও কোর্স নোটিফিকেশন সরাসরি আসবে।")
            } else {
                log("⚠️ কিছু চেকিং অসম্পূর্ণ রয়ে গেছে, বিস্তারিত উপরে দেখুন।")
            }

            FcmDiagnosticState(
                isChecking = false,
                isFirebaseInitialized = isFirebaseInit,
                firebaseProjectId = projectId,
                firebaseAppId = appId,
                isNotificationPermissionGranted = isPermissionGranted,
                fcmToken = fcmToken,
                tokenError = tokenError,
                activeCourseTitle = sessionManager.getActiveProgramTitleBn() ?: "এইচএসসি কোর্স",
                activeProgramId = sessionManager.getActiveProgramId(),
                activePhaseId = sessionManager.getActiveProgramPhaseId(),
                academicClass = sessionManager.getUserClassName() ?: "C11",
                userId = sessionManager.getUserId(),
                topics = topicList,
                unsubscribedTopics = unsubscribedList,
                terminalLogs = logs,
                lastCheckedTimestamp = System.currentTimeMillis()
            )
        }

    fun sendTestNotification(
        context: Context,
        repository: NotificationHistoryRepository
    ): Pair<Boolean, String> {
        return try {
            NotificationHelper.createNotificationChannel(context)
            val channelId = NotificationHelper.CHANNEL_ID
            val notificationManager = NotificationManagerCompat.from(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("type", "live_class")
                putExtra("title", "🔴 লাইভ ক্লাস ও ক্যাম্পেইন টেস্ট নোটিফিকেশন")
                putExtra("is_test", "true")
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_ONE_SHOT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 10000).toInt(),
                intent,
                pendingIntentFlags
            )

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val title = "🔴 লাইভ ক্লাস শুরু হয়েছে! (টেস্ট)"
            val body = "টেস্ট নোটিফিকেশন: আপনার ডিভাইস সফলভাবে Shikho লাইভ নোটিফিকেশন ও পাবলিক ব্রডকাস্ট সিস্টেমে যুক্ত হয়েছে।"

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)

            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            notificationManager.notify(notificationId, builder.build())

            // Save into local Room database
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.save(
                        NotificationHistoryEntity(
                            title = title,
                            body = body,
                            imageUrl = null,
                            type = "live_class",
                            dataJson = """{"is_test":true,"event":"live_class"}""",
                            deepLink = null,
                            receivedAt = System.currentTimeMillis(),
                            isRead = false,
                            isClicked = false
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save test notification to DB: ${e.message}", e)
                }
            }

            Pair(true, "টেস্ট নোটিফিকেশন সফলভাবে পাঠানো হয়েছে! ফোনের স্ট্যাটাস বার চেক করুন।")
        } catch (e: Exception) {
            Log.e(TAG, "Error firing test notification: ${e.message}", e)
            Pair(false, "নোটিফিকেশন পাঠানো যায়নি: ${e.message}")
        }
    }

    @Deprecated("Use syncAllTopics(sessionManager) for automatic replacement")
    fun subscribeAllTopics(sessionManager: SessionManager) {
        syncAllTopics(sessionManager)
    }

    @Deprecated("Use syncAllTopics(sessionManager) for automatic replacement")
    fun subscribeProgramTopics(sessionManager: SessionManager, programId: String, phaseId: String? = null) {
        syncAllTopics(sessionManager)
    }
}
