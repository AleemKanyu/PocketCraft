package com.pocketcraft.server.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.config.RelayLatencySelector
import com.pocketcraft.server.config.RelayServerConfig
import com.pocketcraft.server.config.RemoteConfigManager
import com.pocketcraft.server.config.RelayServers
import com.pocketcraft.server.ui.theme.PocketColors
import androidx.compose.material3.OutlinedTextField
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.DuoButtonVariant
import com.pocketcraft.server.ui.components.PocketCraftCard
import com.pocketcraft.server.ui.components.duoTextFieldShape
import com.pocketcraft.server.ui.components.duoOutlinedTextFieldColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RelayRegionScreen(
    selectedHost: String,
    onBack: () -> Unit,
    onSelectHost: (String) -> Unit
) {
    val context = LocalContext.current
    val selected = RelayServers.getByHost(selectedHost)
    val scope = rememberCoroutineScope()
    val relayRegions by RemoteConfigManager.relayRegions.collectAsState(initial = RelayServers.defaultRegions())
    var isFindingBestRelay by androidx.compose.runtime.remember { mutableStateOf(false) }
    var pings by androidx.compose.runtime.remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var customIpText by androidx.compose.runtime.remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        RemoteConfigManager.initialize(context)
    }

    LaunchedEffect(relayRegions) {
        val measuredPings = mutableMapOf<String, Long>()
        withContext(Dispatchers.IO) {
            relayRegions.forEach { region ->
                val ping = RelayLatencySelector.measureRelayLatency(region.host)
                if (ping != Long.MAX_VALUE) {
                    measuredPings[region.host] = ping
                }
            }
        }
        pings = measuredPings
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                "Choose Region",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        PocketCraftCard(
            cornerRadius = 28.dp,
            containerColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
            } else {
                PocketColors.PrimaryMuted.copy(alpha = 0.52f)
            }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = "Relay region",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Pick the best region for your players",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "This changes which internet relay PocketCraft uses for both Java and Bedrock players.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = PocketColors.Primary)
                    Text(
                        "Current: ${selected.displayName}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (isFindingBestRelay) {
                    Text(
                        text = "Finding best server...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DuoButton(
            text = if (isFindingBestRelay) "Finding best server..." else "Find Best Server",
            onClick = {
                if (isFindingBestRelay) return@DuoButton
                scope.launch {
                    isFindingBestRelay = true
                    val fastest = RelayLatencySelector.pickFastestRelay(relayRegions)
                    onSelectHost(fastest.host)
                    isFindingBestRelay = false
                }
            },
            variant = DuoButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(RelayServers.ALL.filter { region -> relayRegions.any { it.host == region.host } }) { region ->
                RelayRegionCard(
                    config = region,
                    selected = region.host == selectedHost,
                    ping = pings[region.host],
                    onClick = { onSelectHost(region.host) }
                )
            }

            item {
                PocketCraftCard(
                    cornerRadius = 28.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Use Custom Relay",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Specify a private or custom relay server's IP address or hostname to connect through.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = customIpText,
                            onValueChange = { customIpText = it },
                            placeholder = { Text("e.g. 18.225.223.45") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = duoTextFieldShape(),
                            colors = duoOutlinedTextFieldColors()
                        )
                        DuoButton(
                            text = "Connect to Custom Relay",
                            onClick = {
                                val trimmed = customIpText.trim()
                                if (trimmed.isNotBlank()) {
                                    onSelectHost(trimmed)
                                }
                            },
                            variant = DuoButtonVariant.Primary,
                            enabled = customIpText.trim().isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayRegionCard(
    config: RelayServerConfig,
    selected: Boolean,
    ping: Long?,
    onClick: () -> Unit
) {
    PocketCraftCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 28.dp,
        containerColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(PocketColors.PrimaryMuted, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(config.icon, fontSize = 24.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            config.displayName,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (ping != null) {
                            val pingColor = when {
                                ping < 100 -> PocketColors.Online
                                ping < 200 -> PocketColors.Warning
                                else -> PocketColors.Danger
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = pingColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${ping}ms",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                                    color = pingColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                    Text(config.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PocketColors.Primary)
                }
            }

            Text(
                text = "Best for: ${config.bestFor}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DuoButton(
                text = if (selected) "Selected" else "Use This Region",
                onClick = onClick,
                variant = if (selected) DuoButtonVariant.Secondary else DuoButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
