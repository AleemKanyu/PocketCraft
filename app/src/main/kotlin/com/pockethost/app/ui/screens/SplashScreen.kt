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
import androidx.compose.foundation.shape.CircleShape
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
internal fun SplashMascotBackground() {
    val isDark = com.pockethost.app.ui.theme.pocketIsDarkTheme()
    val cubeRes = R.drawable.cube_logo_light
    val mascots = remember(cubeRes) {
        listOf(
            SplashMascot(cubeRes, 36.dp, 24.dp, 96.dp, -14f, 0.08f),
            SplashMascot(R.drawable.ic_mods_pixel, 28.dp, 300.dp, 140.dp, 18f, 0.06f),
            SplashMascot(R.drawable.ic_world_pixel, 32.dp, 16.dp, 520.dp, 10f, 0.06f),
            SplashMascot(R.drawable.ic_pickaxe_pixel, 30.dp, 280.dp, 580.dp, -20f, 0.07f),
            SplashMascot(R.drawable.ic_netherite_chestplate_hd, 26.dp, 48.dp, 660.dp, 0.05f, 0.05f),
            SplashMascot(cubeRes, 40.dp, 220.dp, 720.dp, 16f, 0.08f)
        )
    }

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
        mascots.forEachIndexed { index, mascot ->
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
    val isDark = com.pockethost.app.ui.theme.pocketIsDarkTheme()
    val baseColor = PocketColors.BgApp
    val surfaceColor = PocketColors.SurfaceCard

    val splashBackground = Brush.verticalGradient(
        colors = listOf(
            baseColor,
            surfaceColor,
            baseColor
        )
    )

    val titleColor = PocketColors.TextPrimary
    val statusColor = PocketColors.TextSecondary
    val progressColor = PocketColors.Primary
    val trackColor = PocketColors.InactiveBorder
    val logoRes = R.drawable.cube_logo_light

    val floatTransition = rememberInfiniteTransition(label = "logo_float")
    val floatY by floatTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_float_y"
    )
    val floatRotate by floatTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_rotate"
    )
    val haloPulse by floatTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )
    val haloAlpha by floatTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )
    val logoScale by floatTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
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
        targetValue = if (startEntrance) 1f else 0.88f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "entrance_scale"
    )
    val entranceSlide by animateFloatAsState(
        targetValue = if (startEntrance) 0f else 20f,
        animationSpec = PocketMotion.softFloatTween(durationMillis = 760),
        label = "entrance_slide"
    )

    LaunchedEffect(Unit) {
        startEntrance = true
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density

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
            Box(
                modifier = Modifier.size(88.dp),
                contentAlignment = Alignment.Center
            ) {
                // Soft ambient glowing halo
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .graphicsLayer {
                            scaleX = haloPulse
                            scaleY = haloPulse
                            alpha = haloAlpha
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    PocketColors.Primary,
                                    PocketColors.Primary.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // 3D floating bobbing cube logo
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = null,
                    // Tint with the theme's text colour so the black cube stays visible on dark themes.
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(titleColor),
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            translationY = floatY * density
                            rotationZ = floatRotate
                            scaleX = logoScale
                            scaleY = logoScale
                        }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                            color = PocketColors.Primary,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(1.5.dp, PocketColors.PrimaryBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PH",
                        fontFamily = Monocraft,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = PocketColors.PrimaryText,
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
