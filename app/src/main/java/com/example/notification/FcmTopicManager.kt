package com.example.notification

import android.util.Log
import com.example.auth.SessionManager
import com.google.firebase.messaging.FirebaseMessaging

object FcmTopicManager {
    private const val TAG = "FcmTopicManager"

    fun subscribeAllTopics(sessionManager: SessionManager) {
        try {
            val fm = FirebaseMessaging.getInstance()

            // 1. General topic
            fm.subscribeToTopic("SHIKHO_ALL")
                .addOnSuccessListener { Log.d(TAG, "Subscribed to topic: SHIKHO_ALL") }

            // 2. User specific topic
            val userId = sessionManager.getUserId()
            if (!userId.isNullOrBlank()) {
                fm.subscribeToTopic(userId)
                    .addOnSuccessListener { Log.d(TAG, "Subscribed to user topic: $userId") }
            }

            // 3. Active program topic
            val programId = sessionManager.getActiveProgramId()
            if (!userId.isNullOrBlank() && !programId.isNullOrBlank()) {
                val liveUserProgramTopic = "LIVE_ShikhoNotification_UserID_${userId}_Program_${programId}"
                fm.subscribeToTopic(liveUserProgramTopic)
                    .addOnSuccessListener { Log.d(TAG, "Subscribed to topic: $liveUserProgramTopic") }
            }

            // 4. Program + Phase topic
            val phaseId = sessionManager.getActiveProgramPhaseId()
            if (!programId.isNullOrBlank() && !phaseId.isNullOrBlank()) {
                val liveProgramPhaseTopic = "LIVE_ShikhoNotification_Program_${programId}_Phase_${phaseId}"
                fm.subscribeToTopic(liveProgramPhaseTopic)
                    .addOnSuccessListener { Log.d(TAG, "Subscribed to topic: $liveProgramPhaseTopic") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to FCM topics", e)
        }
    }

    fun subscribeProgramTopics(sessionManager: SessionManager, programId: String, phaseId: String? = null) {
        try {
            val fm = FirebaseMessaging.getInstance()
            val userId = sessionManager.getUserId()

            if (!userId.isNullOrBlank() && programId.isNotBlank()) {
                val liveUserProgramTopic = "LIVE_ShikhoNotification_UserID_${userId}_Program_${programId}"
                fm.subscribeToTopic(liveUserProgramTopic)
                    .addOnSuccessListener { Log.d(TAG, "Subscribed to topic: $liveUserProgramTopic") }
            }

            if (programId.isNotBlank() && !phaseId.isNullOrBlank()) {
                val liveProgramPhaseTopic = "LIVE_ShikhoNotification_Program_${programId}_Phase_${phaseId}"
                fm.subscribeToTopic(liveProgramPhaseTopic)
                    .addOnSuccessListener { Log.d(TAG, "Subscribed to topic: $liveProgramPhaseTopic") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to program FCM topics", e)
        }
    }
}
