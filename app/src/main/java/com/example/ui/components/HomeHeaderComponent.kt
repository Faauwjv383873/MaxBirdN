package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import coil.request.ImageRequest
import com.example.R
import com.example.utils.AvatarUtils

/**
 * Home Header Component matching user design specifications
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C1B3A))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
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
                // App Logo Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.maxbird_logo),
                        contentDescription = "MaxBird Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    // হ্যালো, নাম 👋
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "হ্যালো, $userName",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "👋", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // ক্লাস, গ্রুপ, শিক্ষাপ্রতিষ্ঠান
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // প্রোফাইল অ্যাভাটার (ডানপাশে)
            val fallbackInitial = remember(userName) {
                userName.trim().firstOrNull()?.toString()?.uppercase() ?: "U"
            }

            val imageRequest = remember(avatarUrl, userName, context) {
                AvatarUtils.buildImageRequest(context, avatarUrl, userName)
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        if (isPremium) Color(0xFFFFB800) else Color.White.copy(alpha = 0.25f),
                        CircleShape
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

        // কোর্স সুইচ অপশন pill (Matching IMG_20260912_155802.jpg)
        if (!activeCourseTitle.isNullOrBlank() && onOpenCourseSwitcher != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .clickable { onOpenCourseSwitcher() },
                shape = RoundedCornerShape(30.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activeCourseTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "কোর্স পরিবর্তন",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
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
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
