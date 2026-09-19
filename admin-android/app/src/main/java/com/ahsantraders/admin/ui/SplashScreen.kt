package com.ahsantraders.admin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahsantraders.admin.R

val ScriptFontFamily: FontFamily = FontFamily(
    Font(R.font.script_font, FontWeight.Normal, FontStyle.Italic)
)

/**
 * Layer 2: Jetpack Compose Splash Screen.
 * Displays centered AT brand logo, subtitle, 3 colored sector circles, and tagline.
 */
@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigate: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isShortScreen = screenHeight < 680.dp

    // Responsive dimensions scaled relative to screen width (NOT hardcoded dp)
    val logoWidth = screenWidth * 0.60f
    val circleSize = (screenWidth * 0.17f).coerceIn(52.dp, 76.dp)

    // Fade-in animation state (200ms duration)
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onFirstFrameRendered()
        contentVisible = true
    }

    // Collect navigation events triggered after session evaluation and 1200ms delay
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { target ->
            when (target) {
                SplashNavigationTarget.Dashboard -> onNavigate(Route.Dashboard)
                SplashNavigationTarget.Login -> onNavigate(Route.Login)
            }
        }
    }

    // Full-screen background in brand dark green
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandGreen),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = if (isShortScreen) 16.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top balancing spacer
                Spacer(modifier = Modifier.weight(if (isShortScreen) 0.5f else 1f))

                // Centered Hero Section: Logo Lockup + Subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isShortScreen) 10.dp else 16.dp)
                ) {
                    // Logo Lockup: AT mark + AHSAN TRADERS transparent PNG (~60% screen width)
                    Image(
                        painter = painterResource(R.drawable.logo_lockup),
                        contentDescription = "Ahsan Traders Logo",
                        modifier = Modifier
                            .width(logoWidth)
                            .heightIn(max = if (isShortScreen) 90.dp else 125.dp),
                        contentScale = ContentScale.Fit
                    )

                    // Subtitle: "3 Businesses  |  1 Vision"
                    Text(
                        text = "3 Businesses  |  1 Vision",
                        color = BrandWhite,
                        fontSize = if (isShortScreen) 12.sp else 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.weight(if (isShortScreen) 0.8f else 1.2f))

                // Sector Showcase: Three colored circular badges
                // Order: Red = Chicken Shop, Blue = LPG (Gas), Green = Broiler (Poultry)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    SectorCircleItem(
                        circleSize = circleSize,
                        circleColor = ChickenRed,
                        iconRes = R.drawable.ic_sector_chicken,
                        label = "Chicken\nShop"
                    )
                    SectorCircleItem(
                        circleSize = circleSize,
                        circleColor = LpgBlue,
                        iconRes = R.drawable.ic_sector_lpg,
                        label = "LPG\nBusiness"
                    )
                    SectorCircleItem(
                        circleSize = circleSize,
                        circleColor = BroilerGreen,
                        iconRes = R.drawable.ic_sector_broiler,
                        label = "Poultry\nFarm"
                    )
                }

                Spacer(modifier = Modifier.weight(if (isShortScreen) 1f else 1.5f))

                // Tagline at the bottom in custom script / italic font
                Text(
                    text = "Grow Together With Trust",
                    color = BrandWhite,
                    fontFamily = ScriptFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = if (isShortScreen) 17.sp else 20.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

/**
 * Individual circular sector item with scaled diameter, white icon, and two-line label.
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                modifier = Modifier.size(circleSize * 0.52f)
            )
        }

        Text(
            text = label,
            color = BrandWhite,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp
        )
    }
}
