package com.pocketcraft.server.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pocketcraft.server.billing.PremiumEntitlement
import com.pocketcraft.server.billing.PremiumTier
import com.pocketcraft.server.data.preferences.AppPreferences
import com.pocketcraft.server.ui.theme.Monocraft
import com.pocketcraft.server.ui.theme.PocketColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class SubdomainRegion(val wireValue: String, val label: String) {
    ASIA("as", "Asia"),
    EUROPE("eu", "Europe");
}

private data class UserSubdomainRecord(
    val subdomain: String,
    val region: String?
) {
    val hostname: String
        get() = "$subdomain${regionDomainSuffix(region)}"
}

private fun regionDomainSuffix(region: String?): String = when (region?.trim()?.lowercase(Locale.US)) {
    "as" -> ".as.pocketcraft.online"
    "eu" -> ".eu.pocketcraft.online"
    else -> ".pocketcraft.online"
}

private val discordButtonBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF7A8CFF),
        Color(0xFF5865F2),
        Color(0xFF4452E6)
    )
)

@Composable
fun IpManagerCard(
    entitlement: PremiumEntitlement,
    isPremiumUnlocked: Boolean,
    rolloutEnabled: Boolean,
    onMessage: (String) -> Unit,
    onNavigateToSignUp: () -> Unit = {}
) {
    GameCard(modifier = Modifier.fillMaxWidth()) {
        IpManagerContent(
            entitlement = entitlement,
            isPremiumUnlocked = isPremiumUnlocked,
            rolloutEnabled = rolloutEnabled,
            onMessage = onMessage,
            onNavigateToSignUp = onNavigateToSignUp
        )
    }
}

