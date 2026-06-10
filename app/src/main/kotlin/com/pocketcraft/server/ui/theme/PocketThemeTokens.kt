package com.pocketcraft.server.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.roundToInt

@Composable
fun pocketIsDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Dark-green tint for crisp bottom-only 3D depth (not pure black). */
val Pocket3dShadowTint: Color = Color(0xFF1A3A2A)

/**
 * Crisp bottom-edge shadow strip — no blur, no top/left/right shadow.
 * Drawn inside the composable bounds at the bottom edge.
 */
fun Modifier.bottomShadow(
    shadowColor: Color = Pocket3dShadowTint.copy(alpha = 0.20f),
    shadowHeight: Dp = 4.dp,
    cornerRadius: Dp = 16.dp
): Modifier = this.drawBehind {
    val heightPx = shadowHeight.toPx()
    if (heightPx <= 0f) return@drawBehind
    drawRoundRect(
        color = shadowColor,
        topLeft = Offset(0f, size.height - heightPx),
        size = Size(size.width, heightPx),
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
    )
}

private fun elevationToDepthWidth(elevation: Dp): Dp = when {
    elevation >= 8.dp -> 4.dp
    elevation >= 6.dp -> 3.dp
    elevation >= 4.dp -> 2.5.dp
    else -> 2.dp
}

/**
 * Raised 3D look for cards — side/top border plus a thicker bottom edge only.
 * Maps [elevation] to bottom depth width (6dp ≈ 3dp bottom strip).
 */
@Composable
fun Modifier.card3d(
    elevation: Dp = 6.dp,
    cornerRadius: Dp = 16.dp,
    borderColor: Color = Color.Unspecified,
    depthColor: Color = Color.Unspecified,
    borderWidth: Dp = 1.5.dp,
    depthWidth: Dp? = null,
): Modifier {
    val isDark = pocketIsDarkTheme()
    val side = when {
        borderColor != Color.Unspecified -> borderColor
        isDark -> PocketColors.CardBorderDark
        else -> PocketColors.CardBorder
    }
    val bottom = when {
        depthColor != Color.Unspecified -> depthColor
        isDark -> PocketColors.CardBorderBottomDark
        else -> PocketColors.CardBorderBottom
    }
    return raisedBorder(
        color = side,
        depthColor = bottom,
        cornerRadius = cornerRadius,
        borderWidth = borderWidth,
        depthWidth = depthWidth ?: elevationToDepthWidth(elevation)
    )
}

/** Pill-shaped 3D modifier for small chips and toggle segments. */
@Composable
fun Modifier.pill3d(
    elevation: Dp = 4.dp,
    borderColor: Color = Color.Unspecified,
    depthColor: Color = Color.Unspecified,
    borderWidth: Dp = 1.dp,
    depthWidth: Dp? = null,
): Modifier = card3d(
    elevation = elevation,
    cornerRadius = 50.dp,
    borderColor = borderColor,
    depthColor = depthColor,
    borderWidth = borderWidth,
    depthWidth = depthWidth
)

/** Primary action button 3D modifier (Start Server, etc.). */
@Composable
fun Modifier.button3d(
    elevation: Dp = 8.dp,
    borderColor: Color = Color.Unspecified,
    depthColor: Color = Color.Unspecified,
    depthWidth: Dp? = null,
): Modifier = pill3d(
    elevation = elevation,
    borderColor = borderColor,
    depthColor = depthColor,
    borderWidth = 1.5.dp,
    depthWidth = depthWidth
)

/**
 * Draws the classic raised-border bottom-depth effect in the canvas
 * (no actual drop shadow — just a thicker bottom edge).
 */
fun Modifier.raisedBorder(
    color: Color,          // side/top border color  
    depthColor: Color,     // bottom border color (darker)
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 1.5.dp,
    depthWidth: Dp = 3.dp
): Modifier = this.drawWithContent {
    drawContent()

    val stroke = borderWidth.toPx()
    val depth  = depthWidth.toPx()
    val r      = cornerRadius.toPx()

    raisedBorder(
        cornerRadius = r,
        borderColor = color,
        bottomBorderColor = depthColor,
        sideWidthPx = stroke,
        bottomWidthPx = depth
    )
}

