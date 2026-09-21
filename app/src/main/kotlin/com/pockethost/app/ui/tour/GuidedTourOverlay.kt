package com.pockethost.app.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.ui.theme.PocketColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val ScrimColor = Color(0xE60A0D0A)

/**
 * Dims the whole app, cuts a hole around the current step's target and explains it
 * in a card placed next to that hole.
 *
 * Place this last inside the root box of the app so it covers every other layer,
 * including the top bar and the bottom navigation.
 */
@Composable
fun GuidedTourOverlay(
    controller: TourController,
    modifier: Modifier = Modifier,
    stepPredicate: ((TourStep) -> Boolean)? = null
) {
    val step = controller.currentStep ?: return
    if (stepPredicate != null && !stepPredicate(step)) return
    if (step.anchor != null && controller.boundsOf(step.anchor) == null) return
    val stepCount = controller.steps.size
    val stepIndex = controller.stepIndex

    val density = LocalDensity.current
    val spotlightPadPx = with(density) { step.spotlightPadding.toPx() }
    val cornerPx = with(density) { step.spotlightCornerRadius.toPx() }
    val target = step.anchor?.let { controller.boundsOf(it) }?.inflate(spotlightPadPx)
    val hasSpotlight = target != null && !target.isEmpty

    // Keep the last real target so the hole collapses in place instead of flying to
    // the top-left corner when a step without an anchor comes next.
    var settledTarget by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(target) { target?.let { settledTarget = it } }
    val holeRect = target ?: settledTarget

    val spotlight = rememberSpotlightAnimation(target)
    val pulse by rememberInfiniteTransition(label = "tour_pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tour_pulse_phase"
    )

    LaunchedEffect(step.key) {
        step.anchor?.let { controller.bringAnchorIntoView(it) }
    }

    BackHandler(enabled = controller.isRunning) {
        if (stepIndex > 0) controller.back() else controller.skip()
    }

    val systemBars = WindowInsets.systemBars
    val topInsetPx = systemBars.getTop(density)
    val bottomInsetPx = systemBars.getBottom(density)
    val passThrough = hasSpotlight
    val accent = tourAccentColor()

    Box(modifier = modifier.fillMaxSize()) {

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(color = ScrimColor)

            val alpha = spotlight.alpha.value
            if (alpha <= 0.01f) return@Canvas

            val hole = Rect(
                left = spotlight.left.value,
                top = spotlight.top.value,
                right = spotlight.right.value,
                bottom = spotlight.bottom.value
            )
            if (hole.width <= 0f || hole.height <= 0f) return@Canvas

            drawRoundRect(
                color = Color.Black.copy(alpha = alpha),
                topLeft = hole.topLeft,
                size = hole.size,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                blendMode = BlendMode.DstOut
            )

            // A steady outline plus one ring breathing outwards, so the eye lands on
            // the hole even when the highlighted control is itself brightly coloured.
            drawRoundRect(
                color = accent.copy(alpha = 0.9f * alpha),
                topLeft = hole.topLeft,
                size = hole.size,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = 2.dp.toPx())
            )
            val grow = 14.dp.toPx() * pulse
            drawRoundRect(
                color = accent.copy(alpha = (1f - pulse) * 0.55f * alpha),
                topLeft = Offset(hole.left - grow, hole.top - grow),
                size = Size(hole.width + grow * 2f, hole.height + grow * 2f),
                cornerRadius = CornerRadius(cornerPx + grow, cornerPx + grow),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        if (passThrough) {
            TouchBlockersAround(holeRect)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blockTouches(step.key) { controller.next() }
            )
        }

        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                TourTooltipCard(
                    step = step,
                    stepNumber = stepIndex + 1,
                    stepCount = stepCount,
                    accent = accent,
                    onNext = controller::next,
                    onBack = controller::back,
                    onSkip = controller::skip
                )
            }
        ) { measurables, constraints ->
            val margin = 16.dp.roundToPx()
            val gap = 18.dp.roundToPx()
            val card = measurables.first().measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0),
                    minHeight = 0,
                    maxHeight = constraints.maxHeight
                )
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                val effectiveTopInset = if (constraints.maxHeight < 2000 && holeRect.top < constraints.maxHeight * 0.7f) 0 else topInsetPx
                val topLimit = effectiveTopInset + margin
                val bottomLimit = constraints.maxHeight - bottomInsetPx - margin - card.height
                val y = if (!hasSpotlight) {
                    (constraints.maxHeight - card.height) / 2
                } else {
                    val below = holeRect.bottom.roundToInt() + gap
                    val above = holeRect.top.roundToInt() - gap - card.height
                    val spaceAbove = holeRect.top.roundToInt() - gap - topLimit
                    val spaceBelow = bottomLimit - (holeRect.bottom.roundToInt() + gap)
                    when {
                        step.anchor == TourAnchor.JAR_DOWNLOAD_BUTTON && above >= topLimit -> above
                        step.anchor == TourAnchor.DOWNLOAD_PROGRESS && above >= topLimit -> above
                        spaceAbove >= card.height && spaceAbove > spaceBelow -> above
                        below <= bottomLimit -> below
                        above >= topLimit -> above
                        else -> if (spaceAbove > spaceBelow) above else below
                    }
                }
                card.place(margin, y.coerceIn(topLimit, max(topLimit, bottomLimit)))
            }
        }
    }
}

