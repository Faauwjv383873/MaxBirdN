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
        "মহাকাশ",
        "প্রকৃতি",
        "সাইবার ও রয়েল"
    )

    val presets = listOf(
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_COSMIC,
            nameBn = "নীহারিকা গ্যালাক্সি",
            nameEn = "Cosmic Nebula",
            categoryBn = "মহাকাশ",
            description = "ঘূর্ণায়মান নীহারিকা ও মিটিমিটি তারার অবিরাম মহাজাগতিক আবহ",
            previewColors = listOf(Color(0xFF040B1A), Color(0xFF0F2547), Color(0xFF2563EB)),
            accentColor = Color(0xFF38BDF8)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_AURORA,
            nameBn = "নর্দার্ন অরোরা",
            nameEn = "Arctic Aurora",
            categoryBn = "প্রকৃতি",
            description = "রহস্যময় সবুজ-সায়ান অরোরা আলোর তরঙ্গ ও পাহাড়ের সিলুয়েট",
            previewColors = listOf(Color(0xFF02181B), Color(0xFF064E3B), Color(0xFF10B981)),
            accentColor = Color(0xFF34D399)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_CYBERPUNK_MATRIX,
            nameBn = "সাইবার নিয়ন",
            nameEn = "Cyber Neon",
            categoryBn = "সাইবার ও রয়েল",
            description = "থ্রি-ডি সিন্থওয়েভ নিয়ন গ্রিড, রেট্রো সান ও লেজার ওয়েভ",
            previewColors = listOf(Color(0xFF090214), Color(0xFF4A044E), Color(0xFFEC4899)),
            accentColor = Color(0xFFF472B6)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_FIREFLY_FOREST,
            nameBn = "জোনাকি অরণ্য",
            nameEn = "Firefly Forest",
            categoryBn = "প্রকৃতি",
            description = "গভীর বনের নীরব রাতে ভাসমান সোনালী জোনাকি পোকার মায়াবী আলো",
            previewColors = listOf(Color(0xFF041311), Color(0xFF063729), Color(0xFF84CC16)),
            accentColor = Color(0xFFFACC15)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_SAKURA_BREEZE,
            nameBn = "সাকুরা গোধূলি",
            nameEn = "Sakura Twilight",
            categoryBn = "প্রকৃতি",
            description = "গোধূলি আকাশে ভেসে বেড়ানো গোলাপি সাকুরা ফুলের স্নিগ্ধ আভা",
            previewColors = listOf(Color(0xFF1F0924), Color(0xFF701A75), Color(0xFFF472B6)),
            accentColor = Color(0xFFF9A8D4)
        ),
        WallpaperPresetItem(
            id = HeaderWallpaperConfig.PRESET_ROYAL_VELVET,
            nameBn = "রয়েল গোল্ড",
            nameEn = "Royal Gold",
            categoryBn = "সাইবার ও রয়েল",
            description = "অভিজাত অবসিডিয়ান ব্ল্যাক ও ২৪ ক্যারেট লিকুইড গোল্ডেন সিল্ক তরঙ্গ",
            previewColors = listOf(Color(0xFF0F0B05), Color(0xFF422006), Color(0xFFD97706)),
            accentColor = Color(0xFFFBBF24)
        )
    )

    fun findById(id: String?): WallpaperPresetItem {
        return presets.find { it.id == id } ?: presets.first()
    }
}

/**
 * Animated High-Performance Live Header Background Composable.
 *
 * 100% Continuous C∞ Harmonic Loop Guarantee:
 * - [phase] animates linearly from `0f` to `2 * PI`.
 * - Every wave, orbit, nebula, firefly, and petal uses closed harmonic trajectories (`sin(k * phase + seed)`,
 *   `cos(k * phase + seed)`) where `k` is strictly an integer (`1`, `2`, `3`).
 * - Because `sin(k * 2π + seed) == sin(0 + seed)` and its derivatives match to all orders,
 *   there is NO start point and NO end point — the animation flows infinitely without ever resetting!
 */
