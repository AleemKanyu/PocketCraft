package com.pockethost.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.billing.PremiumEntitlement
import com.pockethost.app.billing.PremiumTier
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PocketMotion
import kotlinx.coroutines.launch

private val premiumPerks = listOf(
    "Custom IP for easy server joining",
    "Unlimited AFK Bots",
    "Support for up to 50 players",
    "More world slots to design & test",
    "Full live operator chat access",
    "Custom server theme"
)

private val headerGradient = listOf(
    Color(0xFF4A148C), // Deep purple
    Color(0xFF7B1FA2),
    Color(0xFFAB47BC),
    Color(0xFFFF6F00), // Amber
    Color(0xFFFFB300)  // Gold
)

/**
 * Fades and lifts a section into place as [progress] runs 0f..1f, offsetting each
 * section by [index] so the sheet contents cascade in instead of popping at once.
 */
private fun Modifier.staggeredAppear(progress: Float, index: Int): Modifier = graphicsLayer {
    val local = ((progress - index * 0.07f) / 0.55f).coerceIn(0f, 1f)
    val eased = PocketMotion.SmoothEase.transform(local)
    alpha = eased
    translationY = (1f - eased) * 30.dp.toPx()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeCelebrationDialog(
    entitlement: PremiumEntitlement,
    onDismissRequest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appear = remember { Animatable(0f) }

    // Play the sheet's own slide-down before the caller drops us out of composition,
    // otherwise the popup just blinks away.
    fun dismiss() {
        scope.launch { sheetState.hide() }
            .invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "celebration")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val badgeScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_scale"
    )
    val raysAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rays_angle"
    )
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "header_shimmer"
    )

    LaunchedEffect(Unit) {
        // Linear driver: staggeredAppear applies the easing per section.
        appear.animateTo(1f, tween(durationMillis = 780, easing = LinearEasing))
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { IosDragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        val isMember = entitlement.tier == PremiumTier.SUPPORTIVE
        val headline = if (isMember) "WELCOME, MEMBER!" else "WELCOME TO PRO!"
        val badgeLabel = if (isMember) "MEMBER PLAN ACTIVE" else "PRO PLAN ACTIVE"
        val subtitle = if (isMember) {
            "Your Member plan is active and linked to your account. Thank you for supporting PocketCraft."
        } else {
            "Your Pro subscription is active and linked to your account."
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Gradient header band ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(brush = Brush.linearGradient(colors = headerGradient))
                    .staggeredAppear(appear.value, 0),
                contentAlignment = Alignment.Center
            ) {
                // Soft light pool behind the badge + a slow shimmer sweep.
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f * glowAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, size.height * 0.36f),
                            radius = size.width * 0.42f
                        ),
                        radius = size.width * 0.42f,
                        center = Offset(size.width / 2f, size.height * 0.36f)
                    )

                    // Rotating light rays behind the crown badge.
                    val center = Offset(size.width / 2f, size.height * 0.36f)
                    rotate(raysAngle, pivot = center) {
                        repeat(12) { i ->
                            rotate(i * 30f, pivot = center) {
                                drawRect(
                                    color = Color.White.copy(alpha = 0.07f * glowAlpha),
                                    topLeft = Offset(center.x - 1.5f, center.y - size.height * 0.5f),
                                    size = Size(3f, size.height * 0.5f)
                                )
                            }
                        }
                    }

                    val sweepX = shimmer * (size.width * 1.7f) - size.width * 0.35f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            start = Offset(sweepX, 0f),
                            end = Offset(sweepX + size.width * 0.32f, size.height)
                        )
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(badgeScale)
                            .background(Color.White.copy(alpha = 0.16f), CircleShape)
                            .border(
                                width = 1.5.dp,
                                color = Color.White.copy(alpha = 0.30f + 0.20f * glowAlpha),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Text(
                        text = headline,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Monocraft,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )

                    // Small confirmation pill so the sheet states what happened.
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Monocraft,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Subtitle ──
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .staggeredAppear(appear.value, 1)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Perks card ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .staggeredAppear(appear.value, 2)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF7B1FA2).copy(alpha = 0.10f),
                                Color(0xFFFFB300).copy(alpha = 0.07f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFAB47BC).copy(alpha = 0.32f),
                                Color(0xFFFFB300).copy(alpha = 0.32f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Text(
                    text = "PRO FEATURES UNLOCKED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Monocraft,
                    letterSpacing = 1.2.sp,
                    color = Color(0xFFAB47BC)
                )

                premiumPerks.forEachIndexed { index, perk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredAppear(appear.value, 3 + index),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
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
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = perk,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ── CTA ──
            DuoButton(
                text = "LET'S GO!",
                onClick = { dismiss() },
                variant = DuoButtonVariant.Pro,
                backgroundBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF7B1FA2),
                        Color(0xFFAB47BC),
                        Color(0xFFFF6F00)
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .staggeredAppear(appear.value, 9),
                minHeight = 52.dp
            )
        }
    }
}
