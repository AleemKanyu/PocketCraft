package com.pockethost.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun GoogleGLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        val halfSize = sizePx / 2f
        val strokeWidth = sizePx * 0.22f
        val rect = Rect(strokeWidth / 2f, strokeWidth / 2f, sizePx - strokeWidth / 2f, sizePx - strokeWidth / 2f)

        // 1. Red (top segment)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 190f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = strokeWidth)
        )
        // 2. Blue (right segment)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = 340f,
            sweepAngle = 65f,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = strokeWidth)
        )
        // 3. Green (bottom segment)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = strokeWidth)
        )
        // 4. Yellow (left segment)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 50f,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = strokeWidth)
        )
        // 5. Horizontal bar of G (Blue)
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(halfSize, halfSize),
            end = Offset(sizePx - strokeWidth / 2f, halfSize),
            strokeWidth = strokeWidth
        )
    }
}
