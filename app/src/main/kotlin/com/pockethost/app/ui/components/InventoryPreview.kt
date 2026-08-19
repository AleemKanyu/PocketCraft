package com.pockethost.app.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pockethost.app.service.InventoryItem
import com.pockethost.app.R

@Composable
fun InventorySlotCell(
    item: InventoryItem?,
    slotSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Box(
        modifier = modifier
            .size(slotSize)
            .background(
                color = Color(0xFF2A2A2A),
                shape = RoundedCornerShape(4.dp)
            )
            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (item != null && item.id.isNotBlank()) {
            // Strip "minecraft:" prefix
            val itemId = item.id.removePrefix("minecraft:")
            val iconUrl = "https://mc-heads.net/item/$itemId"
            
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(iconUrl)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_unknown_item)
                    .error(R.drawable.ic_unknown_item)
                    .build(),
                contentDescription = item.id,
                modifier = Modifier.size(iconSize),
                contentScale = ContentScale.Fit
            )
            
            if (item.count > 1) {
                Text(
                    text = item.count.toString(),
                    fontSize = (slotSize.value * 0.25f).sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun InventoryRow(
    startSlot: Int,
    count: Int,
    slotMap: Map<Int, InventoryItem>,
    slotSize: Dp,
    iconSize: Dp,
    gap: Dp
) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (i in 0 until count) {
            val slot = startSlot + i
            InventorySlotCell(item = slotMap[slot], slotSize = slotSize, iconSize = iconSize)
        }
    }
}

@Composable
fun InventorySection(items: List<InventoryItem>, modifier: Modifier = Modifier) {
    val slotMap = items.associateBy { it.slot }

    PocketHostCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Inventory",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (items.isEmpty()) {
                    Text(
                        text = "Empty",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gapDp = 4.dp
                val gapPx = with(LocalDensity.current) { gapDp.toPx() }
                
                // Horizontal slots: 1 (Armor) + 9 (Main) + 1 (Offhand) = 11 slots
                // Total gaps: 10 gaps between 11 slots
                val totalGapsPx = 10 * gapPx
                val slotSizePx = (constraints.maxWidth.toFloat() - totalGapsPx) / 11f
                val slotSizeDp = with(LocalDensity.current) { slotSizePx.toDp() }
                val iconSizeDp = slotSizeDp * 0.7f

                Row(
                    horizontalArrangement = Arrangement.spacedBy(gapDp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Armor column (left)
                    Column(verticalArrangement = Arrangement.spacedBy(gapDp)) {
                        InventorySlotCell(item = slotMap[103], slotSize = slotSizeDp, iconSize = iconSizeDp) // Helmet
                        InventorySlotCell(item = slotMap[102], slotSize = slotSizeDp, iconSize = iconSizeDp) // Chestplate
                        InventorySlotCell(item = slotMap[101], slotSize = slotSizeDp, iconSize = iconSizeDp) // Leggings
                        InventorySlotCell(item = slotMap[100], slotSize = slotSizeDp, iconSize = iconSizeDp) // Boots
                    }

                    // Main inventory + hotbar (middle)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(gapDp),
                        modifier = Modifier.weight(9f)
                    ) {
                        // Main inventory (3 rows × 9)
                        for (row in 0..2) {
                            InventoryRow(
                                startSlot = 9 + (row * 9),
                                count = 9,
                                slotMap = slotMap,
                                slotSize = slotSizeDp,
                                iconSize = iconSizeDp,
                                gap = gapDp
                            )
                        }
                        
                        Spacer(Modifier.height(gapDp))
                        
                        // Hotbar (1 row × 9)
                        InventoryRow(
                            startSlot = 0,
                            count = 9,
                            slotMap = slotMap,
                            slotSize = slotSizeDp,
                            iconSize = iconSizeDp,
                            gap = gapDp
                        )
                    }

                    // Offhand (right)
                    Column(modifier = Modifier.weight(1f)) {
                        InventorySlotCell(item = slotMap[-106], slotSize = slotSizeDp, iconSize = iconSizeDp) // Offhand
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryPreview(items: List<InventoryItem>, modifier: Modifier = Modifier) {
    InventorySection(items = items, modifier = modifier)
}
