package com.example.ui.theme

import android.net.Uri
import android.util.Base64
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class HeaderWallpaperConfig(
    val type: String = PRESET_COSMIC,
    val customUri: String? = null,
    val customTitle: String? = null,
    val isAnimationEnabled: Boolean = true,
    val dimOpacity: Float = 0.25f,
    val customThemeCode: String? = null
) {
    companion object {
        const val PRESET_COSMIC = "preset_cosmic"
        const val PRESET_NEBULA_PORTAL = "preset_nebula_portal"
        const val PRESET_CYBERPUNK_MATRIX = "preset_cyberpunk_matrix"
        const val PRESET_SUNSET = "preset_sunset"
        const val PRESET_AURORA = "preset_aurora"
        const val PRESET_FIREFLY_FOREST = "preset_firefly_forest"
        const val PRESET_SAKURA_BREEZE = "preset_sakura_breeze"
        const val PRESET_DEEP_OCEAN = "preset_deep_ocean"
        const val PRESET_ROYAL_VELVET = "preset_royal_velvet"
        const val PRESET_SOLAR_ECLIPSE = "preset_solar_eclipse"
        const val PRESET_CYBER = "preset_cyber"
        const val PRESET_MATRIX_STREAM = "preset_matrix_stream"
        const val PRESET_LOFI_NIGHT = "preset_lofi_night"
        const val PRESET_CRYSTAL_PRISM = "preset_crystal_prism"
        const val PRESET_VOLCANIC_EMBER = "preset_volcanic_ember"
        const val PRESET_NATURE = "preset_nature"
        const val CUSTOM_IMAGE = "custom_image"
        const val CUSTOM_CODE = "custom_code"
    }
}

data class WallpaperPresetItem(
    val id: String,
    val nameBn: String,
    val nameEn: String,
    val categoryBn: String,
    val description: String,
    val previewColors: List<Color>,
    val accentColor: Color,
    val isLiveAnimated: Boolean = true
)

data class ImportedWallpaperTheme(
    val id: String = "custom_" + UUID.randomUUID().toString().take(8),
    val titleBn: String,
    val effectStyle: String, // GALAXY, AURORA, MATRIX, PLASMA, SAKURA, FIREFLY, OCEAN, PRISM
    val primaryHex: String,
    val secondaryHex: String,
    val accentHex: String,
    val particleHex: String = "#FFFFFF",
    val speedMultiplier: Int = 1,
    val rawCode: String = ""
) {
    fun toShareableCode(): String {
        if (rawCode.startsWith("MAXTHEME:", ignoreCase = true)) return rawCode
        return "MAXTHEME:v1|${titleBn.replace("|", " ")}|$effectStyle|$primaryHex|$secondaryHex|$accentHex|$particleHex|$speedMultiplier"
    }

    fun parseColors(): List<Color> {
        return listOf(
            HeaderWallpaperCodeManager.parseColorSafe(primaryHex, Color(0xFF0B1120)),
            HeaderWallpaperCodeManager.parseColorSafe(secondaryHex, Color(0xFF1E1B4B)),
            HeaderWallpaperCodeManager.parseColorSafe(accentHex, Color(0xFF38BDF8))
        )
    }

    fun parseParticleColor(): Color {
        return HeaderWallpaperCodeManager.parseColorSafe(particleHex, Color.White)
    }
}

object HeaderWallpaperCodeManager {
    val effectStyles = listOf(
        "AURORA" to "অরোরা ওয়েভ (Aurora Ribbon)",
        "GALAXY" to "কসমিক নেবুলা (Cosmic Galaxy)",
        "MATRIX" to "সাইবার গ্রিড (Cyber Matrix)",
        "PLASMA" to "প্লাজমা ভর্টেক্স (Plasma Portal)",
        "SAKURA" to "সাকুরা ব্লসম (Floating Petals)",
        "FIREFLY" to "জোনাকি কণা (Firefly Swarm)",
        "OCEAN" to "ডিপ ওশান (Bioluminescent Wave)",
        "PRISM" to "ক্রিস্টাল প্রিজম (Crystal Shards)"
    )

    val sampleCommunityCodes = listOf(
        ImportedWallpaperTheme(
            id = "sample_andro_galaxy",
            titleBn = "অ্যান্ড্রোমিডা হাইপারস্পেস",
            effectStyle = "GALAXY",
            primaryHex = "#050816",
            secondaryHex = "#1E1B4B",
            accentHex = "#A855F7",
            particleHex = "#38BDF8",
            speedMultiplier = 1
        ),
        ImportedWallpaperTheme(
            id = "sample_neon_tokyo",
            titleBn = "নিয়ন টোকিও নাইটস",
            effectStyle = "MATRIX",
            primaryHex = "#090418",
            secondaryHex = "#3B0764",
            accentHex = "#EC4899",
            particleHex = "#22D3EE",
            speedMultiplier = 1
        ),
        ImportedWallpaperTheme(
            id = "sample_golden_islamic",
            titleBn = "রয়েল নূরানি গোল্ড",
            effectStyle = "AURORA",
            primaryHex = "#022C22",
            secondaryHex = "#064E3B",
            accentHex = "#F59E0B",
            particleHex = "#FDE68A",
            speedMultiplier = 1
        ),
        ImportedWallpaperTheme(
            id = "sample_blood_moon",
            titleBn = "ক্রিমসন ব্লাড মুন",
            effectStyle = "PLASMA",
            primaryHex = "#180409",
            secondaryHex = "#4C0519",
            accentHex = "#F43F5E",
            particleHex = "#FDA4AF",
            speedMultiplier = 1
        )
    )

    fun parseColorSafe(hex: String?, fallback: Color): Color {
        if (hex.isNullOrBlank()) return fallback
        val clean = hex.trim().removePrefix("#")
        return try {
            val fullHex = when (clean.length) {
                6 -> "FF$clean"
                8 -> clean
                else -> return fallback
            }
            Color(fullHex.toLong(16))
        } catch (_: Exception) {
            fallback
        }
    }

