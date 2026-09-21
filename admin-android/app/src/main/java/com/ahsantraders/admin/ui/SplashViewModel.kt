package com.ahsantraders.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahsantraders.admin.data.AdminRepository
import com.ahsantraders.admin.data.Business
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

sealed interface SplashUiState {
    data object Loading : SplashUiState
    data class Success(val businesses: List<Business>) : SplashUiState
    data class Error(val message: String) : SplashUiState
}

/**
 * Manages the splash screen state and session evaluation.
 * - Reads JWT token from encrypted SessionStore.
 * - Loads businesses from API for dynamic splash screen.
 * - Enforces minimum visible duration of 2500ms (2 to 3 seconds) to ensure brand visibility.
 * - Controls the system splash screen keep-on-screen condition (Layer 1).
 */
class SplashViewModel(private val repo: AdminRepository) : ViewModel() {

    // Layer 1 system splash hold condition
    private val _isSystemSplashLoading = MutableStateFlow(true)
    val isSystemSplashLoading = _isSystemSplashLoading.asStateFlow()

    // Layer 2 splash screen loading indicator
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // UI state for businesses loading
    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // Navigation event to navigate to Dashboard or Login
    private val _navigationEvent = MutableSharedFlow<SplashNavigationTarget>(replay = 1)
    val navigationEvent = _navigationEvent.asSharedFlow()
    
    val baseUrl: String get() = repo.store.baseUrl

    init {
        evaluateSessionAndTiming()
    }

    /** Called when Compose has rendered its first frame, releasing the Layer 1 splash screen. */
    fun onFirstFrameRendered() {
        _isSystemSplashLoading.value = false
    }

    /** Load businesses from API for dynamic splash screen */
    fun loadBusinesses() {
        viewModelScope.launch {
            try {
                // Only try to load businesses if we have a valid session
                val token = repo.store.token()
                if (!token.isNullOrBlank()) {
                    val businesses = repo.api.businesses()
                    _uiState.value = SplashUiState.Success(businesses)
                } else {
                    // No session, use fallback businesses
                    _uiState.value = SplashUiState.Success(getFallbackBusinesses())
                }
            } catch (e: Exception) {
                // If API fails, use fallback businesses
                _uiState.value = SplashUiState.Success(getFallbackBusinesses())
            }
        }
    }
    
    /** Get fallback businesses if API fails */
    private fun getFallbackBusinesses(): List<Business> {
        return listOf(
            Business(
                id = "chicken_fallback",
                name = "Chicken Shop",
                type = "CHICKEN",
                total_shares = 0,
                share_price = 0,
                stock = "",
                stock_cost = 0,
                icon_url = null
            ),
            Business(
                id = "lpg_fallback",
                name = "LPG Business",
                type = "LPG",
                total_shares = 0,
                share_price = 0,
                stock = "",
                stock_cost = 0,
                icon_url = null
            ),
            Business(
                id = "broiler_fallback",
                name = "Broiler Farming",
                type = "BROILER",
                total_shares = 0,
                share_price = 0,
                stock = "",
                stock_cost = 0,
                icon_url = null
            )
        )
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

            // Enforce visible duration of 2500ms (2 to 3 seconds)
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = (2500L - elapsed).coerceAtLeast(0L)
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
