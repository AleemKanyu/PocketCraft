package com.pocketcraft.server.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun FlatEmojiIcon(
    symbol: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    Icon(
        imageVector = flatEmojiIcon(symbol),
        contentDescription = null,
        modifier = modifier,
        tint = tint
    )
}

private fun flatEmojiIcon(symbol: String): ImageVector = when (symbol) {
    "⚙️" -> Icons.Filled.Settings
    "🌍", "🌐", "🌏" -> Icons.Filled.Public
    "🔥" -> Icons.Filled.LocalFireDepartment
    "🌑" -> Icons.Filled.NightlightRound
    "⛰", "🌄" -> Icons.Filled.Terrain
    "🟩" -> Icons.Filled.Landscape
    "🏠" -> Icons.Filled.Home
    "🕹️" -> Icons.Filled.SportsEsports
    "📣" -> Icons.Filled.Campaign
    "🙈" -> Icons.Filled.VisibilityOff
    "👿" -> Icons.Filled.GppBad
    "🐄" -> Icons.Filled.Pets
    "🏙️" -> Icons.Filled.LocationCity
    "⚔️" -> Icons.Filled.SportsMma
    "🔄" -> Icons.Filled.RestartAlt
    "⏳" -> Icons.Filled.HourglassEmpty
    "📥" -> Icons.Filled.Download
    "💾" -> Icons.Filled.Save
    "☁️" -> Icons.Filled.Cloud
    "🎮" -> Icons.Filled.Games
    "💡" -> Icons.Filled.Lightbulb
    "🧩" -> Icons.Filled.Extension
    "✓", "✅" -> Icons.Filled.CheckCircle
    "✗" -> Icons.Filled.Cancel
    "⚠️", "⚠" -> Icons.Filled.WarningAmber
    "📂" -> Icons.Filled.FolderOpen
    "⛏" -> Icons.Filled.Construction
    else -> Icons.Filled.Public
}