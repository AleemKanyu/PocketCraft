package com.pocketcraft.server.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.pocketcraft.server.ui.theme.PocketColors

fun duoTextFieldShape() = RoundedCornerShape(16.dp)

@Composable
fun duoTextFieldColors(): TextFieldColors {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val hintColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    return TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        focusedIndicatorColor = PocketColors.Primary,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = PocketColors.Primary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedPlaceholderColor = hintColor,
        unfocusedPlaceholderColor = hintColor,
        disabledPlaceholderColor = hintColor.copy(alpha = 0.66f),
        focusedLeadingIconColor = hintColor,
        unfocusedLeadingIconColor = hintColor,
        disabledLeadingIconColor = hintColor.copy(alpha = 0.66f),
        focusedTrailingIconColor = hintColor,
        unfocusedTrailingIconColor = hintColor,
        disabledTrailingIconColor = hintColor.copy(alpha = 0.66f)
    )
}

@Composable
fun duoOutlinedTextFieldColors(): TextFieldColors {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val hintColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        focusedBorderColor = PocketColors.Primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
        cursorColor = PocketColors.Primary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedPlaceholderColor = hintColor,
        unfocusedPlaceholderColor = hintColor,
        disabledPlaceholderColor = hintColor.copy(alpha = 0.66f),
        focusedLeadingIconColor = hintColor,
        unfocusedLeadingIconColor = hintColor,
        disabledLeadingIconColor = hintColor.copy(alpha = 0.66f),
        focusedTrailingIconColor = hintColor,
        unfocusedTrailingIconColor = hintColor,
        disabledTrailingIconColor = hintColor.copy(alpha = 0.66f)
    )
}
