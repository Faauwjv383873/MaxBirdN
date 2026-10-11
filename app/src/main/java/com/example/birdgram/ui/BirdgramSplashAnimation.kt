package com.example.birdgram.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * BirdgramSplashAnimation - Ultra-premium Soaring Bird Launch Animation.
 *
 * Displays an ethereal, gliding Telegram bird with flapping wings,
 * pulsing radiant energy waves, cosmic stardust particles, and sleek branding.
 */
@Composable
fun BirdgramSplashAnimation(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BirdFlightTransition")

    // Wing flap oscillation (sinusoidal physics)
    val flapPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WingFlap"
    )

    // Gentle hovering / gliding bob
    val hoverOffset by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HoverGlide"
    )

    // Radiant pulse wave expansion
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseProgress"
    )

    // Shimmer glow wave across brand title
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerPhase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E1621)),
        contentAlignment = Alignment.Center
    ) {
        // Deep background celestial glow
        Box(
            modifier = Modifier
                .size(340.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0088CC).copy(alpha = 0.22f),
                            Color(0xFF0284C7).copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
                .blur(40.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Main Flight Arena Canvas
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2f
                    val centerY = (size.height / 2f) + hoverOffset

                    // 1. Radiant Aura Pulse Waves (Telegram Blue Glow Rings)
                    drawPulseWaves(centerX, centerY, pulseProgress)

                    // 2. Celestial Dust / Speed Particles
                    drawCosmicParticles(centerX, centerY, flapPhase)

                    // 3. Majestic Soaring Bird
                    drawMajesticBird(centerX, centerY, flapPhase)
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Elegant App Branding with Letter Spacing
            Text(
                text = "B I R D G R A M",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
                letterSpacing = 6.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Shimmer Status Pill
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0284C7).copy(alpha = 0.35f),
                                Color(0xFF1E293B)
                            )
                        ),
                        shape = CircleShape
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing Cyan Dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFF38BDF8), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "টেলিগ্রাম ক্লাউডে সংযুক্ত হচ্ছে...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFBAE6FD)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Sleek Shimmer Progress Track
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(3.dp)
                    .background(Color(0xFF1E293B), CircleShape)
            ) {
                val shimmerStart = (shimmerPhase * 240f) - 60f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(70.dp)
                        .offset(x = (shimmerStart * 0.75f).dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFF38BDF8),
                                    Color(0xFF0284C7),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

/**
 * Draws concentric glowing pulse waves radiating outward from the flying bird
 */
private fun DrawScope.drawPulseWaves(centerX: Float, centerY: Float, progress: Float) {
    val maxRadius = size.width * 0.48f

    for (i in 0..2) {
        val waveFraction = (progress + (i * 0.33f)) % 1f
        val currentRadius = 35f + (waveFraction * (maxRadius - 35f))
        val alpha = ((1f - waveFraction) * 0.40f).coerceIn(0f, 1f)

        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = alpha),
            radius = currentRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.8f)
        )
    }
}

/**
 * Draws floating luminous particles / starry dust gliding past the bird
 */
private fun DrawScope.drawCosmicParticles(centerX: Float, centerY: Float, phase: Float) {
    val particleOffsets = listOf(
        Offset(-65f, -45f),
        Offset(75f, -35f),
        Offset(-85f, 30f),
        Offset(80f, 45f),
        Offset(-40f, 70f),
        Offset(55f, 75f)
    )

    for ((index, baseOffset) in particleOffsets.withIndex()) {
        val animOffset = (phase + (index * 1.05f))
        val currentX = centerX + baseOffset.x + (cos(animOffset) * 8f)
        val currentY = centerY + baseOffset.y + (sin(animOffset) * 8f)
        val particleAlpha = ((sin(animOffset) + 1f) / 2f * 0.7f + 0.3f).coerceIn(0f, 1f)

        drawCircle(
            color = Color(0xFFBAE6FD).copy(alpha = particleAlpha),
            radius = (index % 3 + 1.8f),
            center = Offset(currentX, currentY)
        )
    }
}

/**
 * Draws the stylized, soaring Telegram bird with aerodynamic wings, gradient body, and bill
 */
