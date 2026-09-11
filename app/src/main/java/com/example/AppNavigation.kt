package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.auth.AuthState
import com.example.auth.AuthViewModel
import com.example.auth.AuthViewModelFactory
import com.example.auth.SessionManager
import com.example.api.ShikhoApiService
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.OtpScreen
import com.example.ui.screens.ProfileScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember

object Routes {
    const val LOGIN = "login"
    const val OTP = "otp/{phone}/{pinExists}"
    const val PROFILE = "profile"
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    val apiService = remember { ShikhoApiService.create() }
    val sessionManager = remember { SessionManager(context) }
    
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(apiService, sessionManager)
    )
    
    val authState by authViewModel.authState.collectAsState()
    
    val startDestination = if (sessionManager.getAccessToken() != null) {
        Routes.PROFILE
    } else {
        Routes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                authState = authState,
                onNavigateToOtp = { phone, pinExists ->
                    navController.navigate("otp/$phone/$pinExists")
                }
            )
        }
        
        composable(Routes.OTP) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            val pinExists = backStackEntry.arguments?.getString("pinExists")?.toBoolean() ?: false
            OtpScreen(
                phone = phone,
                pinExists = pinExists,
                viewModel = authViewModel,
                authState = authState,
                onLoginSuccess = {
                    navController.navigate(Routes.PROFILE) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.PROFILE) {
            ProfileScreen(
                viewModel = authViewModel,
                authState = authState,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
