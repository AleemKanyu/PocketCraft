package com.pocketcraft.server.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp

object PocketMotion {
    val SmoothEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val SmoothEmphasis = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun softFloatTween(
        durationMillis: Int = 180,
        delayMillis: Int = 0
    ): FiniteAnimationSpec<Float> = tween(
        durationMillis = durationMillis,
        delayMillis = delayMillis,
        easing = SmoothEase
    )

    fun softDpTween(
        durationMillis: Int = 180,
        delayMillis: Int = 0
    ): FiniteAnimationSpec<Dp> = tween(
        durationMillis = durationMillis,
        delayMillis = delayMillis,
        easing = SmoothEase
    )

    fun softIntOffsetTween(
        durationMillis: Int = 180,
        delayMillis: Int = 0
    ): FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = durationMillis,
        delayMillis = delayMillis,
        easing = SmoothEase
    )

    fun gentleSpringFloat(
        stiffness: Float = Spring.StiffnessLow
    ): FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = stiffness
    )

    fun gentleSpringDp(
        stiffness: Float = Spring.StiffnessLow
    ): FiniteAnimationSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = stiffness
    )

    fun gentleSpringIntSize(
        stiffness: Float = Spring.StiffnessLow
    ): FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = stiffness
    )
}
