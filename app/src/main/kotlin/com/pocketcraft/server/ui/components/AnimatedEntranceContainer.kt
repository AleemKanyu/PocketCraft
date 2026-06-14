package com.pocketcraft.server.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.pocketcraft.server.ui.theme.PocketMotion
import kotlinx.coroutines.delay

@Composable
fun AnimatedEntranceContainer(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shouldAnimate = index < 5
    var visible by remember { mutableStateOf(!shouldAnimate) }
    
    if (shouldAnimate) {
        LaunchedEffect(Unit) {
            // Stagger only the first few items on screen load
            val delayTime = index * 40L
            if (delayTime > 0) {
                delay(delayTime)
            }
            visible = true
        }
    }
    
    if (shouldAnimate) {
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = PocketMotion.softFloatTween(durationMillis = 520),
            label = "entrance_alpha"
        )
        val slideY by animateFloatAsState(
            targetValue = if (visible) 0f else 14f,
            animationSpec = PocketMotion.softFloatTween(durationMillis = 560),
            label = "entrance_slide"
        )
        
        Box(
            modifier = modifier
                .graphicsLayer(
                    alpha = alpha,
                    translationY = slideY
                )
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier
        ) {
            content()
        }
    }
}
