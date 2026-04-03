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
        val shouldPromptThisLaunch = launchCount == 2 || launchCount == 6 || launchCount == 10
        if (shouldPromptThisLaunch &&
            !preferences.socialLinksJoined &&
            preferences.socialPromoLastShownLaunch != launchCount
        ) {
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
                    .padding(18.dp)
                    .border(3.dp, Color(0xFF5EDB35), RoundedCornerShape(30.dp)),
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFFF6FFE8),
                tonalElevation = 10.dp
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFDFF7BE), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👋", fontSize = 24.sp)
                    }

                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Join the PocketCraft crew",
                            color = PocketColors.PrimaryDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "Support us by following and joining our Discord and Instagram community. If you've already joined, you can ignore this.",
                            color = Color(0xFF48613B),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp
                        )
                    }

                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val opened = FeedbackService.openDiscord(context)
                                if (opened) {
                                    preferences.socialLinksJoined = true
                                }
                                showCommunityDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CD137)),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            CommunitySocialIcon(
                                assetPath = "file:///android_asset/social/discord.png",
                                fallbackResId = R.drawable.ic_discord,
                                contentDescription = "Discord",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text("Discord", fontWeight = FontWeight.ExtraBold)
                        }

                        Button(
                            onClick = {
                                val opened = FeedbackService.openInstagram(context)
                                if (opened) {
                                    preferences.socialLinksJoined = true
                                }
                                showCommunityDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35C759)),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            CommunitySocialIcon(
                                assetPath = "file:///android_asset/social/instagram.png",
                                fallbackResId = R.drawable.ic_instagram,
                                contentDescription = "Instagram",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text("Instagram", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunitySocialIcon(
    assetPath: String,
    fallbackResId: Int,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = assetPath,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        error = painterResource(id = fallbackResId),
        fallback = painterResource(id = fallbackResId)
    )
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
