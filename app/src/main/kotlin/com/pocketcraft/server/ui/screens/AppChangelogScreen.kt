package com.pocketcraft.server.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcraft.server.ui.components.DuoButton
import com.pocketcraft.server.ui.theme.PocketColors
import com.pocketcraft.server.ui.theme.pocketPopupAccentContainerColor
import com.pocketcraft.server.ui.theme.pocketPopupAccentTintColor
import com.pocketcraft.server.update.GitHubUpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppChangelogScreen(
    releaseInfo: GitHubUpdateChecker.ReleaseInfo,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentContainerColor = pocketPopupAccentContainerColor()
    val accentTintColor = pocketPopupAccentTintColor()

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accentContainerColor,
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Update,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = accentTintColor
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "WHAT'S NEW",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PocketColors.Primary,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Updated to ${releaseInfo.tagName}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp
                )
                Text(
                    text = releaseInfo.releaseName.ifBlank { releaseInfo.tagName },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = releaseInfo.body,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            DuoButton(
                text = "GOT IT",
                icon = null,
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
