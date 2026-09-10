package com.pockethost.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lightbulb
import com.pockethost.app.data.model.Plugin
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.util.LocalAppStrings

@Composable
fun PluginsSection(
    plugins: List<Plugin>,
    onAddPlugin: () -> Unit,
    onDeletePlugin: (Plugin) -> Unit
) {
    val s = LocalAppStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PLUGINS (${plugins.size})",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )

            Button(
                onClick = onAddPlugin,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PocketColors.Primary,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(s.addPlugin, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }

        // Info tip
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PocketColors.Primary.copy(0.08f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp), tint = PocketColors.PrimaryDark)
                Text(
                    s.pluginsTip,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
            }
        }

        if (plugins.isEmpty()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(40.dp), tint = PocketColors.PrimaryDark)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        s.noPluginsInstalled,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    )
                    Text(
                        s.tapToAddPlugin,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.3f)
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plugins) { plugin ->
                    GameCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(28.dp), tint = PocketColors.PrimaryDark)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plugin.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "%.1f MB • ${plugin.fileName}".format(plugin.sizeMb),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                )
                            }
                            IconButton(onClick = { onDeletePlugin(plugin) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = PocketColors.Offline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
