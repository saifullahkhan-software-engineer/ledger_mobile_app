package com.ahsantraders.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ahsantraders.admin.data.AdminRepository
import com.ahsantraders.admin.data.SessionStore
import com.ahsantraders.admin.ui.*
import com.ahsantraders.app.ui.splash.SplashDestination
import com.ahsantraders.app.ui.splash.SplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // LAYER 1 — SYSTEM SPLASH (Android 12+ requirement via androidx.core:core-splashscreen)
        // Must be called BEFORE super.onCreate()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Draw edge-to-edge behind status bar with light status bar icons for readable contrast on dark green
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            val adminVm: AdminViewModel = hiltViewModel()
            val state by adminVm.state.collectAsStateWithLifecycle()
            val navController = rememberNavController()

            AhsanTheme(state.language) {
                NavHost(
                    navController = navController,
                    startDestination = Route.Splash
                ) {
                    // Start destination: Layer 2 Compose Splash Screen
                    composable(Route.Splash) {
                        SplashScreen(
                            onNavigate = { destination ->
                                val target = when (destination) {
                                    SplashDestination.Login -> Route.Login
                                    SplashDestination.Dashboard -> Route.Dashboard
                                }
                                navController.navigate(target) {
                                    popUpTo(Route.Splash) { inclusive = true }
                                }
                            }
                        )
                    }

                    // Authentication Screen
                    composable(Route.Login) {
                        LoginScreen(state, adminVm) {
                            navController.navigate(Route.Dashboard) {
                                popUpTo(Route.Login) { inclusive = true }
                            }
                        }
                    }

                    // Main App / Dashboard
                    composable(Route.Dashboard) {
                        AdminApp(state, adminVm) {
                            navController.navigate(Route.Login) {
                                popUpTo(Route.Dashboard) { inclusive = true }
                            }
                        }
                    }
                }
            }
        }
    }
}
