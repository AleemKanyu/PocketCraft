package com.pockethost.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints

/**
 * A safe Layout wrapper that catches IllegalArgumentException("measure is called on a deactivated node")
 * which occurs during rapid composition teardown on certain vendor devices (e.g. Honor / MagicOS)
 * and Android 16 predictive back / multi-window animations.
 */
@Composable
fun SafeMeasureLayout(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    measurePolicy: MeasureScope.(List<Measurable>, Constraints) -> MeasureResult
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        try {
            measurePolicy(measurables, constraints)
        } catch (e: IllegalArgumentException) {
            if (e.message?.contains("deactivated node", ignoreCase = true) == true) {
                layout(0, 0) {}
            } else {
                throw e
            }
        }
    }
}