@Composable
fun LiveHeaderBackground(
    config: HeaderWallpaperConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "SeamlessLiveWallpaperTransition")

    // Single master harmonic phase 0..2π (100% C∞ continuous across loop boundary)
    val phase by if (config.isAnimationEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "SeamlessWavePhase"
        )
    } else {
        remember { mutableFloatStateOf(0.8f) }
    }

    // Normalized 0..1 cycle synchronized directly with phase (`phase / 2π`) so two timers never desync
    val unitCycle = remember(phase) {
        (phase / (2.0 * PI).toFloat()).coerceIn(0f, 1f)
    }

    // Derived smooth oscillation (0.55f .. 0.98f) from phase
    val glowAlpha = remember(phase) {
        0.76f + 0.22f * sin(phase)
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
                    CosmicSupernovaCanvas(phase, glowAlpha)
                }
            }

            HeaderWallpaperConfig.CUSTOM_CODE -> {
                if (parsedCustomTheme != null) {
                    CustomCodeWallpaperCanvas(
                        theme = parsedCustomTheme,
                        phase = phase,
                        unitCycle = unitCycle,
                        glowAlpha = glowAlpha
                    )
                } else {
                    CosmicSupernovaCanvas(phase, glowAlpha)
                }
            }

            HeaderWallpaperConfig.PRESET_AURORA,
            HeaderWallpaperConfig.PRESET_DEEP_OCEAN,
            HeaderWallpaperConfig.PRESET_NATURE ->
                ArcticAuroraCanvas(phase, glowAlpha)

            HeaderWallpaperConfig.PRESET_CYBERPUNK_MATRIX,
            HeaderWallpaperConfig.PRESET_MATRIX_STREAM,
            HeaderWallpaperConfig.PRESET_CRYSTAL_PRISM ->
                CyberpunkNeonMatrixCanvas(phase, unitCycle, glowAlpha)

            HeaderWallpaperConfig.PRESET_FIREFLY_FOREST ->
                EnchantedFireflyForestCanvas(phase, glowAlpha)

            HeaderWallpaperConfig.PRESET_SAKURA_BREEZE,
            HeaderWallpaperConfig.PRESET_SUNSET ->
                SakuraBlossomBreezeCanvas(phase, glowAlpha)

            HeaderWallpaperConfig.PRESET_ROYAL_VELVET,
            HeaderWallpaperConfig.PRESET_CYBER,
            HeaderWallpaperConfig.PRESET_SOLAR_ECLIPSE,
            HeaderWallpaperConfig.PRESET_VOLCANIC_EMBER ->
                RoyalGoldenSilkCanvas(phase, glowAlpha)

            else ->
                CosmicSupernovaCanvas(phase, glowAlpha)
        }

        // Subtle bottom vignette for crisp text readability on all presets
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.22f)
                        )
                    )
                )
        )
    }
}

// ============================================================================
// 6 ULTRA-PREMIUM SEAMLESS HARMONIC LIVE WALLPAPER CANVASES
// ============================================================================