/**
 * Draws a soft Gaussian drop shadow OUTSIDE the composable bounds.
 * Use BEFORE .clip() so the shadow is not clipped.
 *
 * @param color       Shadow color (use semi-transparent dark)
 * @param blurRadius  Blur spread in dp
 * @param offsetY     Vertical offset — positive = shadow below
 * @param cornerRadius Corner radius of the shape
 * @param spread      Extra expansion beyond the composable bounds
 */
fun Modifier.dropShadow(
    color: Color,
    blurRadius: Dp = 8.dp,
    offsetY: Dp = 4.dp,
    offsetX: Dp = 0.dp,
    cornerRadius: Dp = 16.dp,
    spread: Dp = 0.dp
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        // Paint color = shadow color; BlurMaskFilter spreads it as a Gaussian
        frameworkPaint.color = color.toArgb()
        if (blurRadius != 0.dp) {
            frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
                blurRadius.toPx(),
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
        val s = spread.toPx()
        val r = cornerRadius.toPx()
        canvas.drawRoundRect(
            left    = -s + offsetX.toPx(),
            top     = -s + offsetY.toPx(),
            right   = size.width + s + offsetX.toPx(),
            bottom  = size.height + s + offsetY.toPx(),
            radiusX = r + s,
            radiusY = r + s,
            paint   = paint
        )
    }
}

/** Convenience: card-level drop shadow that adapts to light/dark theme. */
fun Modifier.cardDropShadow(
    isDark: Boolean,
    cornerRadius: Dp = 18.dp
): Modifier = dropShadow(
    color        = if (isDark) Color(0xEE000000) else Color(0x55000000),
    blurRadius   = if (isDark) 14.dp else 10.dp,
    offsetY      = if (isDark) 6.dp else 5.dp,
    cornerRadius = cornerRadius
)

/** Convenience: button-level drop shadow (slightly tighter). */
fun Modifier.buttonDropShadow(
    isDark: Boolean,
    shadowColor: Color = Color.Unspecified,
    cornerRadius: Dp = 14.dp
): Modifier = dropShadow(
    color        = if (shadowColor != Color.Unspecified) shadowColor
                   else if (isDark) Color(0xEE000000) else Color(0x66000000),
    blurRadius   = if (isDark) 16.dp else 12.dp,
    offsetY      = if (isDark) 8.dp else 6.dp,
    cornerRadius = cornerRadius
)

// ---------------------------------------------------------------------------
// Depth & Press System
// ---------------------------------------------------------------------------
// ALL depth is achieved via border-bottom only. No box-shadow, no elevation.
// Resting: thick bottom border → looks raised
// Pressed: collapses to normal width + offset(y=2dp) → looks pushed in
//
// Helper to draw a rounded-rect border with a thicker bottom edge.
// Call from Modifier.drawBehind { raisedBorder(...) }
// ---------------------------------------------------------------------------

