package com.pocketcraft.server.ui.navigation

import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.R
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.pocketGlassControlBrush
import com.pocketcraft.server.ui.theme.raisedBorder
import com.pocketcraft.server.ui.util.MobTheme
import androidx.compose.ui.draw.drawBehind

import com.pocketcraft.server.util.LocalAppStrings

enum class PocketTab { HOME, CONSOLE, PLAYERS, STORAGE, MODS, SETTINGS }

val allTabs = listOf(
    PocketTab.HOME,
    PocketTab.PLAYERS,
    PocketTab.STORAGE,
    PocketTab.MODS,
    PocketTab.SETTINGS
)

@Composable
fun PocketTab.label(): String {
    val s = LocalAppStrings.current
    return when (this) {
        PocketTab.HOME -> s.tabHome
        PocketTab.CONSOLE -> s.tabHome
        PocketTab.PLAYERS -> s.tabPlayers
        PocketTab.STORAGE -> s.tabStorage
        PocketTab.MODS -> s.tabMods
        PocketTab.SETTINGS -> s.tabSettings
    }
}

fun PocketTab.icon() = when (this) {
    PocketTab.HOME, PocketTab.CONSOLE -> Icons.Outlined.Home
    PocketTab.PLAYERS -> Icons.Outlined.Group
    PocketTab.STORAGE -> Icons.Outlined.FolderOpen
    PocketTab.MODS -> Icons.Outlined.GridView
    PocketTab.SETTINGS -> Icons.Outlined.Settings
}

fun PocketTab.selectedIcon() = when (this) {
    PocketTab.HOME, PocketTab.CONSOLE -> Icons.Filled.Home
    PocketTab.PLAYERS -> Icons.Filled.Group
    PocketTab.STORAGE -> Icons.Filled.Folder
    PocketTab.MODS -> Icons.Filled.GridView
    PocketTab.SETTINGS -> Icons.Filled.Settings
}

