package com.pocketcraft.server.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pocketcraft.server.ui.theme.Monocraft
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.R
import kotlin.math.roundToInt

@Composable
fun SplashScreen(
    progress: Float,
    status: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260),
        label = "splash_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF8FFF8),
                        Color(0xFFEFF9EF)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        GameCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.minecraft_api_diamond_pickaxe_hd),
                    contentDescription = "Pickaxe",
                    modifier = Modifier.height(78.dp)
                )
                Text(
                    text = "PocketCraft",
                    fontFamily = Monocraft,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp,
                    color = PocketColors.Primary
                )
                Text(
                    text = "PREPARING YOUR SERVER ENGINE",
                    color = Color(0xFF9FE6A6),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    color = PocketColors.Primary,
                    trackColor = Color(0xFF122512)
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${(animatedProgress * 100f).roundToInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = PocketColors.PrimaryDark,
                        fontSize = 14.sp
                    )
                    Text(
                        text = status,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFB8F5BE),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}