fun DrawScope.raisedBorder(
    cornerRadius: Float,
    borderColor: Color,
    bottomBorderColor: Color,
    sideWidthPx: Float,
    bottomWidthPx: Float
) {
    val inset = sideWidthPx / 2f
    val extraPx = bottomWidthPx - sideWidthPx

    // 1. Draw top/left/right side border
    val r = cornerRadius.coerceAtMost(size.width / 2f).coerceAtMost(size.height / 2f)

    if (r > 0f) {
        val topLeftRect = Rect(
            left = inset,
            top = inset,
            right = inset + 2 * r,
            bottom = inset + 2 * r
        )
        val topRightRect = Rect(
            left = size.width - inset - 2 * r,
            top = inset,
            right = size.width - inset,
            bottom = inset + 2 * r
        )

        val sidePath = Path().apply {
            moveTo(inset, size.height - r)
            lineTo(inset, inset + r)
            arcTo(rect = topLeftRect, startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false)
            lineTo(size.width - inset - r, inset)
            arcTo(rect = topRightRect, startAngleDegrees = 270f, sweepAngleDegrees = 90f, forceMoveTo = false)
            lineTo(size.width - inset, size.height - r)
        }

        drawPath(
            path = sidePath,
            color = borderColor,
            style = Stroke(width = sideWidthPx)
        )
    } else {
        // Fallback for zero radius: draw left, top, right lines
        drawLine(
            color = borderColor,
            start = Offset(inset, 0f),
            end = Offset(inset, size.height),
            strokeWidth = sideWidthPx
        )
        drawLine(
            color = borderColor,
            start = Offset(0f, inset),
            end = Offset(size.width, inset),
            strokeWidth = sideWidthPx
        )
        drawLine(
            color = borderColor,
            start = Offset(size.width - inset, 0f),
            end = Offset(size.width - inset, size.height),
            strokeWidth = sideWidthPx
        )
    }

    // 2. Draw the bottom border
    if (extraPx >= 0f) {
        val rBottom = (r - extraPx / 2f).coerceAtLeast(0f)
        if (rBottom > 0f && r > 0f) {
            val centerXLeft = inset + r
            val centerXRight = size.width - inset - r
            val centerY = size.height - inset - r

            val blRect = Rect(
                left = centerXLeft - rBottom,
                top = centerY - rBottom,
                right = centerXLeft + rBottom,
                bottom = centerY + rBottom
            )
            val brRect = Rect(
                left = centerXRight - rBottom,
                top = centerY - rBottom,
                right = centerXRight + rBottom,
                bottom = centerY + rBottom
            )

            val bottomPath = Path().apply {
                arcTo(rect = brRect, startAngleDegrees = 0f, sweepAngleDegrees = 90f, forceMoveTo = true)
                arcTo(rect = blRect, startAngleDegrees = 90f, sweepAngleDegrees = 90f, forceMoveTo = false)
            }

            drawPath(
                path = bottomPath,
                color = bottomBorderColor,
                style = Stroke(width = bottomWidthPx)
            )
        } else {
            // fallback to drawLine for square corners or if radius is too small
            val bottomInset = size.height - bottomWidthPx / 2f
            drawLine(
                color = bottomBorderColor,
                start = Offset(0f, bottomInset),
                end = Offset(size.width, bottomInset),
                strokeWidth = bottomWidthPx
            )
        }
    }
}