private fun DrawScope.drawMajesticBird(centerX: Float, centerY: Float, phase: Float) {
    val flapScale = sin(phase) // Between -1 and 1
    val wingLift = flapScale * 22f // Wing tip movement

    // 1. Under-wing Soft Glow Aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.5f), Color.Transparent),
            center = Offset(centerX, centerY),
            radius = 60f
        ),
        radius = 60f,
        center = Offset(centerX, centerY)
    )

    // 2. Far Wing (Left / Back Wing)
    val backWingPath = Path().apply {
        moveTo(centerX - 10f, centerY - 6f)
        // Upward and backward arc
        cubicTo(
            centerX - 40f, centerY - 38f - (wingLift * 0.7f),
            centerX - 68f, centerY - 55f - (wingLift * 0.9f),
            centerX - 72f, centerY - 45f - wingLift
        )
        // Wing tip returning to body
        cubicTo(
            centerX - 45f, centerY - 20f,
            centerX - 25f, centerY - 5f,
            centerX - 12f, centerY + 10f
        )
        close()
    }

    drawPath(
        path = backWingPath,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF0284C7), Color(0xFF0369A1)),
            start = Offset(centerX - 10f, centerY),
            end = Offset(centerX - 75f, centerY - 50f)
        ),
        style = Fill
    )

    // 3. Bird Body & Aerodynamic Tail
    val bodyPath = Path().apply {
        // Start at Beak
        moveTo(centerX + 58f, centerY - 8f)
        // Head and Chest arch
        cubicTo(
            centerX + 35f, centerY + 18f,
            centerX + 5f, centerY + 24f,
            centerX - 25f, centerY + 15f
        )
        // Tail feathers sweeping back
        lineTo(centerX - 55f, centerY + 28f)
        lineTo(centerX - 42f, centerY + 8f)
        lineTo(centerX - 60f, centerY + 12f)
        lineTo(centerX - 35f, centerY - 2f)

        // Back line to Beak
        cubicTo(
            centerX + 5f, centerY - 14f,
            centerX + 35f, centerY - 18f,
            centerX + 58f, centerY - 8f
        )
        close()
    }

    drawPath(
        path = bodyPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF38BDF8),
                Color(0xFF0284C7),
                Color(0xFF0369A1)
            ),
            start = Offset(centerX + 50f, centerY - 10f),
            end = Offset(centerX - 50f, centerY + 20f)
        ),
        style = Fill
    )

    // 4. Near Wing (Main Front Wing — Soaring & Powerful)
    val frontWingPath = Path().apply {
        moveTo(centerX + 6f, centerY - 8f)
        // Soaring wingtip reaching high
        cubicTo(
            centerX - 10f, centerY - 55f + wingLift,
            centerX - 35f, centerY - 85f + (wingLift * 1.2f),
            centerX - 48f, centerY - 88f + (wingLift * 1.3f)
        )
        // Wing edge details back to flank
        cubicTo(
            centerX - 28f, centerY - 50f + (wingLift * 0.8f),
            centerX - 15f, centerY - 20f,
            centerX - 18f, centerY + 5f
        )
        close()
    }

    drawPath(
        path = frontWingPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF7DD3FC), // Bright highlighted wingtip
                Color(0xFF38BDF8),
                Color(0xFF0284C7)
            ),
            start = Offset(centerX - 40f, centerY - 80f),
            end = Offset(centerX + 5f, centerY)
        ),
        style = Fill
    )

    // Wing feather highlight spine line
    drawPath(
        path = Path().apply {
            moveTo(centerX + 4f, centerY - 6f)
            quadraticTo(
                centerX - 24f, centerY - 45f + (wingLift * 0.8f),
                centerX - 46f, centerY - 85f + (wingLift * 1.25f)
            )
        },
        color = Color.White.copy(alpha = 0.55f),
        style = Stroke(width = 1.8f, cap = StrokeCap.Round)
    )

    // 5. Beak / Eye Accent (Sharp glowing flight point)
    drawCircle(
        color = Color.White,
        radius = 2.4f,
        center = Offset(centerX + 36f, centerY - 7f)
    )

    // Sleek Beak tip
    drawPath(
        path = Path().apply {
            moveTo(centerX + 48f, centerY - 9f)
            lineTo(centerX + 64f, centerY - 7f)
            lineTo(centerX + 46f, centerY - 4f)
            close()
        },
        color = Color(0xFFFDBA74), // Warm gold-orange beak tip
        style = Fill
    )
}
