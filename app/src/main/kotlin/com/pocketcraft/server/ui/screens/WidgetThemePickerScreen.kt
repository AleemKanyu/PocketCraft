package com.pocketcraft.server.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.pocketcraft.server.server.ServerHostService
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.widget.WidgetThemePrefs
import com.pocketcraft.server.widget.previewThemeScheme
import com.pocketcraft.server.widget.themeFor
import kotlin.math.abs
import kotlinx.coroutines.launch

private data class WidgetThemeOption(
    val key: String,
    val title: String,
    val subtitle: String,
    val proOnly: Boolean,
    val fullSpan: Boolean = false
)

private data class WidgetPaletteState(
    val background: Color,
    val accent: Color,
    val textOnAccent: Color,
    val iconTint: Color
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WidgetThemePickerScreen(
    isPremium: Boolean,
    onBack: () -> Unit,
    onPremiumUpgradeClick: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val options = remember {
        listOf(
            WidgetThemeOption(
                key = WidgetThemePrefs.THEME_CREEPER,
                title = "Creeper",
                subtitle = "PocketCraft's classic widget look.",
                proOnly = false
            ),
            WidgetThemeOption(
                key = WidgetThemePrefs.THEME_CREEPER_DARK,
                title = "Creeper Dark",
                subtitle = "A darker version of the classic palette.",
                proOnly = false
            ),
            WidgetThemeOption(
                key = WidgetThemePrefs.THEME_DIAMOND,
                title = "Diamond",
                subtitle = "Cool blue glass and brighter contrast.",
                proOnly = true
            ),
            WidgetThemeOption(
                key = WidgetThemePrefs.THEME_NETHER,
                title = "Nether",
                subtitle = "Warm crimson tones for a bolder widget.",
                proOnly = true
            ),
            WidgetThemeOption(
                key = WidgetThemePrefs.THEME_CUSTOM,
                title = "Custom Theme",
                subtitle = "Build your own widget palette.",
                proOnly = true,
                fullSpan = true
            )
        )
    }

    var selectedTheme by remember { mutableStateOf(WidgetThemePrefs.THEME_FOLLOW_APP) }
    var manualOverride by remember { mutableStateOf(false) }
    var customPalette by remember {
        mutableStateOf(
            WidgetPaletteState(
                background = Color(0xFF182019),
                accent = Color(0xFF6CBF57),
                textOnAccent = Color.White,
                iconTint = Color(0xFFE9FFD6)
            )
        )
    }
    var showCustomEditor by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val settings = WidgetThemePrefs.ensureAllowedTheme(context)
        selectedTheme = settings.selectedTheme.ifBlank { WidgetThemePrefs.THEME_FOLLOW_APP }
        manualOverride = settings.manualOverride
        customPalette = WidgetPaletteState(
            background = Color(settings.customBackground ?: Color(0xFF182019).toArgb()),
            accent = Color(settings.customAccent ?: Color(0xFF6CBF57).toArgb()),
            textOnAccent = Color(settings.customTextOnAccent ?: Color.White.toArgb()),
            iconTint = Color(settings.customIconTint ?: Color(0xFFE9FFD6).toArgb())
        )
    }

    val themeSettings = com.pocketcraft.server.data.preferences.WidgetThemeSettings(
        selectedTheme = selectedTheme,
        manualOverride = manualOverride,
        customBackground = customPalette.background.toArgb(),
        customAccent = customPalette.accent.toArgb(),
        customTextOnAccent = customPalette.textOnAccent.toArgb(),
        customIconTint = customPalette.iconTint.toArgb()
    )
    val previewScheme = previewThemeScheme(
        context = context,
        themeKey = if (manualOverride) selectedTheme else WidgetThemePrefs.THEME_FOLLOW_APP,
        settings = themeSettings
    )

    androidx.compose.material3.Scaffold(
        containerColor = PocketColors.BgApp,
        topBar = {
            TopAppBar(
                title = { Text("Widget Themes") },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable(onClick = onBack)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PocketColors.BgApp,
                    titleContentColor = PocketColors.TextPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 120.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                WidgetThemeTile(
                    title = "Follow App Theme",
                    subtitle = "Keeps the widget in sync with the app's current colors until you choose a widget-only style.",
                    preview = previewThemeScheme(context, WidgetThemePrefs.THEME_FOLLOW_APP, themeSettings),
                    active = !manualOverride || selectedTheme == WidgetThemePrefs.THEME_FOLLOW_APP,
                    locked = false,
                    badge = "Default",
                    onClick = {
                        scope.launch {
                            WidgetThemePrefs.saveSelection(
                                context = context,
                                themeKey = WidgetThemePrefs.THEME_FOLLOW_APP,
                                manualOverride = false
                            )
                            ServerHostService.pushWidgetUpdate(context)
                            selectedTheme = WidgetThemePrefs.THEME_FOLLOW_APP
                            manualOverride = false
                        }
                    }
                )
            }

            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel(
                        title = "Live Preview",
                        subtitle = if (manualOverride) {
                            "This preview reflects the widget's manual theme selection."
                        } else {
                            "This preview follows the app theme automatically."
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    WidgetPreviewCard(scheme = previewScheme)
                }
            }

            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                SectionLabel(
                    title = "Theme Library",
                    subtitle = "Pick a widget-only theme if you want it to look different from the app."
                )
            }

            items(
                items = options,
                key = { it.key },
                span = { option ->
                    androidx.compose.foundation.lazy.grid.GridItemSpan(if (option.fullSpan) maxLineSpan else 1)
                }
            ) { option ->
                WidgetThemeTile(
                    title = option.title,
                    subtitle = option.subtitle,
                    preview = previewThemeScheme(context, option.key, themeSettings),
                    active = manualOverride && selectedTheme == option.key,
                    locked = option.proOnly && !isPremium,
                    badge = if (option.proOnly) "Pro" else null,
                    onClick = {
                        if (option.proOnly && !isPremium) {
                            onPremiumUpgradeClick()
                            return@WidgetThemeTile
                        }
                        if (option.key == WidgetThemePrefs.THEME_CUSTOM) {
                            showCustomEditor = true
                        } else {
                            scope.launch {
                                WidgetThemePrefs.saveSelection(context, option.key, manualOverride = true)
                                ServerHostService.pushWidgetUpdate(context)
                                selectedTheme = option.key
                                manualOverride = true
                            }
                        }
                    }
                )
            }
        }
    }

    if (showCustomEditor) {
        WidgetCustomThemeBottomSheet(
            initialPalette = customPalette,
            onDismiss = { showCustomEditor = false },
            onSave = { palette ->
                scope.launch {
                    WidgetThemePrefs.saveCustomPalette(
                        context = context,
                        background = palette.background.toArgb(),
                        accent = palette.accent.toArgb(),
                        textOnAccent = palette.textOnAccent.toArgb(),
                        iconTint = palette.iconTint.toArgb()
                    )
                    WidgetThemePrefs.saveSelection(context, WidgetThemePrefs.THEME_CUSTOM, manualOverride = true)
                    ServerHostService.pushWidgetUpdate(context)
                    customPalette = palette
                    selectedTheme = WidgetThemePrefs.THEME_CUSTOM
                    manualOverride = true
                    showCustomEditor = false
                    onMessage("Widget custom theme saved.")
                }
            }
        )
    }
}

