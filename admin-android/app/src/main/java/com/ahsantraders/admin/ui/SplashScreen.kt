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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahsantraders.admin.R
import kotlinx.coroutines.delay

/** Curvy script used for the footer slogan — the one decorative accent on this screen. */
val ScriptFontFamily: FontFamily = try {
    FontFamily(Font(R.font.script_font, FontWeight.Normal))
} catch (e: Exception) {
    FontFamily.Cursive
}

private data class SplashBusinessItem(
    val id: String,
    val name: String,
    val label: String,
    val logoRes: Int,
    val color: Color
)

private val splashBusinesses = listOf(
    SplashBusinessItem("chicken", "Chicken", "Chicken\nShop", R.drawable.chicken_business_logo, ChickenRed),
    SplashBusinessItem("lpg", "LPG", "LPG\nBusiness", R.drawable.gas_business_logo, LpgBlue),
    SplashBusinessItem("broiler", "Broiler", "Broiler\nFarming", R.drawable.poltary_fram_busniess_logo, BroilerGreen)
)

/**
 * Screen 1: Jetpack Compose Splash Screen (First and Only Splash).
 *
 * Displays Ahsan Traders branding with the bundled business logos:
 *   - Logo lockup / brand title
 *   - "3 Businesses | 1 Vision"
 *   - Sector circles for Chicken, LPG, and Broiler using the uploaded logos
 *   - "Grow Together With Trust" slogan
 *   - "Tap to continue" indicator
 *
 * Interaction:
 *   - Logged in users: auto-navigates to Dashboard after 1 second
 *   - Logged out users: waits for user tap anywhere on the screen to navigate to Login
 */
@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigate: (SplashNavigationTarget) -> Unit
) {
    val isShortScreen = LocalConfiguration.current.screenHeightDp.dp < 680.dp

    var heroVisible by remember { mutableStateOf(false) }
    var circlesVisible by remember { mutableStateOf(false) }
    var footerVisible by remember { mutableStateOf(false) }
    var tapIndicatorVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        heroVisible = true
        delay(160)
        circlesVisible = true
        delay(120)
        footerVisible = true
        delay(200)
        tapIndicatorVisible = true
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { target ->
            onNavigate(target)
        }
    }

    // Full-screen canvas in deep forest green - tapping proceeds to login
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashForestGreen)
            .clickable {
                viewModel.navigateToLogin()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(0.1f))

            // ---- Hero: Brand Logo + Subtitle ----
            StageIn(visible = heroVisible, delayMillis = 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isShortScreen) 8.dp else 12.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo_lockup),
                        contentDescription = "Ahsan Traders Logo",
                        modifier = Modifier
                            .width((LocalConfiguration.current.screenWidthDp * 0.62f).dp)
                            .heightIn(max = if (isShortScreen) 86.dp else 116.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = "3 Businesses  |  1 Vision",
                        color = BrandWhite,
                        fontSize = if (isShortScreen) 13.sp else 15.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isShortScreen) 20.dp else 32.dp))

            // ---- Business Sectors: Using uploaded business logos ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                val circleSize: Dp = ((LocalConfiguration.current.screenWidthDp * 0.20f).dp).coerceIn(62.dp, 82.dp)

                splashBusinesses.forEachIndexed { index, item ->
                    val delay = index * 100
                    StageIn(visible = circlesVisible, delayMillis = delay) {
                        SplashBusinessCircleItem(
                            circleSize = circleSize,
                            circleColor = item.color,
                            logoRes = item.logoRes,
                            label = item.label
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isShortScreen) 20.dp else 32.dp))

            // ---- Footer slogan in cursive/script font ----
            StageIn(visible = footerVisible, delayMillis = 0) {
                Text(
                    text = "Grow Together With Trust",
                    color = BrandGold,
                    fontFamily = ScriptFontFamily,
                    style = TextStyle(fontStyle = FontStyle.Italic),
                    fontSize = if (isShortScreen) 17.sp else 21.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // ---- Tap to continue indicator ----
            StageIn(visible = tapIndicatorVisible, delayMillis = 0) {
                Text(
                    text = "Tap to continue",
                    color = BrandWhite.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

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

@Composable
private fun SplashBusinessCircleItem(
    circleSize: Dp,
    circleColor: Color,
    logoRes: Int,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(circleSize + 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(logoRes),
                contentDescription = label,
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        Text(
            text = label,
            color = BrandWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
            maxLines = 2
        )
    }
}
