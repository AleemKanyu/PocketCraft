package com.pocketcraft.server.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pocketcraft.server.data.model.PlayerInfo
import com.pocketcraft.server.data.preferences.AppPreferencesStore
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.components.duoOutlinedTextFieldColors
import com.pocketcraft.server.ui.util.playAppHaptic
import kotlinx.coroutines.launch

enum class ChatFilter {
    ALL,
    PLAYER,
    PRIVATE,
    SYSTEM
}

private val CHAT_LINE_REGEX = Regex("""<(\w+)>\s+(.*)""")  // matches: <Steve> hello world
private val BROADCAST_REGEX = Regex("""\[Server\]\s+(.*)""") // matches: [Server] admin broadcast
private val CONSOLE_WHISPER_REGEX = Regex("""\[(?:Server|console):\s+Whispered\s+(.*)\s+to\s+(\w+)\]""") // matches: [Server: Whispered hello to Steve]
private val PLAYER_WHISPER_REGEX = Regex("""(\w+)\s+whispered\s+to\s+you:\s+(.*)""") // matches: Steve whispered to you: hello

private data class ChatEntry(
    val sender: String,
    val message: String,
    val isAdmin: Boolean = false,
    val isOperator: Boolean = false,
    val isPrivate: Boolean = false
)

private fun parseChatEntries(logs: List<String>): List<ChatEntry> {
    val entries = mutableListOf<ChatEntry>()
    for (line in logs) {
        val cleanLine = com.pocketcraft.server.service.ConsoleParser.parse(line).text.trim()
        val chatMatch = CHAT_LINE_REGEX.find(cleanLine)
        if (chatMatch != null) {
            entries.add(ChatEntry(sender = chatMatch.groupValues[1], message = chatMatch.groupValues[2]))
            continue
        }
        val whisperMatch = CONSOLE_WHISPER_REGEX.find(cleanLine)
        if (whisperMatch != null) {
            val msg = whisperMatch.groupValues[1]
            val actualMsg = if (msg.startsWith("[Operator] (Private) ")) msg.substringAfter("[Operator] (Private) ") else msg
            entries.add(ChatEntry(
                sender = "Operator (Private to ${whisperMatch.groupValues[2]})",
                message = actualMsg,
                isAdmin = true,
                isOperator = true,
                isPrivate = true
            ))
            continue
        }
        val playerWhisperMatch = PLAYER_WHISPER_REGEX.find(cleanLine)
        if (playerWhisperMatch != null) {
            entries.add(ChatEntry(
                sender = "${playerWhisperMatch.groupValues[1]} (Private)",
                message = playerWhisperMatch.groupValues[2],
                isPrivate = true
            ))
            continue
        }
        val broadcastMatch = BROADCAST_REGEX.find(cleanLine)
        if (broadcastMatch != null) {
            val content = broadcastMatch.groupValues[1]
            if (content.startsWith("[Operator] ")) {
                entries.add(ChatEntry(
                    sender = "Operator",
                    message = content.substringAfter("[Operator] "),
                    isAdmin = true,
                    isOperator = true
                ))
            } else {
                entries.add(ChatEntry(sender = "Server", message = content, isAdmin = true))
            }
        }
    }
    return entries
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingChatBottomSheet(
    stateHolder: ServerStateHolder,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val appFeedbackEnabled by AppPreferencesStore.isSoundEnabledFlow(context).collectAsState(initial = true)
    
    fun triggerHaptic() {
        if (!appFeedbackEnabled) return
        scope.launch {
            playAppHaptic(context, hapticFeedback, false)
        }
    }

    fun dismissWithAnimation() {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismissRequest()
            }
        }
    }
    
    var inputText by remember { mutableStateOf("") }
    var selectedSilentPlayer by remember { mutableStateOf<String?>(null) }
    var showChatWalkthrough by rememberSaveable { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(ChatFilter.ALL) }
    
    val chatEntries = remember(stateHolder.logs.size) {
        parseChatEntries(stateHolder.logs.toList())
    }

    val filteredEntries = remember(chatEntries, searchQuery, activeFilter) {
        chatEntries.filter { entry ->
            val matchesFilter = when (activeFilter) {
                ChatFilter.ALL -> true
                ChatFilter.PLAYER -> !entry.isPrivate && !entry.isOperator && entry.sender != "Server"
                ChatFilter.PRIVATE -> entry.isPrivate
                ChatFilter.SYSTEM -> entry.isOperator || entry.sender == "Server"
            }
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                entry.sender.contains(searchQuery, ignoreCase = true) ||
                        entry.message.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }

    // Auto-scroll to bottom on new message
    LaunchedEffect(filteredEntries.size) {
        if (filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(filteredEntries.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PocketColors.Primary.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Forum,
                            null,
                            tint = PocketColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            "Operator Chat",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            fontFamily = Monocraft
                        )
                        Text(
                            "Chatting as server operator",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) PocketColors.Primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { dismissWithAnimation() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search message text or sender name...", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = duoOutlinedTextFieldColors(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )
            }

            // Filter chips row
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(ChatFilter.entries) { filter ->
                    val isSelected = activeFilter == filter
                    val label = when (filter) {
                        ChatFilter.ALL -> "All"
                        ChatFilter.PLAYER -> "💬 Player"
                        ChatFilter.PRIVATE -> "🔒 Whispers"
                        ChatFilter.SYSTEM -> "📢 System"
                    }
                    val chipColor = if (isSelected) {
                        if (filter == ChatFilter.PRIVATE) Color(0xFF8E24AA).copy(alpha = 0.2f)
                        else PocketColors.Primary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                    val borderColor = if (isSelected) {
                        if (filter == ChatFilter.PRIVATE) Color(0xFF8E24AA)
                        else PocketColors.Primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    }
                    val textColor = if (isSelected) {
                        if (filter == ChatFilter.PRIVATE) Color(0xFFAB47BC)
                        else if (isDark) Color.White else PocketColors.PrimaryDark
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                    
                    Surface(
                        modifier = Modifier.clickable {
                            triggerHaptic()
                            activeFilter = filter
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = chipColor,
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Walkthrough Banner
            if (showChatWalkthrough) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = PocketColors.Primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PocketColors.Primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("💡", fontSize = 16.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Quick Chat Guide",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "• Tap player names in the online list to mention them (@name).\n" +
                                "• Click the 🔒 Private Msg badge next to a name to start Whispering (messages will only be visible to that player).\n" +
                                "• When Whispering is active, the send button highlights in purple.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        IconButton(
                            onClick = { showChatWalkthrough = false },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Chat Messages
            if (chatEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "No chat messages yet",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        )
                    }
                }
            } else if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "No matching messages found",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEntries) { entry ->
                        FloatingChatBubble(entry)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            // Tagging suggestion & Silent mode info
            val onlinePlayers = stateHolder.onlinePlayers
            if (onlinePlayers.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Text("Online:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(onlinePlayers) { player ->
                        Surface(
                            modifier = Modifier.clickable {
                                triggerHaptic()
                                val currentText = inputText
                                inputText = if (currentText.endsWith(" ") || currentText.isEmpty()) {
                                    currentText + "@${player.name} "
                                } else {
                                    currentText + " @${player.name} "
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selectedSilentPlayer == player.name) PocketColors.Primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (selectedSilentPlayer == player.name) PocketColors.Primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                ) {
                                    AsyncImage(
                                        model = "https://mc-heads.net/avatar/${player.name}/16",
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Text(player.name, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                
                                // Silent chat toggle helper
                                Box(
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selectedSilentPlayer == player.name) Color(0xFF8E24AA)
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                        )
                                        .clickable {
                                            triggerHaptic()
                                            selectedSilentPlayer = if (selectedSilentPlayer == player.name) null else player.name
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (selectedSilentPlayer == player.name) "🔒 Whispering" else "🔒 Private Msg",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedSilentPlayer == player.name) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            }

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (selectedSilentPlayer != null) "Send silent message to ${selectedSilentPlayer}..." else "Broadcast to all players...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = duoOutlinedTextFieldColors()
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank()) {
                                if (selectedSilentPlayer != null) Color(0xFF8E24AA) else PocketColors.Primary
                            } else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = inputText.isNotBlank()) {
                            triggerHaptic()
                            val text = inputText.trim()
                            if (text.isNotBlank()) {
                                if (stateHolder.status != ServerStatus.ONLINE) {
                                    android.widget.Toast.makeText(context, "Server is offline. Start the server to send messages.", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    if (selectedSilentPlayer != null) {
                                        stateHolder.sendCommand("msg ${selectedSilentPlayer} [Operator] (Private) $text")
                                    } else {
                                        stateHolder.sendCommand("say [Operator] $text")
                                    }
                                    inputText = ""
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) Color.Black
                        else MaterialTheme.colorScheme.onSurface.copy(0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingChatBubble(entry: ChatEntry) {
    val isOperator = entry.isOperator
    val bubbleBg = when {
        entry.isPrivate -> Color(0xFF4A148C).copy(alpha = 0.12f)
        isOperator -> PocketColors.Primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val senderColor = when {
        entry.isPrivate -> Color(0xFFAB47BC)
        isOperator -> PocketColors.Primary
        else -> MaterialTheme.colorScheme.onSurface.copy(0.65f)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOperator) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOperator) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                val cleanSenderName = entry.sender.substringBefore(" ")
                AsyncImage(
                    model = "https://mc-heads.net/avatar/$cleanSenderName/32",
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isOperator) 18.dp else 4.dp,
                        topEnd = if (isOperator) 4.dp else 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp
                    )
                )
                .background(bubbleBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = when {
                    entry.isPrivate && isOperator -> "🔒 ${entry.sender}"
                    entry.isPrivate -> "🔒 ${entry.sender}"
                    isOperator -> "📢 Operator"
                    else -> entry.sender
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = senderColor
            )
            Spacer(Modifier.height(2.dp))
            
            val rawMsg = entry.message
            if (rawMsg.contains("@")) {
                val words = rawMsg.split(" ")
                androidx.compose.foundation.text.BasicText(
                    text = androidx.compose.ui.text.buildAnnotatedString {
                        words.forEachIndexed { i, word ->
                            if (word.startsWith("@") && word.length > 1) {
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = PocketColors.Primary, fontWeight = FontWeight.Bold))
                                append(word)
                                pop()
                            } else {
                                append(word)
                            }
                            if (i < words.lastIndex) append(" ")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.9f)
                    )
                )
            } else {
                Text(
                    text = rawMsg,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.9f)
                )
            }
        }
    }
}