@Composable
private fun WidgetThemeTile(
    title: String,
    subtitle: String,
    preview: com.pocketcraft.server.widget.WidgetColorScheme,
    active: Boolean,
    locked: Boolean,
    badge: String?,
    onClick: () -> Unit
) {
    GameCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) PocketColors.Primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(18.dp)
            ),
        contentPadding = PaddingValues(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(preview.backgroundColor).copy(alpha = 0.98f),
                            Color(preview.backgroundColor).copy(alpha = 0.88f)
                        )
                    )
                )
        ) {

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        color = Color(preview.textPrimary),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = subtitle,
                        color = Color(preview.textSecondary),
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
                if (locked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(preview.textPrimary),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(text = badge ?: "Pro", color = Color(preview.textPrimary), fontSize = 11.sp)
                    }
                } else if (active) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                            tint = Color(preview.onlineDotColor),
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (badge != null) {
                        Text(text = badge, color = Color(preview.textSecondary), fontSize = 11.sp)
                    }
                }
            }
        }
    }

@Composable
private fun WidgetPreviewCard(scheme: com.pocketcraft.server.widget.WidgetColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(scheme.backgroundColor).copy(alpha = 0.96f),
                        Color(scheme.backgroundColor).copy(alpha = 0.88f)
                    )
                )
            )
            .border(1.dp, Color(scheme.borderColor).copy(alpha = 0.9f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(scheme.iconBadgeColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▤", color = Color(scheme.iconTint), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("world", color = Color(scheme.textPrimary), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(scheme.versionChipBg))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("1.21.11", color = Color(scheme.textSecondary), fontSize = 12.sp)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("3 / 10", color = Color(scheme.textPrimary), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(scheme.onlineDotColor))
                        )
                        Text("online", color = Color(scheme.textSecondary), fontSize = 12.sp)
                    }
                }
            }
            HorizontalDivider(color = Color(scheme.borderColor).copy(alpha = 0.65f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(scheme.statusBg))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text("• RUNNING", color = Color(scheme.statusText), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("UPTIME", color = Color(scheme.textMuted), fontSize = 11.sp)
                    Text("01:24:09", color = Color(scheme.textPrimary), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("▷", "START", Pair(Color(scheme.buttonDisabledBg), Color(scheme.buttonDisabledTint))),
                        Triple("□", "STOP", Pair(Color(scheme.buttonStopBg), Color(scheme.buttonStopTint))),
                        Triple("↻", "RESTART", Pair(Color(scheme.buttonRestartBg), Color(scheme.buttonRestartTint)))
                    ).forEach { (symbol, label, colors) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.first)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(symbol, color = colors.second, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(label, color = colors.second, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WidgetCustomThemeBottomSheet(
    initialPalette: WidgetPaletteState,
    onDismiss: () -> Unit,
    onSave: (WidgetPaletteState) -> Unit
) {
    var palette by remember(initialPalette) { mutableStateOf(initialPalette) }
    var selectedChannel by remember { mutableIntStateOf(0) }

    val currentColor = when (selectedChannel) {
        0 -> palette.background
        1 -> palette.accent
        2 -> palette.textOnAccent
        else -> palette.iconTint
    }
    val hsl = remember(currentColor) { colorToHsl(currentColor) }
    var hue by remember(currentColor) { mutableStateOf(hsl[0]) }
    var saturation by remember(currentColor) { mutableStateOf(hsl[1]) }
    var lightness by remember(currentColor) { mutableStateOf(hsl[2]) }
    var hexValue by remember(currentColor) { mutableStateOf(colorToHexString(currentColor)) }

    fun applyColor(color: Color) {
        palette = when (selectedChannel) {
            0 -> palette.copy(background = color)
            1 -> palette.copy(accent = color)
            2 -> palette.copy(textOnAccent = color)
            else -> palette.copy(iconTint = color)
        }
        hexValue = colorToHexString(color)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PocketColors.SurfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    .padding(horizontal = 20.dp, vertical = 3.dp)
            )
            Text(
                text = "Custom Widget Palette",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = PocketColors.TextPrimary
            )
            Text(
                text = "Tune the four widget colors separately, then save them as your Pro widget theme.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            WidgetPreviewCard(
                scheme = themeFor(
                    WidgetThemePrefs.THEME_CUSTOM,
                    com.pocketcraft.server.data.preferences.WidgetThemeSettings(
                        selectedTheme = WidgetThemePrefs.THEME_CUSTOM,
                        manualOverride = true,
                        customBackground = palette.background.toArgb(),
                        customAccent = palette.accent.toArgb(),
                        customTextOnAccent = palette.textOnAccent.toArgb(),
                        customIconTint = palette.iconTint.toArgb()
                    )
                )
            )
            SectionLabel(
                title = "Edit Channels",
                subtitle = "Switch between palette channels and watch the preview update live."
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Background", "Accent", "Badge Text", "Icon/Text").forEachIndexed { index, label ->
                    val active = selectedChannel == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (active) PocketColors.PrimaryMuted.copy(alpha = 0.9f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                            )
                            .clickable {
                                selectedChannel = index
                                val nextColor = when (index) {
                                    0 -> palette.background
                                    1 -> palette.accent
                                    2 -> palette.textOnAccent
                                    else -> palette.iconTint
                                }
                                val nextHsl = colorToHsl(nextColor)
                                hue = nextHsl[0]
                                saturation = nextHsl[1]
                                lightness = nextHsl[2]
                                hexValue = colorToHexString(nextColor)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) PocketColors.PrimaryDark else PocketColors.TextPrimary
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
            }
            SectionLabel(
                title = "Fine Tune",
                subtitle = "Use the sliders or paste a hex code for precise control."
            )
            Slider(
                value = hue,
                valueRange = 0f..360f,
                onValueChange = {
                    hue = it
                    applyColor(hslToColor(hue, saturation, lightness))
                }
            )
            Slider(
                value = saturation,
                valueRange = 0f..1f,
                onValueChange = {
                    saturation = it
                    applyColor(hslToColor(hue, saturation, lightness))
                }
            )
            Slider(
                value = lightness,
                valueRange = 0f..1f,
                onValueChange = {
                    lightness = it
                    applyColor(hslToColor(hue, saturation, lightness))
                }
            )
            OutlinedTextField(
                value = hexValue,
                onValueChange = {
                    hexValue = it
                    parseHexToColor(it)?.let { parsed ->
                        val parsedHsl = colorToHsl(parsed)
                        hue = parsedHsl[0]
                        saturation = parsedHsl[1]
                        lightness = parsedHsl[2]
                        applyColor(parsed)
                    }
                },
                label = { Text("Hex") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(palette) },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PocketColors.TextPrimary)
        Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
    return String.format("#%06X", color.toArgb() and 0xFFFFFF)
}

private fun parseHexToColor(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    return runCatching { Color(android.graphics.Color.parseColor("#$clean")) }.getOrNull()
}
