package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janad.vioraedit.data.models.OverlayAnimation
import com.janad.vioraedit.data.models.StickerOverlay

@Composable
fun StickerEditorPanel(
    selectedSticker: StickerOverlay,
    onUpdateSticker: (StickerOverlay) -> Unit,
    onDeleteSticker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Edit Sticker",
                style = MaterialTheme.typography.titleMedium
            )
            
            IconButton(onClick = onDeleteSticker) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Sticker",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        // Scale Control
        Text("Scale: ${(selectedSticker.scale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = selectedSticker.scale,
            onValueChange = { onUpdateSticker(selectedSticker.copy(scale = it)) },
            valueRange = 0.1f..3.0f
        )

        // Animation Control
        Text("Entrance Animation", style = MaterialTheme.typography.bodyMedium)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(OverlayAnimation.values()) { anim ->
                FilterChip(
                    selected = selectedSticker.animation == anim,
                    onClick = { onUpdateSticker(selectedSticker.copy(animation = anim)) },
                    label = { 
                        Text(
                            text = anim.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                        ) 
                    }
                )
            }
        }

        if (selectedSticker.animation != OverlayAnimation.NONE) {
            Text("Duration: ${selectedSticker.animationDurationMs}ms", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = selectedSticker.animationDurationMs.toFloat(),
                onValueChange = { 
                    onUpdateSticker(selectedSticker.copy(animationDurationMs = it.toLong())) 
                },
                valueRange = 100f..2000f,
                steps = 19
            )
        }
    }
}