/**
 * Four touch-absorbing panes around the hole. Leaving the hole itself uncovered is
 * what lets the user actually press the highlighted control.
 */
@Composable
private fun TouchBlockersAround(hole: Rect) {
    val density = LocalDensity.current
    val left = with(density) { hole.left.toDp() }.coerceAtLeast(0.dp)
    val top = with(density) { hole.top.toDp() }.coerceAtLeast(0.dp)
    val right = with(density) { hole.right.toDp() }.coerceAtLeast(0.dp)
    val bottom = with(density) { hole.bottom.toDp() }.coerceAtLeast(0.dp)
    val holeHeight = (bottom - top).coerceAtLeast(0.dp)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(top)
                .blockTouches("tour_blocker_top")
        )
        Box(
            Modifier
                .offset(y = bottom)
                .fillMaxSize()
                .blockTouches("tour_blocker_bottom")
        )
        Box(
            Modifier
                .offset(y = top)
                .width(left)
                .height(holeHeight)
                .blockTouches("tour_blocker_left")
        )
        Box(
            Modifier
                .offset(x = right, y = top)
                .fillMaxWidth()
                .height(holeHeight)
                .blockTouches("tour_blocker_right")
        )
    }
}

@Composable
private fun TourTooltipCard(
    step: TourStep,
    stepNumber: Int,
    stepCount: Int,
    accent: Color,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val surface = tourSurfaceColor()
    val titleColor = if (surface.luminance() < 0.5f) Color(0xFFF2F7F2) else Color(0xFF14180F)
    val bodyColor = titleColor.copy(alpha = 0.76f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (stepCount > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(stepCount) { index ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (index == stepNumber - 1) 16.dp else 6.dp,
                                height = 6.dp
                            )
                            .clip(CircleShape)
                            .background(
                                if (index <= stepNumber - 1) accent else bodyColor.copy(alpha = 0.25f)
                            )
                    )
                }
            }
        }

        Text(
            text = step.title,
            color = titleColor,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 24.sp
        )
        Text(
            text = step.body,
            color = bodyColor,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onSkip,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Skip tour",
                    color = bodyColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.weight(1f))

            if (stepNumber > 1) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Back",
                        color = bodyColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            when (val advance = step.advance) {
                is TourAdvance.TapTarget -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = advance.hint,
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                is TourAdvance.Button -> Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent)
                        .clickable(onClick = onNext)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = advance.label,
                        color = if (accent.luminance() > 0.5f) Color(0xFF0C120C) else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                is TourAdvance.Info -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                    Text(
                        text = advance.message,
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private class SpotlightAnimation(
    val left: Animatable<Float, AnimationVector1D>,
    val top: Animatable<Float, AnimationVector1D>,
    val right: Animatable<Float, AnimationVector1D>,
    val bottom: Animatable<Float, AnimationVector1D>,
    val alpha: Animatable<Float, AnimationVector1D>
)

@Composable
private fun rememberSpotlightAnimation(target: Rect?): SpotlightAnimation {
    val state = remember {
        SpotlightAnimation(
            left = Animatable(0f),
            top = Animatable(0f),
            right = Animatable(0f),
            bottom = Animatable(0f),
            alpha = Animatable(0f)
        )
    }

    LaunchedEffect(target) {
        if (target == null || target.isEmpty) {
            state.alpha.animateTo(0f, tween(durationMillis = 150))
            return@LaunchedEffect
        }
        if (state.alpha.value <= 0.01f) {
            // Growing out of the target's own centre reads as "look here" rather than
            // as a rectangle sliding in from wherever the previous step happened to be.
            val centre = target.center
            state.left.snapTo(centre.x)
            state.right.snapTo(centre.x)
            state.top.snapTo(centre.y)
            state.bottom.snapTo(centre.y)
        }
        coroutineScope {
            val spec = tween<Float>(durationMillis = 280)
            launch { state.left.animateTo(target.left, spec) }
            launch { state.top.animateTo(target.top, spec) }
            launch { state.right.animateTo(target.right, spec) }
            launch { state.bottom.animateTo(target.bottom, spec) }
            launch { state.alpha.animateTo(1f, tween(durationMillis = 200)) }
        }
    }

    return state
}

/**
 * The overlay always sits on a dark scrim, so it borrows the palette's footer
 * colours — the ones every theme already designs for a dark surface. A custom
 * theme can still hand back a light footer, hence the luminance guard.
 */
@Composable
private fun tourSurfaceColor(): Color {
    val footer = PocketColors.FooterBg
    return if (footer.luminance() < 0.45f) footer else Color(0xFF16201A)
}

@Composable
private fun tourAccentColor(): Color {
    val surface = tourSurfaceColor()
    val primary = PocketColors.currentPalette.primary
    return if (abs(primary.luminance() - surface.luminance()) > 0.2f) {
        primary
    } else {
        lerp(primary, Color.White, 0.65f)
    }
}
