package com.pocketcraft.server.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.billing.PremiumEntitlement
import com.pocketcraft.server.billing.PremiumTier
import com.pocketcraft.server.ui.theme.Monocraft
import java.util.Random

private data class CelebConfetti(
    var x: Float,
    var y: Float,
    val color: Color,
    val size: Float,
    var speedX: Float,
    var speedY: Float,
    var rotation: Float,
    val rotationSpeed: Float
)

private val premiumPerks = listOf(
    "Custom IP for easy server joining" to "🌐",
    "Unlimited AFK Bots" to "🤖",
    "Support for up to 50 players" to "👥",
    "More world slots to design & test" to "🗺️",
    "Full live operator chat access" to "💬",
    "Custom server theme" to "🎨"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeCelebrationDialog(
    entitlement: PremiumEntitlement,
    onDismissRequest: () -> Unit
) {
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }
    val particles = remember { mutableStateListOf<CelebConfetti>() }
    val scale = remember { Animatable(0.85f) }

    val infiniteTransition = rememberInfiniteTransition(label = "crown_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val crownScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "crown_scale"
    )

    LaunchedEffect(canvasWidth, canvasHeight) {
        if (canvasWidth == 0 || canvasHeight == 0) return@LaunchedEffect
        val random = Random()

        repeat(80) {
            particles.add(
                CelebConfetti(
                    x = canvasWidth / 2f,
                    y = canvasHeight * 0.3f,
                    color = listOf(
                        Color(0xFFFFD700),
                        Color(0xFFAB47BC),
                        Color(0xFF7B1FA2),
                        Color(0xFF29B6F6),
                        Color(0xFFFF6F00)
                    ).random(),
                    size = (10..24).random().toFloat(),
                    speedX = (random.nextFloat() - 0.5f) * 700f,
                    speedY = (random.nextFloat() - 0.8f) * 850f,
                    rotation = random.nextFloat() * 360f,
                    rotationSpeed = (random.nextFloat() - 0.5f) * 320f
                )
            )
        }

        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
        )

        var lastTime = System.nanoTime()
        while (true) {
            withFrameMillis {
                val now = System.nanoTime()
                val dt = (now - lastTime) / 1_000_000_000f
                lastTime = now
                particles.forEach { p ->
                    p.x += p.speedX * dt
                    p.y += p.speedY * dt
                    p.speedY += 420f * dt
                    p.rotation += p.rotationSpeed * dt
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { IosDragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    canvasWidth = it.width
                    canvasHeight = it.height
                }
        ) {
            // Confetti canvas behind the sheet content
            if (canvasWidth > 0 && canvasHeight > 0) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    particles.forEach { p ->
                        rotate(p.rotation, pivot = androidx.compose.ui.geometry.Offset(p.x, p.y)) {
                            drawRect(
                                color = p.color.copy(alpha = 0.8f),
                                topLeft = androidx.compose.ui.geometry.Offset(p.x - p.size / 2f, p.y - p.size / 2f),
                                size = androidx.compose.ui.geometry.Size(p.size, p.size)
                            )
                        }
                    }
                }
            }

            // Sheet surface
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale.value)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val headline = if (entitlement.tier == PremiumTier.SUPPORTIVE) {
                    "WELCOME, MEMBER!"
                } else {
                    "WELCOME TO PRO!"
                }
                val subtitle = if (entitlement.tier == PremiumTier.SUPPORTIVE) {
                    "Your Member plan is active and linked to your account. Thank you for supporting PocketCraft."
                } else {
                    "Your Pro subscription is active and linked to your account."
                }
                // ── Premium gradient header band ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF4A148C), // Deep purple
                                    Color(0xFF7B1FA2),
                                    Color(0xFFAB47BC),
                                    Color(0xFFFF6F00), // Amber
                                    Color(0xFFFFB300)  // Gold
                                )
                            ),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Glowing crown
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(crownScale)
                                .background(
                                    Color.White.copy(alpha = glowAlpha * 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👑", fontSize = 34.sp)
                        }
                        Text(
                            text = headline,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = Monocraft,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Subtitle ──
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // ── Perks list ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF4A148C).copy(alpha = 0.08f),
                                    Color(0xFFFFB300).copy(alpha = 0.06f)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFAB47BC).copy(alpha = 0.3f), Color(0xFFFFB300).copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🚀 Pro Features Unlocked",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Monocraft,
                        color = Color(0xFFAB47BC)
                    )
                    premiumPerks.forEach { (perk, emoji) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF7B1FA2), Color(0xFFFF6F00))
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(emoji, fontSize = 14.sp)
                            Text(
                                text = perk,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── CTA button ──
                DuoButton(
                    text = "LET'S GO! 🚀",
                    onClick = onDismissRequest,
                    variant = DuoButtonVariant.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    minHeight = 50.dp
                )
            }
        }
    }
}
