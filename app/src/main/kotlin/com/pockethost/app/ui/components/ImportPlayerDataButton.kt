package com.pockethost.app.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.service.PlayerDataManager
import com.pockethost.app.ui.screens.ServerStateHolder
import com.pockethost.app.ui.theme.ButtonFont
import com.pockethost.app.ui.theme.PocketColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Import player data" for the active world: a zip (world or server backup) brings every
 * player's inventory, stats and advancements; a single .dat file is assigned to one player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPlayerDataButton(
    stateHolder: ServerStateHolder,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var pendingPlayerFile by remember { mutableStateOf<Uri?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    fun runImport(uri: Uri, playerName: String?) {
        importing = true
        scope.launch {
            val result = PlayerDataManager.importPlayerData(context, stateHolder.activeWorld, uri, playerName)
            importing = false
            result
                .onSuccess { count ->
                    stateHolder.invalidateKnownPlayers()
                    stateHolder.refreshAll()
                    toast(if (count == 1) "Imported 1 player file." else "Imported $count player files.")
                }
                .onFailure { toast(it.message ?: "Player data import failed.") }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val kind = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        PlayerDataManager.detectImportKind(ByteArray(4).also { stream.read(it) })
                    }
                }.getOrNull() ?: PlayerDataManager.PlayerImportKind.UNKNOWN
            }
            when (kind) {
                PlayerDataManager.PlayerImportKind.SINGLE_PLAYER_FILE -> pendingPlayerFile = uri
                PlayerDataManager.PlayerImportKind.ZIP -> runImport(uri, null)
                PlayerDataManager.PlayerImportKind.UNKNOWN ->
                    toast("Pick a world or backup .zip, or a player .dat file.")
            }
        }
    }

    OutlinedButton(
        onClick = {
            if (stateHolder.isRunning || stateHolder.isStarting) {
                // A running server writes its own copy over these files when players leave.
                toast("Stop the server before importing player data.")
            } else {
                runCatching { picker.launch(arrayOf("*/*")) }
                    .onFailure { toast("No file picker is available on this device.") }
            }
        },
        enabled = !importing,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (importing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                text = if (importing) "Importing player data..." else "Import player data",
                fontWeight = FontWeight.Bold,
                fontFamily = ButtonFont
            )
        }
    }

    pendingPlayerFile?.let { uri ->
        var playerName by remember(uri) { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        fun close(then: () -> Unit = {}) {
            scope.launch {
                sheetState.hide()
                pendingPlayerFile = null
                then()
            }
        }
        ModalBottomSheet(
            onDismissRequest = { pendingPlayerFile = null },
            sheetState = sheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Whose data is this?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "A player file does not say who it belongs to. Enter the exact username " +
                        "and it will replace that player's inventory and position.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    placeholder = { Text("e.g., Steve") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = duoTextFieldShape(),
                    colors = duoOutlinedTextFieldColors()
                )
                Button(
                    onClick = { val name = playerName.trim(); close { runImport(uri, name) } },
                    enabled = playerName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PocketColors.Primary,
                        contentColor = Color.Black,
                        disabledContainerColor = PocketColors.Primary.copy(0.5f)
                    )
                ) {
                    Text("Import", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { close() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}
