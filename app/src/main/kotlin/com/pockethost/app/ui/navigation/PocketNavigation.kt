package com.pockethost.app.ui.navigation

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import com.pockethost.app.billing.BillingManager
import com.pockethost.app.data.preferences.AppPreferencesStore
import com.pockethost.app.config.RelayServers
import com.pockethost.app.config.RemoteConfigManager
import android.net.Uri
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Language
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
import com.pockethost.app.ui.components.PocketDropdownMenu
import com.pockethost.app.ui.components.PocketDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.data.model.RelayRegion
import com.pockethost.app.R
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.PocketMotion
import com.pockethost.app.ui.theme.pocketGlassControlBrush
import com.pockethost.app.ui.theme.pocketIsDarkTheme
import com.pockethost.app.ui.theme.raisedBorder
import com.pockethost.app.ui.util.MobTheme
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.pockethost.app.ui.util.playTickHaptic

import com.pockethost.app.util.LocalAppStrings

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
    relayLocked: Boolean = false,
    onPremiumUpgradeClick: () -> Unit = {}
) {
    var relayMenuExpanded by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val billingManager = remember { BillingManager.getInstance(context) }
    val isPremium by billingManager.isPremium.collectAsState()
    val relayRegions by RemoteConfigManager.relayRegions.collectAsState(initial = RelayServers.defaultRegions())
    val relayOptions = relayRegions
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
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            color = PocketColors.Primary,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = PocketColors.PrimaryBorder,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .padding(2.5.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "PocketHost icon",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    text = "PocketHost",
                    fontFamily = com.pockethost.app.ui.theme.Monocraft,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = when {
                        LocalConfiguration.current.screenWidthDp < 360 -> 14.sp
                        LocalConfiguration.current.screenWidthDp < 400 -> 17.sp
                        else -> 20.sp
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        },
        actions = {
            val context = LocalContext.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Support & Donate button — shown randomly but frequently (~60% of sessions).
                val showDonationButton = remember { (0..9).random() < 6 }
                if (showDonationButton) {
                val heartTransition = rememberInfiniteTransition(label = "topbar_heart_pulse")
                val heartPulseScale by heartTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "heart_pulse_scale"
                )

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            scaleX = heartPulseScale
                            scaleY = heartPulseScale
                        }
                        .raisedBorder(
                            color = Color(0xFFFF4B72).copy(alpha = 0.55f),
                            depthColor = Color(0xFFC2185B).copy(alpha = 0.65f),
                            cornerRadius = 17.dp,
                            borderWidth = 1.dp,
                            depthWidth = 2.5.dp
                        )
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            if (pocketIsDarkTheme()) {
                                Color(0xFFFF4B72).copy(alpha = 0.14f)
                            } else {
                                Color(0xFFFF4B72).copy(alpha = 0.08f)
                            }
                        )
                        .clickable {
                            onPremiumUpgradeClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_heart_outline),
                        contentDescription = "Support & Donate",
                        modifier = Modifier.size(17.dp),
                        tint = Color(0xFFFF4B72)
                    )
                }
                } // end showDonationButton

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
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.discord_invite_url)))
                            context.startActivity(intent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.discord_colored),
                        contentDescription = "Join Discord",
                        modifier = Modifier.size(18.dp),
                        tint = if (pocketIsDarkTheme()) Color.White else Color.Unspecified
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
                    PocketDropdownMenu(
                        expanded = themeMenuExpanded,
                        onDismissRequest = { themeMenuExpanded = false }
                    ) {
                        MobTheme.entries.forEach { theme ->
                            val isLocked = theme == MobTheme.CUSTOM && !isPremium
                            PremiumDropdownItem(
                                title = theme.themeName,
                                subtitle = if (isLocked) {
                                    "Custom theme is a Pro feature"
                                } else if (theme == currentMobTheme) {
                                    "Currently active"
                                } else {
                                    "Apply this theme"
                                },
                                selected = theme == currentMobTheme,
                                locked = isLocked,
                                lockedLabel = if (theme == MobTheme.CUSTOM) "Pro" else "Soon",
                                onClick = {
                                    themeMenuExpanded = false
                                    if (isLocked) {
                                        onPremiumUpgradeClick()
                                    } else {
                                        onMobThemeChange(theme)
                                    }
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
                            .clickable { relayMenuExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = accentTint.copy(alpha = 0.86f)
                        )
                    }
                    PocketDropdownMenu(
                        expanded = relayMenuExpanded,
                        onDismissRequest = { relayMenuExpanded = false }
                    ) {
                        MenuHeader(
                            title = "Relay Regions",
                            subtitle = if (relayLocked) {
                                "Switch relay while online — players may need to rejoin"
                            } else {
                                "Choose the best route for your players"
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        relayOptions.forEach { region ->
                            val isComingSoon = false
                            PremiumDropdownItem(
                                title = region.label,
                                subtitle = if (isComingSoon) {
                                    "America region is coming soon"
                                } else if (region.host == relayHost) {
                                    if (relayLocked) "Currently active" else "Currently selected"
                                } else if (relayLocked) {
                                    "Switch relay now (players may rejoin)"
                                } else {
                                    "Tap to switch relay region"
                                },
                                selected = region.host == relayHost,
                                locked = isComingSoon,
                                onClick = {
                                    relayMenuExpanded = false
                                    if (isComingSoon) {
                                        Toast.makeText(context, "America server coming soon", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onRelayHostChange(region.host)
                                    }
                                }
                            )
                        }
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

@Composable
private fun MenuHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PremiumDropdownItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    locked: Boolean = false,
    lockedLabel: String = "Soon",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        selected -> Brush.horizontalGradient(
                            listOf(
                                PocketColors.Primary.copy(alpha = 0.18f),
                                PocketColors.PrimaryMuted.copy(alpha = 0.42f)
                            )
                        )
                        locked -> Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                            )
                        )
                        else -> Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f)
                            )
                        )
                    }
                )
                .border(
                    width = if (selected) 1.2.dp else 1.dp,
                    color = when {
                        selected -> PocketColors.Primary.copy(alpha = 0.42f)
                        locked -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)
                    },
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                locked -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = PocketColors.PrimaryDark
                    )
                    Text(
                        text = lockedLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PocketColors.PrimaryDark
                    )
                }
                selected -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = PocketColors.Primary
                )
            }
        }
    }
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

    val navItemSlotHeight = 56.dp
    val footerCurveHeight = 20.dp
    val navRowHeight = navItemSlotHeight
    val circleSizeDp = 42.dp
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val targetCornerRadius = when (navIndicatorShape) {
        "BLOCK" -> 10.dp
        else -> 22.dp
    }
    val animatedCornerRadius by animateDpAsState(
        targetValue = targetCornerRadius,
        animationSpec = PocketMotion.gentleSpringDp(stiffness = Spring.StiffnessMediumLow)
    )

    val indicatorShape = RoundedCornerShape(animatedCornerRadius)
    val indicatorPillColor = PocketColors.NavActivePillBg
    val indicatorBrush = Brush.linearGradient(colors = listOf(indicatorPillColor, indicatorPillColor))
    val indicatorBorder = PocketColors.NavActivePillBorder
    val indicatorDepth = PocketColors.NavActivePillBorderBottom

    var rowWidth by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .curvedFooterBorder(PocketColors.NavActivePillBorder.copy(alpha = 0.5f), 1.2.dp)
            .clip(footerContainerShape)
            .background(Brush.verticalGradient(colors = listOf(navBgColor.copy(alpha = 0.95f), navBgColor)))
    ) {
        Spacer(Modifier.height(footerCurveHeight))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(navRowHeight)
                .padding(horizontal = 8.dp, vertical = 7.dp)
                .onGloballyPositioned { coordinates ->
                    rowWidth = coordinates.size.width
                }
                .pointerInput(rowWidth) {
                    if (rowWidth <= 0) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val initialIndex = ((down.position.x / rowWidth) * bottomNavTabs.size)
                            .toInt()
                            .coerceIn(0, bottomNavTabs.lastIndex)
                        
                        playTickHaptic(context)
                        onTabSelected(bottomNavTabs[initialIndex])
                        
                        var lastIndex = initialIndex
                        while (true) {
                            val event = awaitPointerEvent()
                            val anyDown = event.changes.any { it.pressed }
                            if (!anyDown) break
                            
                            val change = event.changes.firstOrNull() ?: continue
                            val index = ((change.position.x / rowWidth) * bottomNavTabs.size)
                                .toInt()
                                .coerceIn(0, bottomNavTabs.lastIndex)
                            
                            if (index != lastIndex) {
                                lastIndex = index
                                playTickHaptic(context)
                                onTabSelected(bottomNavTabs[index])
                            }
                            change.consume()
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavTabs.forEach { tab ->
                val selected = currentTab == tab
                val tabScale by animateFloatAsState(
                    targetValue = if (selected) 1.03f else 1f,
                    animationSpec = PocketMotion.gentleSpringFloat(stiffness = Spring.StiffnessMediumLow),
                    label = "bottom_nav_tab_scale"
                )
                val tabOffset by animateDpAsState(
                    targetValue = if (selected) (-2).dp else 0.dp,
                    animationSpec = PocketMotion.gentleSpringDp(stiffness = Spring.StiffnessMediumLow),
                    label = "bottom_nav_tab_offset"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(navRowHeight),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        val indicatorModifier = when (navIndicatorShape) {
                            "BLOCK" -> Modifier
                                .fillMaxWidth()
                                .height(navItemSlotHeight)
                            "CIRCLE" -> Modifier.size(circleSizeDp)
                            else -> Modifier
                                .fillMaxWidth()
                                .height(navItemSlotHeight)
                                .padding(horizontal = 2.dp)
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
                        modifier = Modifier
                            .scale(tabScale)
                            .offset(y = tabOffset),
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
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 9.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (selected) selectedColor else unselectedColor,
                            style = MaterialTheme.typography.labelSmall.copy(shadow = tabTextShadow),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(navBottomInset))
    }
}

class ReverseCurvedFooterShape : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { 20.dp.toPx() }.coerceAtMost(size.height * 0.35f).coerceAtMost(size.width * 0.15f)

        val path = Path().apply {
            moveTo(0f, 0f)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, -r, 2 * r, r),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(size.width - r, r)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(size.width - 2 * r, -r, size.width, r),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

fun Modifier.curvedFooterBorder(
    color: Color,
    strokeWidth: Dp = 1.dp
): Modifier = this.drawWithContent {
    drawContent()
    val r = 20.dp.toPx().coerceAtMost(size.height * 0.35f).coerceAtMost(size.width * 0.15f)
    val strokeWidthPx = strokeWidth.toPx()

    val path = Path().apply {
        moveTo(0f, 0f)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(0f, -r, 2 * r, r),
            startAngleDegrees = 180f,
            sweepAngleDegrees = -90f,
            forceMoveTo = false
        )
        lineTo(size.width - r, r)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(size.width - 2 * r, -r, size.width, r),
            startAngleDegrees = 90f,
            sweepAngleDegrees = -90f,
            forceMoveTo = false
        )
    }

    val glowColor = color
    val borderBrush = Brush.horizontalGradient(
        colors = listOf(
            glowColor.copy(alpha = 0.3f),
            glowColor,
            glowColor.copy(alpha = 0.3f)
        )
    )

    // Soft outer neon glow
    drawPath(
        path = path,
        brush = borderBrush,
        alpha = 0.12f,
        style = Stroke(width = strokeWidthPx * 4f, cap = StrokeCap.Round)
    )
    drawPath(
        path = path,
        brush = borderBrush,
        alpha = 0.06f,
        style = Stroke(width = strokeWidthPx * 7f, cap = StrokeCap.Round)
    )

    // Crisp main border line
    drawPath(
        path = path,
        brush = borderBrush,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )
}
