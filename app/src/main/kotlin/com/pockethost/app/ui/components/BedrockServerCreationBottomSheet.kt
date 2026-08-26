package com.pockethost.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethost.app.server.NukkitVersions
import com.pockethost.app.ui.theme.ButtonFont
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.ui.theme.card3d

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedrockServerCreationBottomSheet(
    onDismiss: () -> Unit,
    onCreateBedrockServer: (name: String, port: Int, gamemode: Int, difficulty: Int, maxPlayers: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var serverName by remember { mutableStateOf("Bedrock World") }
    var portText by remember { mutableStateOf(NukkitVersions.DEFAULT_BEDROCK_PORT.toString()) }
    var selectedGamemode by remember { mutableIntStateOf(0) } // 0: Survival, 1: Creative, 2: Adventure
    var selectedDifficulty by remember { mutableIntStateOf(2) } // 0: Peaceful, 1: Easy, 2: Normal, 3: Hard
    var maxPlayersFloat by remember { mutableFloatStateOf(10f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2E7D32)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gamepad,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Host Bedrock Server",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = ButtonFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = "Nukkit Bedrock Native Runtime",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF2E7D32).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "BEDROCK",
                        color = Color(0xFF2E7D32),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Server Name Input
            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = { Text("Server Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Server Port Input
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { char -> char.isDigit() } },
                label = { Text("Bedrock UDP Port (Default 19132)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Gamemode Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Gamemode",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Survival" to 0, "Creative" to 1, "Adventure" to 2).forEach { (label, value) ->
                        val isSelected = selectedGamemode == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color(0xFF2E7D32)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedGamemode = value },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Difficulty Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Difficulty",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Peaceful" to 0, "Easy" to 1, "Normal" to 2, "Hard" to 3).forEach { (label, value) ->
                        val isSelected = selectedDifficulty == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color(0xFF2E7D32)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedDifficulty = value },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Max Players Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Max Players", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("${maxPlayersFloat.toInt()} Players", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
                Slider(
                    value = maxPlayersFloat,
                    onValueChange = { maxPlayersFloat = it },
                    valueRange = 2f..40f,
                    steps = 37
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Create Button
            val portInt = portText.toIntOrNull() ?: NukkitVersions.DEFAULT_BEDROCK_PORT
            val isValid = serverName.isNotBlank()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .card3d(
                        elevation = 6.dp,
                        cornerRadius = 16.dp,
                        borderColor = Color(0xFF1B5E20),
                        depthColor = Color(0xFF1B5E20)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isValid) Color(0xFF2E7D32) else Color.Gray)
                    .clickable(enabled = isValid) {
                        onCreateBedrockServer(
                            serverName.trim(),
                            portInt,
                            selectedGamemode,
                            selectedDifficulty,
                            maxPlayersFloat.toInt()
                        )
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CREATE BEDROCK SERVER",
                    color = Color.White,
                    fontFamily = ButtonFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
