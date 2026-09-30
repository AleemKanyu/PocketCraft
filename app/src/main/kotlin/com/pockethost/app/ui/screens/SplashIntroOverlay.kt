package com.pockethost.app.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.R
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PlayfairDisplay
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.PocketMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sin

private val EaseOut = PocketMotion.SmoothEase
private val EaseIn = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)
private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
/** Speeds up the whole way down, like a block of sand letting go. */
private val Gravity = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f)
/** Settles past the mark and back, so each letter rocks as it stands up. */
private val Pop = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private const val WORDMARK = "PocketHost"
private val LogoSize = 96.dp
/** How far above its spot the cube starts its fall, in dp. */
private const val DROP_HEIGHT = 170f
/** Where the cube's bottom corner sits in the logo image, as a fraction of its height. */
private const val CUBE_BASE = 0.88f

// The backdrop breaks away in square blocks, rippling out from the middle.
private const val BLOCK_COLUMNS = 10
private const val RIPPLE_MS = 380f
private const val BLOCK_MS = 280f
private const val REVEAL_MS = (RIPPLE_MS + BLOCK_MS).toInt()

/** One speck of the dust the cube kicks up: which way it flies, how far, how high, how big (dp). */
private class Dust(val dir: Float, val reach: Float, val lift: Float, val size: Float, val accent: Boolean)

private val LandingDust = listOf(
    Dust(-1f, 62f, 20f, 6f, false), Dust(1f, 66f, 16f, 6f, true),
    Dust(-1f, 40f, 30f, 5f, true), Dust(1f, 44f, 34f, 5f, false),
    Dust(-1f, 84f, 10f, 4f, false), Dust(1f, 88f, 12f, 4f, false),
    Dust(-1f, 24f, 18f, 4f, false), Dust(1f, 28f, 22f, 4f, true),
    Dust(-1f, 54f, 40f, 3f, true), Dust(1f, 58f, 38f, 3f, false)
)

/**
 * Plays over the app on a cold launch, then hands over to it. Where DASHit's
 * logo sharpens and its letters slide out beside it, PocketHost's builds like
 * a Minecraft scene: the cube falls onto its spot and lands with a squash and
 * a puff of pixel dust, the letters of "PocketHost" flip up one after another
 * like placed blocks, the PH tag slaps on with a thud and an enchantment glint
 * sweeps across. The backdrop then breaks away block by block from the middle,
 * uncovering the app as it grows into place.
 *
 * If the app isn't [appReady] once the lockup is built (the first launch
 * unpacks the Minecraft runtime), it holds there with [status] and [progress]
 * until it is.
 */