@Composable
fun IpManagerContent(
    entitlement: PremiumEntitlement,
    isPremiumUnlocked: Boolean,
    rolloutEnabled: Boolean,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSignUp: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    var subdomainInput by remember { mutableStateOf("") }
    var selectedRegion by remember { mutableStateOf(SubdomainRegion.ASIA) }
    var existingRecord by remember { mutableStateOf<UserSubdomainRecord?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var showPremiumBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser?.uid, isPremiumUnlocked) {
        val uid = currentUser?.uid
        if (uid.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching {
            val snapshot = firestore.collection("subdomains")
                .whereEqualTo("ownerId", uid)
                .limit(1)
                .get()
                .await()
            val doc = snapshot.documents.firstOrNull()
            existingRecord = doc?.let {
                UserSubdomainRecord(
                    subdomain = it.id,
                    region = it.getString("region")
                )
            }
            if (existingRecord != null && isPremiumUnlocked) {
                prefs.customSubdomain = existingRecord!!.subdomain
                prefs.customSubdomainRegion = existingRecord!!.region
                subdomainInput = existingRecord!!.subdomain
                selectedRegion = when (existingRecord!!.region) {
                    "eu" -> SubdomainRegion.EUROPE
                    else -> SubdomainRegion.ASIA
                }
            } else {
                prefs.customSubdomain = null
                prefs.customSubdomainRegion = null
                existingRecord = null
            }
        }.onFailure { error ->
            onMessage(error.message ?: "Could not load IP.")
        }
        loading = false
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                PocketColors.Primary.copy(alpha = 0.18f),
                                PocketColors.PrimaryMuted.copy(alpha = 0.30f)
                            )
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, PocketColors.Primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = PocketColors.Primary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Custom IP",
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Monocraft,
                    fontSize = 15.sp
                )
                Text(
                    text = "Create a regional IP for your server relay.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading IP...", fontSize = 12.sp)
                }
            }
            existingRecord != null && !isEditing -> {
                IpSummary(record = existingRecord!!)
                DuoButton(
                    text = "EDIT IP",
                    onClick = {
                        isEditing = true
                    },
                    variant = DuoButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 40.dp
                )
            }
            currentUser == null -> {
                Text(
                    text = "Sign in before creating a custom IP.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            !rolloutEnabled -> {
                Text(
                    text = "Custom IPs are visible here now, but creation is still disabled by remote rollout.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            !isPremiumUnlocked -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Custom IPs unlock with Pro or Member.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DuoButton(
                        text = "UPGRADE TO PRO 🚀",
                        onClick = { showPremiumBottomSheet = true },
                        variant = DuoButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 40.dp
                    )
                }
                if (showPremiumBottomSheet) {
                    PremiumUpgradeBottomSheet(
                        onDismissRequest = { showPremiumBottomSheet = false },
                        onNavigateToSignUp = onNavigateToSignUp
                    )
                }
            }
            else -> {
                OutlinedTextField(
                    value = subdomainInput,
                    onValueChange = { subdomainInput = it.lowercase(Locale.US).filter { ch -> ch.isLetterOrDigit() || ch == '-' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom IP name") },
                    singleLine = true,
                    shape = duoTextFieldShape(),
                    colors = duoOutlinedTextFieldColors(),
                    suffix = {
                        Text(
                            text = regionDomainSuffix(
                                when (selectedRegion) {
                                    SubdomainRegion.EUROPE -> "eu"
                                    SubdomainRegion.ASIA -> "as"
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Monocraft)
                        )
                    },
                    supportingText = {
                        Text("Allowed: lowercase letters, numbers, hyphens.")
                    }
                )

                val previewHost = if (subdomainInput.isBlank()) {
                    "your-name${regionDomainSuffix(if (selectedRegion == SubdomainRegion.EUROPE) "eu" else "as")}"
                } else {
                    "$subdomainInput${regionDomainSuffix(if (selectedRegion == SubdomainRegion.EUROPE) "eu" else "as")}"
                }
                Text(
                    text = previewHost,
                    fontSize = 12.sp,
                    color = PocketColors.Primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketColors.ConsoleWarn.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .border(1.dp, PocketColors.ConsoleWarn.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = PocketColors.ConsoleWarn,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "You will need to restart your server for IP changes to take effect.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val hasExisting = existingRecord != null

                    if (hasExisting) {
                        DuoButton(
                            text = if (saving) "..." else "CANCEL",
                            onClick = {
                                subdomainInput = existingRecord!!.subdomain
                                selectedRegion = when (existingRecord!!.region) {
                                    "eu" -> SubdomainRegion.EUROPE
                                    else -> SubdomainRegion.ASIA
                                }
                                isEditing = false
                            },
                            variant = DuoButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                            enabled = !saving,
                            minHeight = 40.dp
                        )

                        DuoButton(
                            text = if (saving) "..." else "DELETE",
                            onClick = {
                                saving = true
                                scope.launch {
                                    runCatching {
                                        firestore.collection("subdomains")
                                            .document(existingRecord!!.subdomain)
                                            .delete()
                                            .await()
                                        existingRecord = null
                                        prefs.customSubdomain = null
                                        prefs.customSubdomainRegion = null
                                        subdomainInput = ""
                                        isEditing = false
                                        onMessage("Custom IP removed.")
                                    }.onFailure { error ->
                                        onMessage(error.message ?: "Could not remove IP.")
                                    }
                                    saving = false
                                }
                            },
                            variant = DuoButtonVariant.Danger,
                            modifier = Modifier.weight(1f),
                            enabled = !saving,
                            minHeight = 40.dp
                        )
                    }

                    DuoButton(
                        text = if (saving) "SAVING..." else "SAVE",
                        onClick = {
                            val normalized = subdomainInput.trim().lowercase(Locale.US)
                            if (!normalized.matches(Regex("^[a-z0-9-]{3,32}$"))) {
                                onMessage("IP must be 3-32 chars using lowercase letters, numbers, or hyphens.")
                                return@DuoButton
                            }
                            saving = true
                            scope.launch {
                                runCatching {
                                    val detectedRegion = if (prefs.relayHost == "eu.pocketcraft.online") "eu" else "as"
                                    saveSubdomain(
                                        firestore = firestore,
                                        ownerId = currentUser!!.uid,
                                        newSubdomain = normalized,
                                        newRegion = detectedRegion,
                                        oldSubdomain = existingRecord?.subdomain
                                    )
                                    existingRecord = UserSubdomainRecord(normalized, detectedRegion)
                                    prefs.customSubdomain = normalized
                                    prefs.customSubdomainRegion = detectedRegion
                                    isEditing = false
                                    onMessage("Custom IP saved: $normalized${regionDomainSuffix(detectedRegion)}")
                                }.onFailure { error ->
                                    onMessage(error.message ?: "Could not save IP.")
                                }
                                saving = false
                            }
                        },
                        variant = DuoButtonVariant.Primary,
                        modifier = Modifier.weight(if (hasExisting) 1.2f else 1f),
                        enabled = !saving,
                        minHeight = 40.dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpBottomSheet(
    entitlement: PremiumEntitlement,
    isPremiumUnlocked: Boolean,
    rolloutEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onMessage: (String) -> Unit,
    onNavigateToSignUp: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = { IosDragHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IpManagerContent(
                entitlement = entitlement,
                isPremiumUnlocked = isPremiumUnlocked,
                rolloutEnabled = rolloutEnabled,
                onMessage = onMessage,
                onNavigateToSignUp = onNavigateToSignUp
            )
        }
    }
}

@Composable
fun ProDiscordCard(
    entitlement: PremiumEntitlement,
    onMessage: (String) -> Unit
) {
    if (!entitlement.isPremium || entitlement.isSupportive) return

    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    var discordId by remember(entitlement.discordId) { mutableStateOf(entitlement.discordId) }
    var saving by remember { mutableStateOf(false) }
    var savedDiscordId by remember(entitlement.discordId) { mutableStateOf(entitlement.discordId.trim()) }
    var isEditing by remember(entitlement.discordId) { mutableStateOf(entitlement.discordId.isBlank()) }

    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        fontSize = 14.sp
                    )
                    Text(
                        "Link your Discord to get a Pro badge on our server.",
                        fontSize = 10.sp,
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
                readOnly = !isEditing
            )

            if (!isEditing && savedDiscordId.isNotBlank()) {
                DuoButton(
                    text = "EDIT DISCORD ID",
                    onClick = { isEditing = true },
                    variant = DuoButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 46.dp
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
                                            "premiumTier" to entitlement.tier.wireValue,
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        ),
                                        com.google.firebase.firestore.SetOptions.merge()
                                    )
                                    .await()
                                savedDiscordId = trimmedDiscordId
                                discordId = trimmedDiscordId
                                isEditing = false
                                onMessage("Discord ID saved.")
                            }.onFailure { error ->
                                onMessage(error.message ?: "Could not save Discord ID.")
                            }
                            saving = false
                        }
                    },
                    variant = DuoButtonVariant.Discord,
                    backgroundBrush = discordButtonBrush,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && discordId.trim().isNotBlank()
                )
            }
        }
    }
}

