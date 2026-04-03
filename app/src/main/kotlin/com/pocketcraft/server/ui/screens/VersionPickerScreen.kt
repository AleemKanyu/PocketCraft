package com.pocketcraft.server.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.service.VersionCatalog
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.theme.PocketColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VersionItem(
    val id: String,
    val label: String,
    val isLatest: Boolean
)

@Composable
fun VersionPickerScreen(
    selectedVersion: String,
    showWorldSetupHint: Boolean = false,
    worldName: String = "world",
    onVersionSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<VersionItem>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var downloadedVersions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteTarget by remember { mutableStateOf<VersionItem?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }

    val fallbackVersions = listOf(
        VersionItem("1.21.4", "Latest Release", true),
        VersionItem("1.21.3", "Stable", false),
        VersionItem("1.21.2", "Stable", false),
        VersionItem("1.21.1", "Stable", false),
        VersionItem("1.21", "Stable", false),
        VersionItem("1.20.6", "Stable", false),
        VersionItem("1.20.5", "Stable", false),
        VersionItem("1.20.4", "Stable", false),
        VersionItem("1.20.3", "Stable", false),
        VersionItem("1.20.2", "Stable", false),
        VersionItem("1.20.1", "Stable", false)
    )

    LaunchedEffect(refreshToken) {
        try {
            isLoading = true
            errorMessage = null
            val fetchedPaperVersions = VersionCatalog.fetchStableVersions()

            versions = if (fetchedPaperVersions.isNotEmpty()) {
                fetchedPaperVersions.mapIndexed { index, versionStr ->
                    VersionItem(
                        id = versionStr,
                        label = if (index == 0) "Latest Release" else "Stable",
                        isLatest = index == 0
                    )
                }
            } else {
                errorMessage = "Could not fetch latest versions. Using cached list."
                fallbackVersions
            }

            downloadedVersions = withContext(Dispatchers.IO) {
                scanDownloadedVersionIds(context.applicationContext)
            }
            isLoading = false
        } catch (_: Exception) {
            errorMessage = "Using cached versions"
            versions = fallbackVersions
            downloadedVersions = withContext(Dispatchers.IO) {
                scanDownloadedVersionIds(context.applicationContext)
            }
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "POCKETCRAFT",
                        color = PocketColors.PrimaryDark,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Pick Your Minecraft Version",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Text(
                        text = "Choose your world engine and launch in minutes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (showWorldSetupHint) {
                item {
                    GameCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "New World Setup",
                            fontWeight = FontWeight.ExtraBold,
                            color = PocketColors.PrimaryDark
                        )
                        Text(
                            text = "$worldName is new. Select a Minecraft version below before the first launch.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    GameCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Could not fetch latest versions",
                            fontWeight = FontWeight.Bold,
                            color = PocketColors.Offline
                        )
                        Text(
                            text = "Using cached versions instead",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        DuoButton(
                            text = "RETRY FETCH",
                            onClick = { refreshToken++ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        )
                    }
                }
            }

            if (bannerMessage != null) {
                item {
                    GameCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = bannerMessage.orEmpty(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            versions?.let { versionList ->
                items(versionList, key = { it.id }) { version ->
                    VersionCard(
                        version = version,
                        selected = selectedVersion == version.id,
                        isDownloaded = downloadedVersions.contains(version.id),
                        onClick = { onVersionSelected(version.id) },
                        onDelete = { deleteTarget = version }
                    )
                }
            }
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete downloaded version?") },
                text = {
                    Text("This removes Minecraft Java ${target.id} from phone storage. Worlds and backups remain untouched.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        val deletingId = target.id
                        deleteTarget = null
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                deleteDownloadedVersion(context.applicationContext, deletingId)
                            }
                            if (deleted) {
                                downloadedVersions = downloadedVersions - deletingId
                                bannerMessage = "Deleted version $deletingId from device storage."
                            } else {
                                bannerMessage = "Could not delete version $deletingId. Try again."
                            }
                        }
                    }) {
                        Text("Delete", color = PocketColors.Offline)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun VersionCard(
    version: VersionItem,
    selected: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    GameCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) PocketColors.Primary else androidx.compose.ui.graphics.Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = if (selected) PocketColors.PrimaryMuted else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "MC",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                Column {
                    Text(
                        text = "Minecraft Java ${version.id}",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (selected) PocketColors.Primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selected) "Selected" else version.label,
                        color = if (version.isLatest) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDownloaded) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "DOWNLOADED",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                    if (!selected) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete downloaded version",
                                tint = PocketColors.Offline
                            )
                        }
                    }
                }
                if (version.isLatest) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        color = PocketColors.PrimaryMuted
                    ) {
                        Text(
                            text = "LATEST",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = PocketColors.PrimaryDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = if (selected) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun scanDownloadedVersionIds(context: Context): Set<String> {
    val serversRoot = File(context.filesDir, "servers")
    val dirs = serversRoot.listFiles() ?: return emptySet()
    return dirs.asSequence()
        .filter { it.isDirectory }
        .mapNotNull { dir ->
            val version = dir.name
            val jar = File(dir, "paper-$version.jar")
            if (jar.exists() && jar.length() > 1_000_000L) version else null
        }
        .toSet()
}

private fun deleteDownloadedVersion(context: Context, versionId: String): Boolean {
    val serverDir = File(context.filesDir, "servers/$versionId")
    if (!serverDir.exists()) return false
    val props = File(serverDir, "server.properties")
    val worldName = runCatching {
        props.readLines()
            .firstOrNull { it.startsWith("level-name=") }
            ?.substringAfter("=")
            ?.trim()
            ?.ifBlank { "world" }
            ?: "world"
    }.getOrDefault("world")

    val protectedDirs = setOf(
        worldName,
        "${worldName}_nether",
        "${worldName}_the_end",
        "pocketcraft_backups",
        "world_plugin_profiles"
    )

    var deletedAny = false
    serverDir.listFiles().orEmpty().forEach { child ->
        val keep = child.isDirectory && protectedDirs.contains(child.name)
        if (!keep && child.deleteRecursively()) {
            deletedAny = true
        }
    }
    return deletedAny
}
