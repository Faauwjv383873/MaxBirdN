package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.TrackSelectionOverride
import java.util.Locale

@OptIn(UnstableApi::class)
data class VideoTrackQuality(
    val id: String,
    val label: String,
    val height: Int,
    val bitrate: Int,
    val trackGroup: Tracks.Group? = null,
    val trackIndex: Int = 0,
    val targetStreamUrl: String? = null
)

@OptIn(UnstableApi::class)
object ShikhoPlayerManager {

    const val DEFAULT_REFERER = "https://shikho.com/"
    const val DEFAULT_USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 12; V2029 Build/SP1A.210812.003)"

    /**
     * Creates an HttpDataSourceFactory configured with Shikho's required CDN headers.
     * The `referer` header is mandatory for video playback on Shikho CDN servers.
     */
    fun createHttpDataSourceFactory(
        referer: String = DEFAULT_REFERER,
        userAgent: String = DEFAULT_USER_AGENT
    ): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(
                mapOf(
                    "referer" to referer,
                    "Referer" to referer,
                    "Origin" to "https://shikho.com",
                    "Accept-Encoding" to "identity",
                    "Connection" to "Keep-Alive"
                )
            )
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(25000)
            .setAllowCrossProtocolRedirects(true)
    }

    /**
     * Creates a MediaSource properly configured for HLS (.m3u8), standard MP4 streams, and Live streaming.
     */
    fun createMediaSource(
        url: String,
        isLive: Boolean = false,
        dataSourceFactory: DefaultHttpDataSource.Factory = createHttpDataSourceFactory()
    ): MediaSource {
        val uri = Uri.parse(url)
        val isHls = url.contains(".m3u8", ignoreCase = true) || url.contains("hls", ignoreCase = true) || isLive

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .apply {
                if (isHls) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                if (isLive) {
                    setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.02f)
                            .setMinPlaybackSpeed(0.98f)
                            .build()
                    )
                }
            }
            .build()

        return if (isHls) {
            HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
        } else {
            DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }
    }

    /**
     * Builds and configures an ExoPlayer instance with custom Shikho network properties and DefaultTrackSelector.
     */
    fun buildExoPlayer(
        context: Context,
        trackSelector: DefaultTrackSelector? = null,
        dataSourceFactory: DefaultHttpDataSource.Factory = createHttpDataSourceFactory()
    ): ExoPlayer {
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val builder = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)

        if (trackSelector != null) {
            builder.setTrackSelector(trackSelector)
        }

        return builder.build().apply {
            playWhenReady = true
        }
    }

    /**
     * Formats milliseconds into human-readable MM:SS or HH:MM:SS format with Bengali or English digits.
     */
    fun formatTime(timeMs: Long, inBengali: Boolean = false): String {
        if (timeMs <= 0) return if (inBengali) "০০:০০" else "00:00"
        val totalSeconds = (timeMs / 1000).toInt()
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        val formatted = if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }

        return if (inBengali) toBengaliDigits(formatted) else formatted
    }

    private fun toBengaliDigits(input: String): String {
        val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder()
        for (char in input) {
            if (char in '0'..'9') {
                sb.append(banglaDigits[char - '0'])
            } else {
                sb.append(char)
            }
        }
        return sb.toString()
    }
}
