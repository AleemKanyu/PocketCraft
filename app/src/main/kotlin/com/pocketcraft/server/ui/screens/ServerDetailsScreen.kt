package com.pocketcraft.server.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.data.model.ServerType
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.components.ServerDescriptionField
import com.pocketcraft.server.ui.components.ServerPhotoUpload
import com.pocketcraft.server.ui.screens.ServerTypeVersionBottomSheet
import com.pocketcraft.server.ui.components.duoOutlinedTextFieldColors
import com.pocketcraft.server.ui.components.duoTextFieldShape
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import com.pocketcraft.server.billing.BillingManager
import com.pocketcraft.server.billing.PremiumEntitlement
import com.pocketcraft.server.ui.components.IpManagerCard

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailsScreen(
    stateHolder: ServerStateHolder,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onNavigateToSignUp: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val billingManager = remember { BillingManager.getInstance(context) }
    val entitlement by billingManager.entitlement.collectAsState()
    val isPremium by billingManager.isPremium.collectAsState()
    val customSubdomainEnabled by com.pocketcraft.server.config.RemoteConfigManager.customSubdomainEnabled.collectAsState(initial = false)
    val activeWorld = stateHolder.activeWorld
    var serverName by remember(stateHolder.serverName, stateHolder.activeWorld) {
        mutableStateOf(stateHolder.serverName.ifBlank { stateHolder.activeWorld.ifBlank { "world" } })
    }
    var serverDescription by remember(stateHolder.serverDescription) {
        mutableStateOf(stateHolder.serverDescription)
    }
    var serverPhotoUri by remember(stateHolder.serverPhotoUrl) {
        mutableStateOf(
            if (stateHolder.serverPhotoUrl.isNotBlank()) Uri.parse(stateHolder.serverPhotoUrl) else null
        )
    }
    var photoChanged by remember(stateHolder.serverPhotoUrl) { mutableStateOf(false) }
    var selectedVersion by remember(stateHolder.config.gameVersion) {
        mutableStateOf(stateHolder.config.gameVersion)
    }
    var selectedServerType by remember(stateHolder.config.serverType) {
        mutableStateOf(stateHolder.config.serverType)
    }
    var selectedCustomJarPath by remember(stateHolder.config.customJarPath) {
        mutableStateOf(stateHolder.config.customJarPath)
    }
    var joinMessageText by remember(stateHolder.config.joinMessageText) {
        mutableStateOf(stateHolder.config.joinMessageText)
    }
    var joinMessageUrl by remember(stateHolder.config.joinMessageUrl) {
        mutableStateOf(stateHolder.config.joinMessageUrl)
    }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showPremiumBottomSheet by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableStateOf(0f) }
    val versionFieldInteractionSource = remember { MutableInteractionSource() }

    suspend fun persistDetails(showMessage: Boolean, closeAfterSave: Boolean) {
        if (isSaving) return
        isSaving = true
        saveProgress = 0f

        val trimmedName = serverName.trim().ifBlank { activeWorld }
        val trimmedDescription = serverDescription.trim()
        val existingPhoto = stateHolder.serverPhotoUrl.trim()
        val photoUrlToSave = runCatching {
            when {
                photoChanged && serverPhotoUri != null -> {
                    saveProgress = 0.05f
                    stateHolder.importWorldServerPhoto(
                        worldName = activeWorld,
                        sourceUri = serverPhotoUri!!,
                        onProgress = { percent ->
                            saveProgress = 0.05f + (percent.coerceIn(0, 100) / 100f) * 0.8f
                        }
                    )
                }
                photoChanged -> ""
                else -> existingPhoto
            }
        }.getOrElse { error ->
            isSaving = false
            saveProgress = 0f
            if (showMessage) onMessage("Photo upload failed: ${error.message ?: "unknown error"}")
            return
        }

        val nothingChanged =
            trimmedName == stateHolder.serverName.trim() &&
                trimmedDescription == stateHolder.serverDescription.trim() &&
                photoUrlToSave == existingPhoto &&
                selectedVersion.trim() == stateHolder.config.gameVersion &&
                selectedServerType == stateHolder.config.serverType &&
                selectedCustomJarPath == stateHolder.config.customJarPath &&
                joinMessageText.trim() == stateHolder.config.joinMessageText.trim() &&
                joinMessageUrl.trim() == stateHolder.config.joinMessageUrl.trim()

        if (nothingChanged) {
            isSaving = false
            saveProgress = 0f
            if (closeAfterSave) onBack()
            return
        }

        saveProgress = saveProgress.coerceAtLeast(0.92f)
        val msg = stateHolder.updateWorldServerDetails(
            worldName = activeWorld,
            displayName = trimmedName,
            photoUrl = photoUrlToSave,
            description = trimmedDescription
        )
        stateHolder.saveSettings(
            stateHolder.config.copy(
                gameVersion = selectedVersion.trim(),
                serverType = selectedServerType,
                customJarPath = selectedCustomJarPath,
                joinMessageText = if (isPremium) joinMessageText.trim() else stateHolder.config.joinMessageText,
                joinMessageUrl = if (isPremium) joinMessageUrl.trim() else stateHolder.config.joinMessageUrl
            )
        )
        AppPreferencesStore.setSelectedServerType(context, selectedServerType.name)
        AppPreferencesStore.setSelectedVersion(context, selectedVersion.trim())
        photoChanged = false
        saveProgress = 1f
        if (showMessage) {
            onMessage(msg)
        }
        isSaving = false
        saveProgress = 0f
        if (closeAfterSave) {
            onBack()
        }
    }

    LaunchedEffect(versionFieldInteractionSource) {
        versionFieldInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                if (stateHolder.isNavigationLocked) {
                    onMessage("Stop the server before changing versions.")
                } else {
                    showVersionDialog = true
                }
            }
        }
    }

    LaunchedEffect(
        serverName,
        serverDescription,
        serverPhotoUri,
        photoChanged,
        selectedVersion,
        selectedServerType,
        selectedCustomJarPath,
        joinMessageText,
        joinMessageUrl,
        activeWorld
    ) {
        val hasUnsavedChanges =
            serverName.trim() != stateHolder.serverName.trim() ||
                serverDescription.trim() != stateHolder.serverDescription.trim() ||
                photoChanged ||
                selectedVersion.trim() != stateHolder.config.gameVersion ||
                selectedServerType != stateHolder.config.serverType ||
                selectedCustomJarPath != stateHolder.config.customJarPath ||
                (isPremium && joinMessageText.trim() != stateHolder.config.joinMessageText.trim()) ||
                (isPremium && joinMessageUrl.trim() != stateHolder.config.joinMessageUrl.trim())

        if (hasUnsavedChanges) {
            delay(450)
            persistDetails(showMessage = false, closeAfterSave = false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        PocketColors.PrimaryMuted.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Server Details",
                style = MaterialTheme.typography.titleLarge
            )
        }

        IpManagerCard(
            entitlement = entitlement,
            isPremiumUnlocked = isPremium,
            rolloutEnabled = customSubdomainEnabled,
            onMessage = onMessage,
            onNavigateToSignUp = onNavigateToSignUp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PocketColors.Primary.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .border(2.dp, PocketColors.Primary.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = PocketColors.PrimaryDark
                    )
                    Text(
                        text = "Give your server a playful home-card look",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "World: ${stateHolder.activeWorld}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Upload a photo from your phone and add a short description that shows right under the server name.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        GameCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    singleLine = true,
                    label = { Text("Server name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = duoTextFieldShape(),
                    colors = duoOutlinedTextFieldColors()
                )

                ServerDescriptionField(
                    description = serverDescription,
                    onDescriptionChange = { serverDescription = it },
                    modifier = Modifier.fillMaxWidth()
                )

                val hasSelectedVersion = selectedVersion.isNotBlank()
                val selectedRuntimeLabel = when {
                    selectedServerType.supportsVersionSelect ->
                        if (hasSelectedVersion) "${selectedServerType.displayName} $selectedVersion" else ""
                    selectedServerType == ServerType.MODPACK ->
                        selectedCustomJarPath
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "${selectedServerType.displayName} $it" }
                            ?: ""
                    else -> "${selectedServerType.displayName} (Custom JAR)"
                }
                OutlinedTextField(
                    value = selectedRuntimeLabel,
                    onValueChange = {},
                    singleLine = true,
                    readOnly = true,
                    enabled = true,
                    interactionSource = versionFieldInteractionSource,
                    label = { Text("Game version") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = null,
                            tint = PocketColors.PrimaryDark
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = duoTextFieldShape(),
                    colors = duoOutlinedTextFieldColors()
                )

                // In-Game Join Announcement Section (Premium Feature)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isPremium) PocketColors.Primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(20.dp)
                        )
                        .border(
                            1.dp,
                            if (isPremium) PocketColors.Primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Campaign,
                                contentDescription = null,
                                tint = if (isPremium) PocketColors.PrimaryDark else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "In-Game Announcement",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            if (!isPremium) {
                                Surface(
                                    color = PocketColors.Primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.clickable { showPremiumBottomSheet = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = PocketColors.PrimaryDark,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "PRO",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = PocketColors.PrimaryDark
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Custom message broadcasted in game chat whenever a player joins your world.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val textInteractionSource = remember { MutableInteractionSource() }
                        val urlInteractionSource = remember { MutableInteractionSource() }
                        LaunchedEffect(textInteractionSource, urlInteractionSource) {
                            scope.launch {
                                textInteractionSource.interactions.collect { interaction ->
                                    if (interaction is PressInteraction.Release && !isPremium) {
                                        showPremiumBottomSheet = true
                                    }
                                }
                            }
                            scope.launch {
                                urlInteractionSource.interactions.collect { interaction ->
                                    if (interaction is PressInteraction.Release && !isPremium) {
                                        showPremiumBottomSheet = true
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = joinMessageText,
                            onValueChange = { if (isPremium) joinMessageText = it else showPremiumBottomSheet = true },
                            singleLine = true,
                            readOnly = !isPremium,
                            interactionSource = textInteractionSource,
                            label = { Text("Announcement text") },
                            placeholder = { Text("e.g. hosted on Pocketcraft") },
                            trailingIcon = if (!isPremium) {
                                { Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = duoTextFieldShape(),
                            colors = duoOutlinedTextFieldColors()
                        )

                        OutlinedTextField(
                            value = joinMessageUrl,
                            onValueChange = { if (isPremium) joinMessageUrl = it else showPremiumBottomSheet = true },
                            singleLine = true,
                            readOnly = !isPremium,
                            interactionSource = urlInteractionSource,
                            label = { Text("Announcement link / Discord URL") },
                            placeholder = { Text("e.g. https://discord.gg/yourcode") },
                            trailingIcon = if (!isPremium) {
                                { Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = duoTextFieldShape(),
                            colors = duoOutlinedTextFieldColors()
                        )
                    }
                }

                ServerPhotoUpload(
                    photoUri = serverPhotoUri,
                    onPhotoSelected = { uri ->
                        serverPhotoUri = uri
                        photoChanged = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSaving) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (photoChanged) "Uploading and saving server details..." else "Saving server details...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { saveProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                DuoButton(
                    text = "SAVE",
                    onClick = {
                        scope.launch {
                            persistDetails(showMessage = true, closeAfterSave = true)
                        }
                    },
                    enabled = serverName.isNotBlank() && !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }

    if (showVersionDialog) {
        val versionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    versionSheetState.hide()
                    showVersionDialog = false
                }
            },
            sheetState = versionSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            ServerTypeVersionBottomSheet(
                onDismissRequest = {
                    scope.launch {
                        versionSheetState.hide()
                        showVersionDialog = false
                    }
                },
                onConfirm = { type, version, customJar ->
                    scope.launch {
                        selectedServerType = type
                        selectedCustomJarPath = customJar
                        selectedVersion = when (type) {
                            ServerType.MODPACK -> customJar.orEmpty()
                            else -> version.orEmpty()
                        }
                        versionSheetState.hide()
                        showVersionDialog = false
                        onMessage("Version updated.")
                    }
                },
                currentServerType = selectedServerType,
                currentGameVersion = selectedVersion,
                currentCustomJarPath = selectedCustomJarPath
            )
        }
    }

    if (showPremiumBottomSheet) {
        com.pocketcraft.server.ui.components.PremiumUpgradeBottomSheet(
            onDismissRequest = { showPremiumBottomSheet = false },
            onNavigateToSignUp = onNavigateToSignUp
        )
    }
}