@Composable
fun SplashIntroOverlay(
    appReady: Boolean,
    progress: Float,
    status: String,
    onIntroSettled: () -> Unit,
    onReveal: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val isAppReady by rememberUpdatedState(appReady)
    val currentOnIntroSettled by rememberUpdatedState(onIntroSettled)
    val currentOnReveal by rememberUpdatedState(onReveal)
    val currentOnFinished by rememberUpdatedState(onFinished)

    val drop = remember { Animatable(0f) } // 0 → 1: the cube falls onto its spot
    val logoAlpha = remember { Animatable(0f) }
    val squash = remember { Animatable(0f) } // 1 = flattened by the landing
    val dust = remember { Animatable(0f) } // 0 → 1: the landing dust flies out and fades
    val letters = remember { List(WORDMARK.length) { Animatable(0f) } } // 0 → 1: each letter stands up
    val stampScale = remember { Animatable(2.3f) }
    val stampSpin = remember { Animatable(18f) }
    val stampAlpha = remember { Animatable(0f) }
    val thud = remember { Animatable(0f) }
    val glint = remember { Animatable(0f) } // 0 → 1: the glint crosses the lockup
    val lockupFade = remember { Animatable(1f) }
    val loadingAlpha = remember { Animatable(0f) }
    val lockupExit = remember { Animatable(0f) } // 0 → 1: the lockup sinks away
    val reveal = remember { Animatable(0f) } // 0 → 1: the backdrop breaks away
    val overlayAlpha = remember { Animatable(1f) }
    var holdingForApp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val reduceMotion = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
        val readyAtLaunch = isAppReady

        if (reduceMotion) {
            // The finished lockup, faded in and out.
            lockupFade.snapTo(0f)
            drop.snapTo(1f)
            logoAlpha.snapTo(1f)
            letters.forEach { it.snapTo(1f) }
            stampScale.snapTo(1f)
            stampSpin.snapTo(0f)
            stampAlpha.snapTo(1f)
            lockupFade.animateTo(1f, tween(300))
            delay(600)
        } else {
            coroutineScope {
                // 1. The cube drops onto its spot and lands with a squash and a puff of dust.
                launch {
                    delay(60)
                    launch { logoAlpha.animateTo(1f, tween(140, easing = LinearEasing)) }
                    drop.animateTo(1f, tween(380, easing = Gravity))
                    launch { dust.animateTo(1f, tween(560, easing = EaseOut)) }
                    squash.animateTo(1f, tween(70, easing = EaseOut))
                    squash.animateTo(0f, spring(dampingRatio = 0.36f, stiffness = 650f))
                }
                // 2. The letters flip up one after another, like blocks being placed.
                launch {
                    delay(540)
                    letters.forEachIndexed { index, letter ->
                        launch { letter.animateTo(1f, tween(480, delayMillis = 45 * index, easing = Pop)) }
                    }
                }
                // 3. The PH tag slaps on, and the lockup gives a little under the thud.
                launch {
                    delay(980)
                    coroutineScope {
                        launch { stampAlpha.animateTo(1f, tween(90)) }
                        launch { stampSpin.animateTo(0f, tween(170, easing = EaseIn)) }
                        stampScale.animateTo(0.9f, tween(170, easing = EaseIn))
                    }
                    launch { stampScale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 900f)) }
                    thud.animateTo(1f, tween(50, easing = EaseOut))
                    thud.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 700f))
                }
                // 4. An enchantment glint sweeps across the finished lockup.
                launch {
                    delay(1180)
                    glint.animateTo(1f, tween(560, easing = EaseInOut))
                }
            }
        }
        currentOnIntroSettled()

        // Hold on the lockup until the app beneath is ready, showing how the runtime is coming along.
        if (!readyAtLaunch) {
            val readySoon = withTimeoutOrNull(160) { snapshotFlow { isAppReady }.first { it } } != null
            if (!readySoon) {
                holdingForApp = true
                loadingAlpha.animateTo(1f, tween(360, easing = EaseOut))
                snapshotFlow { isAppReady }.first { it }
            }
            // Let the bar fill and the app finish its own loading hop before it's uncovered.
            delay(320)
        }

        if (reduceMotion) {
            currentOnReveal()
            overlayAlpha.animateTo(0f, tween(300))
        } else {
            coroutineScope {
                launch { loadingAlpha.animateTo(0f, tween(180)) }
                launch { lockupExit.animateTo(1f, tween(300, easing = EaseIn)) }
                launch {
                    delay(60)
                    currentOnReveal()
                }
                reveal.animateTo(1f, tween(REVEAL_MS, easing = LinearEasing))
            }
        }
        currentOnFinished()
    }

    val backdropTop = PocketColors.BgApp
    val backdropMiddle = PocketColors.SurfaceCard
    val ink = PocketColors.TextPrimary
    val accent = PocketColors.Primary
    // A light streak reads on dark ink; light ink takes the accent instead.
    val glintColor = if (ink.luminance() < 0.5f) Color.White.copy(alpha = 0.75f) else accent.copy(alpha = 0.9f)
    val breathe: State<Float>? = if (holdingForApp) {
        rememberInfiniteTransition(label = "splash_breathe").animateFloat(
            initialValue = 0.985f,
            targetValue = 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = PocketMotion.SmoothEase),
                repeatMode = RepeatMode.Reverse
            ),
            label = "splash_breathe_scale"
        )
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            // Nothing beneath can be tapped until the app is uncovered.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        BlockBackdrop(
            reveal = { reveal.value },
            top = backdropTop,
            middle = backdropMiddle
        )

        if (holdingForApp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = loadingAlpha.value }
            ) {
                SplashMascotBackground()
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        val exit = lockupExit.value
                        val scale = 1f - 0.08f * exit
                        scaleX = scale
                        scaleY = scale
                        alpha = lockupFade.value * (1f - exit)
                        translationY = (2.5f * thud.value).dp.toPx()
                    }
                    .drawWithContent {
                        val sweep = glint.value
                        if (sweep <= 0f || sweep >= 1f) {
                            drawContent()
                            return@drawWithContent
                        }
                        // A layer of its own keeps the glint on the lockup's pixels, not the backdrop;
                        // it bleeds past the bounds so the tilted tag's corners aren't clipped.
                        val bleed = 12.dp.toPx()
                        val layer = Rect(-bleed, -bleed, size.width + bleed, size.height + bleed)
                        drawContext.canvas.saveLayer(layer, Paint())
                        drawContent()
                        val band = size.width * 0.16f
                        val slant = size.height * 0.4f
                        val centerX = -band - slant + (size.width + 2f * (band + slant)) * sweep
                        val centerY = size.height / 2f
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, glintColor, Color.Transparent),
                                start = Offset(centerX - band, centerY - 0.4f * band),
                                end = Offset(centerX + band, centerY + 0.4f * band)
                            ),
                            topLeft = layer.topLeft,
                            size = layer.size,
                            blendMode = BlendMode.SrcAtop
                        )
                        drawContext.canvas.restore()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(LogoSize)
                        .drawWithContent {
                            // Dust kicks up in front of the cube, so it never shows through its open faces.
                            drawContent()
                            val t = dust.value
                            if (t > 0f && t < 1f) {
                                val baseX = size.width / 2f
                                val baseY = size.height * CUBE_BASE
                                LandingDust.forEach { speck ->
                                    val side = (speck.size * (1f - 0.6f * t)).dp.toPx()
                                    val x = baseX + speck.dir * (10f + speck.reach * t).dp.toPx()
                                    val y = baseY - speck.lift.dp.toPx() * sin(PI.toFloat() * 0.8f * t)
                                    drawRect(
                                        color = (if (speck.accent) accent else ink).copy(alpha = 0.6f * (1f - t)),
                                        topLeft = Offset(x - side / 2f, y - side / 2f),
                                        size = Size(side, side)
                                    )
                                }
                            }
                        }
                        .drawBehind {
                            // The cube's shadow firms up as it nears the ground, then fades with the dust.
                            val fall = drop.value
                            val shadowAlpha = 0.16f * fall * (1f - dust.value)
                            if (shadowAlpha > 0f) {
                                val shadowWidth = size.width * (0.28f + 0.34f * fall)
                                val shadowHeight = 7.dp.toPx()
                                drawOval(
                                    color = ink.copy(alpha = shadowAlpha),
                                    topLeft = Offset(
                                        size.width / 2f - shadowWidth / 2f,
                                        size.height * CUBE_BASE - shadowHeight / 2f
                                    ),
                                    size = Size(shadowWidth, shadowHeight)
                                )
                            }
                        }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.cube_logo_light),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ink),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val flat = squash.value
                                val pulse = breathe?.let { 1f + (it.value - 1f) * loadingAlpha.value } ?: 1f
                                translationY = (-DROP_HEIGHT * (1f - drop.value)).dp.toPx()
                                scaleX = (1f + 0.14f * flat) * pulse
                                scaleY = (1f - 0.16f * flat) * pulse
                                alpha = logoAlpha.value
                                transformOrigin = TransformOrigin(0.5f, CUBE_BASE)
                            }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = WORDMARK }
                ) {
                    WORDMARK.forEachIndexed { index, char ->
                        val letter = letters[index]
                        Text(
                            text = char.toString(),
                            fontFamily = Monocraft,
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            color = ink,
                            letterSpacing = 0.sp,
                            modifier = Modifier.graphicsLayer {
                                val t = letter.value
                                // Lies flat on the ground, then stands up on its bottom edge.
                                rotationX = 90f * (1f - t)
                                translationY = (10f * (1f - t)).dp.toPx()
                                alpha = (t * 2.5f).coerceIn(0f, 1f)
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                cameraDistance = 10f * density
                            }
                        )
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = stampScale.value
                                scaleY = stampScale.value
                                rotationZ = -8f + stampSpin.value
                                alpha = stampAlpha.value
                            }
                            .background(color = accent, shape = RoundedCornerShape(6.dp))
                            .border(1.5.dp, PocketColors.PrimaryBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PH",
                            fontFamily = Monocraft,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = PocketColors.PrimaryText,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            SplashLoadingRow(
                progress = progress,
                status = status,
                modifier = Modifier
                    .then(if (holdingForApp) Modifier else Modifier.clearAndSetSemantics { })
                    .graphicsLayer {
                        val shown = loadingAlpha.value
                        alpha = shown
                        translationY = (8f * (1f - shown)).dp.toPx()
                    }
            )
        }
    }
}

