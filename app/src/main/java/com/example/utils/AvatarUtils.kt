package com.example.utils

import android.content.Context
import coil.request.ImageRequest

object AvatarUtils {
    fun formatAvatarUrl(url: String?): String? {
        if (url.isNullOrBlank() || url.trim().lowercase() == "null") return null
        val clean = url.trim()
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("content://") || clean.startsWith("file://") -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> "https://cdn.shikho.com$clean"
            else -> "https://cdn.shikho.com/$clean"
        }
    }

    fun buildImageRequest(context: Context, url: String?): ImageRequest? {
        val formatted = formatAvatarUrl(url) ?: return null
        return ImageRequest.Builder(context)
            .data(formatted)
            .crossfade(true)
            .allowHardware(false)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile Safari/537.36)")
            .build()
    }
}