    fun parseThemeCode(rawInput: String): Result<ImportedWallpaperTheme> {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("থিম কোড খালি রাখা যাবে না"))
        }

        return try {
            // 1. Check if Base64 encoded MAXTHEME or JSON
            val decodedCandidate = if (!trimmed.startsWith("MAXTHEME:", ignoreCase = true) &&
                !trimmed.startsWith("{") &&
                !trimmed.contains("#")
            ) {
                try {
                    String(Base64.decode(trimmed, Base64.DEFAULT)).trim()
                } catch (_: Exception) {
                    trimmed
                }
            } else {
                trimmed
            }

            // 2. MAXTHEME:v1|Title|STYLE|#Hex1|#Hex2|#Hex3|#ParticleHex|Speed
            if (decodedCandidate.startsWith("MAXTHEME:", ignoreCase = true)) {
                val payload = decodedCandidate.substringAfter(":")
                val parts = payload.split("|").map { it.trim() }
                if (parts.size >= 6) {
                    val isVersioned = parts[0].startsWith("v", ignoreCase = true)
                    val offset = if (isVersioned) 1 else 0
                    val title = parts.getOrNull(offset)?.ifBlank { "কাস্টম লাইভ থিম" } ?: "কাস্টম লাইভ থিম"
                    val style = parts.getOrNull(offset + 1)?.uppercase() ?: "AURORA"
                    val c1 = ensureHex(parts.getOrNull(offset + 2), "#090D16")
                    val c2 = ensureHex(parts.getOrNull(offset + 3), "#1E1B4B")
                    val c3 = ensureHex(parts.getOrNull(offset + 4), "#38BDF8")
                    val pCol = ensureHex(parts.getOrNull(offset + 5), "#FFFFFF")
                    val speed = parts.getOrNull(offset + 6)?.toIntOrNull()?.coerceIn(1, 3) ?: 1

                    val theme = ImportedWallpaperTheme(
                        id = "custom_" + kotlin.math.abs(decodedCandidate.hashCode()),
                        titleBn = title,
                        effectStyle = validStyle(style),
                        primaryHex = c1,
                        secondaryHex = c2,
                        accentHex = c3,
                        particleHex = pCol,
                        speedMultiplier = speed,
                        rawCode = decodedCandidate
                    )
                    return Result.success(theme)
                }
            }

            // 3. JSON format: {"title":"...", "style":"AURORA", "colors":["#...", "#...", "#..."]}
            if (decodedCandidate.startsWith("{") && decodedCandidate.endsWith("}")) {
                val obj = JSONObject(decodedCandidate)
                val title = obj.optString("title", obj.optString("name", "কাস্টম JSON থিম"))
                val style = validStyle(obj.optString("style", obj.optString("effect", "AURORA")).uppercase())
                val colorsArr = obj.optJSONArray("colors")
                val c1 = ensureHex(colorsArr?.optString(0) ?: obj.optString("primary"), "#090D16")
                val c2 = ensureHex(colorsArr?.optString(1) ?: obj.optString("secondary"), "#1E1B4B")
                val c3 = ensureHex(colorsArr?.optString(2) ?: obj.optString("accent"), "#38BDF8")
                val pCol = ensureHex(obj.optString("particle", "#FFFFFF"), "#FFFFFF")
                val speed = obj.optInt("speed", 1).coerceIn(1, 3)

                val theme = ImportedWallpaperTheme(
                    id = "custom_" + kotlin.math.abs(decodedCandidate.hashCode()),
                    titleBn = title,
                    effectStyle = style,
                    primaryHex = c1,
                    secondaryHex = c2,
                    accentHex = c3,
                    particleHex = pCol,
                    speedMultiplier = speed
                )
                return Result.success(theme.copy(rawCode = theme.toShareableCode()))
            }

            // 4. Shorthand Hex list or CSS gradient string containing #RRGGBB codes
            val hexRegex = Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")
            val foundHexes = hexRegex.findAll(decodedCandidate).map { "#" + it.groupValues[1].takeLast(6).uppercase() }.toList()
            if (foundHexes.isNotEmpty()) {
                val c1 = foundHexes.getOrElse(0) { "#090D16" }
                val c2 = foundHexes.getOrElse(1) { c1 }
                val c3 = foundHexes.getOrElse(2) { "#38BDF8" }
                val pCol = foundHexes.getOrElse(3) { "#FFFFFF" }

                val detectedStyle = effectStyles.map { it.first }.firstOrNull {
                    decodedCandidate.contains(it, ignoreCase = true)
                } ?: "AURORA"

                val customName = decodedCandidate
                    .replace(hexRegex, "")
                    .replace(Regex("[,|;:\\-_]+"), " ")
                    .trim()
                    .takeIf { it.length in 2..32 } ?: "কাস্টম কালার থিম"

                val theme = ImportedWallpaperTheme(
                    id = "custom_" + kotlin.math.abs(decodedCandidate.hashCode()),
                    titleBn = customName,
                    effectStyle = detectedStyle,
                    primaryHex = c1,
                    secondaryHex = c2,
                    accentHex = c3,
                    particleHex = pCol,
                    speedMultiplier = 1
                )
                return Result.success(theme.copy(rawCode = theme.toShareableCode()))
            }

            Result.failure(IllegalArgumentException("সঠিক থিম কোড দিন (যেমন: MAXTHEME:... অথবা #Hex কালার কোড)"))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("থিম কোডটি পড়া যায়নি: ${e.localizedMessage ?: "অকার্যকর কোড"}"))
        }
    }

    private fun ensureHex(candidate: String?, fallback: String): String {
        if (candidate.isNullOrBlank()) return fallback
        val clean = candidate.trim().removePrefix("#").takeLast(6)
        return if (clean.length == 6 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "#${clean.uppercase()}"
        } else {
            fallback
        }
    }

    private fun validStyle(style: String): String {
        val valid = setOf("AURORA", "GALAXY", "MATRIX", "PLASMA", "SAKURA", "FIREFLY", "OCEAN", "PRISM")
        return if (style.uppercase() in valid) style.uppercase() else "AURORA"
    }

    fun parseThemeListJson(rawJson: String): List<ImportedWallpaperTheme> {
        val list = mutableListOf<ImportedWallpaperTheme>()
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val item = ImportedWallpaperTheme(
                    id = obj.optString("id", "custom_$i"),
                    titleBn = obj.optString("titleBn", "কাস্টম থিম"),
                    effectStyle = obj.optString("effectStyle", "AURORA"),
                    primaryHex = obj.optString("primaryHex", "#090D16"),
                    secondaryHex = obj.optString("secondaryHex", "#1E1B4B"),
                    accentHex = obj.optString("accentHex", "#38BDF8"),
                    particleHex = obj.optString("particleHex", "#FFFFFF"),
                    speedMultiplier = obj.optInt("speedMultiplier", 1).coerceIn(1, 3),
                    rawCode = obj.optString("rawCode", "")
                )
                list.add(item.copy(rawCode = item.toShareableCode()))
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun serializeThemeListJson(themes: List<ImportedWallpaperTheme>): String {
        val arr = JSONArray()
        themes.forEach { t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("titleBn", t.titleBn)
                put("effectStyle", t.effectStyle)
                put("primaryHex", t.primaryHex)
                put("secondaryHex", t.secondaryHex)
                put("accentHex", t.accentHex)
                put("particleHex", t.particleHex)
                put("speedMultiplier", t.speedMultiplier)
                put("rawCode", t.toShareableCode())
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}

object HeaderWallpaperPresets {
    val categories = listOf(
        "সব থিম",
        "মহাকাশ ও সাই-ফাই",
        "প্রকৃতি ও অরোরা",
        "নিয়ন ও সাইবার",
        "রয়েল ও এনার্জি"
    )

    val presets = listOf(
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_COSMIC,
            nameBn = "কসমিক সুপারনোভা গ্যালাক্সি",
            nameEn = "Cosmic Supernova",
            categoryBn = "মহাকাশ ও সাই-ফাই",
            description = "ঘূর্ণায়মান নীহারিকা, জ্বলজ্বলে নক্ষত্রপুঞ্জ ও ছুটন্ত উল্কাপিণ্ড",
            previewColors = listOf(Color(0xFF040B1A), Color(0xFF0F2547), Color(0xFF2563EB)),
            accentColor = Color(0xFF38BDF8)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_NEBULA_PORTAL,
            nameBn = "কোয়ান্টাম নেবুলা পোর্টাল",
            nameEn = "Quantum Nebula Portal",
            categoryBn = "মহাকাশ ও সাই-ফাই",
            description = "মাল্টি-ডাইমেনশনাল বেগুনি-সায়ান প্লাজমা পোর্টাল ও কোয়ান্টাম রিং",
            previewColors = listOf(Color(0xFF09041A), Color(0xFF3B0764), Color(0xFF06B6D4)),
            accentColor = Color(0xFFC084FC)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_CYBERPUNK_MATRIX,
            nameBn = "সাইবারপাঙ্ক নিয়ন মেট্রিক্স",
            nameEn = "Cyberpunk Neon Matrix",
            categoryBn = "নিয়ন ও সাইবার",
            description = "থ্রি-ডি সিন্থওয়েভ নিয়ন গ্রিড, রেট্রো সান ও লেজার পালস",
            previewColors = listOf(Color(0xFF090214), Color(0xFF4A044E), Color(0xFFEC4899)),
            accentColor = Color(0xFFF472B6)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_AURORA,
            nameBn = "আর্কটিক অরোরা বোরিয়ালিস",
            nameEn = "Arctic Aurora Borealis",
            categoryBn = "প্রকৃতি ও অরোরা",
            description = "রহস্যময় সবুজ-সায়ান নর্দার্ন লাইটস ও তুষারশুভ্র পর্বত শিখর",
            previewColors = listOf(Color(0xFF02181B), Color(0xFF064E3B), Color(0xFF10B981)),
            accentColor = Color(0xFF34D399)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_FIREFLY_FOREST,
            nameBn = "জোনাকি বনের মায়াবী রাত",
            nameEn = "Enchanted Firefly Forest",
            categoryBn = "প্রকৃতি ও অরোরা",
            description = "গভীর বনের নীরব রাতে ভাসমান সোনালী জোনাকি পোকার মিটিমিটি আলো",
            previewColors = listOf(Color(0xFF041311), Color(0xFF063729), Color(0xFF84CC16)),
            accentColor = Color(0xFFFACC15)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_SAKURA_BREEZE,
            nameBn = "চেরি ব্লসম সাকুরা উইন্ড",
            nameEn = "Sakura Blossom Breeze",
            categoryBn = "প্রকৃতি ও অরোরা",
            description = "জাপানি গোধূলি আকাশে ভেসে বেড়ানো গোলাপি সাকুরা ফুলের পাপড়ি",
            previewColors = listOf(Color(0xFF1F0924), Color(0xFF701A75), Color(0xFFF472B6)),
            accentColor = Color(0xFFF9A8D4)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_DEEP_OCEAN,
            nameBn = "বায়োলুমিনেসেন্ট গভীর সমুদ্র",
            nameEn = "Bioluminescent Ocean",
            categoryBn = "প্রকৃতি ও অরোরা",
            description = "গভীর সমুদ্রের ঢেউ, সূর্যালোকের রশ্মি ও উজ্জ্বল নীল প্ল্যাঙ্কটন কণা",
            previewColors = listOf(Color(0xFF021024), Color(0xFF034078), Color(0xFF00B4D8)),
            accentColor = Color(0xFF38BDF8)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_ROYAL_VELVET,
            nameBn = "রয়েল গোল্ডেন সিল্ক ওয়েভ",
            nameEn = "Royal Golden Silk",
            categoryBn = "রয়েল ও এনার্জি",
            description = "অভিজাত অবসিডিয়ান ব্ল্যাক ও ২৪ ক্যারেট লিকুইড গোল্ডেন সিল্ক ওয়েভ",
            previewColors = listOf(Color(0xFF0F0B05), Color(0xFF422006), Color(0xFFD97706)),
            accentColor = Color(0xFFFBBF24)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_SOLAR_ECLIPSE,
            nameBn = "ক্রিমসন সোলার ইকলিপ্স",
            nameEn = "Crimson Solar Eclipse",
            categoryBn = "মহাকাশ ও সাই-ফাই",
            description = "পূর্ণগ্রাস সূর্যগ্রহণের রক্তিম করোনা বলয় ও অগ্নিশিখা আভা",
            previewColors = listOf(Color(0xFF0F0406), Color(0xFF7F1D1D), Color(0xFFF97316)),
            accentColor = Color(0xFFFB923C)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_SUNSET,
            nameBn = "গোধূলি নদী ও দিগন্ত",
            nameEn = "Sunset River Horizon",
            categoryBn = "প্রকৃতি ও অরোরা",
            description = "বাংলার রক্তিম গোধূলি, ভাসমান মেঘ, পাহাড় ও নদীর জলে সোনালী ঝিলিক",
            previewColors = listOf(Color(0xFF831843), Color(0xFFBE185D), Color(0xFFFB7185)),
            accentColor = Color(0xFFFDA4AF)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_CYBER,
            nameBn = "সাইবার গোল্ড এনার্জি",
            nameEn = "Cyber Gold Energy",
            categoryBn = "নিয়ন ও সাইবার",
            description = "বেগুনি ও গোল্ডেন মেটালিক হারমোনিক এনার্জি তরঙ্গ",
            previewColors = listOf(Color(0xFF130D26), Color(0xFF4C1D95), Color(0xFFD97706)),
            accentColor = Color(0xFFF59E0B)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_MATRIX_STREAM,
            nameBn = "ডিজিটাল এমারেল্ড স্ট্রিম",
            nameEn = "Digital Emerald Stream",
            categoryBn = "নিয়ন ও সাইবার",
            description = "হাই-টেক কোয়ান্টাম ডেটা স্ট্রিম ও এমারেল্ড লেজার পালস",
            previewColors = listOf(Color(0xFF02140B), Color(0xFF064E3B), Color(0xFF10B981)),
            accentColor = Color(0xFF34D399)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_LOFI_NIGHT,
            nameBn = "লো-ফাই ক্রিসেন্ট নাইট",
            nameEn = "Lo-Fi Crescent Night",
            categoryBn = "মহাকাশ ও সাই-ফাই",
            description = "শান্ত ইন্ডিগো রাতের আকাশে অর্ধচন্দ্র, মৃদু মেঘ ও তারার মেলা",
            previewColors = listOf(Color(0xFF090D26), Color(0xFF1E1B4B), Color(0xFF6366F1)),
            accentColor = Color(0xFFA5B4FC)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_CRYSTAL_PRISM,
            nameBn = "ক্রিস্টাল প্রিজম অরোরা",
            nameEn = "Crystal Prism",
            categoryBn = "রয়েল ও এনার্জি",
            description = "হলোগ্রাফিক ক্রিস্টাল প্রিজম ও ভাসমান আলোর প্রতিসরিত জ্যামিতি",
            previewColors = listOf(Color(0xFF0C1222), Color(0xFF1E3A8A), Color(0xFFEC4899)),
            accentColor = Color(0xFF38BDF8)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_VOLCANIC_EMBER,
            nameBn = "ফিনিক্স ভলকানিক এমবার",
            nameEn = "Phoenix Volcanic Ember",
            categoryBn = "রয়েল ও এনার্জি",
            description = "উত্তপ্ত গোল্ডেন-ক্রিমসন অগ্নিকণা ও ফিনিক্স এনার্জি আভা",
            previewColors = listOf(Color(0xFF140505), Color(0xFF7C2D12), Color(0xFFEF4444)),
            accentColor = Color(0xFFF97316)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_NATURE,
            nameBn = "নীল আকাশ ও শুভ্র মেঘ",
            nameEn = "Azure Cloudscape",
            categoryBn = "প্রকৃতি ও অরোরা",
            description = "নির্মল নীল আকাশ, সূর্যের কোমল কিরণ ও অবিরাম ভাসমান সাদা মেঘ",
            previewColors = listOf(Color(0xFF0369A1), Color(0xFF0284C7), Color(0xFF38BDF8)),
            accentColor = Color(0xFF7DD3FC)
        )
    )

    fun findById(id: String?): WallpaperPresetItem {
        return presets.find { it.id == id } ?: presets.first()
    }
}

/**
 * Animated High-Performance Live Header Background Composable.
 *
 * Mathematical Seamless Guarantee:
 * - [phase] animates linearly from `0f` to `2 * PI`. Every trigonometric function uses integer multiples
 *   of [phase] (`sin(phase)`, `cos(phase)`, `sin(2 * phase)`), so `f(2*PI) == f(0)` and `f'(2*PI) == f'(0)`.
 * - [driftProgress] animates linearly from `0f` to `1f`. Every drifting particle/cloud uses
 *   `val p = (driftProgress * integerSpeed + seedOffset) % 1f` and fades its edges with `sin(p * PI)`,
 *   so there is NEVER a visible reset or jump — the animation flows infinitely!
 */
@Composable
fun LiveHeaderBackground(
    config: HeaderWallpaperConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "SeamlessLiveWallpaperTransition")

    // Continuous 0..2π angle for all harmonic waves, orbits, and pulses (100% seamless C∞ continuity)
    val phase by if (config.isAnimationEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SeamlessWavePhase"
        )
    } else {
        remember { mutableFloatStateOf(0.8f) }
    }

    // Continuous 0..1 linear progress for drifting clouds, petals, fireflies, and matrix streams
    val driftProgress by if (config.isAnimationEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SeamlessDriftProgress"
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }

    // Derived smooth oscillation (0.45f .. 0.95f) from phase so it never desynchronizes
    val glowAlpha = remember(phase) {
        0.70f + 0.25f * sin(phase)
    }

    val parsedCustomTheme = remember(config.type, config.customThemeCode) {
        if (config.type == HeaderWallpaperConfig.CUSTOM_CODE && !config.customThemeCode.isNullOrBlank()) {
            HeaderWallpaperCodeManager.parseThemeCode(config.customThemeCode).getOrNull()
        } else {
            null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (config.type) {
            HeaderWallpaperConfig.CUSTOM_IMAGE -> {
                if (!config.customUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(config.customUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Custom Header Wallpaper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    val clampDim = config.dimOpacity.coerceIn(0.05f, 0.85f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = (clampDim * 0.85f).coerceIn(0f, 0.9f)),
                                        Color.Black.copy(alpha = (clampDim * 1.25f).coerceIn(0.15f, 0.92f))
                                    )
                                )
                            )
                    )
                } else {
                    CosmicSupernovaCanvas(phase, driftProgress, glowAlpha)
                }
            }

            HeaderWallpaperConfig.CUSTOM_CODE -> {
                if (parsedCustomTheme != null) {
                    CustomCodeWallpaperCanvas(
                        theme = parsedCustomTheme,
                        phase = phase,
                        driftProgress = driftProgress,
                        glowAlpha = glowAlpha
                    )
                } else {
                    CosmicSupernovaCanvas(phase, driftProgress, glowAlpha)
                }
            }

            HeaderWallpaperConfig.PRESET_NEBULA_PORTAL ->
                QuantumNebulaPortalCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_CYBERPUNK_MATRIX ->
                CyberpunkNeonMatrixCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_SUNSET ->
                SunsetBloomCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_AURORA ->
                ArcticAuroraCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_FIREFLY_FOREST ->
                EnchantedFireflyForestCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_SAKURA_BREEZE ->
                SakuraBlossomBreezeCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_DEEP_OCEAN ->
                BioluminescentOceanCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_ROYAL_VELVET ->
                RoyalGoldenSilkCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_SOLAR_ECLIPSE ->
                CrimsonSolarEclipseCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_CYBER ->
                CyberGoldCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_MATRIX_STREAM ->
                DigitalEmeraldStreamCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_LOFI_NIGHT ->
                LoFiCrescentNightCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_CRYSTAL_PRISM ->
                CrystalPrismCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_VOLCANIC_EMBER ->
                PhoenixVolcanicEmberCanvas(phase, driftProgress, glowAlpha)

            HeaderWallpaperConfig.PRESET_NATURE ->
                AzureSkiesCanvas(phase, driftProgress, glowAlpha)

            else ->
                CosmicSupernovaCanvas(phase, driftProgress, glowAlpha)
        }

        // Subtle bottom vignette for crisp text readability on all presets
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.24f)
                        )
                    )
                )
        )
    }
}

