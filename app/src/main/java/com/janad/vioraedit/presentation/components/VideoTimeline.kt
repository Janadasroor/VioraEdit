// File: presentation/components/VideoTimeline.kt
package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive timeline for video trimming
 */
@Composable
fun VideoTimeline(
    videoDurationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    onTrimChanged: (startMs: Long, endMs: Long) -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    thumbnails: List<String> = emptyList() // URLs to video frame thumbnails
) {
    val density = LocalDensity.current
    var trackWidth by remember { mutableStateOf(0f) }
    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }
    var isDraggingPlayhead by remember { mutableStateOf(false) }
    
    val handleWidth = with(density) { 12.dp.toPx() }
    val playheadWidth = with(density) { 2.dp.toPx() }
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        trackWidth = constraints.maxWidth.toFloat()
        
        // Calculate positions
        val trimStartX = (trimStartMs.toFloat() / videoDurationMs.toFloat()) * trackWidth
        val trimEndX = (trimEndMs.toFloat() / videoDurationMs.toFloat()) * trackWidth
        val playheadX = (currentPositionMs.toFloat() / videoDurationMs.toFloat()) * trackWidth
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            when {
                                offset.x in (trimStartX - handleWidth)..(trimStartX + handleWidth) -> {
                                    isDraggingStart = true
                                }
                                offset.x in (trimEndX - handleWidth)..(trimEndX + handleWidth) -> {
                                    isDraggingEnd = true
                                }
                                offset.x in (playheadX - 10)..(playheadX + 10) -> {
                                    isDraggingPlayhead = true
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            
                            when {
                                isDraggingStart -> {
                                    val newStartX = (trimStartX + dragAmount.x).coerceIn(0f, trimEndX - handleWidth)
                                    val newStartMs = ((newStartX / trackWidth) * videoDurationMs).toLong()
                                    onTrimChanged(newStartMs, trimEndMs)
                                }
                                isDraggingEnd -> {
                                    val newEndX = (trimEndX + dragAmount.x).coerceIn(trimStartX + handleWidth, trackWidth)
                                    val newEndMs = ((newEndX / trackWidth) * videoDurationMs).toLong()
                                    onTrimChanged(trimStartMs, newEndMs)
                                }
                                isDraggingPlayhead -> {
                                    val newPlayheadX = (playheadX + dragAmount.x).coerceIn(0f, trackWidth)
                                    val newPositionMs = ((newPlayheadX / trackWidth) * videoDurationMs).toLong()
                                    onSeek(newPositionMs)
                                }
                            }
                        },
                        onDragEnd = {
                            isDraggingStart = false
                            isDraggingEnd = false
                            isDraggingPlayhead = false
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newPositionMs = ((offset.x / trackWidth) * videoDurationMs).toLong()
                        onSeek(newPositionMs)
                    }
                }
        ) {
            val canvasHeight = size.height
            
            // Draw background track
            drawRect(
                color = Color.Gray.copy(alpha = 0.3f),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, canvasHeight)
            )
            
            // Draw selected region
            drawRect(
                color = Color(0xFF4CAF50).copy(alpha = 0.3f),
                topLeft = Offset(trimStartX, 0f),
                size = Size(trimEndX - trimStartX, canvasHeight)
            )
            
            // Draw trim handles
            // Start handle
            drawRect(
                color = Color.White,
                topLeft = Offset(trimStartX - handleWidth / 2, 0f),
                size = Size(handleWidth, canvasHeight)
            )
            drawRect(
                color = Color(0xFF4CAF50),
                topLeft = Offset(trimStartX - handleWidth / 2 + 2, 2f),
                size = Size(handleWidth - 4, canvasHeight - 4)
            )
            
            // End handle
            drawRect(
                color = Color.White,
                topLeft = Offset(trimEndX - handleWidth / 2, 0f),
                size = Size(handleWidth, canvasHeight)
            )
            drawRect(
                color = Color(0xFF4CAF50),
                topLeft = Offset(trimEndX - handleWidth / 2 + 2, 2f),
                size = Size(handleWidth - 4, canvasHeight - 4)
            )
            
            // Draw border around selected region
            drawRect(
                color = Color(0xFF4CAF50),
                topLeft = Offset(trimStartX, 0f),
                size = Size(trimEndX - trimStartX, canvasHeight),
                style = Stroke(width = 3f)
            )
            
            // Draw playhead
            drawLine(
                color = Color.Red,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, canvasHeight),
                strokeWidth = playheadWidth
            )
            
            // Draw playhead circle at top
            drawCircle(
                color = Color.Red,
                radius = 8f,
                center = Offset(playheadX, 0f)
            )
            
            // Draw time markers
            val markerInterval = videoDurationMs / 10
            for (i in 0..10) {
                val markerMs = i * markerInterval
                val markerX = (markerMs.toFloat() / videoDurationMs.toFloat()) * size.width
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(markerX, canvasHeight - 20f),
                    end = Offset(markerX, canvasHeight),
                    strokeWidth = 1f
                )
            }
        }
        
        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTimeMs(trimStartMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
            Text(
                text = formatTimeMs(trimEndMs - trimStartMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
            Text(
                text = formatTimeMs(trimEndMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

/**
 * Frame-by-frame timeline with thumbnail preview
 */
@Composable
fun FrameTimeline(
    frames: List<String>, // List of frame thumbnail URLs/paths
    videoDurationMs: Long,
    currentPositionMs: Long,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var trackWidth by remember { mutableStateOf(0f) }
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newPositionMs = ((offset.x / trackWidth) * videoDurationMs).toLong()
                    onSeek(newPositionMs)
                }
            }
    ) {
        trackWidth = constraints.maxWidth.toFloat()
        
        // Here you would display frame thumbnails in a LazyRow
        // For now, just show a simplified version
        Box(modifier = Modifier.fillMaxSize()) {
            val playheadX = (currentPositionMs.toFloat() / videoDurationMs.toFloat()) * trackWidth
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw playhead indicator
                drawLine(
                    color = Color.Red,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 2f
                )
            }
        }
    }
}

private fun formatTimeMs(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val ms = (milliseconds % 1000) / 10
    return String.format("%02d:%02d.%02d", minutes, seconds, ms)
}