@Composable
fun SupportiveToolsCard(
    entitlement: PremiumEntitlement,
    onMessage: (String) -> Unit
) {
    if (!entitlement.isSupportive) return

    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    var discordId by remember(entitlement.discordId) { mutableStateOf(entitlement.discordId) }
    var savedDiscordId by remember(entitlement.discordId) { mutableStateOf(entitlement.discordId.trim()) }
    var isEditingDiscordId by remember(entitlement.discordId) { mutableStateOf(entitlement.discordId.isBlank()) }

    GameCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFFB300), Color(0xFF7B1FA2))
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Member Tools", fontWeight = FontWeight.ExtraBold, fontFamily = Monocraft, fontSize = 15.sp)
                    Text(
                        "Link your Discord to get a Member badge on our server.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- Discord ID ---
            Text(
                "🎮 Member Discord Badge",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFFAB47BC)
            )
            OutlinedTextField(
                value = discordId,
                onValueChange = { if (isEditingDiscordId) discordId = it.take(64) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Discord Username (e.g. yourname#1234)") },
                singleLine = true,
                readOnly = !isEditingDiscordId
            )

            var savingDiscord by remember { mutableStateOf(false) }

            if (!isEditingDiscordId && savedDiscordId.isNotBlank()) {
                DuoButton(
                    text = "EDIT DISCORD ID",
                    onClick = { isEditingDiscordId = true },
                    variant = DuoButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 46.dp
                )
            } else {
                DuoButton(
                    text = if (savingDiscord) "SAVING..." else "SAVE DISCORD ID",
                    onClick = {
                        val uid = currentUser?.uid
                        if (uid.isNullOrBlank()) {
                            onMessage("Sign in to link your Discord account.")
                            return@DuoButton
                        }
                        savingDiscord = true
                        scope.launch {
                            runCatching {
                                val trimmedDiscordId = discordId.trim()
                                firestore.collection("users")
                                    .document(uid)
                                    .set(
                                        mapOf(
                                            "discordId" to trimmedDiscordId,
                                            "premiumTier" to entitlement.tier.wireValue,
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        ),
                                        com.google.firebase.firestore.SetOptions.merge()
                                    )
                                    .await()
                                savedDiscordId = trimmedDiscordId
                                discordId = trimmedDiscordId
                                isEditingDiscordId = false
                                onMessage("Discord ID saved.")
                            }.onFailure { error ->
                                onMessage(error.message ?: "Could not save Discord ID.")
                            }
                            savingDiscord = false
                        }
                    },
                    variant = DuoButtonVariant.Discord,
                    backgroundBrush = discordButtonBrush,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !savingDiscord,
                    minHeight = 46.dp
                )
            }
        }
    }
}



@Composable
private fun IpSummary(record: UserSubdomainRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Language, contentDescription = null, tint = PocketColors.Primary)
            Text(
                text = record.hostname,
                color = PocketColors.Primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private suspend fun saveSubdomain(
    firestore: FirebaseFirestore,
    ownerId: String,
    newSubdomain: String,
    newRegion: String,
    oldSubdomain: String?
) = withContext(Dispatchers.IO) {
    if (oldSubdomain != null && oldSubdomain != newSubdomain) {
        firestore.collection("subdomains").document(oldSubdomain).delete().await()
    }

    firestore.runTransaction { transaction ->
        val docRef = firestore.collection("subdomains").document(newSubdomain)
        val existing = transaction.get(docRef)

        if (existing.exists()) {
            val existingOwner = existing.getString("ownerId")
            if (existingOwner != ownerId) {
                throw IllegalStateException("That IP is already taken.")
            }
        }

        transaction.set(
            docRef,
            mapOf(
                "serverId" to ownerId,
                "ownerId" to ownerId,
                "createdAt" to (existing.getTimestamp("createdAt") ?: Timestamp.now()),
                "region" to newRegion
            )
        )
    }.await()
}
