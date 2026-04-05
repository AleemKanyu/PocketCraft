package com.pocketcraft.server.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pocketcraft.server.ui.theme.Monocraft
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        Color(0xFFFAFDFA),
                        Color(0xFFF0F9EC),
                        Color(0xFFE8F4E0)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Radial glow background
        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x4D7DEB6B),
                            Color(0x1D7DEB6B),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.Center)
        )

        // Main content card - improved centering
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .weight(1f, fill = false),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFFFAFCF8),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFCCE0C2)),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Pickaxe
                    Image(
                        painter = painterResource(id = R.drawable.minecraft_api_diamond_pickaxe_hd),
                        contentDescription = "Pickaxe",
                        modifier = Modifier.height(74.dp)
                    )
                    
                    // Title
                    Text(
                        text = "PocketCraft",
                        fontFamily = Monocraft,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 29.sp,
                        color = PocketColors.Primary,
                        letterSpacing = 0.8.sp
                    )
                    
                    // Subtitle
                    Text(
                        text = "PREPARING YOUR SERVER ENGINE",
                        color = Color(0xFF7DBF85),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.8.sp
                    )

                    // Spacing
                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress label and percentage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Runtime setup",
                            color = Color(0xFF5B9D62),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${(animatedProgress * 100f).roundToInt()}%",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF5B9D62),
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = Color(0xFF58CC02),
                        trackColor = Color(0xFFDEEDD5)
                    )

                    // Status section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF0F9E8),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFD4E6C8))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF58CC02))
                                )
                                Text(
                                    text = "Initializing",
                                    color = Color(0xFF5B9D62),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                        
                        // Status text
                        Text(
                            text = status,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF7FAF86),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }
    }
}
