package com.example.util

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.Composable
import androidx.media3.exoplayer.ExoPlayer

object PipHelper {
    fun enterPipMode(activity: Activity?, isPlaying: Boolean, aspectRatio: Rational = Rational(16, 9)) {
        if (activity == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()
                activity.enterPictureInPictureMode(params)
            } catch (e: Exception) {
                android.util.Log.e("PipHelper", "Error entering PiP mode: ${e.message}")
            }
        }
    }
}

@Composable
fun SetupPipController(
    player: ExoPlayer?,
    isPlaying: Boolean,
    aspectRatio: Rational = Rational(16, 9),
    onPipEntered: () -> Unit = {}
) {
    // Helper composable to maintain PiP state and playback
}
