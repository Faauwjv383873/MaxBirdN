package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Neo-Minimalism & Glassmorphic Home Top Header Component
 */
@Composable
fun HomeHeaderComponent(
    userName: String,
    userFirstName: String,
    userAvatar: String?,
    userClass: String,
    userGroup: String,
    userSchool: String,
    isPremium: Boolean,
    activeCourseTitle: String,
    onOpenCourseSwitcher: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Format Academic Subtitle (e.g., "একাদশ শ্রেণি • মানবিক • ঢাকা কলেজ")
    val formattedSubtitle = remember(userClass, userGroup, userSchool) {
        val classDisplay = when (userClass) {
            "C11", "Class 11" -> "একাদশ শ্রেণি"
            "C12", "Class 12" -> "দ্বাদশ শ্রেণি"
            "C10", "Class 10" -> "দশম শ্রেণি"
            "C9", "Class 9" -> "নবম শ্রেণি"
            else -> userClass.ifBlank { "একাদশ শ্রেণি" }
        }

        val groupDisplay = when (userGroup.lowercase()) {
            "humanities", "humanities_group" -> "মানবিক"
            "science", "science_group" -> "বিজ্ঞান"
            "business", "business_studies", "commerce" -> "ব্যবসায় শিক্ষা"
            else -> userGroup.ifBlank { "" }
        }

        listOf(classDisplay, groupDisplay, userSchool)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { "এইচএসসি শিক্ষার্থী" }
    }

    val firstNameDisplay = remember(userFirstName, userName) {
        when {
            userFirstName.isNotBlank() && userFirstName != "শিক্ষার্থী" -> userFirstName
            userName.isNotBlank() && userName != "শিক্ষার্থী" -> userName.split(" ").firstOrNull() ?: userName
            else -> "শিক্ষার্থী"
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    initialOffsetY = { -30 },
                    animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                ),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    ambientColor = Color(0xFF0F172A).copy(alpha = 0.25f),
                    spotColor = Color(0xFF4338CA).copy(alpha = 0.35f)
                ),
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            color = Color.Transparent
        ) {
            // Live Gradient Background (Deep Navy Blue to Soft Purple/Indigo)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0A1128), // Deep Navy
                                Color(0xFF1E1B4B), // Midnight Indigo
                                Color(0xFF312E81), // Royal Indigo
                                Color(0xFF4338CA)  // Vibrant Soft Purple
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                // Background Glass Glow Elements
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 20.dp, y = (-20).dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF818CF8).copy(alpha = 0.28f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Top Row: Greetings & Info on Left, Avatar on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Info Column
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            // 1. Premium Golden Crown Badge
                            if (isPremium) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0x33FFD700),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        brush = Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFFFFD54F),
                                                Color(0xFFFFB300)
                                            )
                                        )
                                    ),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.WorkspacePremium,
                                            contentDescription = "Premium Badge",
                                            tint = Color(0xFFFFD54F),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "প্রিমিয়াম",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFE082)
                                        )
                                    }
                                }
                            }

                            // 2. User Greetings & Name
                            Text(
                                text = "হ্যালো, $firstNameDisplay 👋",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            // 3. Academic Subtitle
                            Text(
                                text = formattedSubtitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.82f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Right: Profile Avatar with Golden Crown & Haptic Feedback
                        Box(
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(54.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = CircleShape,
                                        spotColor = Color(0xFFFFD54F).copy(alpha = 0.4f)
                                    )
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAvatarClick()
                                    },
                                shape = CircleShape,
                                color = Color(0xFF1E1B4B),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 2.dp,
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            Color(0xFFFFD54F),
                                            Color(0xFFFFB300),
                                            Color(0xFF818CF8),
                                            Color(0xFFFFD54F)
                                        )
                                    )
                                )
                            ) {
                                if (!userAvatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userAvatar,
                                        contentDescription = "User Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val initial = firstNameDisplay.trim().firstOrNull()?.toString()?.uppercase() ?: "U"
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFF4F46E5),
                                                        Color(0xFF7C3AED)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initial,
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Crown Badge on Top-Right of Avatar
                            Surface(
                                modifier = Modifier
                                    .size(20.dp)
                                    .offset(x = 3.dp, y = (-3).dp)
                                    .clip(CircleShape),
                                shape = CircleShape,
                                color = Color(0xFF0F172A),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = Color(0xFFFFD54F)
                                )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Course Switcher Glassmorphic Pill Button
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenCourseSwitcher()
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.22f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF6366F1).copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "বর্তমান কোর্স",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = activeCourseTitle,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp,
                                    Color.White.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "কোর্স পরিবর্তন",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.95f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "কোর্স সুইচ",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
