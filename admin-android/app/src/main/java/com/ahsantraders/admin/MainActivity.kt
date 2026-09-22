package com.ahsantraders.admin

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ahsantraders.admin.ui.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 12+ always shows a system splash; strip it the instant our first frame is ready
        // so it never sits as a second “logo only” screen before the Compose splash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashView ->
                splashView.remove()
            }
        }

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
                    // Compose Splash Screen (First and Only Splash)
                    composable(Route.Splash) {
                        val splashVm: SplashViewModel = hiltViewModel()
                        SplashScreen(
                            viewModel = splashVm,
                            onNavigate = { target ->
                                val dest = when (target) {
                                    SplashNavigationTarget.Login -> Route.Login
                                    SplashNavigationTarget.Dashboard -> Route.Dashboard
                                }
                                navController.navigate(dest) {
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
