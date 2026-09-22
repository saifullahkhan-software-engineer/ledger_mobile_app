package com.ahsantraders.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahsantraders.admin.data.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SplashNavigationTarget {
    Dashboard,
    Login
}

/**
 * Manages the Compose splash screen state and session evaluation.
 * - Single splash screen (Compose): first and only splash.
 * - If user is logged in (valid token): auto-navigates to Dashboard after the branded splash has been shown.
 * - If not logged in: waits for user interaction (tap anywhere to proceed to Login).
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val repo: AdminRepository
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<SplashNavigationTarget>(replay = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()

    init {
        evaluateSessionAndTiming()
    }

    /** Called when user taps the splash screen to navigate to login. */
    fun navigateToLogin() {
        viewModelScope.launch {
            _navigationEvent.emit(SplashNavigationTarget.Login)
        }
    }

    private fun evaluateSessionAndTiming() {
        viewModelScope.launch {
            val token = runCatching { repo.store.token() }.getOrNull()
            val hasValidToken = !token.isNullOrBlank()

            if (hasValidToken) {
                // Keep the branded splash on screen long enough to read (not a flash).
                delay(2800)
                _navigationEvent.emit(SplashNavigationTarget.Dashboard)
            }
            // If not logged in: wait for user tap (no auto-navigation)
        }
    }
}
