package com.pocketcraft.server.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.BuildConfig
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.components.GameCard

@Composable
fun LegalCenterScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Legal Center",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "Review privacy, terms, and policy disclosures before enabling optional data processing.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Policy version: ${BuildConfig.LEGAL_POLICY_VERSION}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Documents",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    DuoButton(
                        text = "OPEN PRIVACY POLICY",
                        onClick = {
                            if (!openExternalUrl(context, BuildConfig.PRIVACY_POLICY_URL)) {
                                onMessage("Could not open Privacy Policy URL.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DuoButton(
                        text = "OPEN TERMS OF USE",
                        onClick = {
                            if (!openExternalUrl(context, BuildConfig.TERMS_OF_USE_URL)) {
                                onMessage("Could not open Terms of Use URL.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            DuoButton(
                text = "BACK TO SETTINGS",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
    }
}

private fun openExternalUrl(context: android.content.Context, url: String): Boolean {
    if (url.isBlank()) return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
