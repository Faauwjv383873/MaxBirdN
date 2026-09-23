package com.example.util

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import com.example.MainActivity
import com.example.R

private const val TAG = "PipHelper"

object PipHelper {
    const val ACTION_PIP_REWIND_5 = "com.example.pip.ACTION_REWIND_5"
    const val ACTION_PIP_FORWARD_5 = "com.example.pip.ACTION_FORWARD_5"
    const val ACTION_PIP_PLAY_PAUSE = "com.example.pip.ACTION_PLAY_PAUSE"

    fun isPipSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private fun getSanitizedAspectRatio(aspectRatio: Rational?): Rational {
        if (aspectRatio == null || aspectRatio.isZero || aspectRatio.isInfinite || aspectRatio.isNaN) {
            return Rational(16, 9)
        }
        val ratio = aspectRatio.toFloat()
        // Android PiP requires aspect ratio between 1/2.39 (0.418) and 2.39/1 (2.39)
        return when {
            ratio < 0.418f -> Rational(100, 239)
            ratio > 2.39f -> Rational(239, 100)
            else -> aspectRatio
        }
    }

    fun buildPipParams(
        context: Context,
        isPlaying: Boolean,
        aspectRatio: Rational = Rational(16, 9)
    ): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        try {
            val builder = PictureInPictureParams.Builder()

            // 1. Actions: 5s Rewind, Play/Pause, 5s Forward
            val actions = ArrayList<RemoteAction>()

            // 5 Seconds Rewind Action
            val rewindIntent = Intent(ACTION_PIP_REWIND_5).setPackage(context.packageName)
            val rewindPendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                rewindIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val rewindIcon = Icon.createWithResource(context, R.drawable.ic_pip_replay_5)
            val rewindAction = RemoteAction(
                rewindIcon,
                "৫ সে. পেছনে",
                "৫ সেকেন্ড পেছনে যান",
                rewindPendingIntent
            )
            actions.add(rewindAction)

            // Play / Pause Action
            val playPauseIntent = Intent(ACTION_PIP_PLAY_PAUSE).setPackage(context.packageName)
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val playPauseIcon = if (isPlaying) {
                Icon.createWithResource(context, R.drawable.ic_pip_pause)
            } else {
                Icon.createWithResource(context, R.drawable.ic_pip_play)
            }
            val playPauseTitle = if (isPlaying) "পজ করুন" else "প্লে করুন"
            val playPauseAction = RemoteAction(
                playPauseIcon,
                playPauseTitle,
                playPauseTitle,
                playPausePendingIntent
            )
            actions.add(playPauseAction)

            // 5 Seconds Forward Action
            val forwardIntent = Intent(ACTION_PIP_FORWARD_5).setPackage(context.packageName)
            val forwardPendingIntent = PendingIntent.getBroadcast(
                context,
                103,
                forwardIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val forwardIcon = Icon.createWithResource(context, R.drawable.ic_pip_forward_5)
            val forwardAction = RemoteAction(
                forwardIcon,
                "৫ সে. সামনে",
                "৫ সেকেন্ড সামনে যান",
                forwardPendingIntent
            )
            actions.add(forwardAction)

            builder.setActions(actions)
            builder.setAspectRatio(getSanitizedAspectRatio(aspectRatio))

            // Modern Android 12+ (API 31+) PiP enhancements
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(isPlaying)
                builder.setSeamlessResizeEnabled(true)
            }

            return builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Error building PictureInPictureParams", e)
            return null
        }
    }

    fun updatePipParams(
        activity: Activity?,
        isPlaying: Boolean,
        aspectRatio: Rational = Rational(16, 9)
    ) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val params = buildPipParams(activity, isPlaying, aspectRatio)
            if (params != null) {
                activity.setPictureInPictureParams(params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating PiP params: ${e.message}")
        }
    }

    fun enterPipMode(
        activity: Activity?,
        isPlaying: Boolean,
        aspectRatio: Rational = Rational(16, 9)
    ): Boolean {
        if (activity == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return try {
                val params = buildPipParams(activity, isPlaying, aspectRatio)
                if (params != null) {
                    activity.enterPictureInPictureMode(params)
                } else {
                    activity.enterPictureInPictureMode()
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error entering PiP mode", e)
                Toast.makeText(activity, "PiP মোড চালু করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                false
            }
        } else {
            Toast.makeText(activity, "আপনার ডিভাইসে PiP মোড সমর্থিত নয়", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}

/**
 * Disposable Composable hook that registers the broadcast receiver for 5s rewind, play/pause,
 * and 5s forward while in PiP mode, and keeps PiP params in sync with player state.
 */
@Composable
fun SetupPipController(
    player: Player?,
    isPlaying: Boolean,
    aspectRatio: Rational = Rational(16, 9),
    onPipEntered: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mainActivity = activity as? MainActivity

    // Synchronize PiP parameters whenever playback status or aspect ratio changes
    LaunchedEffect(player, isPlaying, aspectRatio) {
        PipHelper.updatePipParams(activity, isPlaying, aspectRatio)
    }

    DisposableEffect(player, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (player == null || intent == null) return

                when (intent.action) {
                    PipHelper.ACTION_PIP_REWIND_5 -> {
                        val currentPos = player.currentPosition
                        val newPos = (currentPos - 5000L).coerceAtLeast(0L)
                        player.seekTo(newPos)
                        PipHelper.updatePipParams(activity, player.isPlaying, aspectRatio)
                    }
                    PipHelper.ACTION_PIP_FORWARD_5 -> {
                        val currentPos = player.currentPosition
                        val duration = player.duration
                        val maxPos = if (duration > 0L) duration else Long.MAX_VALUE
                        val newPos = (currentPos + 5000L).coerceAtMost(maxPos)
                        player.seekTo(newPos)
                        PipHelper.updatePipParams(activity, player.isPlaying, aspectRatio)
                    }
                    PipHelper.ACTION_PIP_PLAY_PAUSE -> {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        PipHelper.updatePipParams(activity, !player.isPlaying, aspectRatio)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(PipHelper.ACTION_PIP_REWIND_5)
            addAction(PipHelper.ACTION_PIP_FORWARD_5)
            addAction(PipHelper.ACTION_PIP_PLAY_PAUSE)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering PiP receiver", e)
        }

        // On Android 8.0-11.0, auto-enter PiP when home button is pressed
        if (mainActivity != null) {
            mainActivity.onUserLeaveHintListener = {
                if (player?.isPlaying == true) {
                    onPipEntered?.invoke()
                    PipHelper.enterPipMode(mainActivity, true, aspectRatio)
                }
            }
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            if (mainActivity != null) {
                mainActivity.onUserLeaveHintListener = null
            }
        }
    }
}
