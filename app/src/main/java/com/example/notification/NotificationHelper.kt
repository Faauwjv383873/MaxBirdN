package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID = "shikho_push_notifications"
    const val CHANNEL_NAME = "শিখো লাইভ নোটিফিকেশন"
    const val CHANNEL_DESC = "শিখোর সকল লাইভ ক্লাস, পরীক্ষা, রুটিন ও পাবলিক ক্যাম্পেইন নোটিফিকেশন"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager == null) {
                    Log.w(TAG, "NotificationManager is null, cannot create channel")
                    return
                }

                val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 150, 350)
                    setSound(defaultSoundUri, audioAttributes)
                    enableLights(true)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }

                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "✅ NotificationChannel '$CHANNEL_ID' successfully created / updated")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create notification channel: ${e.message}", e)
            }
        }
    }
}
