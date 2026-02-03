package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.janad.vioraedit.data.models.CanvasConfig
import com.janad.vioraedit.data.models.CanvasMode

@Composable
fun CanvasPanel(
    config: CanvasConfig,
    onConfigChanged: (CanvasConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Background & Fit",
            style = MaterialTheme.typography.titleMedium
        )

        // Mode Selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CanvasModeOption(
                icon = Icons.Default.Crop,
                label = "Fill",
                isSelected = config.mode == CanvasMode.CROP,
                onClick = { onConfigChanged(config.copy(mode = CanvasMode.CROP)) }
            )
            CanvasModeOption(
                icon = Icons.Default.BlurOn,
                label = "Blur",
                isSelected = config.mode == CanvasMode.FIT_BLUR,
                onClick = { onConfigChanged(config.copy(mode = CanvasMode.FIT_BLUR)) }
            )
            CanvasModeOption(
                icon = Icons.Default.FormatPaint,
                label = "Color",
                isSelected = config.mode == CanvasMode.FIT_COLOR,
                onClick = { onConfigChanged(config.copy(mode = CanvasMode.FIT_COLOR)) }
            )
        }

        // Contextual Controls
        when (config.mode) {
            CanvasMode.FIT_BLUR -> {
                Text("Blur Intensity", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = config.blurRadius,
                    onValueChange = { onConfigChanged(config.copy(blurRadius = it)) },
                    valueRange = 5f..100f
                )
            }
            CanvasMode.FIT_COLOR -> {
                Text("Background Color", style = MaterialTheme.typography.bodySmall)
                val colors = listOf(
                    Color.Black, Color.White, Color.Red, 
                    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
                    Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF00BCD4),
                    Color(0xFF009688), Color(0xFF4CAF50), Color(0xFFFFEB3B),
                    Color(0xFFFF9800), Color(0xFFFF5722)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(colors) { color ->
                        ColorCircle(
                            color = color,
                            isSelected = config.color == color,
                            onClick = { onConfigChanged(config.copy(color = color)) }
                        )
                    }
                }
            }
            CanvasMode.CROP -> {
                Text(
                    text = "Video fills the screen. Use 'Ratio' tab to change aspect ratio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CanvasModeOption(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
}
