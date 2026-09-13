package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.utils.AvatarUtils

/**
 * Animated Clean Greeting Text Composable
 * Keeps full Bengali words intact without breaking grapheme clusters.
 */
@Composable
fun AnimatedRainbowGreetingText(
    greetingPrefix: String = "হ্যালো,",
    userName: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GreetingTransition")
    val hueShift by infiniteTransition.animateFloat(
        initialValue = 190f, // Sky Blue
        targetValue = 220f, // Royal Indigo
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HueShift"
    )

    val textColor = remember(hueShift) {
        Color.hsv(hue = hueShift, saturation = 0.75f, value = 0.98f)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$greetingPrefix $userName",
            color = textColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = "👋", fontSize = 16.sp)
    }
}

/**
 * High-Density Compact Home Header Component
 * Minimizes top status bar waste, features animated letter colors & sleek course switcher.
 */
@Composable
fun HomeHeader(
    userName: String,
    subtitle: String,
    avatarUrl: String,
    isPremium: Boolean,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeCourseTitle: String? = null,
    onOpenCourseSwitcher: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Glowing subtle border animation for course switcher
    val infiniteTransition = rememberInfiniteTransition(label = "HeaderGlow")
    val borderGlowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "BorderGlow"
    )

    val courseSwitcherBorderGradient = remember(borderGlowOffset) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF38BDF8).copy(alpha = 0.4f),
                Color(0xFF818CF8).copy(alpha = 0.5f),
                Color(0xFFC084FC).copy(alpha = 0.4f),
                Color(0xFF38BDF8).copy(alpha = 0.4f)
            ),
            startX = borderGlowOffset * 500f,
            endX = (borderGlowOffset + 1f) * 500f
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070F26),
                        Color(0xFF0F172A)
                    )
                )
            )
            .statusBarsPadding()
            .padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact App Logo Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.maxbird_logo),
                        contentDescription = "MaxBird Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Animated Rainbow Multi-color Greeting
                    AnimatedRainbowGreetingText(
                        greetingPrefix = "হ্যালো,",
                        userName = userName
                    )

                    // Subtitle (Class, Group, School)
                    Text(
                        text = subtitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Profile Avatar (Compact 42.dp)
            val fallbackInitial = remember(userName) {
                userName.trim().firstOrNull()?.toString()?.uppercase() ?: "U"
            }

            val imageRequest = remember(avatarUrl, userName, context) {
                AvatarUtils.buildImageRequest(context, avatarUrl, userName)
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        color = if (isPremium) Color(0xFFFFB800) else Color(0xFF38BDF8).copy(alpha = 0.6f),
                        shape = CircleShape
                    )
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = "Profile Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        AvatarFallbackBadge(fallbackInitial)
                    },
                    loading = {
                        AvatarFallbackBadge(fallbackInitial)
                    }
                )
            }
        }

        // Compact Course Switcher Pill
        if (!activeCourseTitle.isNullOrBlank() && onOpenCourseSwitcher != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpenCourseSwitcher() },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                border = BorderStroke(1.dp, courseSwitcherBorderGradient)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeCourseTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "কোর্স পরিবর্তন",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarFallbackBadge(initial: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0284C7),
                        Color(0xFF6366F1)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