/**
 * The splash backdrop, drawn as a grid of square blocks. Before [reveal] starts
 * they tile seamlessly; as it runs, each block shrinks away in turn, the ones
 * nearest the middle first with a little scatter so it reads like chunks
 * unloading rather than a clean wipe.
 */
@Composable
private fun BlockBackdrop(reveal: () -> Float, top: Color, middle: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val brush = Brush.verticalGradient(
                    colors = listOf(top, middle, top),
                    startY = 0f,
                    endY = size.height
                )
                val block = size.width / BLOCK_COLUMNS
                val rows = ceil(size.height / block).toInt()
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val farthest = hypot(centerX, centerY)
                // When each block starts to go, in ms after the reveal begins.
                val starts = FloatArray(rows * BLOCK_COLUMNS) { index ->
                    val row = index / BLOCK_COLUMNS
                    val column = index % BLOCK_COLUMNS
                    val distance = hypot(
                        (column + 0.5f) * block - centerX,
                        (row + 0.5f) * block - centerY
                    ) / farthest
                    val scatter = ((row * 73856093) xor (column * 19349663)).and(0xFF) / 255f
                    (0.82f * distance + 0.18f * scatter) * RIPPLE_MS
                }
                onDrawBehind {
                    val elapsed = reveal() * REVEAL_MS
                    if (elapsed <= 0f) {
                        drawRect(brush)
                        return@onDrawBehind
                    }
                    for (index in starts.indices) {
                        val gone = ((elapsed - starts[index]) / BLOCK_MS).coerceIn(0f, 1f)
                        if (gone >= 1f) continue
                        val side = block * (1f - EaseInOut.transform(gone))
                        val inset = (block - side) / 2f
                        // Whole blocks overlap by a pixel so no seams show between them.
                        val bleed = if (gone == 0f) 1f else 0f
                        drawRect(
                            brush = brush,
                            topLeft = Offset(
                                (index % BLOCK_COLUMNS) * block + inset,
                                (index / BLOCK_COLUMNS) * block + inset
                            ),
                            size = Size(side + bleed, side + bleed),
                            alpha = 1f - gone * gone
                        )
                    }
                }
            }
    )
}

/** The runtime status line and progress bar shown under the splash lockup. */
@Composable
internal fun SplashLoadingRow(progress: Float, status: String, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260),
        label = "splash_progress"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(top = 10.dp),
            color = PocketColors.TextSecondary,
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
            color = PocketColors.Primary,
            trackColor = PocketColors.InactiveBorder
        )
    }
}
