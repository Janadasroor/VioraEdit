// File: presentation/VideoEditorScreen.kt
package com.janad.vioraedit.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janad.vioraedit.data.models.*
import com.janad.vioraedit.presentation.components.AudioEditorPanel
import com.janad.vioraedit.presentation.components.DraggableStickerOverlay
import com.janad.vioraedit.presentation.components.DraggableTextOverlay
import com.janad.vioraedit.presentation.components.FiltersPanel
import com.janad.vioraedit.presentation.components.StickerPickerPanel
import com.janad.vioraedit.presentation.components.TextOverlayEditorPanel
import com.janad.vioraedit.presentation.components.VideoPlayerComposable
import com.janad.vioraedit.presentation.components.VideoTimeline
import timber.log.Timber


/**
 * Main video editor screen with tabs for different editing features
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    videoUri: String,
    onClose: () -> Unit,
    onExportComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoEditorViewModel
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(EditorTab.TRIM) }
    var selectedTextOverlay by remember { mutableStateOf<TextOverlay?>(null) }
    var selectedStickerOverlay by remember { mutableStateOf<StickerOverlay?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showApplyConfirmDialog by remember { mutableStateOf(false) }

    // Track if changes have been made since last apply
    var hasUnappliedChanges by remember { mutableStateOf(false) }

    // Track video URI for refreshing player when it changes
    var currentVideoUri by remember { mutableStateOf(videoUri) }

    // Snackbar host state for showing messages
    val snackbarHostState = remember { SnackbarHostState() }

    // Load video on first composition
    LaunchedEffect(videoUri) {
        Timber.d("Loading video: $videoUri")
        viewModel.onIntent(VideoEditorIntent.LoadVideo(videoUri))
    }

    // Update video URI when it changes in state
    LaunchedEffect(uiState.editState.videoUri) {
        if (currentVideoUri != uiState.editState.videoUri) {
            currentVideoUri = uiState.editState.videoUri
            Timber.d("Video URI changed to: $currentVideoUri")
        }
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VideoEditorEvent.ExportCompleted -> {
                    onExportComplete(event.outputPath)
                    snackbarHostState.showSnackbar("Video exported successfully")
                }
                is VideoEditorEvent.ChangesApplied -> {
                    hasUnappliedChanges = false
                    snackbarHostState.showSnackbar("Changes applied successfully")
                }
                is VideoEditorEvent.UndoCompleted -> {
                    snackbarHostState.showSnackbar("Undo completed")
                }
                is VideoEditorEvent.RedoCompleted -> {
                    snackbarHostState.showSnackbar("Redo completed")
                }
                is VideoEditorEvent.Error -> {
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            VideoEditorTopBar(
                onClose = onClose,
                onExport = { showExportDialog = true },
                onApplyChanges = { showApplyConfirmDialog = true },
                onUndo = { viewModel.onIntent(VideoEditorIntent.Undo) },
                onRedo = { viewModel.onIntent(VideoEditorIntent.Redo) },
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                isProcessing = uiState.isProcessing,
                hasUnappliedChanges = hasUnappliedChanges
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                // Timeline
                VideoTimeline(
                    videoDurationMs = uiState.editState.videoDurationMs,
                    trimStartMs = uiState.editState.trimRange.startMs,
                    trimEndMs = uiState.editState.trimRange.endMs,
                    currentPositionMs = playbackPosition,
                    onTrimChanged = { start, end ->
                        viewModel.onIntent(VideoEditorIntent.UpdateTrimRange(start, end))
                        hasUnappliedChanges = true
                    },
                    onSeek = { position ->
                        viewModel.onIntent(VideoEditorIntent.UpdatePlaybackPosition(position))
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tab selector
                EditorTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Video preview with overlays
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Key the video player with current URI to force refresh
                key(currentVideoUri) {
                    VideoPlayerComposable(
                        videoUri = currentVideoUri,
                        startPosition = uiState.editState.trimRange.startMs,
                        endPosition = uiState.editState.trimRange.endMs,
                        onPositionChanged = { position ->
                            viewModel.onIntent(VideoEditorIntent.UpdatePlaybackPosition(position))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Render text overlays
                uiState.editState.textOverlays.forEach { overlay ->
                    if (playbackPosition in overlay.startTimeMs..overlay.endTimeMs) {
                        DraggableTextOverlay(
                            overlay = overlay,
                            isSelected = selectedTextOverlay?.id == overlay.id,
                            onPositionChanged = { position ->
                                viewModel.onIntent(
                                    VideoEditorIntent.UpdateTextOverlay(
                                        overlay.copy(position = position)
                                    )
                                )
                                hasUnappliedChanges = true
                            },
                            onRotationChanged = { rotation ->
                                viewModel.onIntent(
                                    VideoEditorIntent.UpdateTextOverlay(
                                        overlay.copy(rotation = rotation)
                                    )
                                )
                                hasUnappliedChanges = true
                            },
                            onSelect = { selectedTextOverlay = overlay }
                        )
                    }
                }

                // Render sticker overlays
                uiState.editState.stickerOverlays.forEach { overlay ->
                    if (playbackPosition in overlay.startTimeMs..overlay.endTimeMs) {
                        DraggableStickerOverlay(
                            overlay = overlay,
                            isSelected = selectedStickerOverlay?.id == overlay.id,
                            onPositionChanged = { position ->
                                viewModel.onIntent(
                                    VideoEditorIntent.UpdateStickerOverlay(
                                        overlay.copy(position = position)
                                    )
                                )
                                hasUnappliedChanges = true
                            },
                            onScaleChanged = { scale ->
                                viewModel.onIntent(
                                    VideoEditorIntent.UpdateStickerOverlay(
                                        overlay.copy(scale = scale)
                                    )
                                )
                                hasUnappliedChanges = true
                            },
                            onRotationChanged = { rotation ->
                                viewModel.onIntent(
                                    VideoEditorIntent.UpdateStickerOverlay(
                                        overlay.copy(rotation = rotation)
                                    )
                                )
                                hasUnappliedChanges = true
                            },
                            onSelect = { selectedStickerOverlay = overlay }
                        )
                    }
                }

                // Processing overlay
                if (uiState.isProcessing) {
                    ProcessingOverlay(
                        progress = uiState.processingProgress,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Editor panels based on selected tab
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                tonalElevation = 2.dp
            ) {
                when (selectedTab) {
                    EditorTab.TRIM -> {
                        TrimPanel(
                            aspectRatio = uiState.editState.cropAspectRatio,
                            onAspectRatioChanged = { ratio ->
                                viewModel.onIntent(VideoEditorIntent.UpdateCropRatio(ratio))
                                hasUnappliedChanges = true
                            }
                        )
                    }
                    EditorTab.FILTERS -> {
                        FiltersPanel(
                            appliedFilters = uiState.editState.filters,
                            onFilterAdded = { filter ->
                                viewModel.onIntent(VideoEditorIntent.AddFilter(filter))
                                hasUnappliedChanges = true
                            },
                            onFilterRemoved = { filter ->
                                viewModel.onIntent(VideoEditorIntent.RemoveFilter(filter))
                                hasUnappliedChanges = true
                            },
                            onFilterUpdated = { index, filter ->
                                viewModel.onIntent(VideoEditorIntent.UpdateFilter(index, filter))
                                hasUnappliedChanges = true
                            }
                        )
                    }
                    EditorTab.AUDIO -> {
                        AudioEditorPanel(
                            audioTracks = uiState.editState.audioTracks,
                            videoDurationMs = uiState.editState.videoDurationMs,
                            onAddAudioTrack = { track ->
                                viewModel.onIntent(VideoEditorIntent.AddAudioTrack(track))
                                hasUnappliedChanges = true
                            },
                            onRemoveAudioTrack = { trackId ->
                                viewModel.onIntent(VideoEditorIntent.RemoveAudioTrack(trackId))
                                hasUnappliedChanges = true
                            },
                            onUpdateVolume = { trackId, volume ->
                                viewModel.onIntent(VideoEditorIntent.UpdateAudioVolume(trackId, volume))
                                hasUnappliedChanges = true
                            },
                            onUpdateFade = { trackId, fadeIn, fadeOut ->
                                // Update audio track with fade
                                hasUnappliedChanges = true
                            }
                        )
                    }
                    EditorTab.TEXT -> {
                        TextOverlayEditorPanel(
                            selectedOverlay = selectedTextOverlay,
                            onUpdateOverlay = { overlay ->
                                viewModel.onIntent(VideoEditorIntent.UpdateTextOverlay(overlay))
                                selectedTextOverlay = null
                                hasUnappliedChanges = true
                            },
                            onAddOverlay = { overlay ->
                                viewModel.onIntent(VideoEditorIntent.AddTextOverlay(overlay))
                                hasUnappliedChanges = true
                            },
                            onDeleteOverlay = {
                                selectedTextOverlay?.let {
                                    viewModel.onIntent(VideoEditorIntent.RemoveTextOverlay(it.id))
                                }
                                selectedTextOverlay = null
                                hasUnappliedChanges = true
                            }
                        )
                    }
                    EditorTab.STICKERS -> {
                        StickerPickerPanel(
                            onStickerSelected = { stickerUri ->
                                viewModel.onIntent(
                                    VideoEditorIntent.AddStickerOverlay(
                                        StickerOverlay(
                                            id = "",
                                            imageUri = stickerUri,
                                            position = androidx.compose.ui.geometry.Offset(100f, 100f),
                                            startTimeMs = playbackPosition,
                                            endTimeMs = uiState.editState.videoDurationMs
                                        )
                                    )
                                )
                                hasUnappliedChanges = true
                            }
                        )
                    }
                    EditorTab.SPEED -> {
                        SpeedPanel(
                            playbackSpeed = uiState.editState.playbackSpeed,
                            isReversed = uiState.editState.isReversed,
                            onSpeedChanged = { speed ->
                                viewModel.onIntent(VideoEditorIntent.UpdatePlaybackSpeed(speed))
                                hasUnappliedChanges = true
                            },
                            onReverseToggle = {
                                viewModel.onIntent(VideoEditorIntent.ToggleReverse)
                                hasUnappliedChanges = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Apply changes confirmation dialog
    if (showApplyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showApplyConfirmDialog = false },
            title = { Text("Apply Changes") },
            text = {
                Text("This will process the video with your current edits. The changes will be permanent and can be undone later.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onIntent(VideoEditorIntent.ApplyChangesDirectly)
                        showApplyConfirmDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export dialog
    if (showExportDialog) {
        ExportDialog(
            currentSettings = uiState.editState,
            onDismiss = { showExportDialog = false },
            onExport = { compressionLevel, outputFormat ->
                viewModel.onIntent(VideoEditorIntent.UpdateCompressionLevel(compressionLevel))
                viewModel.onIntent(VideoEditorIntent.UpdateOutputFormat(outputFormat))
                viewModel.onIntent(VideoEditorIntent.ExportVideo)
                showExportDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorTopBar(
    onClose: () -> Unit,
    onExport: () -> Unit,
    onApplyChanges: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    isProcessing: Boolean,
    hasUnappliedChanges: Boolean,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
           // Text("Video Editor")
                },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        },
        actions = {
            // Undo button
            IconButton(
                onClick = onUndo,
                enabled = canUndo && !isProcessing
            ) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo && !isProcessing) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }

            // Redo button
            IconButton(
                onClick = onRedo,
                enabled = canRedo && !isProcessing
            ) {
                Icon(
                    Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo && !isProcessing) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }

            Spacer(Modifier.width(8.dp))

            // Apply changes button
            Button(
                onClick = onApplyChanges,
                enabled = !isProcessing && hasUnappliedChanges,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasUnappliedChanges) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Apply")
            }

            Spacer(Modifier.width(8.dp))

            // Export button
            Button(
                onClick = onExport,
                enabled = !isProcessing,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Export")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}

@Composable
fun EditorTabRow(
    selectedTab: EditorTab,
    onTabSelected: (EditorTab) -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier.fillMaxWidth(),
        edgePadding = 8.dp
    ) {
        EditorTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = { Text(tab.title) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun TrimPanel(
    aspectRatio: AspectRatio,
    onAspectRatioChanged: (AspectRatio) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Crop Aspect Ratio",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectRatio.values().forEach { ratio ->
                FilterChip(
                    selected = aspectRatio == ratio,
                    onClick = { onAspectRatioChanged(ratio) },
                    label = { Text(ratio.ratio) },
                    leadingIcon = if (aspectRatio == ratio) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun SpeedPanel(
    playbackSpeed: Float,
    isReversed: Boolean,
    onSpeedChanged: (Float) -> Unit,
    onReverseToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Playback Speed",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                FilterChip(
                    selected = playbackSpeed == speed,
                    onClick = { onSpeedChanged(speed) },
                    label = { Text("${speed}x") }
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Reverse Video",
                style = MaterialTheme.typography.bodyLarge
            )

            Switch(
                checked = isReversed,
                onCheckedChange = { onReverseToggle() }
            )
        }
    }
}

@Composable
fun ProcessingOverlay(
    progress: ProcessingProgress,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.size(64.dp),
                strokeWidth = 6.dp
            )

            Text(
                text = progress.currentOperation.name.replace("_", " "),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "${(progress.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ExportDialog(
    currentSettings: VideoEditState,
    onDismiss: () -> Unit,
    onExport: (CompressionLevel, OutputFormat) -> Unit
) {
    var selectedCompression by remember { mutableStateOf(currentSettings.compressionLevel) }
    var selectedFormat by remember { mutableStateOf(currentSettings.outputFormat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Compression Quality", style = MaterialTheme.typography.titleSmall)
                CompressionLevel.values().forEach { level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedCompression == level,
                            onClick = { selectedCompression = level }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(level.name)
                    }
                }

                HorizontalDivider()

                Text("Output Format", style = MaterialTheme.typography.titleSmall)
                OutputFormat.values().forEach { format ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedFormat == format,
                            onClick = { selectedFormat = format }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(format.extension.uppercase())
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onExport(selectedCompression, selectedFormat) }) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class EditorTab(val title: String, val icon: ImageVector) {
    TRIM("Trim", Icons.Default.ContentCut),
    FILTERS("Filters", Icons.Default.FilterAlt),
    AUDIO("Audio", Icons.Default.MusicNote),
    TEXT("Text", Icons.Default.TextFields),
    STICKERS("Stickers", Icons.Default.AddPhotoAlternate),
    SPEED("Speed", Icons.Default.Speed)
}