fun DrawScope.creeperPattern() {
    val faceColor = Color(0xFF2A5A2A).copy(alpha = 0.08f)
    val darkColor = Color(0xFF0E180E).copy(alpha = 0.08f)

    val fw = 22.dp.toPx()
    val fh = 18.dp.toPx()
    val spacingX = 226.dp.toPx()
    val spacingY = 191.dp.toPx()

    val cols = ((size.width  / spacingX) + 2).roundToInt()
    val rows = ((size.height / spacingY) + 2).roundToInt()

    for (row in -1..rows) {
        for (col in -1..cols) {
            val rowOffset = if (row % 2 == 0) 0f else spacingX / 2f
            val cx = col * spacingX + rowOffset
            val cy = row * spacingY

            // Deterministic pseudo-random rotation per tile
            val rotDeg = ((col * 7 + row * 13) % 24 - 12).toFloat()

            withTransform({
                translate(cx + fw / 2f, cy + fh / 2f)
                rotate(rotDeg)
            }) {
                val hx = -fw / 2f
                val hy = -fh / 2f
                val cr = 1.5.dp.toPx()

                // Face body
                drawRoundRect(faceColor,
                    topLeft = Offset(hx, hy), size = Size(fw, fh),
                    cornerRadius = CornerRadius(cr))
                // Left eye
                val ew = 5.dp.toPx(); val eyeY = hy + 3.dp.toPx(); val eyeCr = CornerRadius(0.5.dp.toPx())
                drawRoundRect(darkColor, topLeft = Offset(hx + 3.dp.toPx(), eyeY),
                    size = Size(ew, ew), cornerRadius = eyeCr)
                // Right eye
                drawRoundRect(darkColor, topLeft = Offset(hx + 14.dp.toPx(), eyeY),
                    size = Size(ew, ew), cornerRadius = eyeCr)
                // Mouth top bar
                val mY = hy + 10.dp.toPx(); val mCr = CornerRadius(0.3.dp.toPx())
                drawRoundRect(darkColor, topLeft = Offset(hx + 8.dp.toPx(), mY),
                    size = Size(6.dp.toPx(), 2.dp.toPx()), cornerRadius = mCr)
                // Mouth left leg
                drawRoundRect(darkColor, topLeft = Offset(hx + 6.dp.toPx(), mY + 2.dp.toPx()),
                    size = Size(3.dp.toPx(), 4.dp.toPx()), cornerRadius = mCr)
                // Mouth right leg
                drawRoundRect(darkColor, topLeft = Offset(hx + 13.dp.toPx(), mY + 2.dp.toPx()),
                    size = Size(3.dp.toPx(), 4.dp.toPx()), cornerRadius = mCr)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Skeleton skull background pattern — scattered at 9% opacity
// ---------------------------------------------------------------------------

private fun DrawScope.drawSkullSilhouette(centerX: Float, centerY: Float, size: Float, color: Color) {
    val w = size
    val h = size * 1.12f
    val cr = CornerRadius(w * 0.32f, w * 0.32f)

    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - w / 2f, centerY - h * 0.52f),
        size = Size(w, h * 0.62f),
        cornerRadius = cr
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - w * 0.34f, centerY + h * 0.02f),
        size = Size(w * 0.68f, h * 0.34f),
        cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
    )

    val socket = color.copy(alpha = color.alpha * 0.55f)
    drawRoundRect(
        color = socket,
        topLeft = Offset(centerX - w * 0.30f, centerY - h * 0.18f),
        size = Size(w * 0.17f, w * 0.20f),
        cornerRadius = CornerRadius(w * 0.04f)
    )
    drawRoundRect(
        color = socket,
        topLeft = Offset(centerX + w * 0.13f, centerY - h * 0.18f),
        size = Size(w * 0.17f, w * 0.20f),
        cornerRadius = CornerRadius(w * 0.04f)
    )
    drawRoundRect(
        color = socket,
        topLeft = Offset(centerX - w * 0.05f, centerY + h * 0.02f),
        size = Size(w * 0.10f, w * 0.09f),
        cornerRadius = CornerRadius(1.5f)
    )
    for (i in 0..3) {
        val toothX = centerX - w * 0.24f + i * w * 0.13f
        drawRoundRect(
            color = color,
            topLeft = Offset(toothX, centerY + h * 0.18f),
            size = Size(w * 0.09f, h * 0.11f),
            cornerRadius = CornerRadius(1f)
        )
    }
}

fun DrawScope.skeletonPattern() {
    val skullColor = Color(0xFF1E2230).copy(alpha = 0.09f)
    val spacingX = 168.dp.toPx()
    val spacingY = 148.dp.toPx()
    val skullSize = 28.dp.toPx()

    val cols = ((size.width / spacingX) + 2).roundToInt()
    val rows = ((size.height / spacingY) + 2).roundToInt()

    for (row in -1..rows) {
        for (col in -1..cols) {
            val rowOffset = if (row % 2 == 0) 0f else spacingX / 2f
            val cx = col * spacingX + rowOffset + skullSize / 2f
            val cy = row * spacingY + skullSize / 2f
            val rotDeg = ((col * 11 + row * 17) % 28 - 14).toFloat()

            withTransform({
                translate(cx, cy)
                rotate(rotDeg)
            }) {
                drawSkullSilhouette(0f, 0f, skullSize, skullColor)
            }
        }
    }
}

@Composable
fun Modifier.pocketDecoratedBackground(): Modifier = this
    .background(pocketAppBackgroundBrush())
    .drawBehind {
        when (PocketColors.activeMobTheme) {
            com.pocketcraft.server.ui.util.MobTheme.CREEPER -> creeperPattern()
            else -> skeletonPattern()
        }
    }

// ---------------------------------------------------------------------------
// Border / surface colour helpers
// ---------------------------------------------------------------------------

@Composable
fun pocketCardBorderColor(): Color = if (pocketIsDarkTheme()) PocketColors.BorderDark else PocketColors.CardBorder

@Composable
fun pocketHighContrastBorderColor(): Color = if (pocketIsDarkTheme()) PocketColors.BorderStrong else PocketColors.PrimaryBorder

// ---------------------------------------------------------------------------
// Shadow colours — kept for call-sites that still use them
// ---------------------------------------------------------------------------

@Composable
fun pocketCardShadowColor(): Color = Color.Transparent

@Composable
fun pocketCardAmbientShadowColor(): Color = Color.Transparent

// ---------------------------------------------------------------------------
// Card brush — flat solid (no gradient)
// ---------------------------------------------------------------------------

@Composable
fun pocketGlassCardBrush(): Brush = Brush.linearGradient(
    colors = listOf(
        if (pocketIsDarkTheme()) PocketColors.SurfaceCardDark else PocketColors.SurfaceCard,
        if (pocketIsDarkTheme()) PocketColors.SurfaceCardDark else PocketColors.SurfaceCard
    )
)

// ---------------------------------------------------------------------------
// App background — flat solid, creeper pattern drawn separately in the UI
// ---------------------------------------------------------------------------

@Composable
fun pocketAppBackgroundBrush(): Brush = Brush.linearGradient(
    colors = listOf(
        if (pocketIsDarkTheme()) PocketColors.BgDark else PocketColors.BgApp,
        if (pocketIsDarkTheme()) PocketColors.BgDark else PocketColors.BgApp
    )
)

// ---------------------------------------------------------------------------
// Control brush (toggles, inactive surfaces) — flat solid
// ---------------------------------------------------------------------------

@Composable
fun pocketGlassControlBrush(): Brush = Brush.linearGradient(
    colors = listOf(
        if (pocketIsDarkTheme()) PocketColors.SurfaceVarDark else PocketColors.InactiveBg,
        if (pocketIsDarkTheme()) PocketColors.SurfaceVarDark else PocketColors.InactiveBg
    )
)

// ---------------------------------------------------------------------------
// Primary action button brush — flat solid green (no gradient)
// ---------------------------------------------------------------------------

@Composable
fun pocketPremiumActionBrush(isDanger: Boolean = false): Brush = Brush.linearGradient(
    colors = if (isDanger) {
        listOf(PocketColors.Danger, PocketColors.Danger)
    } else {
        listOf(PocketColors.Primary, PocketColors.Primary)
    }
)

// ---------------------------------------------------------------------------
// Sheet / popup tokens
// ---------------------------------------------------------------------------

@Composable
fun pocketSheetBorderColor(): Color = if (pocketIsDarkTheme()) PocketColors.BorderDark else PocketColors.CardBorder

@Composable
fun pocketPopupAccentContainerColor(): Color = if (pocketIsDarkTheme()) PocketColors.SurfaceVarDark else PocketColors.TagBg

@Composable
fun pocketPopupAccentTintColor(): Color = if (pocketIsDarkTheme()) PocketColors.TextDark else PocketColors.PrimaryDark

// ---------------------------------------------------------------------------
// Warning banner tokens (amber — kept intentionally distinct)
// ---------------------------------------------------------------------------

@Composable
fun pocketWarningSurfaceColor(): Color = if (pocketIsDarkTheme()) Color(0xFF21362D) else Color(0xFFF9F3E7)

@Composable
fun pocketWarningBorderColor(): Color = if (pocketIsDarkTheme()) Color(0xFF4C7B66) else Color(0xFFE7C88B)

@Composable
fun pocketWarningIconChipColor(): Color = if (pocketIsDarkTheme()) Color(0xFF2F4F42) else Color(0xFFFFDFAE)

@Composable
fun pocketWarningAccentColor(): Color = if (pocketIsDarkTheme()) Color(0xFFE7C96C) else Color(0xFFCD8A00)

@Composable
fun pocketWarningTitleColor(): Color = if (pocketIsDarkTheme()) PocketColors.TextDark else Color(0xFF6F4E00)

@Composable
fun pocketWarningBodyColor(): Color = if (pocketIsDarkTheme()) PocketColors.TextDark.copy(alpha = 0.82f) else Color(0xFF7C6536)
