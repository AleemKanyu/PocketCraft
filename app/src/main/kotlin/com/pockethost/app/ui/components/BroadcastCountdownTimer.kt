package com.pockethost.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Animated countdown timer that ticks every second. Shows HH:MM:SS or MM:SS depending on
 * the time remaining. Each digit flips with a smooth vertical slide animation, and the
 * overall timer has a subtle pulsing glow ring. Hidden once the countdown reaches zero.
 *
 * @param expiresAtMs   Unix epoch milliseconds when the timer expires (from Firestore Timestamp.toDate().time).
 * @param accentColor   Color used for the digit backgrounds and glow ring.
 * @param label         Optional small label shown above the timer (e.g. "Ends in").
 * @param modifier      Outer modifier for positioning.
 */
@Composable
fun BroadcastCountdownTimer(
    expiresAtMs: Long,
    accentColor: Color,
    label: String = "Ends in",
    modifier: Modifier = Modifier
) {
    var remainingMs by remember { mutableLongStateOf((expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L)) }

    LaunchedEffect(expiresAtMs) {
        while (true) {
            val diff = expiresAtMs - System.currentTimeMillis()
            remainingMs = diff.coerceAtLeast(0L)
            if (diff <= 0L) break
            delay(1000L)
        }
    }

    if (remainingMs <= 0L) return

    val totalSeconds = (remainingMs / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    // Pulsing glow ring
    val infiniteTransition = rememberInfiniteTransition(label = "timer_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val digitBg = accentColor.copy(alpha = 0.18f)
    val digitText = accentColor

    // Label + digits in a single left-aligned row so it flows naturally
    // under the banner body text without floating in the centre.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "Ends in" label — subtle chip so it doesn't overpower the banner
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = accentColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor.copy(alpha = 0.8f),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        // Digit pairs — no outer glow Box, just a clean inline row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (hours > 0) {
                DigitPair(value = hours, bg = digitBg, textColor = digitText)
                TimerSeparator(color = digitText)
            }
            DigitPair(value = minutes, bg = digitBg, textColor = digitText)
            TimerSeparator(color = digitText)
            DigitPair(value = seconds, bg = digitBg, textColor = digitText)
        }
    }
}

/** Renders a two-digit animated block (e.g. "04"). Each digit flips independently. */
@Composable
private fun DigitPair(value: Int, bg: Color, textColor: Color, digitSize: Dp = 22.dp) {
    val tens = value / 10
    val units = value % 10
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        SingleDigit(digit = tens, bg = bg, textColor = textColor, size = digitSize)
        SingleDigit(digit = units, bg = bg, textColor = textColor, size = digitSize)
    }
}

/** A single animated digit block with vertical slide flip on change. */
@Composable
private fun SingleDigit(digit: Int, bg: Color, textColor: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(width = size, height = size + 6.dp)
            .background(color = bg, shape = RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = digit,
            transitionSpec = {
                slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ) togetherWith slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                )
            },
            label = "digit_$digit"
        ) { d ->
            Text(
                text = d.toString(),
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp
            )
        }
    }
}

/** The colon separator between digit pairs. Pulses slightly. */
@Composable
private fun TimerSeparator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "sep_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sep_alpha"
    )
    Text(
        text = ":",
        color = color.copy(alpha = alpha),
        fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}
