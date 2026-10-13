package com.pockethost.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.os.SystemClock
import com.pockethost.app.ui.screens.ServerStateHolder
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.card3d
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class PregenSize(val label: String, val radiusBlocks: Int) {
    /** Chunks in the square Chunky fills: the side is twice the radius, 16 blocks per chunk. */
    val chunks: Int get() = (radiusBlocks * 2 / 16).let { it * it }
}

private val PREGEN_SIZES = listOf(
    PregenSize("Small", 500),
    PregenSize("Medium", 1000),
    PregenSize("Large", 2000)
)
private val PREGEN_LIMITS: List<Pair<String, Int?>> = listOf(
    "No limit" to null,
    "15 min" to 15,
    "30 min" to 30,
    "1 hour" to 60
)

/**
 * World pre-generation: pick how much of the world to build ahead of time, then watch a
 * live countdown while Chunky works. Pops in with a spring and fades out.
 */
@Composable
fun PregenPopup(stateHolder: ServerStateHolder) {
    val scope = rememberCoroutineScope()
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val dismiss: () -> Unit = {
        scope.launch {
            pop.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
            stateHolder.closePregenSheet()
        }
    }

    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PocketHostCard(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = 0.82f + 0.18f * pop.value
                    scaleX = scale
                    scaleY = scale
                    alpha = pop.value.coerceIn(0f, 1f)
                },
            cornerRadius = 28.dp
        ) {
            val mode = when {
                stateHolder.pregenFinished -> "done"
                stateHolder.pregenActive -> "running"
                else -> "setup"
            }
            Header(
                status = when {
                    mode == "done" -> "Done"
                    mode == "running" && stateHolder.pregenPaused -> "Paused"
                    mode == "running" -> "Building"
                    else -> "Ready"
                },
                active = mode != "setup" && !stateHolder.pregenPaused
            )
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(140)) },
                label = "pregen-mode"
            ) { current ->
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (current) {
                        "running" -> Running(stateHolder, dismiss)
                        "done" -> Done(stateHolder, dismiss)
                        else -> Setup(stateHolder)
                    }
                }
            }
        }
    }
}

@Composable
private fun Setup(stateHolder: ServerStateHolder) {
    var sizeIndex by remember { mutableIntStateOf(1) }
    var limitIndex by remember { mutableIntStateOf(0) }

    Title("Pre-generate World")
    Hint("Builds the world around spawn ahead of time, so players do not lag while exploring.")

    if (!stateHolder.pregenSupported) {
        Hint("This needs a Paper or Purpur server.")
        return
    }

    Label("How much")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PREGEN_SIZES.forEachIndexed { index, size ->
            Choice(
                title = size.label,
                detail = "${size.radiusBlocks} blocks",
                selected = index == sizeIndex,
                modifier = Modifier.weight(1f),
                onClick = { sizeIndex = index }
            )
        }
    }
    Hint("About ${"%,d".format(PREGEN_SIZES[sizeIndex].chunks)} chunks, ${PREGEN_SIZES[sizeIndex].radiusBlocks} blocks out from spawn in every direction.")

    Label("Stop after")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PREGEN_LIMITS.forEachIndexed { index, (label, _) ->
            Choice(
                title = label,
                detail = null,
                selected = index == limitIndex,
                modifier = Modifier.weight(1f),
                onClick = { limitIndex = index }
            )
        }
    }

    stateHolder.pregenMessage?.let { Hint(it, warning = true) }

    DuoButton(
        text = "Start",
        enabled = stateHolder.isRunning,
        onClick = { stateHolder.startPregen(PREGEN_SIZES[sizeIndex].radiusBlocks, PREGEN_LIMITS[limitIndex].second) }
    )
    if (!stateHolder.isRunning) Hint("Start the server first.")
}