@Composable
private fun CosmicSupernovaCanvas(phase: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF030816), Color(0xFF0A1936), Color(0xFF172554)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        // Orbiting Nebula Core 1 (Closed figure-8 harmonic Lissajous curve)
        val orb1Center = Offset(
            x = w * (0.76f + 0.08f * cos(phase)),
            y = h * (0.34f + 0.10f * sin(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.46f * glowAlpha),
                    Color(0xFF6366F1).copy(alpha = 0.22f * glowAlpha),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = w * 0.56f
            ),
            radius = w * 0.56f,
            center = orb1Center
        )

        // Orbiting Nebula Core 2
        val orb2Center = Offset(
            x = w * (0.24f + 0.07f * sin(phase)),
            y = h * (0.68f + 0.09f * cos(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFA855F7).copy(alpha = 0.36f * glowAlpha),
                    Color(0xFF3B82F6).copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = orb2Center
        )

        // Multi-layered Galactic Silk Waves (Continuous Travelling Sine Waves)
        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.54f,
            amplitude = h * 0.11f,
            colorStart = Color(0xFF38BDF8).copy(alpha = 0.20f),
            colorEnd = Color(0xFFC084FC).copy(alpha = 0.24f)
        )
        drawFlowingRibbon(
            phase = -phase + 1.4f,
            baseYRatio = 0.72f,
            amplitude = h * 0.08f,
            colorStart = Color(0xFF6366F1).copy(alpha = 0.22f),
            colorEnd = Color(0xFF22D3EE).copy(alpha = 0.18f)
        )

        // Orbiting & Twinkling Starfield (100% harmonic — never resets)
        val stars = listOf(
            Triple(0.10f, 0.22f, 2.1f), Triple(0.24f, 0.16f, 2.7f),
            Triple(0.38f, 0.32f, 1.7f), Triple(0.54f, 0.18f, 2.3f),
            Triple(0.68f, 0.26f, 2.9f), Triple(0.86f, 0.20f, 2.2f),
            Triple(0.91f, 0.48f, 1.9f), Triple(0.74f, 0.68f, 2.5f),
            Triple(0.46f, 0.72f, 1.8f), Triple(0.18f, 0.62f, 2.4f),
            Triple(0.31f, 0.82f, 1.6f), Triple(0.62f, 0.48f, 2.1f)
        )
        stars.forEachIndexed { idx, (rx, ry, rad) ->
            val twinkle = (sin(phase * 2f + idx * 1.15f) + 1f) * 0.5f
            val driftX = cos(phase + idx * 0.9f) * 6f
            val driftY = sin(phase + idx * 0.9f) * 4f
            val center = Offset(w * rx + driftX, h * ry + driftY)
            drawCircle(
                color = Color.White.copy(alpha = 0.30f + twinkle * 0.68f),
                radius = rad * (0.85f + twinkle * 0.45f),
                center = center
            )
            if (idx % 3 == 0) {
                val flare = rad * (2.2f + 1.8f * twinkle)
                drawLine(
                    color = Color(0xFFBAE6FD).copy(alpha = 0.42f * twinkle),
                    start = Offset(center.x - flare, center.y),
                    end = Offset(center.x + flare, center.y),
                    strokeWidth = 1.2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFBAE6FD).copy(alpha = 0.42f * twinkle),
                    start = Offset(center.x, center.y - flare),
                    end = Offset(center.x, center.y + flare),
                    strokeWidth = 1.2f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun ArcticAuroraCanvas(phase: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF021017), Color(0xFF04282E), Color(0xFF064E3B))
            )
        )

        // Glowing Emerald-Cyan Corona Orb
        val auroraOrb = Offset(
            x = w * (0.75f + 0.08f * sin(phase)),
            y = h * (0.28f + 0.06f * cos(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF34D399).copy(alpha = 0.38f * glowAlpha),
                    Color(0xFF06B6D4).copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = auroraOrb,
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = auroraOrb
        )

        // 3 Continuous Harmonic Northern Lights Ribbons
        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.34f,
            amplitude = h * 0.13f,
            colorStart = Color(0xFF10B981).copy(alpha = 0.42f * glowAlpha),
            colorEnd = Color(0xFF06B6D4).copy(alpha = 0.36f * glowAlpha)
        )
        drawFlowingRibbon(
            phase = -phase + 1.8f,
            baseYRatio = 0.50f,
            amplitude = h * 0.10f,
            colorStart = Color(0xFF34D399).copy(alpha = 0.30f * glowAlpha),
            colorEnd = Color(0xFFA855F7).copy(alpha = 0.26f * glowAlpha)
        )
        drawFlowingRibbon(
            phase = phase * 2f + 0.9f,
            baseYRatio = 0.64f,
            amplitude = h * 0.07f,
            colorStart = Color(0xFF2DD4BF).copy(alpha = 0.24f),
            colorEnd = Color(0xFF38BDF8).copy(alpha = 0.22f)
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
private fun CyberpunkNeonMatrixCanvas(phase: Float, unitCycle: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF070210), Color(0xFF1F0638), Color(0xFF4A044E))
            )
        )

        val horizonY = h * 0.56f

        // Breathing Synthwave Retro Sun
        val sunCenter = Offset(
            w * (0.78f + 0.02f * cos(phase)),
            horizonY - h * (0.06f + 0.02f * sin(phase))
        )
        val sunRadius = h * (0.27f + 0.015f * sin(phase))
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFDE047).copy(alpha = 0.88f),
                    Color(0xFFF97316).copy(alpha = 0.86f),
                    Color(0xFFEC4899).copy(alpha = 0.92f)
                ),
                startY = sunCenter.y - sunRadius,
                endY = sunCenter.y + sunRadius
            ),
            radius = sunRadius,
            center = sunCenter
        )

        // Glowing Horizon Line
        drawLine(
            color = Color(0xFF22D3EE).copy(alpha = 0.85f * glowAlpha),
            start = Offset(0f, horizonY),
            end = Offset(w, horizonY),
            strokeWidth = 2.5f
        )

        // Perspective Vertical Grid Lines
        val vanishX = w * (0.5f + 0.03f * sin(phase))
        for (i in -6..6) {
            val bottomX = vanishX + i * (w * 0.14f)
            drawLine(
                color = Color(0xFFEC4899).copy(alpha = 0.34f),
                start = Offset(vanishX + i * (w * 0.03f), horizonY),
                end = Offset(bottomX, h),
                strokeWidth = 1.4f
            )
        }

        // Seamless Infinite Tiled Horizontal Grid Lines (Line i at cycle=1 becomes Line i+1 at cycle=0)
        val gridCount = 6
        for (i in 0 until gridCount) {
            val p = ((i.toFloat() + unitCycle * gridCount) / gridCount) % 1f
            val y = horizonY + (h - horizonY) * (p * p)
            val alpha = sin(p * PI.toFloat()).coerceIn(0f, 1f) * 0.60f
            drawLine(
                color = Color(0xFF22D3EE).copy(alpha = alpha),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.6f
            )
        }
    }
}

