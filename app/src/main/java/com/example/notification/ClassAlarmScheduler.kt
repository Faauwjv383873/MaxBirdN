package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.example.api.StudentLessonItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ClassAlarmScheduler {
    private const val TAG = "ClassAlarmScheduler"
    private const val PREFS_NAME = "shikho_alarm_scheduled_prefs"
    const val CHANNEL_ID = "shikho_class_alarms_channel"
    const val CHANNEL_NAME = "ক্লাস রিমাইন্ডার ও এলার্ম"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "নির্দিষ্ট সময়ে লাইভ ক্লাস শুরুর নোটিফিকেশন"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun schedule7DayClassAlarms(context: Context, programId: String, lessons: List<StudentLessonItem>) {
        createNotificationChannel(context)
        // 1. Cancel previous alarms first
        cancelAllAlarms(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val scheduledIds = mutableSetOf<Int>()

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentTime = System.currentTimeMillis()

        for ((index, lesson) in lessons.withIndex()) {
            val startTimeStr = lesson.start_time ?: lesson.live_class?.start_time ?: continue
            val lessonTitle = lesson.title ?: lesson.live_class?.chapter_name ?: "লাইভ ক্লাস"
            val subjectName = lesson.subject_name ?: lesson.live_class?.subject_name ?: "কোর্স"
            val lessonId = lesson.id.ifBlank { lesson.live_class?.id ?: "lesson_$index" }

            try {
                val date = isoFormat.parse(startTimeStr) ?: continue
                val triggerTime = date.time

                if (triggerTime > currentTime) {
                    val requestCode = (lessonId.hashCode() + index) % 1000000
                    scheduledIds.add(requestCode)

                    val intent = Intent(context, ClassAlarmReceiver::class.java).apply {
                        putExtra("subject_name", subjectName)
                        putExtra("lesson_title", lessonTitle)
                        putExtra("lesson_id", lessonId)
                    }

                    val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        pendingIntentFlags
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                        } else {
                            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }

                    Log.d(TAG, "⏰ Scheduled alarm for $subjectName: $lessonTitle at ${Date(triggerTime)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing start time '$startTimeStr': ${e.message}")
            }
        }

        editor.putStringSet("scheduled_alarm_ids", scheduledIds.map { it.toString() }.toSet())
        editor.apply()
        Log.d(TAG, "✅ Scheduled ${scheduledIds.size} class alarms for program $programId")
    }

    fun cancelAllAlarms(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val idsStr = prefs.getStringSet("scheduled_alarm_ids", emptySet()) ?: emptySet()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (idStr in idsStr) {
            val requestCode = idStr.toIntOrNull() ?: continue
            val intent = Intent(context, ClassAlarmReceiver::class.java)
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags)
            try {
                alarmManager.cancel(pendingIntent)
            } catch (_: Exception) {}
        }

        prefs.edit().clear().apply()
        Log.d(TAG, "🗑️ Cancelled all previous class alarms")
    }
}
