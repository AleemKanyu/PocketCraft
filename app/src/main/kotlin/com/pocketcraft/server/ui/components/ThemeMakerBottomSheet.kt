package com.pocketcraft.server.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.theme.CustomThemePalette
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.util.MobTheme
import com.pocketcraft.server.ui.util.ThemePreferenceStore
import kotlin.math.*

data class PresetTheme(
    val name: String,
    val primary: Color,
    val bgApp: Color,
    val surfaceCard: Color,
    val primaryText: Color,
    val textPrimary: Color
)

val presets = listOf(
    PresetTheme(
        name = "Sunset Glow",
        primary = Color(0xFFFF5E36),
        bgApp = Color(0xFF1A0F0C),
        surfaceCard = Color(0xFF2B1B18),
        primaryText = Color(0xFFFFFFFF),
        textPrimary = Color(0xFFFFECE8)
    ),
    PresetTheme(
        name = "Deep Ocean",
        primary = Color(0xFF00D2FF),
        bgApp = Color(0xFF0A1118),
        surfaceCard = Color(0xFF131E2A),
        primaryText = Color(0xFF0A1118),
        textPrimary = Color(0xFFE0F7FC)
    ),
    PresetTheme(
        name = "Forest Moss",
        primary = Color(0xFF76C75F),
        bgApp = Color(0xFF0E130E),
        surfaceCard = Color(0xFF171F17),
        primaryText = Color(0xFF0E130E),
        textPrimary = Color(0xFFEFF7EF)
    ),
    PresetTheme(
        name = "Royal Lavender",
        primary = Color(0xFFBB86FC),
        bgApp = Color(0xFF120E16),
        surfaceCard = Color(0xFF1D1724),
        primaryText = Color(0xFF120E16),
        textPrimary = Color(0xFFF2EBF7)
    ),
    PresetTheme(
        name = "Obsidian Red",
        primary = Color(0xFFFF3333),
        bgApp = Color(0xFF0F0B0B),
        surfaceCard = Color(0xFF1B1313),
        primaryText = Color(0xFFFFFFFF),
        textPrimary = Color(0xFFFFF0F0)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeMakerBottomSheet(
    onDismiss: () -> Unit,
    onThemeApplied: (MobTheme) -> Unit
) {
    val context = LocalContext.current
    // Force the bottom sheet to be fully expanded on open
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Save initial state so we can restore on cancel
    val previousMobTheme = remember { PocketColors.activeMobTheme }
    val previousPrimary = remember { CustomThemePalette.customPrimary }
    val previousBgApp = remember { CustomThemePalette.customBgApp }
    val previousSurfaceCard = remember { CustomThemePalette.customSurfaceCard }
    val previousPrimaryText = remember { CustomThemePalette.customPrimaryText }
    val previousTextPrimary = remember { CustomThemePalette.customTextPrimary }

    var isSaved by remember { mutableStateOf(false) }
    var isRgbMode by remember { mutableStateOf(false) }

    fun restorePreviousTheme() {
        if (!isSaved) {
            CustomThemePalette.customPrimary = previousPrimary
            CustomThemePalette.customBgApp = previousBgApp
            CustomThemePalette.customSurfaceCard = previousSurfaceCard
            CustomThemePalette.customPrimaryText = previousPrimaryText
            CustomThemePalette.customTextPrimary = previousTextPrimary
            PocketColors.activeMobTheme = previousMobTheme
        }
    }

    var activeCategory by remember { mutableStateOf(0) }
    val categories = listOf("Accent", "App BG", "Card BG", "Primary Text", "Main Text")

    val currentColor = when (activeCategory) {
        0 -> CustomThemePalette.customPrimary
        1 -> CustomThemePalette.customBgApp
        2 -> CustomThemePalette.customSurfaceCard
        3 -> CustomThemePalette.customPrimaryText
        else -> CustomThemePalette.customTextPrimary
    }

    val hsl = remember(currentColor) { colorToHsl(currentColor) }
    val hue = hsl[0]
    val saturation = hsl[1]
    val lightness = hsl[2]

    fun updateColor(h: Float, s: Float, l: Float) {
        val newColor = hslToColor(h, s, l)
        when (activeCategory) {
            0 -> CustomThemePalette.customPrimary = newColor
            1 -> CustomThemePalette.customBgApp = newColor
            2 -> CustomThemePalette.customSurfaceCard = newColor
            3 -> CustomThemePalette.customPrimaryText = newColor
            else -> CustomThemePalette.customTextPrimary = newColor
        }
        // Preview theme live
        PocketColors.activeMobTheme = MobTheme.CUSTOM
    }

    fun updateRgb(r: Float, g: Float, b: Float) {
        val newColor = Color(r, g, b)
        when (activeCategory) {
            0 -> CustomThemePalette.customPrimary = newColor
            1 -> CustomThemePalette.customBgApp = newColor
            2 -> CustomThemePalette.customSurfaceCard = newColor
            3 -> CustomThemePalette.customPrimaryText = newColor
            else -> CustomThemePalette.customTextPrimary = newColor
        }
        PocketColors.activeMobTheme = MobTheme.CUSTOM
    }

    fun applyPreset(preset: PresetTheme) {
        CustomThemePalette.customPrimary = preset.primary
        CustomThemePalette.customBgApp = preset.bgApp
        CustomThemePalette.customSurfaceCard = preset.surfaceCard
        CustomThemePalette.customPrimaryText = preset.primaryText
        CustomThemePalette.customTextPrimary = preset.textPrimary
        PocketColors.activeMobTheme = MobTheme.CUSTOM
    }

    ModalBottomSheet(
        onDismissRequest = {
            restorePreviousTheme()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = PocketColors.SurfaceCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(PocketColors.CardBorder, CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Custom Theme Maker",
                    color = PocketColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Design your own custom color palette",
                    color = PocketColors.TextSecondary,
                    fontSize = 12.sp
                )
            }

            // Presets
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Theme Presets",
                    color = PocketColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { preset ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(preset.surfaceCard)
                                .border(1.dp, preset.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { applyPreset(preset) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(preset.primary)
                            )
                            Text(
                                text = preset.name,
                                color = preset.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Tabs/Categories
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(categories.size) { idx ->
                    val isSelected = activeCategory == idx
                    val categoryColor = when (idx) {
                        0 -> CustomThemePalette.customPrimary
                        1 -> CustomThemePalette.customBgApp
                        2 -> CustomThemePalette.customSurfaceCard
                        3 -> CustomThemePalette.customPrimaryText
                        else -> CustomThemePalette.customTextPrimary
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) PocketColors.Primary.copy(alpha = 0.15f) else PocketColors.InactiveBg)
                            .border(
                                1.dp,
                                if (isSelected) PocketColors.Primary else PocketColors.InactiveBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { activeCategory = idx }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(categoryColor)
                                .border(1.dp, PocketColors.CardBorder, CircleShape)
                        )
                        Text(
                            text = categories[idx],
                            color = if (isSelected) PocketColors.TextPrimary else PocketColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Compact Mode Selector (Color Wheel vs RGB Sliders)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PocketColors.InactiveBg)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isRgbMode) PocketColors.Primary else Color.Transparent)
                        .clickable { isRgbMode = false }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Color Wheel",
                        color = if (!isRgbMode) PocketColors.PrimaryText else PocketColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isRgbMode) PocketColors.Primary else Color.Transparent)
                        .clickable { isRgbMode = true }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        "RGB / HEX",
                        color = if (isRgbMode) PocketColors.PrimaryText else PocketColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Main Editor Section (Wheel or RGB depending on Mode)
            if (!isRgbMode) {
                // Color Wheel Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        val radius = size.width / 2f
                                        val dx = offset.x - centerX
                                        val dy = offset.y - centerY
                                        val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                                        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).let { if (it < 0) it + 360 else it }
                                        updateColor(angle.toFloat(), (dist / radius).toFloat(), lightness)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        val position = change.position
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        val radius = size.width / 2f
                                        val dx = position.x - centerX
                                        val dy = position.y - centerY
                                        val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                                        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).let { if (it < 0) it + 360 else it }
                                        updateColor(angle.toFloat(), (dist / radius).toFloat(), lightness)
                                    }
                                }
                        ) {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val radius = size.width / 2f

                            val sweepBrush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                ),
                                center = Offset(centerX, centerY)
                            )
                            drawCircle(brush = sweepBrush, radius = radius, center = Offset(centerX, centerY))

                            val radialBrush = Brush.radialGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                center = Offset(centerX, centerY),
                                radius = radius
                            )
                            drawCircle(brush = radialBrush, radius = radius, center = Offset(centerX, centerY))

                            drawCircle(
                                color = Color.Black.copy(alpha = 0.15f),
                                radius = radius,
                                center = Offset(centerX, centerY),
                                style = Stroke(width = 1.dp.toPx())
                            )

                            val angleRad = Math.toRadians(hue.toDouble())
                            val dist = saturation * radius
                            val handleX = centerX + cos(angleRad).toFloat() * dist
                            val handleY = centerY + sin(angleRad).toFloat() * dist

                            drawCircle(
                                color = currentColor,
                                radius = 9.dp.toPx(),
                                center = Offset(handleX, handleY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 9.dp.toPx(),
                                center = Offset(handleX, handleY),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.4f),
                                radius = 10.dp.toPx(),
                                center = Offset(handleX, handleY),
                                style = Stroke(width = 0.5.dp.toPx())
                            )
                        }
                    }

                    // Lightness Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lightness", color = PocketColors.TextSecondary, fontSize = 12.sp)
                            Text("${(lightness * 100).toInt()}%", color = PocketColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = lightness,
                            onValueChange = { updateColor(hue, saturation, it) },
                            colors = SliderDefaults.colors(
                                thumbColor = PocketColors.Primary,
                                activeTrackColor = PocketColors.Primary.copy(alpha = 0.5f),
                                inactiveTrackColor = PocketColors.InactiveBg
                            )
                        )
                    }
                }
            } else {
                // RGB / HEX Mode Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("RGB Sliders", color = PocketColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    // Red
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("R", color = PocketColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.width(12.dp))
                        Slider(
                            value = currentColor.red,
                            onValueChange = { updateRgb(it, currentColor.green, currentColor.blue) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Red,
                                activeTrackColor = Color.Red.copy(alpha = 0.4f)
                            )
                        )
                        Text("${(currentColor.red * 255).toInt()}", color = PocketColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                    }

                    // Green
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("G", color = PocketColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.width(12.dp))
                        Slider(
                            value = currentColor.green,
                            onValueChange = { updateRgb(currentColor.red, it, currentColor.blue) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Green,
                                activeTrackColor = Color.Green.copy(alpha = 0.4f)
                            )
                        )
                        Text("${(currentColor.green * 255).toInt()}", color = PocketColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                    }

                    // Blue
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("B", color = PocketColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.width(12.dp))
                        Slider(
                            value = currentColor.blue,
                            onValueChange = { updateRgb(currentColor.red, currentColor.green, it) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Blue,
                                activeTrackColor = Color.Blue.copy(alpha = 0.4f)
                            )
                        )
                        Text("${(currentColor.blue * 255).toInt()}", color = PocketColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                    }

                    // HEX Code TextField
                    val hexText = remember(currentColor) { colorToHexString(currentColor) }
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { newVal ->
                            val parsed = parseHexToColor(newVal)
                            if (parsed != null) {
                                updateRgb(parsed.red, parsed.green, parsed.blue)
                            }
                        },
                        label = { Text("HEX Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PocketColors.Primary,
                            unfocusedBorderColor = PocketColors.CardBorder
                        )
                    )
                }
            }

            // Save / Cancel Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        restorePreviousTheme()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PocketColors.TextPrimary
                    ),
                    border = borderStroke()
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        isSaved = true
                        ThemePreferenceStore.saveCustomColors(context)
                        ThemePreferenceStore.saveMobTheme(context, MobTheme.CUSTOM)
                        com.pocketcraft.server.server.ServerHostService.pushWidgetUpdate(context)
                        onThemeApplied(MobTheme.CUSTOM)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PocketColors.Primary,
                        contentColor = PocketColors.PrimaryText
                    )
                ) {
                    Text("Save Theme")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun borderStroke() = androidx.compose.foundation.BorderStroke(
    1.dp,
    PocketColors.CardBorder
)

private fun colorToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min

    var h = 0f
    var s = 0f
    val l = (max + min) / 2f

    if (max != min) {
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        h *= 60f
    }
    return floatArrayOf(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}

private fun colorToHexString(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06X", argb and 0xFFFFFF)
}

private fun parseHexToColor(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    return runCatching {
        val colorInt = android.graphics.Color.parseColor("#" + clean)
        Color(colorInt)
    }.getOrNull()
}