// ============================================================================
// SEAMLESS INFINITE LIVE WALLPAPER CANVASES (60 FPS HARDWARE ACCELERATED)
// ============================================================================

@Composable
private fun CosmicSupernovaCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF030914), Color(0xFF07162C), Color(0xFF0E2954))
            )
        )

        // Orbiting Nebula Core 1 (Seamless circular Lissajous path)
        val orb1Center = Offset(
            x = w * (0.76f + 0.06f * cos(phase)),
            y = h * (0.32f + 0.08f * sin(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.42f * glowAlpha),
                    Color(0xFF6366F1).copy(alpha = 0.18f * glowAlpha),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = w * 0.52f
            ),
            radius = w * 0.52f,
            center = orb1Center
        )

        // Orbiting Nebula Core 2
        val orb2Center = Offset(
            x = w * (0.22f + 0.05f * sin(phase)),
            y = h * (0.72f + 0.07f * cos(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFA855F7).copy(alpha = 0.32f * glowAlpha),
                    Color(0xFF3B82F6).copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = w * 0.46f
            ),
            radius = w * 0.46f,
            center = orb2Center
        )

        // Flowing Galactic Dust Ribbon
        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.58f,
            amplitude = h * 0.10f,
            colorStart = Color(0xFF38BDF8).copy(alpha = 0.18f),
            colorEnd = Color(0xFFC084FC).copy(alpha = 0.22f)
        )

        // Twinkling Starfield (Integer harmonics of phase -> 100% seamless)
        val stars = listOf(
            Triple(0.10f, 0.22f, 2.0f), Triple(0.24f, 0.14f, 2.6f),
            Triple(0.38f, 0.32f, 1.6f), Triple(0.54f, 0.18f, 2.2f),
            Triple(0.68f, 0.26f, 2.8f), Triple(0.86f, 0.20f, 2.1f),
            Triple(0.92f, 0.48f, 1.8f), Triple(0.74f, 0.68f, 2.4f),
            Triple(0.46f, 0.72f, 1.7f), Triple(0.18f, 0.62f, 2.3f),
            Triple(0.31f, 0.84f, 1.5f), Triple(0.62f, 0.48f, 2.0f)
        )
        stars.forEachIndexed { idx, (rx, ry, rad) ->
            val twinkle = (sin(phase * 2f + idx * 1.1f) + 1f) * 0.5f
            val center = Offset(w * rx, h * ry)
            drawCircle(
                color = Color.White.copy(alpha = 0.25f + twinkle * 0.7f),
                radius = rad * (0.8f + twinkle * 0.5f),
                center = center
            )
            if (idx % 3 == 0 && twinkle > 0.6f) {
                val flare = rad * 3.2f * twinkle
                drawLine(
                    color = Color(0xFFBAE6FD).copy(alpha = 0.45f * twinkle),
                    start = Offset(center.x - flare, center.y),
                    end = Offset(center.x + flare, center.y),
                    strokeWidth = 1.2f
                )
                drawLine(
                    color = Color(0xFFBAE6FD).copy(alpha = 0.45f * twinkle),
                    start = Offset(center.x, center.y - flare),
                    end = Offset(center.x, center.y + flare),
                    strokeWidth = 1.2f
                )
            }
        }

        // Seamless Shooting Star / Comet across top sky
        val cometP = (driftProgress * 2f) % 1f
        val cometEnvelope = sin(cometP * PI.toFloat()).coerceIn(0f, 1f)
        val cometX = w * (0.1f + cometP * 0.85f)
        val cometY = h * (0.12f + cometP * 0.35f)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF38BDF8).copy(alpha = 0.55f * cometEnvelope),
                    Color.White.copy(alpha = 0.9f * cometEnvelope)
                ),
                start = Offset(cometX - 55f, cometY - 22f),
                end = Offset(cometX, cometY)
            ),
            start = Offset(cometX - 55f, cometY - 22f),
            end = Offset(cometX, cometY),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun QuantumNebulaPortalCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF070312), Color(0xFF1A0836), Color(0xFF0F172A))
            )
        )

        val portalCenter = Offset(w * 0.78f, h * 0.42f)

        // Concentric Quantum Energy Rings
        for (i in 1..4) {
            val ringPhase = phase + i * 0.8f
            val radiusX = w * (0.12f * i + 0.015f * sin(ringPhase))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        if (i % 2 == 0) Color(0xFF06B6D4).copy(alpha = 0.22f * glowAlpha)
                        else Color(0xFFC084FC).copy(alpha = 0.25f * glowAlpha),
                        Color.Transparent
                    ),
                    center = portalCenter,
                    radius = radiusX
                ),
                radius = radiusX,
                center = portalCenter
            )
            drawCircle(
                color = if (i % 2 == 0) Color(0xFF22D3EE).copy(alpha = 0.28f)
                else Color(0xFFA855F7).copy(alpha = 0.28f),
                radius = radiusX * 0.75f,
                center = Offset(
                    portalCenter.x + cos(ringPhase) * 6f,
                    portalCenter.y + sin(ringPhase) * 6f
                ),
                style = Stroke(width = 1.6f)
            )
        }

        // Orbiting Quantum Particles
        for (i in 0 until 10) {
            val angle = phase + (i * 2.0 * PI / 10.0).toFloat()
            val orbitR = w * (0.14f + (i % 3) * 0.09f)
            val px = portalCenter.x + cos(angle) * orbitR
            val py = portalCenter.y + sin(angle) * orbitR * 0.65f
            drawCircle(
                color = if (i % 2 == 0) Color(0xFF67E8F9) else Color(0xFFF0ABFC),
                radius = 2.6f,
                center = Offset(px, py)
            )
        }

        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.72f,
            amplitude = h * 0.11f,
            colorStart = Color(0xFF9333EA).copy(alpha = 0.28f),
            colorEnd = Color(0xFF06B6D4).copy(alpha = 0.30f)
        )
    }
}

