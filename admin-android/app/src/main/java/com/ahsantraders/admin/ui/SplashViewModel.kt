package com.ahsantraders.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahsantraders.admin.data.AdminRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SplashNavigationTarget {
    data object Dashboard : SplashNavigationTarget
    data object Login : SplashNavigationTarget
}

/**
 * Manages the splash screen state and session evaluation.
 * - Reads JWT token from encrypted SessionStore.
 * - Enforces minimum visible duration of ~1200ms to eliminate visual flicker.
 * - Controls the system splash screen keep-on-screen condition (Layer 1).
 */
class SplashViewModel(private val repo: AdminRepository) : ViewModel() {

    // Layer 1 system splash hold condition
    private val _isSystemSplashLoading = MutableStateFlow(true)
    val isSystemSplashLoading = _isSystemSplashLoading.asStateFlow()

    // Layer 2 splash screen loading indicator
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // Navigation event to navigate to Dashboard or Login
    private val _navigationEvent = MutableSharedFlow<SplashNavigationTarget>(replay = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()

    init {
        evaluateSessionAndTiming()
    }

    /** Called when Compose has rendered its first frame, releasing the Layer 1 splash screen. */
    fun onFirstFrameRendered() {
        _isSystemSplashLoading.value = false
    }

    private fun evaluateSessionAndTiming() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            // Read JWT from storage
            val token = repo.store.token()
            val hasValidToken = !token.isNullOrBlank()

            val target = if (hasValidToken) {
                SplashNavigationTarget.Dashboard
            } else {
                SplashNavigationTarget.Login
            }

            // Enforce minimum visible duration of ~1200ms
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = (1200L - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining)
            }

            // Ready to transition
            _isLoading.value = false
            _isSystemSplashLoading.value = false
            _navigationEvent.emit(target)
        }
    }
}
