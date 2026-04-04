package com.pocketcraft.server.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.pocketcraft.server.feedback.FeedbackService
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.R
import com.pocketcraft.server.ui.theme.PocketColors
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Screen { LOADING, VERSION_PICKER, DOWNLOADING, SERVER }

@Composable
fun PocketCraftApp() {
    var screen by remember { mutableStateOf(Screen.SERVER) }
    var transitionTarget by remember { mutableStateOf<Screen?>(null) }
    var versionId by remember { mutableStateOf("1.21.1") }
    var downloadedVersions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showCommunityDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context) }
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val stateHolder = remember(versionId) { ServerStateHolder(context.applicationContext, versionId) }

    DisposableEffect(stateHolder) {
        onDispose { stateHolder.dispose() }
    }

    LaunchedEffect(Unit) {
        val setupComplete = AppPreferencesStore.isSetupCompleteFlow(context).first()
        val savedVersionId = AppPreferencesStore.getSelectedVersionFlow(context).first()
        val pendingAutoDownloadVersion = AppPreferencesStore.getPendingAutoDownloadVersionFlow(context).first()

        versionId = savedVersionId

        if (!setupComplete) {
            AppPreferencesStore.setSetupComplete(context, true)
        }
        downloadedVersions = scanDownloadedVersionIds(context.applicationContext)

        if (!pendingAutoDownloadVersion.isNullOrBlank() && !downloadedVersions.contains(pendingAutoDownloadVersion)) {
            versionId = pendingAutoDownloadVersion
            transitionTarget = Screen.DOWNLOADING
            screen = Screen.LOADING
        }
        AppPreferencesStore.setPendingAutoDownloadVersion(context, null)
    }

    LaunchedEffect(screen) {
        if (screen != Screen.LOADING) return@LaunchedEffect
        val nextScreen = transitionTarget ?: Screen.SERVER
        delay(220)
        screen = nextScreen
        transitionTarget = null
    }

    LaunchedEffect(screen) {
        if (screen != Screen.SERVER) return@LaunchedEffect
        val launchCount = preferences.appLaunchCount
        val shouldPrompt = (launchCount == 2 || launchCount == 4) && !preferences.socialLinksJoined
        if (shouldPrompt) {
            preferences.socialPromoLastShownLaunch = launchCount
            delay(700)
            showCommunityDialog = true
        }
    }

    BackHandler(enabled = screen == Screen.VERSION_PICKER || screen == Screen.DOWNLOADING) {
        screen = Screen.SERVER
    }

    fun requestVersionChange(version: String) {
        versionId = version
        scope.launch {
            AppPreferencesStore.setSetupComplete(context, true)
            AppPreferencesStore.setSelectedVersion(context, version)
            stateHolder.markActiveWorldSetupCompleted()
            transitionTarget = if (downloadedVersions.contains(version)) Screen.SERVER else Screen.DOWNLOADING
            screen = Screen.LOADING
        }
    }

    when (screen) {
        Screen.LOADING -> SplashScreen(
            progress = 0.66f,
            status = if (transitionTarget == Screen.DOWNLOADING) "Preparing download..." else "Preparing PocketCraft..."
        )

        Screen.VERSION_PICKER -> VersionPickerScreen(
            selectedVersion = versionId,
            showWorldSetupHint = stateHolder.activeWorldNeedsSetup,
            worldName = stateHolder.config.worldName.ifBlank { "world" },
            onVersionSelected = ::requestVersionChange
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
            onVersionSelected = ::requestVersionChange,
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

    if (showCommunityDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCommunityDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .padding(18.dp),
                shape = RoundedCornerShape(28.dp),
                color = PocketColors.Primary
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Discord icon
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Discord",
                            modifier = Modifier.size(36.dp),
                            tint = Color.Black
                        )
                    }

                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Join our Discord",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = Color.Black
                        )
                        Text(
                            text = "Connect with the PocketCraft community, share your worlds, and get tips from other players",
                            fontSize = 14.sp,
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Button(
                        onClick = {
                            val opened = FeedbackService.openDiscord(context)
                            if (opened) {
                                preferences.socialLinksJoined = true
                            }
                            showCommunityDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.25f),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Text("Join Discord", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    TextButton(
                        onClick = { showCommunityDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Maybe later", color = Color.Black.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                }
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
            version.takeIf { jar.exists() && jar.length() > 1_000_000L }
        }
        .toSet()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
