package com.ahsantraders.admin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.ahsantraders.admin.R
import kotlinx.coroutines.delay

/** Curvy script used for the footer slogan — the one decorative accent on this screen. */
val ScriptFontFamily: FontFamily = FontFamily(
    Font(R.font.script_font, FontWeight.Normal)
)

/**
 * Layer 2: Jetpack Compose Splash Screen.
 *
 * A single, centered brand composition on the deep forest-green canvas:
 *   logo lockup → "3 Businesses | 1 Vision" → three sector circles → script footer.
 *
 * **Dynamic version**: Business icons are loaded from API instead of being hardcoded.
 *
 * Design decisions (per the UI/UX analysis):
 *   • One main composition — content is centered and vertically dense instead of
 *     stretched to all four edges, so the footer never collides with the
 *     navigation bar on short / low-aspect-ratio screens.
 *   • Two typefaces only — the default sans-serif family for the header/body and
 *     the script font for the footer slogan.
 *   • Simple sequential entrance — the hero fades in, then the three sector
 *     circles stagger in (Chicken → LPG → Broiler) for a lightweight 2-second
 *     launch feel; no looping or springy motion.
 */
@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigate: (String) -> Unit
) {
    val isShortScreen = LocalConfiguration.current.screenHeightDp.dp < 680.dp
    val uiState by viewModel.uiState.collectAsState()

    // Sequential entrance stages: hero → sector circles → footer.
    var heroVisible by remember { mutableStateOf(false) }
    var circlesVisible by remember { mutableStateOf(false) }
    var footerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onFirstFrameRendered()
        viewModel.loadBusinesses()
        heroVisible = true
        delay(160)
        circlesVisible = true
        delay(80)
        footerVisible = true
    }

    // Collect navigation events triggered by user tap or auto-navigate for logged-in users
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { target ->
            when (target) {
                SplashNavigationTarget.Dashboard -> onNavigate(Route.Dashboard)
                SplashNavigationTarget.Login -> onNavigate(Route.Login)
            }
        }
    }

    // Single centered canvas in deep forest green - clickable to navigate
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashForestGreen)
            .clickable {
                // Navigate to login when user taps the screen
                viewModel.navigateToLogin()
            },
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is SplashUiState.Loading -> {
                // Show loading indicator while fetching businesses
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = BrandGold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading businesses...",
                        color = BrandWhite,
                        fontSize = 14.sp
                    )
                }
            }
            is SplashUiState.Success -> {
                val businesses = (uiState as SplashUiState.Success).businesses
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // ---- Hero: logo lockup + subtitle ----
                    StageIn(visible = heroVisible, delayMillis = 0) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (isShortScreen) 8.dp else 12.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.logo_lockup),
                                contentDescription = "Ahsan Traders Logo",
                                modifier = Modifier
                                    .width((LocalConfiguration.current.screenWidthDp * 0.60f).dp)
                                    .heightIn(max = if (isShortScreen) 86.dp else 116.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = "${businesses.size} Businesses  |  1 Vision",
                                color = BrandWhite,
                                fontSize = if (isShortScreen) 12.sp else 14.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 2.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isShortScreen) 26.dp else 38.dp))

                    // ---- Dynamic sector showcase: loaded from API ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        val circleSize =
                            ((LocalConfiguration.current.screenWidthDp * 0.15f).dp).coerceIn(50.dp, 66.dp)
                        
                        businesses.forEachIndexed { index, business ->
                            val color = getBusinessColor(business.type)
                            val delay = index * 110
                            
                            StageIn(visible = circlesVisible, delayMillis = delay) {
                                DynamicBusinessCircleItem(
                                    circleSize = circleSize,
                                    circleColor = color,
                                    business = business,
                                    baseUrl = viewModel.baseUrl
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isShortScreen) 26.dp else 38.dp))

                    // ---- Footer slogan in the script font ----
                    StageIn(visible = footerVisible, delayMillis = 0) {
                        Text(
                            text = "Grow Together With Trust",
                            color = BrandGold,
                            fontFamily = ScriptFontFamily,
                            style = TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            fontSize = if (isShortScreen) 16.sp else 19.sp,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Tap to continue indicator
                    StageIn(visible = footerVisible, delayMillis = 200) {
                        Text(
                            text = "Tap to continue",
                            color = BrandWhite.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            is SplashUiState.Error -> {
                // Show error state with fallback to hardcoded businesses
                val fallbackBusinesses = getFallbackBusinesses()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Show fallback businesses if API fails
                    Image(
                        painter = painterResource(R.drawable.logo_lockup),
                        contentDescription = "Ahsan Traders Logo",
                        modifier = Modifier
                            .width((LocalConfiguration.current.screenWidthDp * 0.60f).dp)
                            .heightIn(max = if (isShortScreen) 86.dp else 116.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = "3 Businesses  |  1 Vision",
                        color = BrandWhite,
                        fontSize = if (isShortScreen) 12.sp else 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(if (isShortScreen) 26.dp else 38.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        val circleSize =
                            ((LocalConfiguration.current.screenWidthDp * 0.15f).dp).coerceIn(50.dp, 66.dp)
                        
                        fallbackBusinesses.forEachIndexed { index, business ->
                            val color = getBusinessColor(business.type)
                            val delay = index * 110
                            
                            StageIn(visible = circlesVisible, delayMillis = delay) {
                                SectorCircleItem(
                                    circleSize = circleSize,
                                    circleColor = color,
                                    iconRes = getFallbackIcon(business.type),
                                    label = business.name
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(if (isShortScreen) 26.dp else 38.dp))
                    
                    Text(
                        text = "Grow Together With Trust",
                        color = BrandGold,
                        fontFamily = ScriptFontFamily,
                        style = TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        fontSize = if (isShortScreen) 16.sp else 19.sp,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Tap to continue indicator
                    StageIn(visible = footerVisible, delayMillis = 200) {
                        Text(
                            text = "Tap to continue",
                            color = BrandWhite.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Fades a child in with an optional staggered delay. */
@Composable
private fun StageIn(
    visible: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible, delayMillis) {
        if (visible) {
            delay(delayMillis.toLong())
            show = true
        } else {
            show = false
        }
    }
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(durationMillis = 240)),
        exit = fadeOut(animationSpec = tween(durationMillis = 160))
    ) {
        content()
    }
}

/**
 * Dynamic business circle item that loads icon from API
 */
@Composable
private fun DynamicBusinessCircleItem(
    circleSize: Dp,
    circleColor: Color,
    business: com.ahsantraders.admin.data.Business,
    baseUrl: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(circleSize)
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            if (business.icon_url != null) {
                // Load icon from API
                val iconUrl = if (business.icon_url.startsWith("http")) {
                    business.icon_url
                } else {
                    "$baseUrl${business.icon_url}"
                }
                
                Image(
                    painter = rememberAsyncImagePainter(iconUrl),
                    contentDescription = business.name,
                    modifier = Modifier.size(circleSize * 0.5f),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Fallback to default icon based on business type
                val fallbackIcon = getFallbackIcon(business.type)
                Icon(
                    painter = painterResource(fallbackIcon),
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(circleSize * 0.5f)
                )
            }
        }
        Text(
            text = business.name,
            color = BrandWhite,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2
        )
    }
}

/**
 * Individual circular sector item with fixed diameter, white icon and two-line label.
 * Labels stay one weight/family (sans-serif) for consistency.
 */
@Composable
private fun SectorCircleItem(
    circleSize: Dp,
    circleColor: Color,
    iconRes: Int,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(circleSize)
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = BrandWhite,
                modifier = Modifier.size(circleSize * 0.5f)
            )
        }
        Text(
            text = label,
            color = BrandWhite,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2
        )
    }
}

/** Get color based on business type */
private fun getBusinessColor(type: String): Color {
    return when (type.lowercase()) {
        "chicken", "chicken shop" -> ChickenRed
        "lpg", "lpg business" -> LpgBlue
        "broiler", "broiler farming", "poultry" -> BroilerGreen
        else -> BrandWhite
    }
}

/** Get fallback icon based on business type */
private fun getFallbackIcon(type: String): Int {
    return when (type.lowercase()) {
        "chicken", "chicken shop" -> R.drawable.ic_sector_chicken
        "lpg", "lpg business" -> R.drawable.ic_sector_lpg
        "broiler", "broiler farming", "poultry" -> R.drawable.ic_sector_broiler
        else -> R.drawable.ic_sector_chicken // Default fallback
    }
}

/** Get fallback businesses if API fails */
private fun getFallbackBusinesses(): List<com.ahsantraders.admin.data.Business> {
    return listOf(
        com.ahsantraders.admin.data.Business(
            id = "chicken_fallback",
            name = "Chicken Shop",
            type = "CHICKEN",
            total_shares = 0,
            share_price = 0,
            stock = "",
            stock_cost = 0,
            icon_url = null
        ),
        com.ahsantraders.admin.data.Business(
            id = "lpg_fallback",
            name = "LPG Business",
            type = "LPG",
            total_shares = 0,
            share_price = 0,
            stock = "",
            stock_cost = 0,
            icon_url = null
        ),
        com.ahsantraders.admin.data.Business(
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
