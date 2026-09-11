package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.api.ShikhoApiService
import com.example.auth.AuthViewModel
import com.example.auth.AuthViewModelFactory
import com.example.auth.SessionManager
import com.example.home.HomeViewModel
import com.example.home.HomeViewModelFactory
import com.example.ui.screens.*

object Routes {
    const val LOGIN = "login"
    const val PIN = "pin/{phone}"
    const val OTP = "otp/{phone}/{authType}"
    const val SET_PIN = "set_pin/{phone}"
    const val RESET_SUCCESS = "reset_success/{phone}"
    const val HOME = "home"
    const val PROFILE = "profile"
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    val sessionManager = remember { SessionManager(context) }
    val apiService = remember { ShikhoApiService.create(sessionManager) }
    
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(apiService, sessionManager)
    )
    
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(apiService, sessionManager)
    )
    
    val authState by authViewModel.authState.collectAsState()
    
    val startDestination = if (sessionManager.getAccessToken() != null) {
        Routes.HOME
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
                onNavigateToPin = { phone ->
                    navController.navigate("pin/$phone")
                },
                onNavigateToOtp = { phone, authType ->
                    navController.navigate("otp/$phone/$authType")
                }
            )
        }
        
        composable(Routes.PIN) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            PinScreen(
                phone = phone,
                viewModel = authViewModel,
                authState = authState,
                onLoginSuccess = {
                    homeViewModel.loadDashboardData()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onForgotPasswordNavigate = { authType ->
                    navController.navigate("otp/$phone/$authType")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.OTP) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            val authType = backStackEntry.arguments?.getString("authType") ?: "signup"
            OtpScreen(
                phone = phone,
                authType = authType,
                viewModel = authViewModel,
                authState = authState,
                onNavigateToSetPin = { p ->
                    navController.navigate("set_pin/$p") {
                        popUpTo(Routes.LOGIN)
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SET_PIN) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            SetPinScreen(
                phone = phone,
                viewModel = authViewModel,
                authState = authState,
                onPinSetSuccess = { p ->
                    navController.navigate("reset_success/$p") {
                        popUpTo(Routes.LOGIN)
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.RESET_SUCCESS) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            ResetSuccessScreen(
                phone = phone,
                onLoginClick = { p ->
                    navController.navigate("pin/$p") {
                        popUpTo(Routes.LOGIN)
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToProfile = {
                    homeViewModel.selectTab(3)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
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