@Composable
private fun CyberpunkNeonMatrixCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF070210), Color(0xFF1F0638), Color(0xFF4A044E))
            )
        )

        val horizonY = h * 0.56f

        // Synthwave Retro Sun
        val sunCenter = Offset(w * 0.78f, horizonY - h * 0.06f)
        val sunRadius = h * 0.28f
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFDE047).copy(alpha = 0.85f),
                    Color(0xFFF97316).copy(alpha = 0.85f),
                    Color(0xFFEC4899).copy(alpha = 0.90f)
                ),
                startY = sunCenter.y - sunRadius,
                endY = sunCenter.y + sunRadius
            ),
            radius = sunRadius,
            center = sunCenter
        )

        // Glowing Horizon Line
        drawLine(
            color = Color(0xFF22D3EE).copy(alpha = 0.8f * glowAlpha),
            start = Offset(0f, horizonY),
            end = Offset(w, horizonY),
            strokeWidth = 2.5f
        )

        // Perspective Vertical Grid Lines
        val vanishX = w * 0.5f
        for (i in -6..6) {
            val bottomX = vanishX + i * (w * 0.14f)
            drawLine(
                color = Color(0xFFEC4899).copy(alpha = 0.32f),
                start = Offset(vanishX + i * (w * 0.03f), horizonY),
                end = Offset(bottomX, h),
                strokeWidth = 1.4f
            )
        }

        // Seamless Scrolling Horizontal Grid Lines
        val gridCount = 6
        for (i in 0 until gridCount) {
            val p = ((driftProgress * 3f + i.toFloat() / gridCount) % 1f)
            // Exponential depth spacing for true 3D perspective
            val y = horizonY + (h - horizonY) * (p * p)
            val alpha = sin(p * PI.toFloat()).coerceIn(0f, 1f) * 0.55f
            drawLine(
                color = Color(0xFF22D3EE).copy(alpha = alpha),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.5f
            )
        }
    }
}

