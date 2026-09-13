package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AuthState
import com.example.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    authState: AuthState,
    onNavigateToPin: (String) -> Unit,
    onNavigateToOtp: (String, String) -> Unit
) {
    var phoneInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.NavigateToPin -> {
                onNavigateToPin(authState.phone)
                viewModel.resetState()
            }
            is AuthState.NavigateToOtp -> {
                onNavigateToOtp(authState.phone, authState.authType)
                viewModel.resetState()
            }
            is AuthState.Error -> {
                errorMessage = authState.message
            }
            else -> {}
        }
    }

    val backgroundGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F172A), // Deep Slate Navy
                Color(0xFF020617), // Midnight Dark
                Color(0xFF090D16)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // Decorative background subtle accent circle
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-80).dp, y = (-80).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7C3AED).copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0284C7).copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Header & Logo
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(600)) + slideInVertically(
                    initialOffsetY = { -40 },
                    animationSpec = tween(600, easing = FastOutSlowInEasing)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
                                ),
                                shape = RoundedCornerShape(22.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.maxbird_logo),
                            contentDescription = "Shikho Logo",
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "শিখো ডিজিটাল একাডেমি",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "সহজ ও আনন্দদায়ক শেখার নতুন অভিজ্ঞতা",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Bento Grid UI Layout
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(700, delayMillis = 150)) + slideInVertically(
                    initialOffsetY = { 30 },
                    animationSpec = tween(700, delayMillis = 150, easing = FastOutSlowInEasing)
                )
            ) {
                BentoGridSection()
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Phone Input Card (Linear Modern Luxe Style)
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(800, delayMillis = 300)) + slideInVertically(
                    initialOffsetY = { 40 },
                    animationSpec = tween(800, delayMillis = 300, easing = FastOutSlowInEasing)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "মোবাইল নম্বর লিখুন",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "লগইন বা একাউন্ট খুলতে ১১ ডিজিটের নম্বর দিন",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Phone Input Container
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0F172A))
                                .border(
                                    width = if (phoneInput.length == 11) 1.5.dp else 1.dp,
                                    color = if (phoneInput.length == 11) Color(0xFF38BDF8) else Color(0xFF334155),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Country Code Badge (+880)
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(Color(0xFF1E293B))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "+880",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF334155))
                            )

                            // Input Field
                            TextField(
                                value = phoneInput,
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() } && newValue.length <= 11) {
                                        phoneInput = newValue
                                        errorMessage = null
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    errorContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFF38BDF8)
                                ),
                                placeholder = {
                                    Text(
                                        "17XXXXXXXX",
                                        color = Color(0xFF64748B),
                                        fontSize = 15.sp
                                    )
                                },
                                trailingIcon = {
                                    if (phoneInput.isNotEmpty()) {
                                        IconButton(onClick = { phoneInput = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        if (phoneInput.length >= 10) {
                                            viewModel.checkUser(phoneInput)
                                        }
                                    }
                                ),
                                singleLine = true
                            )
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFF87171),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Animated Action Button: "পরবর্তী" / "এগিয়ে যাও"
                        val isEnabled = authState != AuthState.Loading && phoneInput.length >= 10
                        val buttonScale by animateFloatAsState(
                            targetValue = if (isEnabled) 1.0f else 0.98f,
                            animationSpec = springAnimationSpec()
                        )

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.checkUser(phoneInput)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .scale(buttonScale)
                                .clip(RoundedCornerShape(16.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isEnabled) Color(0xFF0284C7) else Color(0xFF334155),
                                disabledContainerColor = Color(0xFF1E293B)
                            ),
                            enabled = isEnabled
                        ) {
                            if (authState == AuthState.Loading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "এগিয়ে যাও",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEnabled) Color.White else Color(0xFF64748B)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = if (isEnabled) Color.White else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Bento Grid Component (Linear Modern Luxe Style)
@Composable
private fun BentoGridSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Row 1: Main Feature Card (Span 2)
        BentoTile(
            title = "১০০K+ শিক্ষার্থীর বিশ্বস্ত প্ল্যাটফর্ম",
            subtitle = "স্মার্ট লার্নিং টুলস দিয়ে নিজের প্রস্তুতি যাচাই করো",
            icon = Icons.Default.School,
            iconTint = Color(0xFF38BDF8),
            gradientColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
            borderColor = Color(0xFF38BDF8).copy(alpha = 0.4f),
            badgeText = "জনপ্রিয়"
        )

        // Row 2: Two Equal Bento Tiles
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BentoTile(
                    title = "লাইভ ক্লাস",
                    subtitle = "সেরা শিক্ষকদের রেকর্ডেড ও লাইভ ক্লাস",
                    icon = Icons.Default.PlayCircleFilled,
                    iconTint = Color(0xFFF43F5E),
                    gradientColors = listOf(Color(0xFF881337).copy(alpha = 0.4f), Color(0xFF1E293B)),
                    borderColor = Color(0xFFF43F5E).copy(alpha = 0.3f)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                BentoTile(
                    title = "Think AI",
                    subtitle = "তাৎক্ষণিক এআই সমাধান ও কুইজ",
                    icon = Icons.Default.Psychology,
                    iconTint = Color(0xFFA855F7),
                    gradientColors = listOf(Color(0xFF581C87).copy(alpha = 0.4f), Color(0xFF1E293B)),
                    borderColor = Color(0xFFA855F7).copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun BentoTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    gradientColors: List<Color>,
    borderColor: Color,
    badgeText: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(iconTint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = iconTint.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = iconTint,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 15.sp,
                    maxLines = 2
                )
            }
        }
    }
}

private fun <T> springAnimationSpec() = androidx.compose.animation.core.spring<T>(
    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
)
