package com.pocketcraft.server.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.pocketcraft.server.billing.PremiumEntitlement
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val discordButtonBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF7A8CFF),
        Color(0xFF5865F2),
        Color(0xFF4452E6)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordBadgePopup(
    entitlement: PremiumEntitlement,
    onDismissRequest: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    var discordId by remember(entitlement.discordId) { mutableStateOf(entitlement.discordId) }
    var saving by remember { mutableStateOf(false) }
    var savedDiscordId by remember(entitlement.discordId) { mutableStateOf(entitlement.discordId.trim()) }
    var isEditing by remember(entitlement.discordId) { mutableStateOf(entitlement.discordId.isBlank()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { IosDragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFFB300), Color(0xFFAB47BC))
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎮", fontSize = 16.sp)
                }
                Column {
                    Text(
                        "Discord Badge",
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Monocraft,
                        fontSize = 15.sp
                    )
                    Text(
                        "Link your Discord to get a Pro badge on our server.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = discordId,
                onValueChange = { if (isEditing) discordId = it.take(64) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Discord Username (e.g. yourname#1234)") },
                singleLine = true,
                readOnly = !isEditing,
                shape = duoTextFieldShape(),
                colors = duoOutlinedTextFieldColors()
            )

            if (!isEditing && savedDiscordId.isNotBlank()) {
                DuoButton(
                    text = "EDIT DISCORD ID",
                    onClick = { isEditing = true },
                    variant = DuoButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 48.dp
                )
            } else {
                DuoButton(
                    text = if (saving) "SAVING..." else "SAVE DISCORD ID",
                    onClick = {
                        val uid = currentUser?.uid
                        if (uid.isNullOrBlank()) {
                            onMessage("Sign in to link your Discord account.")
                            return@DuoButton
                        }
                        saving = true
                        scope.launch {
                            runCatching {
                                val trimmedDiscordId = discordId.trim()
                                firestore.collection("users")
                                    .document(uid)
                                    .set(
                                        mapOf(
                                            "discordId" to trimmedDiscordId,
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        ),
                                        com.google.firebase.firestore.SetOptions.merge()
                                    )
                                    .await()
                                savedDiscordId = trimmedDiscordId
                                discordId = trimmedDiscordId
                                isEditing = false
                                onMessage("Discord ID saved.")
                                sheetState.hide()
                                onDismissRequest()
                            }.onFailure { error ->
                                onMessage(error.message ?: "Could not save Discord ID.")
                            }
                            saving = false
                        }
                    },
                    variant = DuoButtonVariant.Discord,
                    backgroundBrush = discordButtonBrush,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && discordId.trim().isNotBlank(),
                    minHeight = 48.dp
                )
            }

            DuoButton(
                text = "MAYBE LATER",
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismissRequest()
                    }
                },
                variant = DuoButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 44.dp
            )
        }
    }
}
