package com.pocketcraft.server.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.R
import com.pocketcraft.server.ui.components.FlatEmojiIcon
import com.pocketcraft.server.ui.theme.PocketColors

enum class PocketTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    CONSOLE("Console", Icons.Outlined.Terminal, Icons.Filled.Terminal),
    PLAYERS("Players", Icons.Outlined.Group, Icons.Filled.Group),
    STORAGE("Storage", Icons.Outlined.FolderOpen, Icons.Filled.Folder),
    PLUGINS("Plugins", Icons.Outlined.Extension, Icons.Filled.Extension),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

val bottomNavTabs = listOf(
    PocketTab.HOME,
    PocketTab.PLAYERS,
    PocketTab.STORAGE,
    PocketTab.PLUGINS,
    PocketTab.SETTINGS
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketTopBar(
    relayHost: String,
    onRelayHostChange: (String) -> Unit,
    relayLocked: Boolean = false
) {
    var relayMenuExpanded by remember { mutableStateOf(false) }
    val relayOptions = mapOf(
        "play.pocketcraft.online" to "Global",
        "mine.pocketcraft.online" to "Asia"
    )

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Color(0xFFEAF6D8),
                            RoundedCornerShape(16.dp)
                        )
                        .border(2.dp, PocketColors.Primary.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                        .padding(5.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_creeper),
                        contentDescription = "PocketCraft icon",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = "PocketCraft",
                    fontFamily = com.pocketcraft.server.ui.theme.Monocraft,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }
        },
        actions = {
            Box {
                IconButton(
                    onClick = { if (!relayLocked) relayMenuExpanded = true },
                    enabled = !relayLocked,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .background(PocketColors.PrimaryMuted, RoundedCornerShape(16.dp))
                        .border(2.dp, PocketColors.Primary.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                        .size(44.dp)
                ) {
                    FlatEmojiIcon(if (relayHost.contains("mine")) "🌏" else "🌐", modifier = Modifier.size(18.dp), tint = PocketColors.PrimaryDark)
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
                                        FlatEmojiIcon("✓", modifier = Modifier.size(14.dp), tint = PocketColors.Primary)
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
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketBottomNav(
    currentTab: PocketTab,
    onTabSelected: (PocketTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        bottomNavTabs.forEach { tab ->
            val selected = currentTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.label.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PocketColors.Primary,
                    selectedTextColor = PocketColors.Primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
