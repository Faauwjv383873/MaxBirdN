package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.example.auth.SessionManager
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Manages Firebase Cloud Messaging (FCM) topic subscriptions and notification channels
 * matching the authentic Shikho notification architecture (`projects/shikho-tech`):
 * 1. `SHIKHO_ALL` (Global broadcasts, campaigns, orientation live, routine reminders)
 * 2. `{userId}` (Personal account notifications)
 * 3. `LIVE_ShikhoNotification_UserID_{userId}_Program_{programId}` (Selected course notifications)
 * 4. `LIVE_ShikhoNotification_Program_{programId}_Phase_{phaseId}` (Selected course phase live class & exam alerts)
 *
 * Whenever the user switches courses on the Home Screen, the previous course's topics are
 * immediately unsubscribed (`:unsubscribe`) and the newly selected course's topics are subscribed (`:subscribe`).
 */
object ShikhoNotificationManager {
    private const val TAG = "ShikhoNotificationMgr"

    // Primary Notification Channels
    const val CHANNEL_PUSH_NOTIFICATIONS = "shikho_push_notifications"
    const val CHANNEL_LIVE_CLASSES = "shikho_live_classes"

    // Global topic for all Shikho students
    const val TOPIC_SHIKHO_ALL = "SHIKHO_ALL"

    data class FcmTerminalLog(
        val timestamp: String,
        val type: String, // "INFO", "SUCCESS", "WARN", "ERROR", "PUSH"
        val message: String
    )

    private val _liveLogs = kotlinx.coroutines.flow.MutableStateFlow<List<FcmTerminalLog>>(emptyList())
    val liveLogs: kotlinx.coroutines.flow.StateFlow<List<FcmTerminalLog>> = _liveLogs

