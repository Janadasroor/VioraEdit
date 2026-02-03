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
import com.janad.vioraedit.presentation.components.CanvasPanel
import com.janad.vioraedit.presentation.components.StickerEditorPanel
import com.janad.vioraedit.presentation.components.VideoEditorTopBar
import com.janad.vioraedit.presentation.components.TrimPanel
import com.janad.vioraedit.presentation.components.SpeedPanel
import com.janad.vioraedit.presentation.components.ProcessingOverlay
import com.janad.vioraedit.presentation.components.ExportDialog
import com.janad.vioraedit.presentation.components.EditorTab
import com.janad.vioraedit.presentation.components.EditorTabRow
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

    // Share helper
    val shareHelper = remember { com.janad.vioraedit.presentation.utils.VideoShareHelper(context) }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VideoEditorEvent.ExportCompleted -> {
                    onExportComplete(event.outputPath)
                    val result = snackbarHostState.showSnackbar(
                        message = "Video exported successfully",
                        actionLabel = "Share",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        shareHelper.shareVideo(event.outputPath)
                    }
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
                onSaveDraft = { viewModel.onIntent(VideoEditorIntent.SaveDraft) },
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
                    thumbnails = uiState.thumbnails,
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
                    EditorTab.CANVAS -> {
                        CanvasPanel(
                            config = uiState.editState.canvasConfig,
                            onConfigChanged = { config ->
                                viewModel.onIntent(VideoEditorIntent.UpdateCanvasConfig(config))
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
                                viewModel.onIntent(VideoEditorIntent.UpdateAudioFade(trackId, fadeIn, fadeOut))
                                hasUnappliedChanges = true
                            },
                            onUpdateTiming = { trackId, start, sourceStart, duration ->
                                viewModel.onIntent(VideoEditorIntent.UpdateAudioTiming(trackId, start, sourceStart, duration))
                                hasUnappliedChanges = true
                            },

                            onRecordVoiceover = { path ->
                                // Add voiceover as a new audio track
                                viewModel.onIntent(
                                    VideoEditorIntent.AddAudioTrack(
                                        AudioTrack(
                                            id = java.util.UUID.randomUUID().toString(),
                                            uri = android.net.Uri.fromFile(java.io.File(path)).toString(),
                                            startTimeMs = playbackPosition, // Start at current playback position
                                            endTimeMs = uiState.editState.videoDurationMs, // Default to end (will be clipped by file length in reality)
                                            volume = 1.0f // Default loud for voiceover
                                        )
                                    )
                                )
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
                        if (selectedStickerOverlay != null) {
                            StickerEditorPanel(
                                selectedSticker = selectedStickerOverlay!!,
                                onUpdateSticker = { sticker ->
                                    viewModel.onIntent(VideoEditorIntent.UpdateStickerOverlay(sticker))
                                    selectedStickerOverlay = sticker // Update selection reference
                                    hasUnappliedChanges = true
                                },
                                onDeleteSticker = {
                                    viewModel.onIntent(VideoEditorIntent.RemoveStickerOverlay(selectedStickerOverlay!!.id))
                                    selectedStickerOverlay = null
                                    hasUnappliedChanges = true
                                }
                            )
                        } else {
                            StickerPickerPanel(
                                onStickerSelected = { stickerUri ->
                                    if (stickerUri.startsWith("content://") || stickerUri.startsWith("file://")) {
                                        // Image Sticker
                                        viewModel.onIntent(
                                            VideoEditorIntent.AddStickerOverlay(
                                                StickerOverlay(
                                                    id = "",
                                                    imageUri = stickerUri,
                                                    position = androidx.compose.ui.geometry.Offset(300f, 300f),
                                                    scale = 0.5f,
                                                    startTimeMs = playbackPosition,
                                                    endTimeMs = uiState.editState.videoDurationMs
                                                )
                                            )
                                        )
                                    } else {
                                        // Emoji Sticker (TextOverlay)
                                        viewModel.onIntent(
                                            VideoEditorIntent.AddTextOverlay(
                                                TextOverlay(
                                                    id = "", // UUID generated in ViewModel
                                                    text = stickerUri,
                                                    position = androidx.compose.ui.geometry.Offset(200f, 200f),
                                                    fontSize = 100f, // Large size for "sticker" look
                                                    startTimeMs = playbackPosition,
                                                    endTimeMs = uiState.editState.videoDurationMs
                                                )
                                            )
                                        )
                                    }
                                    hasUnappliedChanges = true
                                }
                            )
                        }
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
