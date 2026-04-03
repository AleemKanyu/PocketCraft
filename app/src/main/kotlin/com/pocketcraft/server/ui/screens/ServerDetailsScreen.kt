package com.pocketcraft.server.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.GameCard
import com.pocketcraft.server.ui.components.ServerDescriptionField
import com.pocketcraft.server.ui.components.ServerPhotoUpload
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ServerDetailsScreen(
    stateHolder: ServerStateHolder,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val activeWorld = stateHolder.config.worldName
    var serverName by remember(stateHolder.serverName, stateHolder.config.worldName) {
        mutableStateOf(stateHolder.serverName.ifBlank { stateHolder.config.worldName.ifBlank { "world" } })
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
    var isSaving by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableStateOf(0f) }

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
                photoUrlToSave == existingPhoto

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

    LaunchedEffect(serverName, serverDescription, serverPhotoUri, photoChanged, activeWorld) {
        val hasUnsavedChanges =
            serverName.trim() != stateHolder.serverName.trim() ||
                serverDescription.trim() != stateHolder.serverDescription.trim() ||
                photoChanged

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
                        text = "World: ${stateHolder.config.worldName}",
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
                    shape = RoundedCornerShape(18.dp)
                )

                ServerDescriptionField(
                    description = serverDescription,
                    onDescriptionChange = { serverDescription = it },
                    modifier = Modifier.fillMaxWidth()
                )

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
    }
}
