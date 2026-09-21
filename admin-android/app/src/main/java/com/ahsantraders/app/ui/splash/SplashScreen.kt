package com.ahsantraders.app.ui.splash

/*
 * ============================================================================
 * AT Traders — Splash Screen (single file)
 * ============================================================================
 */

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ahsantraders.admin.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ============================================================================
// 1. BRAND PALETTE
// ============================================================================

val BrandGreen = Color(0xFF0E4429)
val BrandGold = Color(0xFFE3A72F)
val BrandWhite = Color(0xFFFFFFFF)
private val CircleFallback = Color(0xFF2A6B4A)

// ============================================================================
// 2. MODEL + REPOSITORY CONTRACT
// ============================================================================

data class BusinessBrand(
    val id: String,
    val label: String,
    val iconUrl: String?,
    val colorHex: String
)

enum class SplashDestination { Login, Dashboard }

interface SplashRepository {
    suspend fun readAccessToken(): String?
    suspend fun cachedBusinesses(): List<BusinessBrand>
    suspend fun refreshBusinesses()
}

// ============================================================================
// 3. VIEWMODEL
// ============================================================================

data class SplashUiState(
    val businesses: List<BusinessBrand> = emptyList(),
    val destination: SplashDestination? = null
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val repository: SplashRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SplashUiState())
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()

            val cached = runCatching { repository.cachedBusinesses() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(businesses = cached)

            val token = runCatching { repository.readAccessToken() }.getOrNull()
            val next = if (token.isNullOrBlank()) SplashDestination.Login else SplashDestination.Dashboard

            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (elapsed < MIN_VISIBLE_MS) delay(MIN_VISIBLE_MS - elapsed)

            _state.value = _state.value.copy(destination = next)
        }

        viewModelScope.launch { runCatching { repository.refreshBusinesses() } }
    }

    private companion object {
        const val MIN_VISIBLE_MS = 1400L
    }
}

// ============================================================================
// 4. UI
// ============================================================================

@Composable
fun SplashScreen(
    onNavigate: (SplashDestination) -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let(onNavigate)
    }

    SplashContent(businesses = state.businesses)
}

@Composable
fun SplashContent(businesses: List<BusinessBrand>) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandGreen)
    ) {
        val monogramWidth = maxWidth * 0.34f
        val circleSize: Dp = (maxWidth * 0.23f).coerceIn(64.dp, 104.dp)

        val businessCount = if (businesses.isEmpty()) 3 else businesses.size

        AnimatedVisibility(visible = visible, enter = fadeIn(tween(300))) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.16f))

                // NOTE: Using ic_brand as fallback if ic_at_monogram is missing
                Image(
                    painter = painterResource(R.drawable.logo_lockup),
                    contentDescription = null,
                    modifier = Modifier.width(monogramWidth)
                )

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "AHSAN",
                    color = BrandWhite,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif, // Replace with FontFamily(Font(R.font.playfair_display_bold)) when font is added
                    letterSpacing = 2.sp
                )

                Text(
                    text = "TRADERS",
                    color = BrandGold,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif, // Replace with FontFamily(Font(R.font.playfair_display_bold)) when font is added
                    letterSpacing = 8.sp
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "$businessCount Businesses   |   1 Vision",
                    color = BrandWhite,
                    fontSize = 16.sp
                )

                Spacer(Modifier.height(32.dp))

                if (businesses.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        businesses.forEach { business ->
                            BusinessBadge(business = business, size = circleSize)
                        }
                    }
                } else {
                    Spacer(Modifier.height(circleSize + 44.dp))
                }

                Spacer(Modifier.weight(1f))

                Text(
                    text = "Grow Together\nWith Trust",
                    color = BrandWhite,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = try { FontFamily(Font(R.font.script_font)) } catch(e: Exception) { FontFamily.Cursive } // Uses existing script_font.ttf
                )

                Spacer(Modifier.weight(0.22f))
            }
        }
    }
}

@Composable
private fun BusinessBadge(business: BusinessBrand, size: Dp) {
    val circleColor = remember(business.colorHex) { business.colorHex.toColorOrNull() ?: CircleFallback }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            if (!LocalInspectionMode.current && business.iconUrl != null) {
                AsyncImage(
                    model = business.iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = business.label.toTwoLines(),
            color = BrandWhite,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================================================
// 5. HELPERS
// ============================================================================

private fun String.toColorOrNull(): Color? =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrNull()

private fun String.toTwoLines(): String =
    substringBefore(' ') + if (contains(' ')) "\n" + substringAfter(' ') else ""

// ============================================================================
// 6. PREVIEWS
// ============================================================================

private val previewBusinesses = listOf(
    BusinessBrand("1", "Chicken Shop", null, "#B4131C"),
    BusinessBrand("2", "Broiler Farming", null, "#14713A"),
    BusinessBrand("3", "LPG Business", null, "#1355C4")
)

@Preview(name = "Tall — loaded", widthDp = 360, heightDp = 800)
@Composable
private fun SplashLoadedTallPreview() = SplashContent(previewBusinesses)

@Preview(name = "Short — loaded", widthDp = 360, heightDp = 640)
@Composable
private fun SplashLoadedShortPreview() = SplashContent(previewBusinesses)

@Preview(name = "Cold start — no data yet", widthDp = 360, heightDp = 800)
@Composable
private fun SplashEmptyPreview() = SplashContent(emptyList())