@Composable
private fun EnchantedFireflyForestCanvas(phase: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF03110E), Color(0xFF062920), Color(0xFF0B3B2D))
            )
        )

        // Soft Emerald Moonbeams
        val moonCenter = Offset(
            w * (0.82f + 0.04f * cos(phase)),
            h * (0.24f + 0.04f * sin(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF34D399).copy(alpha = 0.28f * glowAlpha), Color.Transparent),
                center = moonCenter,
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = moonCenter
        )

        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.68f,
            amplitude = h * 0.08f,
            colorStart = Color(0xFF10B981).copy(alpha = 0.22f),
            colorEnd = Color(0xFF84CC16).copy(alpha = 0.18f)
        )

        // 12 Closed-Orbit Harmonic Golden Fireflies (Never snap or reset!)
        val fireflySeeds = listOf(
            Triple(0.12f, 0.68f, 0.2f), Triple(0.24f, 0.42f, 1.1f),
            Triple(0.36f, 0.74f, 2.3f), Triple(0.48f, 0.36f, 3.4f),
            Triple(0.60f, 0.64f, 4.2f), Triple(0.72f, 0.46f, 5.1f),
            Triple(0.84f, 0.70f, 0.8f), Triple(0.91f, 0.38f, 2.7f),
            Triple(0.18f, 0.28f, 3.9f), Triple(0.54f, 0.80f, 1.6f),
            Triple(0.66f, 0.26f, 4.8f), Triple(0.42f, 0.54f, 5.7f)
        )
        fireflySeeds.forEachIndexed { index, (seedX, seedY, offset) ->
            val fx = w * seedX + sin(phase + offset) * (w * 0.055f) + cos(phase * 2f + offset) * (w * 0.02f)
            val fy = h * seedY + cos(phase + offset) * (h * 0.12f) + sin(phase * 2f + offset) * (h * 0.05f)
            val pulse = (sin(phase * 2f + index * 0.9f) + 1f) * 0.5f
            val alpha = 0.40f + 0.60f * pulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFDE047).copy(alpha = alpha * 0.78f),
                        Color(0xFF84CC16).copy(alpha = alpha * 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(fx, fy),
                    radius = 15f
                ),
                radius = 15f,
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
private fun SakuraBlossomBreezeCanvas(phase: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1A0826), Color(0xFF4A1259), Color(0xFF831843))
            )
        )

        // Warm Pink Twilight Glow
        val sunCenter = Offset(
            w * (0.78f + 0.05f * cos(phase)),
            h * (0.30f + 0.05f * sin(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFF9A8D4).copy(alpha = 0.42f * glowAlpha),
                    Color(0xFFF43F5E).copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = sunCenter,
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = sunCenter
        )

        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.62f,
            amplitude = h * 0.09f,
            colorStart = Color(0xFFF472B6).copy(alpha = 0.24f),
            colorEnd = Color(0xFFC084FC).copy(alpha = 0.22f)
        )

        // Closed-Orbit Swirling Sakura Petals (Continuous harmonic breeze)
        for (i in 0 until 14) {
            val seed = i * 0.52f
            val baseX = ((i * 0.077f) + 0.06f).coerceIn(0.05f, 0.95f)
            val baseY = ((i * 0.13f) % 0.76f) + 0.12f
            val px = w * baseX + sin(phase + seed) * (w * 0.06f) + cos(phase * 2f + seed) * (w * 0.025f)
            val py = h * baseY + cos(phase + seed) * (h * 0.10f)
            val shimmer = 0.55f + 0.45f * sin(phase * 2f + seed)
            val petalColor = if (i % 2 == 0) Color(0xFFFBCFE8) else Color(0xFFF472B6)
            drawOval(
                color = petalColor.copy(alpha = 0.78f * shimmer),
                topLeft = Offset(px, py),
                size = Size(10f + (i % 3) * 3f, 6f + (i % 2) * 2f)
            )
        }
    }
}

