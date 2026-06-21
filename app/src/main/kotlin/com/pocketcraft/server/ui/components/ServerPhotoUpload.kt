package com.pocketcraft.server.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.pocketcraft.server.ui.theme.PocketColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ServerPhotoUpload(
    photoUri: Uri?,
    onPhotoSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedUriForCrop by remember { mutableStateOf<Uri?>(null) }
    var showPhotoOptions by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedUriForCrop = it
        }
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
                .clickable {
                    if (photoUri != null) {
                        showPhotoOptions = true
                    } else {
                        imagePickerLauncher.launch(arrayOf("image/*"))
                    }
                }
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Server photo",
                    modifier = Modifier.fillMaxSize(),
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

    // Photo options dialog if current photo exists
    if (showPhotoOptions && photoUri != null) {
        Dialog(
            onDismissRequest = { showPhotoOptions = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Edit Server Photo",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DuoButton(
                        text = "Crop Current Photo",
                        onClick = {
                            showPhotoOptions = false
                            selectedUriForCrop = photoUri
                        },
                        variant = DuoButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DuoButton(
                        text = "Change Photo",
                        onClick = {
                            showPhotoOptions = false
                            imagePickerLauncher.launch(arrayOf("image/*"))
                        },
                        variant = DuoButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DuoButton(
                        text = "Cancel",
                        onClick = { showPhotoOptions = false },
                        variant = DuoButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Image Crop Dialog
    selectedUriForCrop?.let { uri ->
        ImageCropDialog(
            photoUri = uri,
            onDismiss = { selectedUriForCrop = null },
            onCropped = { croppedUri ->
                selectedUriForCrop = null
                onPhotoSelected(croppedUri)
            }
        )
    }
}

@Composable
fun ImageCropDialog(
    photoUri: Uri,
    onDismiss: () -> Unit,
    onCropped: (Uri) -> Unit
) {
    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCropping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(photoUri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(photoUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                originalBitmap = bitmap
            } catch (e: Exception) {
                Log.e("ImageCropDialog", "Failed to load bitmap", e)
            } finally {
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PocketColors.Primary)
            }
        }
    } else {
        val bitmap = originalBitmap
        if (bitmap == null) {
            LaunchedEffect(Unit) {
                onDismiss()
            }
            return
        }

        val bmW = bitmap.width.toFloat()
        val bmH = bitmap.height.toFloat()

        var zoom by remember { mutableFloatStateOf(1.0f) }
        
        val cropSize = minOf(bmW, bmH) / zoom
        val x0 = (bmW - cropSize) / 2f
        val y0 = (bmH - cropSize) / 2f
        
        var panX by remember { mutableFloatStateOf(0.0f) }
        var panY by remember { mutableFloatStateOf(0.0f) }
        
        val maxPanX = x0
        val maxPanY = y0
        
        LaunchedEffect(zoom) {
            panX = panX.coerceIn(-maxPanX, maxPanX)
            panY = panY.coerceIn(-maxPanY, maxPanY)
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Crop Server Picture",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val containerSizeDp = 260.dp
                    val density = LocalDensity.current
                    val containerSizePx = with(density) { containerSizeDp.toPx() }
                    
                    val scale = if (bmW > bmH) containerSizePx / bmH else containerSizePx / bmW
                    
                    Box(
                        modifier = Modifier
                            .size(containerSizeDp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black)
                            .border(2.dp, PocketColors.Primary.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            .clipToBounds()
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(
                                    width = with(density) { (bmW * scale).toDp() },
                                    height = with(density) { (bmH * scale).toDp() }
                                )
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = panX * scale
                                    translationY = panY * scale
                                }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Zoom: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(60.dp), color = MaterialTheme.colorScheme.onSurface)
                            Slider(
                                value = zoom,
                                onValueChange = { zoom = it },
                                valueRange = 1.0f..3.0f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = PocketColors.Primary,
                                    activeTrackColor = PocketColors.Primary
                                )
                            )
                            Text(text = "${"%.1f".format(zoom)}x", fontSize = 12.sp, modifier = Modifier.width(36.dp), color = MaterialTheme.colorScheme.onSurface)
                        }

                        if (maxPanX > 0f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Pan X: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(60.dp), color = MaterialTheme.colorScheme.onSurface)
                                Slider(
                                    value = panX,
                                    onValueChange = { panX = it },
                                    valueRange = -maxPanX..maxPanX,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = PocketColors.Primary,
                                        activeTrackColor = PocketColors.Primary
                                    )
                                )
                            }
                        }

                        if (maxPanY > 0f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Pan Y: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(60.dp), color = MaterialTheme.colorScheme.onSurface)
                                Slider(
                                    value = panY,
                                    onValueChange = { panY = it },
                                    valueRange = -maxPanY..maxPanY,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = PocketColors.Primary,
                                        activeTrackColor = PocketColors.Primary
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DuoButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            variant = DuoButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                            enabled = !isCropping
                        )
                        DuoButton(
                            text = if (isCropping) "Saving..." else "Crop",
                            onClick = {
                                isCropping = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val cropX = (x0 - panX).toInt().coerceIn(0, (bmW - cropSize).toInt())
                                        val cropY = (y0 - panY).toInt().coerceIn(0, (bmH - cropSize).toInt())
                                        val size = cropSize.toInt().coerceAtMost(bitmap.width - cropX).coerceAtMost(bitmap.height - cropY)
                                        
                                        val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, size, size)
                                        
                                        val cacheFile = File(context.cacheDir, "cropped_server_icon_${System.currentTimeMillis()}.png")
                                        cacheFile.outputStream().use { out ->
                                            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                        }
                                        val outputUri = Uri.fromFile(cacheFile)
                                        withContext(Dispatchers.Main) {
                                            onCropped(outputUri)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ImageCropDialog", "Failed to crop image", e)
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            isCropping = false
                                        }
                                    }
                                }
                            },
                            variant = DuoButtonVariant.Primary,
                            modifier = Modifier.weight(1f),
                            enabled = !isCropping
                        )
                    }
                }
            }
        }
    }
}
