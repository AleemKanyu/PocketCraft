package com.pocketcraft.server.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.R
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PlayfairDisplay
import com.pocketcraft.server.ui.theme.PocketColors

private data class SplashMascot(
    val drawableRes: Int,
    val size: Dp,
    val offsetX: Dp,
    val offsetY: Dp,
    val rotation: Float,
    val alpha: Float
)

private val splashMascots = listOf(
    SplashMascot(R.drawable.ic_launcher_creeper, 36.dp, 24.dp, 96.dp, -14f, 0.07f),
    SplashMascot(R.drawable.ic_mods_pixel, 28.dp, 300.dp, 140.dp, 18f, 0.06f),
    SplashMascot(R.drawable.ic_world_pixel, 32.dp, 16.dp, 520.dp, 10f, 0.06f),
    SplashMascot(R.drawable.ic_pickaxe_pixel, 30.dp, 280.dp, 580.dp, -20f, 0.07f),
    SplashMascot(R.drawable.ic_netherite_chestplate_hd, 26.dp, 48.dp, 660.dp, 8f, 0.05f),
    SplashMascot(R.drawable.ic_launcher_creeper, 40.dp, 220.dp, 720.dp, 16f, 0.06f)
)

@Composable
private fun SplashMascotBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        splashMascots.forEach { mascot ->
            Image(
                painter = painterResource(id = mascot.drawableRes),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = mascot.offsetX, y = mascot.offsetY)
                    .size(mascot.size)
                    .rotate(mascot.rotation)
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
    val titleColor    = PocketColors.TextPrimary
    val statusColor   = PocketColors.TextSecondary
    val progressColor = PocketColors.Primary
    val trackColor    = PocketColors.InactiveBg
    val splashBackground = colorResource(id = R.color.splash_background)

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
        ) {
            Image(
                painter = painterResource(id = R.drawable.minecraft_api_diamond_pickaxe_hd),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PocketCraft",
                fontFamily = Monocraft,
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                color = titleColor,
                letterSpacing = 0.sp
            )

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