@Composable
private fun RoyalGoldenSilkCanvas(phase: Float, glowAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF090704), Color(0xFF1C1308), Color(0xFF33200A))
            )
        )

        val orbCenter = Offset(
            w * (0.78f + 0.06f * cos(phase)),
            h * (0.28f + 0.06f * sin(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFF59E0B).copy(alpha = 0.38f * glowAlpha),
                    Color(0xFFB45309).copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = orbCenter,
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = orbCenter
        )

        drawFlowingRibbon(
            phase = phase,
            baseYRatio = 0.46f,
            amplitude = h * 0.11f,
            colorStart = Color(0xFFFBBF24).copy(alpha = 0.32f * glowAlpha),
            colorEnd = Color(0xFFD97706).copy(alpha = 0.20f)
        )
        drawFlowingRibbon(
            phase = -phase + 1.5f,
            baseYRatio = 0.66f,
            amplitude = h * 0.09f,
            colorStart = Color(0xFFFDE68A).copy(alpha = 0.24f * glowAlpha),
            colorEnd = Color(0xFF92400E).copy(alpha = 0.30f)
        )

        // Shimmering 24K Gold Dust Motes in Harmonic Orbits
        for (i in 0 until 10) {
            val seed = i * 0.68f
            val gx = w * (0.10f + i * 0.085f) + sin(phase + seed) * 16f
            val gy = h * (0.25f + (i % 3) * 0.22f) + cos(phase + seed) * 12f
            val twinkle = (sin(phase * 2f + seed) + 1f) * 0.5f
            drawCircle(
                color = Color(0xFFFDE68A).copy(alpha = 0.35f + 0.55f * twinkle),
                radius = 2.2f + (i % 2) * 1.2f,
                center = Offset(gx, gy)
            )
        }
    }
}

@Composable
private fun CustomCodeWallpaperCanvas(
    theme: ImportedWallpaperTheme,
    phase: Float,
    unitCycle: Float,
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
                    val p = ((unitCycle * speed) + c * 0.1f) % 1f
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
                    val seed = i * 0.55f
                    val px = w * (0.08f + i * 0.075f) + sin(effPhase + seed) * 16f
                    val py = h * (0.28f + (i % 3) * 0.22f) + cos(effPhase + seed) * 14f
                    val pulse = 0.45f + 0.55f * ((sin(effPhase * 2f + seed) + 1f) * 0.5f)
                    drawCircle(
                        color = particleCol.copy(alpha = 0.85f * pulse),
                        radius = 3.2f,
                        center = Offset(px, py)
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
                    phase = -effPhase + 1.7f,
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
        val steps = 28
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