@Composable
private fun ArcticAuroraCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF021017), Color(0xFF04282E), Color(0xFF064E3B))
            )
        )

        // Multi-layered Northern Lights Ribbons
        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.36f,
            amplitude = h * 0.13f,
            colorStart = Color(0xFF10B981).copy(alpha = 0.42f * glowAlpha),
            colorEnd = Color(0xFF06B6D4).copy(alpha = 0.35f * glowAlpha)
        )
        drawFlowingRibbon(
            phase = phase + 1.8f,
            baseYRatio = 0.52f,
            amplitude = h * 0.10f,
            colorStart = Color(0xFF34D399).copy(alpha = 0.28f * glowAlpha),
            colorEnd = Color(0xFFA855F7).copy(alpha = 0.25f * glowAlpha)
        )

        // Distant Arctic Mountain Silhouette at Bottom
        val mountainPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.82f)
            lineTo(w * 0.18f, h * 0.68f)
            lineTo(w * 0.34f, h * 0.79f)
            lineTo(w * 0.55f, h * 0.63f)
            lineTo(w * 0.76f, h * 0.77f)
            lineTo(w * 0.90f, h * 0.69f)
            lineTo(w, h * 0.78f)
            lineTo(w, h)
            close()
        }
        drawPath(mountainPath, color = Color(0xFF021619).copy(alpha = 0.72f))
    }
}

