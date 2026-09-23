package com.example

import android.app.Application
import android.util.Log
import com.example.auth.SessionManager
import com.example.notification.FcmTopicManager
import com.example.notification.NotificationHelper
import com.google.firebase.FirebaseApp

class ShikhoApp : Application() {
    companion object {
        private const val TAG = "ShikhoApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 ShikhoApp onCreate initiated")

        // 1. Ensure FirebaseApp is initialized immediately on app startup
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d(TAG, "✅ FirebaseApp initialized in Application.onCreate")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize FirebaseApp in ShikhoApp: ${e.message}", e)
        }

        // 2. Ensure NotificationChannel is created right when app launches
        NotificationHelper.createNotificationChannel(this)

        // 3. Immediately synchronize and replace FCM topics in background
        val sessionManager = SessionManager(this)
        FcmTopicManager.syncAllTopics(sessionManager)
    }
}