@Composable
private fun Running(stateHolder: ServerStateHolder, dismiss: () -> Unit) {
    val progress = stateHolder.pregenProgress
    val paused = stateHolder.pregenPaused

    // Chunky reports every few seconds; count down locally in between so the timer never stalls.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    val secondsLeft = progress?.let {
        if (paused) it.etaSeconds else (it.etaSeconds - (now - it.updatedAtMs) / 1000).coerceAtLeast(0)
    }
    // Keyed on `now` so the elapsed clock ticks along with the countdown.
    val elapsed = remember(now, stateHolder.pregenStartedAtMs) {
        stateHolder.pregenStartedAtMs?.let { ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0) } ?: 0
    }

    Title(if (paused) "Paused" else "Building your world")

    TimerRing(
        fraction = (progress?.percent ?: 0f) / 100f,
        paused = paused,
        center = if (secondsLeft == null) "--:--" else clock(secondsLeft),
        caption = if (secondsLeft == null) "starting" else "left"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Stat("Done", "${(progress?.percent ?: 0f).toInt()}%", Modifier.weight(1f))
        Stat("Time", clock(elapsed), Modifier.weight(1f))
        Stat("Speed", progress?.let { "${it.chunksPerSecond.toInt()}/s" } ?: "-", Modifier.weight(1f))
    }

    stateHolder.pregenMessage?.let { Hint(it, warning = true) }
    stateHolder.pregenTimeLimitMinutes?.let { Hint("Pauses by itself after $it minutes.") }

    DuoButton(
        text = if (paused) "Resume" else "Pause",
        onClick = { if (paused) stateHolder.resumePregen() else stateHolder.pausePregen() },
        variant = DuoButtonVariant.Primary
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) {
            DuoButton(text = "Hide", onClick = dismiss, variant = DuoButtonVariant.SecondaryGray)
        }
        Box(modifier = Modifier.weight(1f)) {
            DuoButton(text = "Stop", onClick = { stateHolder.stopPregen() }, variant = DuoButtonVariant.Danger)
        }
    }
    Hint("It keeps going in the background and pauses while players are online.")
}

@Composable
private fun Done(stateHolder: ServerStateHolder, dismiss: () -> Unit) {
    val elapsed = stateHolder.pregenStartedAtMs?.let { ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0) } ?: 0
    Title("World is ready")
    TimerRing(fraction = 1f, paused = false, center = "100%", caption = "done")
    Hint("Built ${"%,d".format(stateHolder.pregenProgress?.chunks ?: 0)} chunks in ${clock(elapsed)}.")
    DuoButton(text = "Done", onClick = dismiss)
}

@Composable
private fun Header(status: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "WORLD BUILDER",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = PocketColors.Primary
        )
        val tint = if (active) PocketColors.Online else PocketColors.TextSecondary
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(tint))
            Text(status, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = tint)
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 21.sp,
        fontWeight = FontWeight.ExtraBold,
        color = PocketColors.TextPrimary
    )
}

@Composable
private fun TimerRing(fraction: Float, paused: Boolean, center: String, caption: String) {
    val sweep by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "pregen-sweep"
    )
    val pulse by rememberInfiniteTransition(label = "pregen-pulse").animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pregen-pulse-alpha"
    )
    val track = PocketColors.CardBorder
    val depth = PocketColors.CardBorderBottom
    val accent = if (paused) PocketColors.TextSecondary else PocketColors.Primary

    Box(modifier = Modifier.padding(vertical = 4.dp).size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val stroke = 15.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            // Same raised look as the cards: a darker rim sitting just under the track.
            drawArc(depth, -90f, 360f, false, Offset(inset, inset + 3.dp.toPx()), arcSize, style = Stroke(stroke))
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (!paused && sweep > 0f) {
                drawArc(
                    accent, -90f, 360f * sweep, false, Offset(inset, inset), arcSize,
                    alpha = pulse, style = Stroke(stroke + 9.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            drawArc(
                accent, -90f, (360f * sweep).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                center,
                fontSize = 30.sp,
                fontFamily = Monocraft,
                color = PocketColors.TextPrimary
            )
            Text(caption.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = accent)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .card3d(elevation = 3.dp, cornerRadius = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = PocketColors.TextPrimary)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PocketColors.TextSecondary)
    }
}

@Composable
private fun Choice(title: String, detail: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .then(
                if (selected) {
                    Modifier.card3d(
                        elevation = 3.dp,
                        cornerRadius = 14.dp,
                        borderColor = PocketColors.Primary,
                        depthColor = PocketColors.PrimaryDark
                    )
                } else {
                    Modifier.card3d(elevation = 3.dp, cornerRadius = 14.dp)
                }
            )
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) PocketColors.Primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = if (selected) PocketColors.Primary else PocketColors.TextPrimary,
            maxLines = 1
        )
        if (detail != null) {
            Text(detail, fontSize = 10.sp, color = PocketColors.TextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = PocketColors.Primary
    )
}

@Composable
private fun Hint(text: String, warning: Boolean = false) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = if (warning) PocketColors.Warning else PocketColors.TextSecondary
    )
}

private fun clock(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