@Composable
private fun EnchantedFireflyForestCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF03110E), Color(0xFF062920), Color(0xFF0B3B2D))
            )
        )

        // Soft Emerald Moonbeams
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF34D399).copy(alpha = 0.24f * glowAlpha), Color.Transparent),
                center = Offset(w * 0.82f, h * 0.22f),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.82f, h * 0.22f)
        )

        // Seamless Floating Golden Fireflies
        val fireflySeeds = listOf(
            Triple(0.12f, 0.75f, 0.05f), Triple(0.26f, 0.55f, 0.22f),
            Triple(0.39f, 0.80f, 0.41f), Triple(0.52f, 0.48f, 0.63f),
            Triple(0.67f, 0.70f, 0.17f), Triple(0.79f, 0.58f, 0.82f),
            Triple(0.88f, 0.76f, 0.34f), Triple(0.45f, 0.32f, 0.91f),
            Triple(0.19f, 0.38f, 0.54f), Triple(0.63f, 0.36f, 0.73f)
        )
        fireflySeeds.forEachIndexed { index, (seedX, seedY, offset) ->
            val p = (driftProgress + offset) % 1f
            val env = sin(p * PI.toFloat()).coerceIn(0f, 1f)
            val pulse = (sin(phase * 2f + index) + 1f) * 0.5f
            val fx = w * seedX + sin(phase + index) * 18f
            val fy = h * (seedY - p * 0.38f)
            val alpha = (0.35f + 0.65f * pulse) * env

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFDE047).copy(alpha = alpha * 0.75f),
                        Color(0xFF84CC16).copy(alpha = alpha * 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(fx, fy),
                    radius = 14f
                ),
                radius = 14f,
                center = Offset(fx, fy)
            )
            drawCircle(
                color = Color(0xFFFEF08A).copy(alpha = alpha),
                radius = 2.8f,
                center = Offset(fx, fy)
            )
        }
    }
}

@Composable
private fun SakuraBlossomBreezeCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1A0826), Color(0xFF4A1259), Color(0xFF831843))
            )
        )

        // Warm Pink Moon Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFF9A8D4).copy(alpha = 0.38f * glowAlpha),
                    Color(0xFFF43F5E).copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(w * 0.80f, h * 0.28f),
                radius = w * 0.45f
            ),
            radius = w * 0.45f,
            center = Offset(w * 0.80f, h * 0.28f)
        )

        // Seamless Drifting Sakura Petals
        for (i in 0 until 12) {
            val seed = i / 12f
            val p = (driftProgress + seed) % 1f
            val env = sin(p * PI.toFloat()).coerceIn(0f, 1f)
            val px = w * (-0.05f + p * 1.1f)
            val py = h * (((i * 0.19f) % 0.85f) + 0.08f * sin(phase + i))
            val petalColor = if (i % 2 == 0) Color(0xFFFBCFE8) else Color(0xFFF472B6)
            drawOval(
                color = petalColor.copy(alpha = 0.75f * env),
                topLeft = Offset(px, py),
                size = Size(10f + (i % 3) * 3f, 6f + (i % 2) * 2f)
            )
        }
    }
}

@Composable
private fun BioluminescentOceanCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF011627), Color(0xFF022C43), Color(0xFF054A91))
            )
        )

        // Layered Ocean Waves
        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.50f,
            amplitude = h * 0.08f,
            colorStart = Color(0xFF00B4D8).copy(alpha = 0.25f),
            colorEnd = Color(0xFF0077B6).copy(alpha = 0.30f)
        )
        drawFlowingRibbon(
            phase = phase + 2.1f,
            baseYRatio = 0.68f,
            amplitude = h * 0.09f,
            colorStart = Color(0xFF48CAE4).copy(alpha = 0.32f * glowAlpha),
            colorEnd = Color(0xFF023E8A).copy(alpha = 0.35f)
        )

        // Rising Bioluminescent Bubbles
        for (i in 0 until 10) {
            val p = (driftProgress * 2f + i * 0.1f) % 1f
            val env = sin(p * PI.toFloat()).coerceIn(0f, 1f)
            val bx = w * (0.08f + i * 0.09f) + sin(phase + i) * 10f
            val by = h * (0.95f - p * 0.80f)
            drawCircle(
                color = Color(0xFF67E8F9).copy(alpha = 0.65f * env),
                radius = 2.5f + (i % 3) * 1.4f,
                center = Offset(bx, by)
            )
        }
    }
}

@Composable
private fun RoyalGoldenSilkCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF090704), Color(0xFF1C1308), Color(0xFF33200A))
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFF59E0B).copy(alpha = 0.32f * glowAlpha),
                    Color(0xFFB45309).copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(w * 0.78f, h * 0.28f),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.78f, h * 0.28f)
        )

        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.48f,
            amplitude = h * 0.11f,
            colorStart = Color(0xFFFBBF24).copy(alpha = 0.30f * glowAlpha),
            colorEnd = Color(0xFFD97706).copy(alpha = 0.18f)
        )
        drawFlowingRibbon(
            phase = phase + 1.5f,
            baseYRatio = 0.68f,
            amplitude = h * 0.09f,
            colorStart = Color(0xFFFDE68A).copy(alpha = 0.22f * glowAlpha),
            colorEnd = Color(0xFF92400E).copy(alpha = 0.28f)
        )
    }
}

@Composable
private fun CrimsonSolarEclipseCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF090204), Color(0xFF28070C), Color(0xFF4C0519))
            )
        )

        val eclipseCenter = Offset(w * 0.78f, h * 0.40f)
        val eclipseR = h * 0.27f

        // Corona Outer Flare
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFB923C).copy(alpha = 0.65f * glowAlpha),
                    Color(0xFFE11D48).copy(alpha = 0.35f * glowAlpha),
                    Color.Transparent
                ),
                center = eclipseCenter,
                radius = eclipseR * 2.2f
            ),
            radius = eclipseR * 2.2f,
            center = eclipseCenter
        )

        // Dark Moon Disc Blocking the Sun
        drawCircle(
            color = Color(0xFF090204),
            radius = eclipseR * 0.88f,
            center = eclipseCenter
        )

        // Bright Diamond Ring Edge
        drawCircle(
            color = Color(0xFFFDE047).copy(alpha = 0.75f * glowAlpha),
            radius = eclipseR * 0.90f,
            center = eclipseCenter,
            style = Stroke(width = 2.2f)
        )
    }
}

