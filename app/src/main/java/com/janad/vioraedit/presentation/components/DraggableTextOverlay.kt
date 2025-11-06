// File: presentation/components/OverlayEditor.kt
package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janad.vioraedit.data.models.StickerOverlay
import com.janad.vioraedit.data.models.TextOverlay
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Draggable and resizable text overlay component
 */
@Composable
fun DraggableTextOverlay(
    overlay: TextOverlay,
    isSelected: Boolean,
    onPositionChanged: (Offset) -> Unit,
    onRotationChanged: (Float) -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offset by remember(overlay.position) { mutableStateOf(overlay.position) }
    var rotation by remember(overlay.rotation) { mutableStateOf(overlay.rotation) }
    
    Box(
        modifier = modifier
            .offset(offset.x.dp, offset.y.dp)
            .rotate(rotation)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSelect() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offset = Offset(
                            x = offset.x + dragAmount.x,
                            y = offset.y + dragAmount.y
                        )
                        onPositionChanged(offset)
                    }
                )
            }
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color.Blue, RoundedCornerShape(4.dp))
                } else Modifier
            )
    ) {
        Text(
            text = overlay.text,
            fontSize = overlay.fontSize.sp,
            color = overlay.color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    overlay.backgroundColor ?: Color.Transparent,
                    RoundedCornerShape(4.dp)
                )
                .padding(8.dp)
        )
        
        // Rotation handle when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .offset(12.dp, (-12).dp)
                    .background(Color.Blue, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val angle = atan2(dragAmount.y, dragAmount.x)
                            rotation += Math
                                .toDegrees(angle.toDouble())
                                .toFloat()
                            onRotationChanged(rotation)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "Rotate",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Draggable and resizable sticker overlay component
 */
@Composable
fun DraggableStickerOverlay(
    overlay: StickerOverlay,
    isSelected: Boolean,
    onPositionChanged: (Offset) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onRotationChanged: (Float) -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offset by remember(overlay.position) { mutableStateOf(overlay.position) }
    var scale by remember(overlay.scale) { mutableStateOf(overlay.scale) }
    var rotation by remember(overlay.rotation) { mutableStateOf(overlay.rotation) }
    
    Box(
        modifier = modifier
            .offset(offset.x.dp, offset.y.dp)
            .rotate(rotation)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSelect() })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offset = Offset(
                            x = offset.x + dragAmount.x,
                            y = offset.y + dragAmount.y
                        )
                        onPositionChanged(offset)
                    }
                )
            }
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color.Blue, RoundedCornerShape(4.dp))
                } else Modifier
            )
    ) {
        // Sticker image would be loaded here
        Box(
            modifier = Modifier
                .size((100 * scale).dp)
                .background(Color.LightGray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Sticker",
                modifier = Modifier.size((50 * scale).dp)
            )
        }
        
        // Scale handle when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .offset(12.dp, 12.dp)
                    .background(Color.Blue, CircleShape)
                    .pointerInput(Unit) {
                        var initialDistance = 0f
                        var initialScale = scale
                        
                        detectDragGestures(
                            onDragStart = {
                                initialDistance = 0f
                                initialScale = scale
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val distance = sqrt(
                                    dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                )
                                val newScale = (initialScale + distance / 100f).coerceIn(0.5f, 3f)
                                scale = newScale
                                onScaleChanged(newScale)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOutMap,
                    contentDescription = "Scale",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Text overlay editor panel
 */
@Composable
fun TextOverlayEditorPanel(
    selectedOverlay: TextOverlay?,
    onUpdateOverlay: (TextOverlay) -> Unit,
    onAddOverlay: (TextOverlay) -> Unit,
    onDeleteOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(selectedOverlay?.text ?: "") }
    var fontSize by remember { mutableStateOf(selectedOverlay?.fontSize ?: 24f) }
    var selectedColor by remember { mutableStateOf(selectedOverlay?.color ?: Color.White) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (selectedOverlay != null) "Edit Text" else "Add Text",
            style = MaterialTheme.typography.titleMedium
        )
        
        // Text input
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Text") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // Font size slider
        Column {
            Text(
                text = "Font Size: ${fontSize.toInt()}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                valueRange = 12f..72f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Color picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Text Color:", style = MaterialTheme.typography.bodyMedium)
            
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(predefinedColors) { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (color == selectedColor) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    selectedColor = color
                                }
                            }
                    )
                }
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedOverlay != null) {
                Button(
                    onClick = {
                        onUpdateOverlay(
                            selectedOverlay.copy(
                                text = text,
                                fontSize = fontSize,
                                color = selectedColor
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Update")
                }
                
                OutlinedButton(
                    onClick = onDeleteOverlay,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            } else {
                Button(
                    onClick = {
                        if (text.isNotEmpty()) {
                            onAddOverlay(
                                TextOverlay(
                                    id = "",
                                    text = text,
                                    position = Offset(100f, 100f),
                                    fontSize = fontSize,
                                    color = selectedColor
                                )
                            )
                            text = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = text.isNotEmpty()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Text")
                }
            }
        }
    }
}

/**
 * Sticker picker panel
 */
@Composable
fun StickerPickerPanel(
    onStickerSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Stickers",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Grid of stickers
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(20) { index ->
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                onStickerSelected("sticker_$index")
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Sticker $index",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}

private val predefinedColors = listOf(
    Color.White,
    Color.Black,
    Color.Red,
    Color(0xFFFF6B6B),
    Color(0xFFFFD93D),
    Color(0xFF6BCB77),
    Color(0xFF4D96FF),
    Color(0xFF9D4EDD),
    Color(0xFFFF006E),
    Color(0xFF8338EC)
)