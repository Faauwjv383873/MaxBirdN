package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthState
import com.example.auth.AuthViewModel

@Composable
fun PinScreen(
    phone: String,
    viewModel: AuthViewModel,
    authState: AuthState,
    onLoginSuccess: () -> Unit,
    onForgotPasswordNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    var pinValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.LoginSuccess -> {
                onLoginSuccess()
                viewModel.resetState()
            }
            is AuthState.NavigateToOtp -> {
                onForgotPasswordNavigate(authState.authType)
                viewModel.resetState()
            }
            is AuthState.Error -> {
                errorMessage = authState.message
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter PIN",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Enter your 6-digit permanent PIN",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        PinDigitInputField(
            value = pinValue,
            length = 6,
            isPassword = true,
            onValueChange = {
                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                    pinValue = it
                    errorMessage = null
                    if (it.length == 6) {
                        viewModel.submitPin(phone, it)
                    }
                }
            }
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        TextButton(
            onClick = { viewModel.triggerForgotPassword(phone) },
            enabled = authState != AuthState.Loading
        ) {
            Text(
                text = "Forgot Password?",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        TextButton(onClick = onBack) {
            Text("Back", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }

        if (authState == AuthState.Loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

@Composable
fun PinDigitInputField(
    value: String,
    length: Int,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(length) { index ->
                    val char = when {
                        index >= value.length -> ""
                        isPassword -> "•"
                        else -> value[index].toString()
                    }
                    val isFocused = value.length == index

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    )
}
