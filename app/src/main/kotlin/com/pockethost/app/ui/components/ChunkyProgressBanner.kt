package com.pockethost.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.card3d
import com.pockethost.app.ui.theme.pocketIsDarkTheme

/**
 * Small chip that shows world pre-generation is running: a ring, the percent and how
 * many chunks are built so far. Sits in a corner so it never covers the screen, and
 * opens the world builder when tapped.
 */
@Composable
fun ChunkyProgressBanner(
    progress: Int?,
    chunks: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = progress != null,
        modifier = modifier,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(220)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.8f, animationSpec = tween(160))
    ) {
        val percent = progress ?: 0
        val sweep by animateFloatAsState(percent / 100f, tween(700), label = "pregen-chip")
        val track = PocketColors.CardBorder
        val accent = PocketColors.Primary
        Row(
            modifier = Modifier
                .card3d(elevation = 4.dp, cornerRadius = 22.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (pocketIsDarkTheme()) PocketColors.SurfaceCardDark else PocketColors.SurfaceCard)
                .clickable(onClick = onClick)
                .padding(start = 9.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(modifier = Modifier.size(26.dp)) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2
                val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, -90f, 360f, false, topLeft, arc, style = Stroke(stroke))
                drawArc(accent, -90f, 360f * sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column {
                Text(
                    "$percent% built",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PocketColors.TextPrimary
                )
                if (chunks != null && chunks > 0) {
                    Text(
                        "${"%,d".format(chunks)} chunks",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PocketColors.TextSecondary
                    )
                }
            }
        }
    }
}