@Composable
private fun SunsetBloomCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF831843),
                    Color(0xFFBE185D),
                    Color(0xFFDB2777),
                    Color(0xFFF43F5E),
                    Color(0xFFFB7185)
                )
            )
        )

        // Breathing Golden Sun
        val sunY = h * (0.48f + 0.03f * sin(phase))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFDE047).copy(alpha = 0.58f * glowAlpha),
                    Color(0xFFF43F5E).copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = Offset(w * 0.32f, sunY),
                radius = w * 0.42f
            ),
            radius = w * 0.42f,
            center = Offset(w * 0.32f, sunY)
        )

        // 100% Seamless Drifting Clouds
        drawSeamlessCloud(driftProgress, seedOffset = 0.0f, yRatio = 0.22f, scale = 1.1f, baseAlpha = 0.20f)
        drawSeamlessCloud(driftProgress, seedOffset = 0.5f, yRatio = 0.38f, scale = 0.88f, baseAlpha = 0.15f)

        // Rolling Hills
        val hillPathBack = Path().apply {
            moveTo(0f, h * 0.65f)
            cubicTo(w * 0.25f, h * 0.55f, w * 0.45f, h * 0.72f, w * 0.7f, h * 0.60f)
            cubicTo(w * 0.85f, h * 0.52f, w * 0.95f, h * 0.58f, w, h * 0.55f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hillPathBack, color = Color(0xFF9D174D).copy(alpha = 0.45f))

        val hillPathFront = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.3f, h * 0.68f, w * 0.55f, h * 0.85f, w * 0.8f, h * 0.72f)
            cubicTo(w * 0.92f, h * 0.66f, w * 0.98f, h * 0.70f, w, h * 0.68f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hillPathFront, color = Color(0xFF701A75).copy(alpha = 0.65f))

        // Shimmering River Reflection
        val riverPath = Path().apply {
            moveTo(w * 0.2f, h)
            cubicTo(
                w * 0.4f, h * (0.86f + 0.02f * sin(phase)),
                w * 0.6f, h * (0.91f + 0.02f * cos(phase)),
                w * 0.76f, h
            )
            close()
        }
        drawPath(riverPath, color = Color(0xFFFDE047).copy(alpha = 0.28f * glowAlpha))
    }
}

@Composable
private fun CyberGoldCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0F0B24), Color(0xFF1B113B), Color(0xFF2E1065))
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.38f * glowAlpha), Color.Transparent),
                center = Offset(w * (0.82f + 0.04f * cos(phase)), h * (0.26f + 0.04f * sin(phase))),
                radius = w * 0.58f
            ),
            radius = w * 0.58f,
            center = Offset(w * (0.82f + 0.04f * cos(phase)), h * (0.26f + 0.04f * sin(phase)))
        )

        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.68f,
            amplitude = h * 0.09f,
            colorStart = Color(0xFFD97706).copy(alpha = 0.35f),
            colorEnd = Color(0xFF7C3AED).copy(alpha = 0.50f)
        )
    }
}

@Composable
private fun DigitalEmeraldStreamCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF020F09), Color(0xFF042619), Color(0xFF064E3B))
            )
        )

        // Seamless Falling Matrix Light Streams
        val columns = 12
        for (c in 0 until columns) {
            val p = (driftProgress * 2f + (c * 0.17f)) % 1f
            val env = sin(p * PI.toFloat()).coerceIn(0f, 1f)
            val x = w * (0.06f + c * 0.08f)
            val headY = h * p
            val tailY = (headY - h * 0.32f).coerceAtLeast(0f)

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF10B981).copy(alpha = 0.45f * env),
                        Color(0xFF6EE7B7).copy(alpha = 0.85f * env)
                    ),
                    startY = tailY,
                    endY = headY
                ),
                start = Offset(x, tailY),
                end = Offset(x, headY),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun LoFiCrescentNightCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF070A1E), Color(0xFF161B40), Color(0xFF312E81))
            )
        )

        // Glowing Crescent Moon
        val moonCenter = Offset(w * 0.80f, h * 0.28f)
        val moonR = h * 0.16f
        drawCircle(
            color = Color(0xFFFEF08A).copy(alpha = 0.90f),
            radius = moonR,
            center = moonCenter
        )
        drawCircle(
            color = Color(0xFF111536),
            radius = moonR * 0.85f,
            center = Offset(moonCenter.x - moonR * 0.35f, moonCenter.y - moonR * 0.2f)
        )

        // Soft Night Clouds
        drawSeamlessCloud(driftProgress, seedOffset = 0.15f, yRatio = 0.36f, scale = 1.0f, baseAlpha = 0.14f)
        drawSeamlessCloud(driftProgress, seedOffset = 0.65f, yRatio = 0.58f, scale = 0.82f, baseAlpha = 0.11f)
    }
}

@Composable
private fun CrystalPrismCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF090D1C), Color(0xFF1E1B4B), Color(0xFF3B0764))
            )
        )

        val colors = listOf(Color(0xFF38BDF8), Color(0xFFEC4899), Color(0xFFA855F7), Color(0xFF2DD4BF))
        for (i in 0 until 6) {
            val angle = phase + i * 1.04f
            val cx = w * (0.18f + i * 0.14f) + cos(angle) * 14f
            val cy = h * (0.35f + (i % 2) * 0.25f) + sin(angle) * 12f
            val r = h * (0.22f + (i % 3) * 0.06f)

            val shard = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.75f, cy)
                lineTo(cx, cy + r)
                lineTo(cx - r * 0.75f, cy)
                close()
            }
            drawPath(
                shard,
                color = colors[i % colors.size].copy(alpha = 0.16f * glowAlpha)
            )
        }
    }
}

@Composable
private fun PhoenixVolcanicEmberCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0E0404), Color(0xFF2E0909), Color(0xFF7C2D12))
            )
        )

        // Bottom Magma Glow
        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.72f,
            amplitude = h * 0.08f,
            colorStart = Color(0xFFEF4444).copy(alpha = 0.38f * glowAlpha),
            colorEnd = Color(0xFFF59E0B).copy(alpha = 0.42f * glowAlpha)
        )

        // Rising Glowing Embers
        for (i in 0 until 12) {
            val p = (driftProgress * 2f + i * 0.083f) % 1f
            val env = sin(p * PI.toFloat()).coerceIn(0f, 1f)
            val ex = w * (0.07f + i * 0.078f) + sin(phase * 2f + i) * 12f
            val ey = h * (0.95f - p * 0.85f)
            drawCircle(
                color = if (i % 2 == 0) Color(0xFFFBBF24).copy(alpha = 0.85f * env)
                else Color(0xFFF97316).copy(alpha = 0.75f * env),
                radius = 2.2f + (i % 3) * 1.2f,
                center = Offset(ex, ey)
            )
        }
    }
}

