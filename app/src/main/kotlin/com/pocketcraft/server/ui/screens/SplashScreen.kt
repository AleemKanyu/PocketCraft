package com.pocketcraft.server.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import com.pocketcraft.server.ui.theme.Monocraft
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.R

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
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backgroundBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                PocketColors.BgDark,
                Color(0xFF11211D),
                Color(0xFF0E1B18)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF8FBF7),
                Color(0xFFF1F7EE),
                Color(0xFFE6F1DF)
            )
        )
    }
    val titleColor = PocketColors.Primary
    val statusColor = if (isDarkTheme) Color(0xFF88C39B) else Color(0xFF6E9E70)
    val progressColor = if (isDarkTheme) Color(0xFF408A71) else Color(0xFF58CC02)
    val trackColor = if (isDarkTheme) Color(0xFF16362C) else Color(0xFFDCE7D6)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {

                // Main image layer
                Image(
                    painter = painterResource(id = R.drawable.minecraft_api_diamond_pickaxe_hd),
                    contentDescription = "Pickaxe",
                    modifier = Modifier
                        .height(76.dp)
                )
            }

            Text(
                text = "PocketCraft",
                modifier = Modifier.padding(top = 14.dp),
                fontFamily = Monocraft,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                color = titleColor,
                letterSpacing = 0.6.sp
            )

            Text(
                text = status,
                modifier = Modifier.padding(top = 8.dp),
                color = statusColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(0.62f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = progressColor,
                trackColor = trackColor
            )
        }
    }
}
