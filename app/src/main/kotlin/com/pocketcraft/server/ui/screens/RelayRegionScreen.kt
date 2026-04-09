package com.pocketcraft.server.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.config.RelayServerConfig
import com.pocketcraft.server.config.RelayServers
import com.pocketcraft.server.ui.theme.PocketColors

@Composable
fun RelayRegionScreen(
    selectedHost: String,
    onBack: () -> Unit,
    onSelectHost: (String) -> Unit
) {
    val selected = RelayServers.getByHost(selectedHost)

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
            Text("Choose Region", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PocketColors.PrimaryMuted.copy(alpha = 0.52f))
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
                        color = PocketColors.PrimaryDark
                    )
                }
                Text(
                    text = "Pick the best region for your players",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
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
                    Text("Current: ${selected.displayName}", fontWeight = FontWeight.SemiBold, color = PocketColors.PrimaryDark)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(RelayServers.ALL) { region ->
                RelayRegionCard(
                    config = region,
                    selected = region.host == selectedHost,
                    onClick = { onSelectHost(region.host) }
                )
            }
        }
    }
}

@Composable
private fun RelayRegionCard(
    config: RelayServerConfig,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        )
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
                    Text(config.displayName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
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

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) PocketColors.PrimaryMuted else PocketColors.Primary,
                    contentColor = if (selected) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (selected) "Selected" else "Use This Region", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
