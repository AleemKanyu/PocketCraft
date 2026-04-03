package com.pocketcraft.server.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pocketcraft.server.ui.theme.PocketColors

@Composable
fun ServerPhotoUpload(
    photoUri: Uri?,
    onPhotoSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPhotoSelected(it) }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Server Photo",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, PocketColors.Primary.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                .clickable { imagePickerLauncher.launch("image/*") }
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Server photo",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .background(PocketColors.PrimaryMuted, RoundedCornerShape(18.dp))
                            .border(2.dp, PocketColors.Primary.copy(alpha = 0.22f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Collections,
                            contentDescription = null,
                            tint = PocketColors.PrimaryDark
                        )
                    }
                    Text(
                        text = "Add a photo from your phone",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "It will show on the main server card.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
