package com.pocketcraft.server.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pocketcraft.server.MainActivity
import com.pocketcraft.server.R
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.data.preferences.WidgetThemeSettings
import com.pocketcraft.server.server.ServerHostService
import com.pocketcraft.server.service.ServerFileManager
import java.io.File
import java.util.Properties
import kotlinx.coroutines.flow.first

private object ServerWidgetStateKeys {
    val status = stringPreferencesKey("status")
    val worldName = stringPreferencesKey("world_name")
    val versionId = stringPreferencesKey("version_id")
    val players = intPreferencesKey("players")
    val maxPlayers = intPreferencesKey("max_players")
    val startedAtMillis = longPreferencesKey("started_at_millis")
    val themeKey = stringPreferencesKey("theme_key")
    val themeManualOverride = booleanPreferencesKey("theme_manual_override")
    val customBackground = intPreferencesKey("custom_background")
    val customAccent = intPreferencesKey("custom_accent")
    val customTextOnAccent = intPreferencesKey("custom_text_on_accent")
    val customIconTint = intPreferencesKey("custom_icon_tint")
    val startEnabled = booleanPreferencesKey("start_enabled")
    val stopEnabled = booleanPreferencesKey("stop_enabled")
    val restartEnabled = booleanPreferencesKey("restart_enabled")
}

data class ServerWidgetSnapshot(
    val status: String,
    val worldName: String,
    val versionId: String,
    val players: Int,
    val maxPlayers: Int,
    val startedAtMillis: Long,
    val themeKey: String,
    val themeSettings: WidgetThemeSettings,
    val startEnabled: Boolean,
    val stopEnabled: Boolean,
    val restartEnabled: Boolean
)

class ServerWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent(context, currentState())
        }
    }

    @Composable
    private fun WidgetContent(context: Context, prefs: Preferences) {
        val themeKey = prefs[ServerWidgetStateKeys.themeKey] ?: WidgetThemePrefs.THEME_FOLLOW_APP
        val themeSettings = WidgetThemeSettings(
            selectedTheme = themeKey,
            manualOverride = prefs[ServerWidgetStateKeys.themeManualOverride] ?: false,
            customBackground = prefs[ServerWidgetStateKeys.customBackground],
            customAccent = prefs[ServerWidgetStateKeys.customAccent],
            customTextOnAccent = prefs[ServerWidgetStateKeys.customTextOnAccent],
            customIconTint = prefs[ServerWidgetStateKeys.customIconTint]
        )
        val scheme = resolveWidgetThemeScheme(context, themeSettings)
        val status = prefs[ServerWidgetStateKeys.status] ?: ServerHostService.RUNTIME_STATE_OFFLINE
        val worldName = prefs[ServerWidgetStateKeys.worldName] ?: "world"
        val versionId = prefs[ServerWidgetStateKeys.versionId] ?: ""
        val players = prefs[ServerWidgetStateKeys.players] ?: 0
        val maxPlayers = prefs[ServerWidgetStateKeys.maxPlayers] ?: 10
        val startedAt = prefs[ServerWidgetStateKeys.startedAtMillis] ?: 0L
        val startEnabled = prefs[ServerWidgetStateKeys.startEnabled] ?: true
        val stopEnabled = prefs[ServerWidgetStateKeys.stopEnabled] ?: false
        val restartEnabled = prefs[ServerWidgetStateKeys.restartEnabled] ?: false
        val startIntent = Intent(context, ServerHostService::class.java).apply {
            action = ServerHostService.ACTION_START
            putExtra(ServerHostService.EXTRA_VERSION_ID, versionId)
            putExtra(ServerHostService.EXTRA_WORLD_NAME, worldName)
        }
        val stopIntent = Intent(context, ServerHostService::class.java).apply {
            action = ServerHostService.ACTION_STOP
        }
        val restartIntent = Intent(context, ServerHostService::class.java).apply {
            action = ServerHostService.ACTION_RESTART
        }
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        Box(
            modifier = widgetSurfaceModifier(scheme)
                .clickable(actionStartActivity(openAppIntent))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                HeaderRow(
                    worldName = worldName,
                    versionId = versionId,
                    players = players,
                    maxPlayers = maxPlayers,
                    status = status,
                    scheme = scheme
                )

                Spacer(GlanceModifier.height(8.dp))

                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorProvider(Color(scheme.borderColor)))
                ) {}

                Spacer(GlanceModifier.height(8.dp))

                StatusAndUptimeRow(
                    status = status,
                    startedAtMillis = startedAt,
                    scheme = scheme
                )

                Spacer(GlanceModifier.height(8.dp))

                val isServerOffline = status == ServerHostService.RUNTIME_STATE_OFFLINE

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    if (isServerOffline) {
                        WidgetActionButton(
                            modifier = GlanceModifier.fillMaxWidth(),
                            label = "START",
                            symbol = "▷",
                            enabled = startEnabled,
                            background = 0x1F3DDC84.toInt(),
                            tint = 0xFF3DDC84.toInt(),
                            disabledBackground = scheme.buttonDisabledBg,
                            disabledTint = scheme.buttonDisabledTint,
                            action = actionStartService(startIntent, isForegroundService = true)
                        )
                    } else {
                        WidgetActionButton(
                            modifier = GlanceModifier.defaultWeight(),
                            label = "STOP",
                            symbol = "□",
                            enabled = stopEnabled,
                            background = scheme.buttonStopBg,
                            tint = scheme.buttonStopTint,
                            disabledBackground = scheme.buttonDisabledBg,
                            disabledTint = scheme.buttonDisabledTint,
                            action = actionStartService(stopIntent, isForegroundService = true)
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetActionButton(
                            modifier = GlanceModifier.defaultWeight(),
                            label = "RESTART",
                            symbol = "↻",
                            enabled = restartEnabled,
                            background = scheme.buttonRestartBg,
                            tint = scheme.buttonRestartTint,
                            disabledBackground = scheme.buttonDisabledBg,
                            disabledTint = scheme.buttonDisabledTint,
                            action = actionStartService(restartIntent, isForegroundService = true)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    worldName: String,
    versionId: String,
    players: Int,
    maxPlayers: Int,
    status: String,
    scheme: WidgetColorScheme
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_launcher_creeper),
            contentDescription = "PocketCraft server",
            modifier = GlanceModifier.width(42.dp).height(42.dp)
        )

        Spacer(GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = worldName,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(scheme.textPrimary)),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(4.dp))
            Box(
                modifier = GlanceModifier
                    .cornerRadius(10.dp)
                    .background(ColorProvider(Color(scheme.versionChipBg)))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = versionId.ifBlank { "Ready when you are" },
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color(scheme.textSecondary)),
                        fontSize = 12.sp
                    )
                )
            }
        }

        Spacer(GlanceModifier.width(10.dp))

        Column(
            horizontalAlignment = Alignment.Horizontal.End
        ) {
            Text(
                text = "$players / $maxPlayers",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(scheme.textPrimary)),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(3.dp))
            presenceLabel(status)?.let { presence ->
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(999.dp)
                            .background(ColorProvider(Color(scheme.onlineDotColor)))
                            .padding(horizontal = 3.dp, vertical = 3.dp)
                    ) {}
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = presence,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(Color(scheme.textSecondary)),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusAndUptimeRow(
    status: String,
    startedAtMillis: Long,
    scheme: WidgetColorScheme
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        StatusBadge(
            label = statusLabel(status),
            background = scheme.statusBg,
            textColor = scheme.statusText
        )
        Spacer(GlanceModifier.defaultWeight())
        Column(
            horizontalAlignment = Alignment.Horizontal.End
        ) {
            Text(
                text = "UPTIME",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(scheme.textMuted)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = uptimeLabel(status, startedAtMillis),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(scheme.textPrimary)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    background: Int,
    textColor: Int
) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(14.dp)
            .background(ColorProvider(Color(background)))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(Color(textColor)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun WidgetActionButton(
    modifier: GlanceModifier,
    label: String,
    symbol: String,
    enabled: Boolean,
    background: Int,
    tint: Int,
    disabledBackground: Int,
    disabledTint: Int,
    action: Action
) {
    val resolvedBackground = if (enabled) background else disabledBackground
    val resolvedTint = if (enabled) tint else disabledTint
    Box(
        modifier = modifier
            .cornerRadius(16.dp)
            .background(ColorProvider(Color(resolvedBackground)))
            .height(48.dp)
            .then(if (enabled) GlanceModifier.clickable(action) else GlanceModifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = symbol,
                style = TextStyle(
                    color = ColorProvider(Color(resolvedTint)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(1.dp))
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(resolvedTint)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

private fun widgetSurfaceModifier(scheme: WidgetColorScheme): GlanceModifier {
    val base = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(28.dp)
    return if (scheme.backgroundDrawableRes != 0) {
        base.background(ImageProvider(scheme.backgroundDrawableRes))
    } else {
        base.background(ColorProvider(Color(scheme.backgroundColor)))
    }
}

private fun statusLabel(status: String): String = when (status) {
    ServerHostService.RUNTIME_STATE_RUNNING -> "RUNNING"
    ServerHostService.RUNTIME_STATE_STARTING, "starting" -> "STARTING"
    "stopping" -> "STOPPING"
    else -> "OFFLINE"
}

private fun presenceLabel(status: String): String? = when (status) {
    ServerHostService.RUNTIME_STATE_RUNNING -> "online"
    ServerHostService.RUNTIME_STATE_STARTING, "starting" -> "booting"
    "stopping" -> "ending"
    else -> null
}

private fun uptimeLabel(status: String, startedAtMillis: Long): String {
    return when {
        startedAtMillis <= 0L || status != ServerHostService.RUNTIME_STATE_RUNNING -> "0:00"
        else -> {
            val elapsedSeconds = ((System.currentTimeMillis() - startedAtMillis) / 1000L).coerceAtLeast(0L)
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            "%d:%02d".format(hours, minutes)
        }
    }
}

object ServerWidgetUpdater {
    suspend fun push(context: Context, statusOverride: String? = null) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(ServerWidget::class.java)
        if (ids.isEmpty()) return
        val snapshot = buildSnapshot(context, statusOverride)
        ids.forEach { id ->
            updateAppWidgetState(context, id) { prefs: MutablePreferences ->
                prefs.apply {
                    this[ServerWidgetStateKeys.status] = snapshot.status
                    this[ServerWidgetStateKeys.worldName] = snapshot.worldName
                    this[ServerWidgetStateKeys.versionId] = snapshot.versionId
                    this[ServerWidgetStateKeys.players] = snapshot.players
                    this[ServerWidgetStateKeys.maxPlayers] = snapshot.maxPlayers
                    this[ServerWidgetStateKeys.startedAtMillis] = snapshot.startedAtMillis
                    this[ServerWidgetStateKeys.themeKey] = snapshot.themeKey
                    this[ServerWidgetStateKeys.themeManualOverride] = snapshot.themeSettings.manualOverride
                    writeOptionalColor(ServerWidgetStateKeys.customBackground, snapshot.themeSettings.customBackground)
                    writeOptionalColor(ServerWidgetStateKeys.customAccent, snapshot.themeSettings.customAccent)
                    writeOptionalColor(ServerWidgetStateKeys.customTextOnAccent, snapshot.themeSettings.customTextOnAccent)
                    writeOptionalColor(ServerWidgetStateKeys.customIconTint, snapshot.themeSettings.customIconTint)
                    this[ServerWidgetStateKeys.startEnabled] = snapshot.startEnabled
                    this[ServerWidgetStateKeys.stopEnabled] = snapshot.stopEnabled
                    this[ServerWidgetStateKeys.restartEnabled] = snapshot.restartEnabled
                }
            }
        }
        ServerWidget().updateAll(context)
    }

    suspend fun buildSnapshot(context: Context, statusOverride: String? = null): ServerWidgetSnapshot {
        val versionId = ServerHostService.getPersistedActiveVersion(context)
            .ifBlank { AppPreferencesStore.getSelectedVersionFlow(context).first().orEmpty() }
        val selectedWorld = AppPreferencesStore.getSelectedWorldFlow(context).first()
        val activeWorld = ServerHostService.getPersistedActiveWorld(context).ifBlank { selectedWorld }
        val resolvedStatus = resolveStatus(context, versionId)
        val status = when {
            resolvedStatus == ServerHostService.RUNTIME_STATE_RUNNING -> resolvedStatus
            resolvedStatus == ServerHostService.RUNTIME_STATE_OFFLINE && statusOverride == "stopping" -> statusOverride
            statusOverride != null -> statusOverride
            else -> resolvedStatus
        }
        val startedAt = AppPreferencesStore.getServerStartedAtMillis(context)
            .takeIf { status == ServerHostService.RUNTIME_STATE_RUNNING }
            ?: 0L
        val themeSettings = WidgetThemePrefs.ensureAllowedTheme(context)
        val maxPlayers = readMaxPlayers(context, activeWorld)
        val isRunning = status == ServerHostService.RUNTIME_STATE_RUNNING
        val isStarting = status == ServerHostService.RUNTIME_STATE_STARTING || status == "starting"
        val isStopping = status == "stopping"
        return ServerWidgetSnapshot(
            status = status,
            worldName = activeWorld,
            versionId = versionId,
            players = if (isRunning || isStarting) ServerHostService.getPersistedPlayerCount(context) else 0,
            maxPlayers = maxPlayers,
            startedAtMillis = startedAt,
            themeKey = themeSettings.selectedTheme.ifBlank { WidgetThemePrefs.THEME_FOLLOW_APP },
            themeSettings = themeSettings,
            startEnabled = !isRunning && !isStarting && !isStopping && versionId.isNotBlank(),
            stopEnabled = isRunning || isStarting,
            restartEnabled = isRunning || isStarting
        )
    }

    fun resolveStatus(context: Context, versionId: String): String {
        if (versionId.isBlank()) return ServerHostService.RUNTIME_STATE_OFFLINE
        return ServerHostService.getPersistedRuntimeState(context, versionId)
    }

    private fun readMaxPlayers(context: Context, worldName: String): Int {
        val serverDir = ServerFileManager.getServerDir(context, worldName)
        val propsFile = File(serverDir, "server.properties")
        val props = Properties()
        if (propsFile.exists()) {
            runCatching { propsFile.inputStream().use(props::load) }
        }
        return props.getProperty("max-players", "10").toIntOrNull()?.coerceIn(1, 50) ?: 10
    }

}

private fun MutablePreferences.writeOptionalColor(
    key: androidx.datastore.preferences.core.Preferences.Key<Int>,
    value: Int?
) {
    if (value == null) {
        remove(key)
    } else {
        this[key] = value
    }
}
