package com.pocketcraft.server.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Screen { LOADING, VERSION_PICKER, DOWNLOADING, SERVER }

@Composable
fun PocketCraftApp() {
    var screen by remember { mutableStateOf(Screen.LOADING) }
    var versionId by remember { mutableStateOf("1.21.1") }
    var downloadedVersions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showExitDialog by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0) }
    var loadingTarget by remember { mutableStateOf(0) }
    var loadingStatus by remember { mutableStateOf("Opening PocketCraft...") }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val stateHolder = remember(versionId) { ServerStateHolder(context.applicationContext, versionId) }

    DisposableEffect(stateHolder) {
        onDispose { stateHolder.dispose() }
    }

    LaunchedEffect(screen, loadingTarget) {
        if (screen != Screen.LOADING) return@LaunchedEffect
        while (loadingProgress < loadingTarget) {
            delay(
                when {
                    loadingProgress < 25 -> 32L
                    loadingProgress < 70 -> 22L
                    loadingProgress < 95 -> 16L
                    else -> 12L
                }
            )
            loadingProgress = (loadingProgress + 1).coerceAtMost(loadingTarget)
        }
    }

    LaunchedEffect(Unit) {
        loadingProgress = 0
        loadingTarget = 8
        loadingStatus = "Opening PocketCraft..."
        delay(120)
        val setupComplete = AppPreferencesStore.isSetupCompleteFlow(context).first()

        loadingTarget = 36
        loadingStatus = "Loading saved preferences..."
        delay(160)
        val savedVersionId = AppPreferencesStore.getSelectedVersionFlow(context).first()

        versionId = savedVersionId

        if (!setupComplete) {
            loadingTarget = 62
            loadingStatus = "Finishing first-time setup..."
            AppPreferencesStore.setSetupComplete(context, true)
            delay(180)
        }

        loadingTarget = 88
        loadingStatus = "Scanning Minecraft versions..."
        downloadedVersions = scanDownloadedVersionIds(context.applicationContext)

        while (loadingProgress < 88) {
            delay(10)
        }

        loadingTarget = 100
        loadingStatus = "PocketCraft Ready"

        while (loadingProgress < 100) {
            delay(10)
        }

        delay(140)
        screen = Screen.SERVER
    }

    BackHandler(enabled = screen == Screen.VERSION_PICKER || screen == Screen.DOWNLOADING) {
        screen = Screen.SERVER
    }

    when (screen) {
        Screen.LOADING -> {
            SplashScreen(progress = loadingProgress / 100f, status = loadingStatus)
        }

        Screen.VERSION_PICKER -> VersionPickerScreen(
            selectedVersion = versionId,
            showWorldSetupHint = stateHolder.activeWorldNeedsSetup,
            worldName = stateHolder.config.worldName.ifBlank { "world" },
            onVersionSelected = { version ->
                versionId = version
                scope.launch {
                    AppPreferencesStore.setSetupComplete(context, true)
                    AppPreferencesStore.setSelectedVersion(context, version)
                    stateHolder.markActiveWorldSetupCompleted()
                    screen = if (downloadedVersions.contains(version)) Screen.SERVER else Screen.DOWNLOADING
                }
            }
        )

        Screen.DOWNLOADING -> AutoDownloadScreen(
            versionId = versionId,
            onReady = {
                downloadedVersions = downloadedVersions + versionId
                screen = Screen.SERVER
            }
        )

        Screen.SERVER -> ServerScreen(
            stateHolder = stateHolder,
            onChangeVersion = { screen = Screen.VERSION_PICKER },
            onRequestExit = { showExitDialog = true }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit PocketCraft") },
            text = { Text("Are you sure you want to exit?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    activity?.finish()
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("No")
                }
            }
        )
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
            version.takeIf { jar.exists() && jar.length() > 1_000_000L }
        }
        .toSet()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