    fun logTerminal(type: String, message: String) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val time = sdf.format(java.util.Date())
        val entry = FcmTerminalLog(timestamp = time, type = type, message = message)
        _liveLogs.value = (_liveLogs.value + entry).takeLast(100)
        Log.d(TAG, "[$type] $message")
    }

    fun clearLogs() {
        _liveLogs.value = emptyList()
    }

    /**
     * Initializes high-importance notification channels for Android 8.0+ (API 26+)
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()

            // 1. General, Routine, Campaign & Account Notifications Channel
            val pushChannel = NotificationChannel(
                CHANNEL_PUSH_NOTIFICATIONS,
                "শিখো নোটিফিকেশন ও ঘোষণা",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "লাইভ ক্লাস, পরীক্ষা, রুটিন এবং গুরুত্বপূর্ণ ঘোষণা সংক্রান্ত নোটিফিকেশন"
                enableLights(true)
                enableVibration(true)
                setSound(defaultSoundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(pushChannel)

            // 2. Real-time Live Class & Exam Alerts Channel
            val liveClassChannel = NotificationChannel(
                CHANNEL_LIVE_CLASSES,
                "লাইভ ক্লাস ও পরীক্ষা অ্যালার্ট",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "লাইভ ক্লাস ও মডেল টেস্ট শুরুর তাৎক্ষণিক অ্যালার্ট ও রিমাইন্ডার"
                enableLights(true)
                enableVibration(true)
                setSound(defaultSoundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(liveClassChannel)
        }
    }

    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo("com.google.android.gms", 0)
            appInfo.enabled
        } catch (_: Throwable) {
            false
        }
    }

    private const val SHIKHO_FIREBASE_APP_NAME = "shikho-tech-fcm"
    private const val SHIKHO_PROJECT_ID = "shikho-tech"
    private const val SHIKHO_APP_ID = "1:173696198638:android:a7dd172fc44d09216c50ff"
    private const val SHIKHO_SENDER_ID = "173696198638"
    private const val SHIKHO_API_KEY_B64 = "QUl6YVN5QVZqbVBBWUtWdE9henBhcC1Ycnk4M1BGYzhxNUoxczhN"

    fun getShikhoFirebaseMessaging(context: Context): FirebaseMessaging? {
        return try {
            val appContext = context.applicationContext
            val existingApps = com.google.firebase.FirebaseApp.getApps(appContext)
            if (existingApps.isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(appContext)
            }
            val defaultApp = runCatching { com.google.firebase.FirebaseApp.getInstance() }.getOrNull()
            if (defaultApp?.options?.projectId == SHIKHO_PROJECT_ID) {
                return FirebaseMessaging.getInstance()
            }
            val existingShikhoApp = com.google.firebase.FirebaseApp.getApps(appContext)
                .firstOrNull { it.name == SHIKHO_FIREBASE_APP_NAME }
                ?: run {
                    val apiKey = String(
                        android.util.Base64.decode(SHIKHO_API_KEY_B64, android.util.Base64.DEFAULT)
                    ).trim()
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setProjectId(SHIKHO_PROJECT_ID)
                        .setApplicationId(SHIKHO_APP_ID)
                        .setGcmSenderId(SHIKHO_SENDER_ID)
                        .setApiKey(apiKey)
                        .setStorageBucket("shikho-tech.appspot.com")
                        .build()
                    com.google.firebase.FirebaseApp.initializeApp(appContext, options, SHIKHO_FIREBASE_APP_NAME)
                }
            runCatching { existingShikhoApp.get(FirebaseMessaging::class.java) }
                .getOrElse { FirebaseMessaging.getInstance() }
        } catch (_: Throwable) {
            runCatching { FirebaseMessaging.getInstance() }.getOrNull()
        }
    }

    /**
     * Synchronizes all authentic Shikho FCM topic subscriptions on `projects/shikho-tech`:
     * 1. `SHIKHO_ALL`
     * 2. `{userId}`
     * 3. `LIVE_ShikhoNotification_UserID_{userId}_Program_{programId}`
     * 4. `LIVE_ShikhoNotification_Program_{programId}_Phase_{phaseId}`
     */
    fun syncAllTopicSubscriptions(context: Context) {
        try {
            if (!isGooglePlayServicesAvailable(context)) {
                logTerminal("INFO", "ℹ️ Google Play Services unavailable. FCM registration deferred.")
                return
            }

            val appContext = context.applicationContext
            val sessionManager = SessionManager(appContext)
            val fcm = getShikhoFirebaseMessaging(appContext) ?: return
            try {
                fcm.isAutoInitEnabled = true
            } catch (_: Throwable) {}

            val userId = sessionManager.getUserId()?.trim()

            // Subscribe base & active course topics immediately (TopicsSubscriber queues & executes in order)
            subscribeVerifiedTopics(fcm, sessionManager, userId)

            // Also fetch & persist the latest FCM token
            try {
                fcm.token.addOnCompleteListener { task ->
                    if (task.isSuccessful && !task.result.isNullOrBlank()) {
                        val token = task.result
                        sessionManager.setFcmToken(token)
                        logTerminal("INFO", "🔑 Shikho FCM Token Ready: ${token.take(16)}...")
                    }
                }
            } catch (_: Exception) {}
        } catch (e: Throwable) {
            logTerminal("ERROR", "🚨 Exception during FCM sync: ${e.message}")
        }
    }

    private fun subscribeVerifiedTopics(
        fcm: FirebaseMessaging,
        sessionManager: SessionManager,
        userId: String?
    ) {
        // 1. Global topic: SHIKHO_ALL
        try {
            fcm.subscribeToTopic(TOPIC_SHIKHO_ALL)
                .addOnSuccessListener {
                    logTerminal("SUCCESS", "✅ Subscribed to Topic: $TOPIC_SHIKHO_ALL")
                }
        } catch (_: Exception) {}

        // 2. User-specific direct topic: {userId}
        if (!userId.isNullOrBlank()) {
            try {
                fcm.subscribeToTopic(userId)
                    .addOnSuccessListener {
                        logTerminal("SUCCESS", "✅ Subscribed to User Topic: $userId")
                    }
            } catch (_: Exception) {}
        }

        // 3. Active Course & Phase topics (from Home Screen course selection)
        val activeProgramId = sessionManager.getActiveProgramId()?.trim()
        val activePhaseId = sessionManager.getActiveProgramPhaseId()?.trim()

        if (!activeProgramId.isNullOrBlank()) {
            subscribeProgramAndPhase(fcm, sessionManager, userId, activeProgramId, activePhaseId)
        }
    }

    /**
     * Dynamically switches or updates the subscribed course (`newProgramId`) and phase (`newPhaseId`)
     * on Shikho's FCM server (`projects/shikho-tech`).
     * - Immediately unsubscribes (`:unsubscribe`) from any previous course or phase topics.
     * - Immediately subscribes (`:subscribe`) to the newly selected course and phase topics.
     */
    @Synchronized
    fun updateProgramAndPhase(context: Context, newProgramId: String, newPhaseId: String?) {
        val cleanProgramId = newProgramId.trim()
        if (cleanProgramId.isBlank()) return
        val cleanPhaseId = newPhaseId?.trim()?.takeIf { it.isNotBlank() }

        try {
            val appContext = context.applicationContext
            val sessionManager = SessionManager(appContext)
            if (!isGooglePlayServicesAvailable(appContext)) {
                sessionManager.setSubscribedFcmProgramId(cleanProgramId)
                if (cleanPhaseId != null) {
                    sessionManager.setSubscribedFcmPhaseId(cleanPhaseId)
                }
                return
            }

            val fcm = getShikhoFirebaseMessaging(appContext) ?: return
            val userId = sessionManager.getUserId()?.trim()

            // Ensure base topics (SHIKHO_ALL and userId) are also active
            try { fcm.subscribeToTopic(TOPIC_SHIKHO_ALL) } catch (_: Throwable) {}
            if (!userId.isNullOrBlank()) {
                try { fcm.subscribeToTopic(userId) } catch (_: Throwable) {}
            }

            subscribeProgramAndPhase(fcm, sessionManager, userId, cleanProgramId, cleanPhaseId)
        } catch (e: Throwable) {
            Log.i(TAG, "Program topic update warning: ${e.message}")
        }
    }

    @Synchronized
    private fun subscribeProgramAndPhase(
        fcm: FirebaseMessaging,
        sessionManager: SessionManager,
        userId: String?,
        newProgramId: String,
        newPhaseId: String?
    ) {
        val oldProgramId = sessionManager.getSubscribedFcmProgramId()?.trim()
        val oldPhaseId = sessionManager.getSubscribedFcmPhaseId()?.trim()
        val previouslySubscribedSet = sessionManager.getSubscribedCourseTopics().toMutableSet()

        // Include legacy tracked topics in previouslySubscribedSet so nothing is ever missed
        if (!oldProgramId.isNullOrBlank()) {
            if (!userId.isNullOrBlank()) {
                previouslySubscribedSet.add("LIVE_ShikhoNotification_UserID_${userId}_Program_${oldProgramId}")
            }
            if (!oldPhaseId.isNullOrBlank()) {
                previouslySubscribedSet.add("LIVE_ShikhoNotification_Program_${oldProgramId}_Phase_${oldPhaseId}")
            }
        }

        // Build target topics for the currently selected course
        val targetTopics = LinkedHashSet<String>()
        if (!userId.isNullOrBlank()) {
            targetTopics.add("LIVE_ShikhoNotification_UserID_${userId}_Program_${newProgramId}")
        }
        val effectivePhaseId = when {
            !newPhaseId.isNullOrBlank() -> newPhaseId
            oldProgramId == newProgramId && !oldPhaseId.isNullOrBlank() -> oldPhaseId
            else -> null
        }
        if (!effectivePhaseId.isNullOrBlank()) {
            targetTopics.add("LIVE_ShikhoNotification_Program_${newProgramId}_Phase_${effectivePhaseId}")
        }

        // 1. Unsubscribe from ALL old course/phase topics that are not in targetTopics
        val topicsToUnsubscribe = previouslySubscribedSet.filter { oldTopic ->
            if (oldProgramId != null && oldProgramId != newProgramId) {
                // Course changed: unsubscribe everything from the old course
                oldTopic !in targetTopics
            } else if (!newPhaseId.isNullOrBlank()) {
                // Same course, phase known: unsubscribe any different phase topic
                oldTopic !in targetTopics
            } else {
                // Same course, phase not passed yet: keep current course's existing phase topic
                !oldTopic.contains("_Program_${newProgramId}")
            }
        }

        for (oldTopic in topicsToUnsubscribe) {
            try {
                fcm.unsubscribeFromTopic(oldTopic)
                    .addOnSuccessListener {
                        logTerminal("INFO", "🔄 Unsubscribed old course topic: $oldTopic")
                    }
            } catch (_: Throwable) {}
            previouslySubscribedSet.remove(oldTopic)
        }

        // 2. Subscribe to the selected course's topics
        for (newTopic in targetTopics) {
            try {
                fcm.subscribeToTopic(newTopic)
                    .addOnSuccessListener {
                        logTerminal("SUCCESS", "✅ Subscribed to course topic: $newTopic")
                    }
            } catch (_: Throwable) {}
            previouslySubscribedSet.add(newTopic)
        }

        // 3. Persist current subscription state in SessionManager
        sessionManager.setSubscribedFcmProgramId(newProgramId)
        if (!effectivePhaseId.isNullOrBlank()) {
            sessionManager.setSubscribedFcmPhaseId(effectivePhaseId)
        } else if (oldProgramId != newProgramId) {
            sessionManager.setSubscribedFcmPhaseId(null)
        }
        sessionManager.setSubscribedCourseTopics(previouslySubscribedSet)
    }

    /**
     * Unsubscribes only from the active course & phase topics (e.g., when changing syllabus/class
     * or when no course is currently selected) while keeping `SHIKHO_ALL` and `{userId}`.
     */
    @Synchronized
    fun unsubscribeActiveCourseTopics(context: Context) {
        try {
            val appContext = context.applicationContext
            val sessionManager = SessionManager(appContext)
            val userId = sessionManager.getUserId()?.trim()
            val oldProgramId = sessionManager.getSubscribedFcmProgramId()?.trim()
            val oldPhaseId = sessionManager.getSubscribedFcmPhaseId()?.trim()
            val topicsToRemove = sessionManager.getSubscribedCourseTopics().toMutableSet()

            if (!oldProgramId.isNullOrBlank()) {
                if (!userId.isNullOrBlank()) {
                    topicsToRemove.add("LIVE_ShikhoNotification_UserID_${userId}_Program_${oldProgramId}")
                }
                if (!oldPhaseId.isNullOrBlank()) {
                    topicsToRemove.add("LIVE_ShikhoNotification_Program_${oldProgramId}_Phase_${oldPhaseId}")
                }
            }

            if (isGooglePlayServicesAvailable(appContext)) {
                val fcm = getShikhoFirebaseMessaging(appContext)
                if (fcm != null) {
                    for (topic in topicsToRemove) {
                        try {
                            fcm.unsubscribeFromTopic(topic)
                        } catch (_: Throwable) {}
                    }
                }
            }

            sessionManager.setSubscribedFcmProgramId(null)
            sessionManager.setSubscribedFcmPhaseId(null)
            sessionManager.setSubscribedCourseTopics(emptySet())
        } catch (e: Throwable) {
            Log.i(TAG, "unsubscribeActiveCourseTopics warning: ${e.message}")
        }
    }

    /**
     * Unsubscribes from user and course-specific topics upon logout
     */
    @Synchronized
    fun unsubscribeAll(context: Context) {
        try {
            val appContext = context.applicationContext
            val sessionManager = SessionManager(appContext)
            val userId = sessionManager.getUserId()?.trim()

            unsubscribeActiveCourseTopics(appContext)

            if (isGooglePlayServicesAvailable(appContext) && !userId.isNullOrBlank()) {
                val fcm = getShikhoFirebaseMessaging(appContext)
                try {
                    fcm?.unsubscribeFromTopic(userId)
                } catch (_: Throwable) {}
            }
            Log.d(TAG, "Unsubscribed from personalized topics on logout.")
        } catch (e: Throwable) {
            Log.i(TAG, "Optional unsubscribeFromTopics: ${e.message}")
        }
    }
}
