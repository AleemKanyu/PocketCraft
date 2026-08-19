package com.pockethost.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.pockethost.app.ui.theme.PocketColors

@Composable
fun ServerDescriptionField(
    description: String,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Server Description",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        TextField(
            value = description,
            onValueChange = { newValue ->
                if (newValue.length <= 500) {
                    onDescriptionChange(newValue)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .border(2.dp, PocketColors.Primary.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
            placeholder = {
                Text(
                    text = "Tell players what your server is about...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PocketColors.PrimaryMuted.copy(alpha = 0.35f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = PocketColors.PrimaryLight,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = PocketColors.PrimaryLight
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal
            )
        )

        Text(
            text = "${description.length}/500 characters",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
