// File: presentation/components/AudioEditorPanel.kt
package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janad.vioraedit.data.models.AudioTrack
import java.util.UUID

/**
 * Audio editor panel for managing background music and audio tracks
 */
@Composable
fun AudioEditorPanel(
    audioTracks: List<AudioTrack>,
    videoDurationMs: Long,
    onAddAudioTrack: (AudioTrack) -> Unit,
    onRemoveAudioTrack: (String) -> Unit,
    onUpdateVolume: (String, Float) -> Unit,
    onUpdateFade: (String, Long, Long) -> Unit,
    onUpdateTiming: (String, Long, Long, Long) -> Unit, // id, start, sourceStart, duration
    onRecordVoiceover: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showVoiceoverDialog by remember { mutableStateOf(false) }

    if (showVoiceoverDialog) {
        VoiceoverRecorderDialog(
            onDismiss = { showVoiceoverDialog = false },
            onRecordingCompleted = { path ->
                onRecordVoiceover(path)
                showVoiceoverDialog = false
            }
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val audioPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            onAddAudioTrack(
                AudioTrack(
                    id = UUID.randomUUID().toString(),
                    uri = it.toString(),
                    startTimeMs = 0L,
                    endTimeMs = videoDurationMs, // Default to full duration
                    volume = 0.5f // Default volume
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Audio Tracks",
                style = MaterialTheme.typography.titleMedium
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Record Button
                Button(
                    onClick = { showVoiceoverDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Voiceover")
                }

                Button(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Music")
                }
            }
        }
        
        if (audioTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No audio tracks added",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(audioTracks) { track ->
                    AudioTrackItem(
                        track = track,
                        videoDurationMs = videoDurationMs,
                        onRemove = { onRemoveAudioTrack(track.id) },
                        onVolumeChange = { volume -> onUpdateVolume(track.id, volume) },
                        onFadeChange = { fadeIn, fadeOut -> onUpdateFade(track.id, fadeIn, fadeOut) },
                        onTimingChange = { start, sourceStart, duration -> onUpdateTiming(track.id, start, sourceStart, duration) }
                    )
                }
            }
        }
    }
}

@Composable
fun AudioTrackItem(
    track: AudioTrack,
    videoDurationMs: Long,
    onRemove: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onFadeChange: (Long, Long) -> Unit,
    onTimingChange: (Long, Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(track.volume) }
    var fadeInMs by remember { mutableStateOf(track.fadeInMs) }
    var fadeOutMs by remember { mutableStateOf(track.fadeOutMs) }
    
    // Timing state
    var startTimeMs by remember { mutableStateOf(track.startTimeMs) }
    var sourceStartMs by remember { mutableStateOf(track.sourceStartMs) }
    var durationMs by remember { mutableStateOf(track.endTimeMs - track.startTimeMs) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (track.isOriginalAudio) Icons.Default.Mic else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Column {
                        Text(
                            text = if (track.isOriginalAudio) "Original Audio" else "Track ${track.id.takeLast(4)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Text(
                            text = "${formatTime(startTimeMs)} - ${formatTime(startTimeMs + durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                    
                    if (!track.isOriginalAudio) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            // Expanded controls
            if (expanded) {
                Divider()
                
                if (!track.isOriginalAudio) {
                    Text("Timing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    
                    // Start Position on Timeline
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Start Time", style = MaterialTheme.typography.bodySmall)
                            Text(formatTime(startTimeMs), style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = startTimeMs.toFloat(),
                            onValueChange = { 
                                startTimeMs = it.toLong()
                                onTimingChange(startTimeMs, sourceStartMs, durationMs)
                            },
                            valueRange = 0f..videoDurationMs.toFloat()
                        )
                    }
                    
                    // Source Trim (Start offset in the file)
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Trim Start (Skip Intro)", style = MaterialTheme.typography.bodySmall)
                            Text(formatTime(sourceStartMs), style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = sourceStartMs.toFloat(),
                            onValueChange = { 
                                sourceStartMs = it.toLong()
                                onTimingChange(startTimeMs, sourceStartMs, durationMs)
                            },
                            valueRange = 0f..60000f // Assume max 60s offset for UI simplicity
                        )
                    }
                    
                    Divider()
                }
                
                // Volume control
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Volume",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${(volume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            onVolumeChange(it)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Fade controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Fade In
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fade In: ${fadeInMs / 1000.0}s",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = fadeInMs.toFloat(),
                            onValueChange = {
                                fadeInMs = it.toLong()
                                onFadeChange(fadeInMs, fadeOutMs)
                            },
                            valueRange = 0f..3000f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Fade Out
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fade Out: ${fadeOutMs / 1000.0}s",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = fadeOutMs.toFloat(),
                            onValueChange = {
                                fadeOutMs = it.toLong()
                                onFadeChange(fadeInMs, fadeOutMs)
                            },
                            valueRange = 0f..3000f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Audio waveform visualization (simplified)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Waveform",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Audio mixing controls for original video audio
 */
@Composable
fun OriginalAudioControls(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onMuteToggle) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Original Audio",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Slider(
                        value = if (isMuted) 0f else volume,
                        onValueChange = onVolumeChange,
                        enabled = !isMuted,
                        valueRange = 0f..1f
                    )
                }
                
                Text(
                    text = if (isMuted) "Muted" else "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
fun VoiceoverRecorderDialog(
    onDismiss: () -> Unit,
    onRecordingCompleted: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recorder = remember { com.janad.vioraedit.domain.AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }
    
    // Timer effect
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                recordingDuration = System.currentTimeMillis() - startTime
                kotlinx.coroutines.delay(100)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { 
            if (isRecording) {
                recorder.cancelRecording()
            }
            onDismiss()
        },
        title = { Text("Record Voiceover") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = formatTime(recordingDuration),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                
                if (isRecording) {
                    Text("Recording...", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Tap mic to start")
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                FloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            val path = recorder.stopRecording()
                            isRecording = false
                            if (path != null) {
                                onRecordingCompleted(path)
                            }
                        } else {
                            recorder.startRecording()
                            isRecording = true
                        }
                    },
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop" else "Record",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        dismissButton = {
             TextButton(onClick = {
                 if (isRecording) recorder.cancelRecording()
                 onDismiss() 
             }) {
                 Text("Cancel")
             }
        }
    )
}