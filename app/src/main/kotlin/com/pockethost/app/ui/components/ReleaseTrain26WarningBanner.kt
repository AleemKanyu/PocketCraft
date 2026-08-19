package com.pockethost.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.service.MinecraftVersionPolicy
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.util.LocalAppStrings

@Composable
fun ReleaseTrain26WarningBanner(
    activeVersion: String?,
    onChangeVersion: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val visible = MinecraftVersionPolicy.shouldShowReleaseTrain26Warning(activeVersion)
    var dismissed by remember(activeVersion) { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val container = if (isDark) PocketColors.ConsoleWarn.copy(alpha = 0.14f) else PocketColors.ConsoleWarn.copy(alpha = 0.12f)
    val border = PocketColors.ConsoleWarn.copy(alpha = if (isDark) 0.55f else 0.4f)
    val versionLabel = activeVersion?.takeIf { it.isNotBlank() } ?: "26.x"

    AnimatedVisibility(
        visible = visible && !dismissed,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = container,
            border = androidx.compose.foundation.BorderStroke(1.dp, border)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = PocketColors.ConsoleWarn,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.releaseTrain26Title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.releaseTrain26Body.format(versionLabel),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { dismissed = true }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.close,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (onChangeVersion != null) {
                    TextButton(onClick = onChangeVersion) {
                        Text(
                            text = strings.releaseTrain26Action,
                            fontWeight = FontWeight.SemiBold,
                            color = PocketColors.PrimaryDark
                        )
                    }
                }
            }
        }
    }
}