val bottomNavTabs = listOf(
    PocketTab.HOME,
    PocketTab.PLAYERS,
    PocketTab.STORAGE,
    PocketTab.MODS,
    PocketTab.SETTINGS
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketTopBar(
    relayHost: String,
    onRelayHostChange: (String) -> Unit,
    currentMobTheme: MobTheme,
    onMobThemeChange: (MobTheme) -> Unit,
    relayLocked: Boolean = false
) {
    var relayMenuExpanded by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }
    val relayOptions = mapOf(
        "play.pocketcraft.online" to "Global",
        "mine.pocketcraft.online" to "Asia"
    )
    val glassButtonBorder = PocketColors.IconBtnBorder
    val glassButtonDepth = PocketColors.IconBtnBorderBottom
    val subduedIconTint = PocketColors.IconBtnIcon
    val accentTint = PocketColors.Primary

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = PocketColors.FooterLogoBg,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .drawBehind {
                            raisedBorder(
                                cornerRadius      = 9.dp.toPx(),
                                borderColor       = PocketColors.PrimaryBorder,
                                bottomBorderColor = PocketColors.PrimaryBorderBottom,
                                sideWidthPx       = 1.5.dp.toPx(),
                                bottomWidthPx     = 3.dp.toPx()
                            )
                        }
                        .padding(5.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_creeper),
                        contentDescription = "PocketCraft icon",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = "PocketCraft",
                    fontFamily = com.pocketcraft.server.ui.theme.Monocraft,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            val context = LocalContext.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Discord button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .raisedBorder(
                            color = glassButtonBorder,
                            depthColor = glassButtonDepth,
                            cornerRadius = 17.dp,
                            borderWidth = 1.dp,
                            depthWidth = 2.5.dp
                        )
                        .clip(RoundedCornerShape(17.dp))
                        .background(PocketColors.IconBtnBg)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/pocketcraft"))
                            context.startActivity(intent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.discord_colored),
                        contentDescription = "Join Discord",
                        modifier = Modifier.size(18.dp),
                        tint = Color.Unspecified
                    )
                }

                Box {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .raisedBorder(
                                color = glassButtonBorder,
                                depthColor = glassButtonDepth,
                                cornerRadius = 17.dp,
                                borderWidth = 1.dp,
                                depthWidth = 2.5.dp
                            )
                            .clip(RoundedCornerShape(17.dp))
                            .background(PocketColors.IconBtnBg)
                            .clickable { themeMenuExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = "Choose mob theme",
                            modifier = Modifier.size(16.dp),
                            tint = subduedIconTint.copy(alpha = 0.86f)
                        )
                    }
                    DropdownMenu(
                        expanded = themeMenuExpanded,
                        onDismissRequest = { themeMenuExpanded = false },
                        shape = RoundedCornerShape(22.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        shadowElevation = 10.dp
                    ) {
                        MobTheme.entries.forEach { theme ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(theme.themeName)
                                        if (theme == currentMobTheme) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = PocketColors.Primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    themeMenuExpanded = false
                                    onMobThemeChange(theme)
                                }
                            )
                        }
                    }
                }

                Box {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .raisedBorder(
                                color = glassButtonBorder,
                                depthColor = glassButtonDepth,
                                cornerRadius = 17.dp,
                                borderWidth = 1.dp,
                                depthWidth = 2.5.dp
                            )
                            .clip(RoundedCornerShape(17.dp))
                            .background(PocketColors.IconBtnBg)
                            .clickable(enabled = !relayLocked) { relayMenuExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (relayLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f) else accentTint.copy(alpha = 0.86f)
                        )
                    }
                    DropdownMenu(
                        expanded = relayMenuExpanded,
                        onDismissRequest = { relayMenuExpanded = false },
                        shape = RoundedCornerShape(22.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        shadowElevation = 10.dp
                    ) {
                        relayOptions.forEach { (host, label) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label)
                                        if (host == relayHost) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = PocketColors.Primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    relayMenuExpanded = false
                                    if (!relayLocked) {
                                        onRelayHostChange(host)
                                    }
                                }
                            )
                        }
                        androidx.compose.material3.HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "More servers coming soon...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            onClick = { relayMenuExpanded = false },
                            enabled = false
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PocketColors.BgApp,
            titleContentColor = PocketColors.TextPrimary,
            actionIconContentColor = PocketColors.IconBtnIcon
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketBottomNav(
    currentTab: PocketTab,
    onTabSelected: (PocketTab) -> Unit
) {
    val context = LocalContext.current
    val navIndicatorShape by AppPreferencesStore.getNavIndicatorShapeFlow(context).collectAsState(initial = "PILL")

    val navBgColor = PocketColors.FooterBg
    val footerContainerShape = remember { ReverseCurvedFooterShape() }

    val selectedColor = PocketColors.NavActiveText
    val unselectedColor = PocketColors.FooterText
    val tabTextShadow = Shadow(color = Color.Transparent, offset = Offset.Zero, blurRadius = 0f)

    val navItemSlotWidth = 64.dp
    val navItemSlotHeight = 56.dp
    val footerCurveHeight = 18.dp
    val navRowHeight = navItemSlotHeight
    val circleSizeDp = 42.dp

    val targetCornerRadius = when (navIndicatorShape) {
        "BLOCK" -> 10.dp
        else -> 22.dp
    }
    val animatedCornerRadius by animateDpAsState(
        targetValue = targetCornerRadius,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    )

    val indicatorShape = RoundedCornerShape(animatedCornerRadius)
    val indicatorPillColor = PocketColors.NavActivePillBg
    val indicatorBrush = Brush.linearGradient(colors = listOf(indicatorPillColor, indicatorPillColor))
    val indicatorBorder = PocketColors.NavActivePillBorder
    val indicatorDepth = PocketColors.NavActivePillBorderBottom

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(footerContainerShape)
            .background(navBgColor)
            .navigationBarsPadding()
    ) {
        Spacer(Modifier.height(footerCurveHeight))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(navRowHeight)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavTabs.forEach { tab ->
                val selected = currentTab == tab
                val tabScale by animateFloatAsState(
                    targetValue = if (selected) 1.06f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "bottom_nav_tab_scale"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(navRowHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(tab)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        val indicatorModifier = when (navIndicatorShape) {
                            "BLOCK" -> Modifier
                                .fillMaxWidth()
                                .height(navItemSlotHeight)
                            "CIRCLE" -> Modifier.size(circleSizeDp)
                            else -> Modifier.size(navItemSlotWidth, navItemSlotHeight)
                        }
                        Box(
                            modifier = indicatorModifier
                                .raisedBorder(
                                    color = indicatorBorder,
                                    depthColor = indicatorDepth,
                                    cornerRadius = animatedCornerRadius,
                                    borderWidth = 1.dp,
                                    depthWidth = 2.5.dp
                                )
                                .clip(indicatorShape)
                                .background(brush = indicatorBrush, shape = indicatorShape)
                        )
                    }
                    Column(
                        modifier = Modifier.scale(tabScale),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon() else tab.icon(),
                            contentDescription = tab.label(),
                            modifier = Modifier.size(21.dp),
                            tint = if (selected) selectedColor else unselectedColor
                        )
                        Text(
                            text = tab.label(),
                            fontSize = 9.5.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (selected) selectedColor else unselectedColor,
                            style = MaterialTheme.typography.labelSmall.copy(shadow = tabTextShadow),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private class ReverseCurvedFooterShape : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val curveDepth = with(density) { 18.dp.toPx() }.coerceAtMost(size.height * 0.35f)
        val curveWidth = with(density) { 52.dp.toPx() }.coerceAtMost(size.width * 0.22f)

        val path = Path().apply {
            moveTo(0f, curveDepth)
            cubicTo(
                curveWidth * 0.20f, curveDepth,
                curveWidth * 0.34f, 0f,
                curveWidth, 0f
            )
            lineTo(size.width - curveWidth, 0f)
            cubicTo(
                size.width - curveWidth * 0.34f, 0f,
                size.width - curveWidth * 0.20f, curveDepth,
                size.width, curveDepth
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}
