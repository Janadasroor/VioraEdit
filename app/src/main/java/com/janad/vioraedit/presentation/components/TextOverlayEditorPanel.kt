package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.janad.vioraedit.data.models.TextOverlay
import com.janad.vioraedit.data.models.AppFont
import java.util.UUID

/**
 * Panel for adding and editing text overlays
 */
import com.janad.vioraedit.data.models.OverlayAnimation

// ... imports

@Composable
fun TextOverlayEditorPanel(
    selectedOverlay: TextOverlay?,
    videoDurationMs: Long,
    onUpdateOverlay: (TextOverlay) -> Unit,
    onAddOverlay: (TextOverlay) -> Unit,
    onDeleteOverlay: () -> Unit,
    onTextToSpeech: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    
    // ... (keep existing state logic)
    LaunchedEffect(selectedOverlay) {
        if (selectedOverlay != null) {
            inputText = selectedOverlay.text
        } else {
            inputText = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ... (keep Header)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedOverlay != null) "Edit Text" else "Add Text",
                style = MaterialTheme.typography.titleMedium
            )
            
            if (selectedOverlay != null) {
                // Read Aloud Button
                IconButton(onClick = { onTextToSpeech(selectedOverlay.text) }) {
                    Icon(
                        androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Read Aloud",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDeleteOverlay) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Text",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Text Input
        OutlinedTextField(
            value = inputText,
            onValueChange = { 
                inputText = it
                selectedOverlay?.let { overlay ->
                    onUpdateOverlay(overlay.copy(text = it))
                }
            },
            label = { Text("Caption Text") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        // Only show add button if no overlay is selected
        if (selectedOverlay == null) {
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onAddOverlay(
                            TextOverlay(
                                id = UUID.randomUUID().toString(),
                                text = inputText,
                                position = androidx.compose.ui.geometry.Offset(200f, 400f),
                                fontSize = 60f,
                                color = Color.White,
                                startTimeMs = 0,
                                endTimeMs = videoDurationMs
                            )
                        )
                        inputText = ""
                    }
                },
                modifier = Modifier.align(Alignment.End),
                enabled = inputText.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Text")
            }
        } else {
            // Edit Controls - TABS
            var currentTab by remember { mutableStateOf(0) } // 0: Style, 1: Font, 2: Anim, 3: Timing
            
            ScrollableTabRow(selectedTabIndex = currentTab, edgePadding = 0.dp) {
                Tab(selected = currentTab == 0, onClick = { currentTab = 0 }, text = { Text("Color") })
                Tab(selected = currentTab == 1, onClick = { currentTab = 1 }, text = { Text("Font") })
                Tab(selected = currentTab == 2, onClick = { currentTab = 2 }, text = { Text("Anim") })
                Tab(selected = currentTab == 3, onClick = { currentTab = 3 }, text = { Text("Timing") })
            }
            
            Spacer(Modifier.height(8.dp))
            
            when (currentTab) {
                0 -> {
                    // STYLE TAB (Keep existing content)
                    Text("Color", style = MaterialTheme.typography.bodyMedium)
                    val colors = listOf(
                        Color.White, Color.Black, Color.Red, Color.Blue, 
                        Color.Green, Color.Yellow, Color.Magenta, Color.Cyan
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(colors) { color ->
                            ColorCircle(color = color, isSelected = selectedOverlay.color == color, onClick = { onUpdateOverlay(selectedOverlay.copy(color = color)) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Size: ${selectedOverlay.fontSize.toInt()}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = selectedOverlay.fontSize,
                        onValueChange = { size -> onUpdateOverlay(selectedOverlay.copy(fontSize = size)) },
                        valueRange = 20f..200f
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Background", modifier = Modifier.weight(1f))
                        Switch(
                            checked = selectedOverlay.backgroundColor != null,
                            onCheckedChange = { checked ->
                                val newBg = if (checked) Color.Black.copy(alpha = 0.5f) else null
                                onUpdateOverlay(selectedOverlay.copy(backgroundColor = newBg))
                            }
                        )
                    }
                }
                1 -> {
                    // FONT TAB (Keep existing content)
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val fontRepo = remember { com.janad.vioraedit.data.models.FontRepository(context) }
                    val fonts = fontRepo.getAvailableFonts()
                    LazyColumn(modifier = Modifier.height(200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fonts) { font ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onUpdateOverlay(selectedOverlay.copy(fontFamily = font.name)) },
                                colors = CardDefaults.cardColors(containerColor = if (selectedOverlay.fontFamily == font.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            ) {
                                Text(text = font.name, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                2 -> {
                    // ANIMATION TAB (Keep existing content)
                    Text("Entrance Animation", style = MaterialTheme.typography.bodyMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(OverlayAnimation.values()) { anim ->
                            FilterChip(
                                selected = selectedOverlay.animation == anim,
                                onClick = { onUpdateOverlay(selectedOverlay.copy(animation = anim)) },
                                label = { Text(text = anim.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }) }
                            )
                        }
                    }
                    if (selectedOverlay.animation != OverlayAnimation.NONE) {
                        Text("Duration: ${selectedOverlay.animationDurationMs}ms", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = selectedOverlay.animationDurationMs.toFloat(),
                            onValueChange = { onUpdateOverlay(selectedOverlay.copy(animationDurationMs = it.toLong())) },
                            valueRange = 100f..2000f, steps = 19
                        )
                    }
                }
                3 -> {
                    // TIMING TAB
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Visible Duration", style = MaterialTheme.typography.bodyMedium)
                        
                        var range by remember(selectedOverlay) { 
                            mutableStateOf(selectedOverlay.startTimeMs.toFloat()..selectedOverlay.endTimeMs.toFloat()) 
                        }
                        
                        RangeSlider(
                            value = range,
                            onValueChange = { 
                                range = it
                                // Real-time update might be heavy, consider onValueChangeFinished
                                onUpdateOverlay(selectedOverlay.copy(
                                    startTimeMs = it.start.toLong(),
                                    endTimeMs = it.endInclusive.toLong()
                                ))
                            },
                            valueRange = 0f..videoDurationMs.toFloat(),
                            steps = 0
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(range.start.toLong()))
                            Text(formatTime(range.endInclusive.toLong()))
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}


@Composable
fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}