@Composable
private fun AzureSkiesCanvas(phase: Float, driftProgress: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0369A1), Color(0xFF0284C7), Color(0xFF38BDF8))
            )
        )

        // Soft Sun Glow in Top Right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFEF9C3).copy(alpha = 0.42f * glowAlpha),
                    Color.Transparent
                ),
                center = Offset(w * 0.85f, h * 0.18f),
                radius = w * 0.45f
            ),
            radius = w * 0.45f,
            center = Offset(w * 0.85f, h * 0.18f)
        )

        // 100% Seamless Infinite Drifting Clouds
        drawSeamlessCloud(driftProgress, seedOffset = 0.0f, yRatio = 0.28f, scale = 1.2f, baseAlpha = 0.34f)
        drawSeamlessCloud(driftProgress, seedOffset = 0.35f, yRatio = 0.54f, scale = 0.92f, baseAlpha = 0.26f)
        drawSeamlessCloud(driftProgress, seedOffset = 0.70f, yRatio = 0.38f, scale = 0.78f, baseAlpha = 0.22f)
    }
}

@Composable
private fun CustomCodeWallpaperCanvas(
    theme: ImportedWallpaperTheme,
    phase: Float,
    driftProgress: Float,
    glowAlpha: Float
) {
    val colors = remember(theme) { theme.parseColors() }
    val particleCol = remember(theme) { theme.parseParticleColor() }
    val speed = theme.speedMultiplier.coerceIn(1, 3)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(colors = colors)
        )

        val effPhase = phase * speed
        val effDrift = (driftProgress * speed) % 1f

        // Ambient radial glow using accent color
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.last().copy(alpha = 0.42f * glowAlpha), Color.Transparent),
                center = Offset(w * (0.76f + 0.06f * cos(effPhase)), h * (0.32f + 0.06f * sin(effPhase))),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * (0.76f + 0.06f * cos(effPhase)), h * (0.32f + 0.06f * sin(effPhase)))
        )

        when (theme.effectStyle.uppercase()) {
            "MATRIX" -> {
                for (c in 0 until 10) {
                    val p = (effDrift + c * 0.1f) % 1f
                    val env = sin(p * PI.toFloat()).coerceIn(0f, 1f)
                    val x = w * (0.08f + c * 0.09f)
                    val headY = h * p
                    val tailY = (headY - h * 0.30f).coerceAtLeast(0f)
                    drawLine(
                        color = particleCol.copy(alpha = 0.65f * env),
                        start = Offset(x, tailY),
                        end = Offset(x, headY),
                        strokeWidth = 2.4f,
                        cap = StrokeCap.Round
                    )
                }
            }

            "GALAXY", "PLASMA" -> {
                for (i in 0 until 10) {
                    val angle = effPhase + i * 0.62f
                    val ox = w * 0.72f + cos(angle) * (w * 0.08f + i * 10f)
                    val oy = h * 0.42f + sin(angle) * (h * 0.08f + i * 5f)
                    drawCircle(
                        color = particleCol.copy(alpha = 0.70f),
                        radius = 2.5f + (i % 3),
                        center = Offset(ox, oy)
                    )
                }
                drawFlowingRibbon(
                    phase = effPhase,
                    baseYRatio = 0.64f,
                    amplitude = h * 0.10f,
                    colorStart = colors.last().copy(alpha = 0.32f),
                    colorEnd = particleCol.copy(alpha = 0.24f)
                )
            }

            "SAKURA", "FIREFLY" -> {
                for (i in 0 until 12) {
                    val p = (effDrift + i / 12f) % 1f
                    val env = sin(p * PI.toFloat()).coerceIn(0f, 1f)
                    val px = w * (0.06f + ((i * 0.083f + p * 0.3f) % 0.9f))
                    val py = h * (0.90f - p * 0.75f)
                    drawCircle(
                        color = particleCol.copy(alpha = 0.80f * env),
                        radius = 3.2f,
                        center = Offset(px + sin(effPhase + i) * 10f, py)
                    )
                }
            }

            else -> { // AURORA, OCEAN, PRISM
                drawFlowingRibbon(
                    phase = effPhase,
                    baseYRatio = 0.44f,
                    amplitude = h * 0.12f,
                    colorStart = colors.last().copy(alpha = 0.38f * glowAlpha),
                    colorEnd = particleCol.copy(alpha = 0.26f * glowAlpha)
                )
                drawFlowingRibbon(
                    phase = effPhase + 1.7f,
                    baseYRatio = 0.66f,
                    amplitude = h * 0.09f,
                    colorStart = colors[1].copy(alpha = 0.35f),
                    colorEnd = colors.last().copy(alpha = 0.30f)
                )
            }
        }
    }
}

private fun DrawScope.drawFlowingRibbon(
    phase: Float,
    baseYRatio: Float,
    amplitude: Float,
    colorStart: Color,
    colorEnd: Color
) {
    val w = size.width
    val h = size.height
    val baseY = h * baseYRatio

    val ribbonPath = Path().apply {
        moveTo(0f, baseY + sin(phase) * amplitude)
        val steps = 24
        for (i in 1..steps) {
            val ratio = i.toFloat() / steps
            val x = w * ratio
            val y = baseY + sin(ratio * 2f * PI.toFloat() + phase) * amplitude
            lineTo(x, y)
        }
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        path = ribbonPath,
        brush = Brush.horizontalGradient(listOf(colorStart, colorEnd, colorStart))
    )
}

/**
 * Draws a cloud that drifts across the header width and fades smoothly at both boundaries
 * using `sin(p * PI)` so there is NEVER a visible wrap or jump.
 */
private fun DrawScope.drawSeamlessCloud(
    driftProgress: Float,
    seedOffset: Float,
    yRatio: Float,
    scale: Float,
    baseAlpha: Float
) {
    val w = size.width
    val h = size.height
    val p = (driftProgress + seedOffset) % 1f
    val edgeFade = sin(p * PI.toFloat()).coerceIn(0f, 1f)
    val margin = 48.dp.toPx() * scale
    val x = -margin + p * (w + 2f * margin)
    val y = h * yRatio
    val color = Color.White.copy(alpha = baseAlpha * edgeFade)

    val r = 24.dp.toPx() * scale
    drawCircle(color, radius = r, center = Offset(x, y))
    drawCircle(color, radius = r * 0.8f, center = Offset(x - r * 0.7f, y + r * 0.2f))
    drawCircle(color, radius = r * 0.85f, center = Offset(x + r * 0.7f, y + r * 0.2f))
    drawCircle(color, radius = r * 0.65f, center = Offset(x + r * 1.3f, y + r * 0.3f))
}
