package com.example.utils

import android.content.Context
import coil.decode.SvgDecoder
import coil.request.ImageRequest

object AvatarUtils {
    fun formatAvatarUrl(url: String?, nameSeed: String? = null): String {
        val seed = (nameSeed ?: "Student").trim().filter { it.isLetterOrDigit() }.ifBlank { "Student" }
        if (url.isNullOrBlank() || url.trim().lowercase() == "null") {
            return "https://api.dicebear.com/7.x/adventurer/png?seed=$seed"
        }
        var clean = url.trim()

        // Automatically upgrade any insecure http:// to https://
        if (clean.startsWith("http://", ignoreCase = true)) {
            clean = "https://" + clean.substring(7)
        }

        return when {
            clean.startsWith("https://", ignoreCase = true) || clean.startsWith("data:", ignoreCase = true) -> clean
            clean.startsWith("content://", ignoreCase = true) || clean.startsWith("file://", ignoreCase = true) -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("www.", ignoreCase = true) -> "https://$clean"
            clean.contains("cloudinary.com", ignoreCase = true) -> "https://$clean"
            clean.contains("shikho.com", ignoreCase = true) -> "https://$clean"
            clean.contains(".com/", ignoreCase = true) || clean.contains(".net/", ignoreCase = true) || clean.contains(".org/", ignoreCase = true) || clean.contains(".io/", ignoreCase = true) -> "https://$clean"
            clean.startsWith("Profile/", ignoreCase = true) || clean.startsWith("/Profile/", ignoreCase = true) -> {
                val path = clean.removePrefix("/")
                "https://res.cloudinary.com/cross-border-education-technologies-pte-ltd/image/upload/$path"
            }
            clean.startsWith("/") -> "https://cdn.shikho.com$clean"
            clean.startsWith("avatar_", ignoreCase = true) || clean.startsWith("preset_", ignoreCase = true) || clean.length < 15 -> {
                when (clean.lowercase()) {
                    "avatar_1", "1", "felix" -> "https://api.dicebear.com/7.x/adventurer/png?seed=Felix"
                    "avatar_2", "2", "aneka" -> "https://api.dicebear.com/7.x/adventurer/png?seed=Aneka"
                    "avatar_3", "3", "sam" -> "https://api.dicebear.com/7.x/adventurer/png?seed=Sam"
                    "avatar_4", "4", "milo" -> "https://api.dicebear.com/7.x/adventurer/png?seed=Milo"
                    "avatar_5", "5", "zoe" -> "https://api.dicebear.com/7.x/adventurer/png?seed=Zoe"
                    "avatar_6", "6", "robot" -> "https://api.dicebear.com/7.x/bottts/png?seed=StudentRobot"
                    else -> "https://api.dicebear.com/7.x/adventurer/png?seed=$clean"
                }
            }
            else -> "https://cdn.shikho.com/$clean"
        }
    }

    fun buildImageRequest(context: Context, url: String?, nameSeed: String? = null): ImageRequest {
        val formatted = formatAvatarUrl(url, nameSeed)
        return ImageRequest.Builder(context)
            .data(formatted)
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(true)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12; Mobile)")
            .build()
    }
}

