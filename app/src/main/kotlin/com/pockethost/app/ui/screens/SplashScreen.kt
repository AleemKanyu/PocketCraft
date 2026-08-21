package com.pockethost.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.R
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PocketMotion
import com.pockethost.app.ui.theme.PlayfairDisplay
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.CustomThemePalette
import com.pockethost.app.ui.util.MobTheme

private data class SplashMascot(
    val drawableRes: Int,
    val size: Dp,
    val offsetX: Dp,
    val offsetY: Dp,
    val rotation: Float,
    val alpha: Float
)

private val splashMascots = listOf(
    SplashMascot(R.drawable.cube_logo_dark, 36.dp, 24.dp, 96.dp, -14f, 0.07f),
    SplashMascot(R.drawable.ic_mods_pixel, 28.dp, 300.dp, 140.dp, 18f, 0.06f),
    SplashMascot(R.drawable.ic_world_pixel, 32.dp, 16.dp, 520.dp, 10f, 0.06f),
    SplashMascot(R.drawable.ic_pickaxe_pixel, 30.dp, 280.dp, 580.dp, -20f, 0.07f),
    SplashMascot(R.drawable.ic_netherite_chestplate_hd, 26.dp, 48.dp, 660.dp, 8f, 0.05f),
    SplashMascot(R.drawable.cube_logo_dark, 40.dp, 220.dp, 720.dp, 16f, 0.06f)
)

@Composable
private fun SplashMascotBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_drift")
    
    val driftY1 by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift_y1"
    )
    val driftY2 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift_y2"
    )
    val slowRotation by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slow_rotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        splashMascots.forEachIndexed { index, mascot ->
            val dy = if (index % 2 == 0) driftY1 else driftY2
            val dr = if (index % 3 == 0) slowRotation else -slowRotation
            
            Image(
                painter = painterResource(id = mascot.drawableRes),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = mascot.offsetX, y = mascot.offsetY + dy.dp)
                    .size(mascot.size)
                    .rotate(mascot.rotation + dr)
                    .alpha(mascot.alpha)
            )
        }
    }
}

@Composable
fun SplashScreen(
    progress: Float,
    status: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue   = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260),
        label         = "splash_progress"
    )
    // Rich dark greenish tone background
    val baseColor = Color(0xFF0D1610)
    val centerColor = Color(0xFF132317)

    val splashBackground = Brush.verticalGradient(
        colors = listOf(
            baseColor,
            centerColor,
            baseColor
        )
    )

    val titleColor = when (PocketColors.activeMobTheme) {
        MobTheme.SKELETON -> Color(0xFFF0EBE0)
        MobTheme.CREEPER -> Color(0xFFEFF7EF)
        MobTheme.SIMPLE_WHITE, MobTheme.SIMPLE_DARK -> Color(0xFFEFF7EF)
        MobTheme.CUSTOM -> CustomThemePalette.textPrimary
    }

    val statusColor = when (PocketColors.activeMobTheme) {
        MobTheme.SKELETON -> Color(0xFF8A94A8)
        MobTheme.CREEPER -> Color(0xFF8AAA8A)
        MobTheme.SIMPLE_WHITE, MobTheme.SIMPLE_DARK -> Color(0xFF8AAA8A)
        MobTheme.CUSTOM -> CustomThemePalette.textSecondary
    }

    val progressColor = when (PocketColors.activeMobTheme) {
        MobTheme.SKELETON -> Color(0xFF7A8490)
        MobTheme.CREEPER -> Color(0xFF4ADE80)
        MobTheme.SIMPLE_WHITE, MobTheme.SIMPLE_DARK -> Color(0xFF4ADE80)
        MobTheme.CUSTOM -> CustomThemePalette.primary
    }

    val trackColor = when (PocketColors.activeMobTheme) {
        MobTheme.SKELETON -> Color(0xFF2E3440)
        MobTheme.CREEPER -> Color(0xFF2C362C)
        MobTheme.SIMPLE_WHITE, MobTheme.SIMPLE_DARK -> Color(0xFF2C362C)
        MobTheme.CUSTOM -> CustomThemePalette.inactiveBg
    }

    val pulseTransition = rememberInfiniteTransition(label = "logo_pulse")
    val logoScale by pulseTransition.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = PocketMotion.SmoothEase),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    // Entrance animations
    var startEntrance by remember { mutableStateOf(false) }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (startEntrance) 1f else 0f,
        animationSpec = PocketMotion.softFloatTween(durationMillis = 700),
        label = "entrance_alpha"
    )
    val entranceScale by animateFloatAsState(
        targetValue = if (startEntrance) 1f else 0.94f,
        animationSpec = PocketMotion.softFloatTween(durationMillis = 760),
        label = "entrance_scale"
    )
    val entranceSlide by animateFloatAsState(
        targetValue = if (startEntrance) 0f else 16f,
        animationSpec = PocketMotion.softFloatTween(durationMillis = 760),
        label = "entrance_slide"
    )

    LaunchedEffect(Unit) {
        startEntrance = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashBackground),
        contentAlignment = Alignment.Center
    ) {
        SplashMascotBackground()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .graphicsLayer(
                    alpha = entranceAlpha,
                    scaleX = entranceScale,
                    scaleY = entranceScale,
                    translationY = entranceSlide
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.cube_logo_dark),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "PocketHost",
                    fontFamily = Monocraft,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = titleColor,
                    letterSpacing = 0.sp
                )

                Spacer(modifier = Modifier.size(8.dp))

                Box(
                    modifier = Modifier
                        .rotate(-8f)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF059669), Color(0xFF047857))
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(1.5.dp, Color(0xFFA7F3D0), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PH",
                        fontFamily = Monocraft,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }

            Text(
                text = status,
                modifier = Modifier.padding(top = 10.dp),
                color = statusColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = PlayfairDisplay
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = progressColor,
                trackColor = trackColor
            )
        }
    }
}
