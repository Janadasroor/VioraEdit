// File: presentation/VideoEditorViewModel.kt
package com.janad.vioraedit.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.janad.vioraedit.data.models.*
import com.janad.vioraedit.domain.VideoProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val videoProcessor = VideoProcessor(context)

    // State
    private val _uiState = MutableStateFlow(VideoEditorUiState())
    val uiState: StateFlow<VideoEditorUiState> = _uiState.asStateFlow()

    // Events
    private val _events = MutableSharedFlow<VideoEditorEvent>()
    val events: SharedFlow<VideoEditorEvent> = _events.asSharedFlow()

    // Current playback position
    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    // Undo/Redo stacks
    private val undoStack = mutableListOf<EditCommand>()
    private val redoStack = mutableListOf<EditCommand>()
    private val maxHistorySize = 50

    // Current working video path (updated with each apply)
    private var currentWorkingVideoPath: String? = null

    fun onIntent(intent: VideoEditorIntent) {
        when (intent) {
            is VideoEditorIntent.LoadVideo -> loadVideo(intent.uri)
            is VideoEditorIntent.UpdateTrimRange -> updateTrimRange(intent.startMs, intent.endMs)
            is VideoEditorIntent.UpdateCropRatio -> updateCropRatio(intent.aspectRatio)
            is VideoEditorIntent.AddFilter -> addFilter(intent.filter)
            is VideoEditorIntent.RemoveFilter -> removeFilter(intent.filter)
            is VideoEditorIntent.UpdateFilter -> updateFilter(intent.index, intent.filter)
            is VideoEditorIntent.AddAudioTrack -> addAudioTrack(intent.track)
            is VideoEditorIntent.RemoveAudioTrack -> removeAudioTrack(intent.trackId)
            is VideoEditorIntent.UpdateAudioVolume -> updateAudioVolume(intent.trackId, intent.volume)
            is VideoEditorIntent.AddTextOverlay -> addTextOverlay(intent.overlay)
            is VideoEditorIntent.UpdateTextOverlay -> updateTextOverlay(intent.overlay)
            is VideoEditorIntent.RemoveTextOverlay -> removeTextOverlay(intent.overlayId)
            is VideoEditorIntent.AddStickerOverlay -> addStickerOverlay(intent.overlay)
            is VideoEditorIntent.UpdateStickerOverlay -> updateStickerOverlay(intent.overlay)
            is VideoEditorIntent.RemoveStickerOverlay -> removeStickerOverlay(intent.overlayId)
            is VideoEditorIntent.UpdatePlaybackSpeed -> updatePlaybackSpeed(intent.speed)
            is VideoEditorIntent.ToggleReverse -> toggleReverse()
            is VideoEditorIntent.UpdateCompressionLevel -> updateCompressionLevel(intent.level)
            is VideoEditorIntent.UpdateOutputFormat -> updateOutputFormat(intent.format)
            is VideoEditorIntent.ExportVideo -> exportVideo()
            is VideoEditorIntent.UpdatePlaybackPosition -> _playbackPosition.value = intent.positionMs
            is VideoEditorIntent.QuickTrim -> quickTrim(intent.startMs, intent.endMs)
            is VideoEditorIntent.ApplyChangesDirectly -> applyChangesDirectly()
            is VideoEditorIntent.Undo -> undo()
            is VideoEditorIntent.Redo -> redo()
        }
    }

    private fun loadVideo(uriString: String) {
        viewModelScope.launch {
            Timber.d("Loading video: $uriString")
            _uiState.update { it.copy(isLoading = true) }

            try {
                val uri = Uri.parse(uriString)

                takePersistableUriPermission(uri)
                Timber.d("Persistable URI permission taken")

                if (!isUriAccessible(uri)) {
                    Timber.e("Cannot access video file")
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(VideoEditorEvent.Error("Cannot access video file"))
                    return@launch
                }

                val durationMs = 30000L // Get actual duration

                // Reset working video path and history
                currentWorkingVideoPath = uriString
                undoStack.clear()
                redoStack.clear()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        editState = it.editState.copy(
                            videoUri = uriString,
                            videoDurationMs = durationMs,
                            trimRange = TrimRange(0L, durationMs)
                        ),
                        canUndo = false,
                        canRedo = false
                    )
                }
                Timber.d("Video loaded: ${uiState.value.editState.videoUri}")
                _events.emit(VideoEditorEvent.VideoLoaded(uriString))
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.emit(VideoEditorEvent.Error("Failed to load video: ${e.message}"))
            }
        }
    }

    /**
     * Apply current pending changes directly to the video
     */
    private fun applyChangesDirectly() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            try {
                val currentState = _uiState.value.editState
                val inputPath = currentWorkingVideoPath ?: currentState.videoUri

                // Create command for undo
                val command = EditCommand(
                    previousState = currentState.copy(videoUri = inputPath),
                    previousVideoPath = inputPath,
                    newState = currentState
                )

                // Process video with current edits using the current working path
                val stateWithCurrentVideo = currentState.copy(videoUri = inputPath)
                videoProcessor.processVideo(stateWithCurrentVideo) { progress, operation ->
                    _uiState.update {
                        it.copy(
                            processingProgress = ProcessingProgress(
                                currentOperation = operation,
                                progress = progress / 100f
                            )
                        )
                    }
                }.collect { result ->
                    when (result) {
                        is ExportResult.Success -> {
                            Timber.d("Changes applied: ${result.outputPath}")

                            // Update working video path
                            currentWorkingVideoPath = result.outputPath
                            command.newVideoPath = result.outputPath

                            // Add to undo stack
                            undoStack.add(command)
                            if (undoStack.size > maxHistorySize) {
                                undoStack.removeAt(0)
                            }
                            redoStack.clear()

                            // Update UI state
                            _uiState.update {
                                it.copy(
                                    isProcessing = false,
                                    editState = it.editState.copy(videoUri = result.outputPath),
                                    canUndo = true,
                                    canRedo = false,
                                    processingProgress = ProcessingProgress(
                                        currentOperation = ProcessingOperation.COMPLETED,
                                        progress = 1f,
                                        isCompleted = true
                                    )
                                )
                            }

                            _events.emit(VideoEditorEvent.ChangesApplied(result.outputPath))
                        }
                        is ExportResult.Error -> {
                            Timber.e("Apply changes error: ${result.message}")
                            _uiState.update { it.copy(isProcessing = false) }
                            _events.emit(VideoEditorEvent.Error(result.message))
                        }
                        is ExportResult.Progress -> {
                            _uiState.update {
                                it.copy(
                                    processingProgress = ProcessingProgress(
                                        currentOperation = result.operation,
                                        progress = result.percentage / 100f
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error applying changes")
                _uiState.update { it.copy(isProcessing = false) }
                _events.emit(VideoEditorEvent.Error("Error applying changes: ${e.message}"))
            }
        }
    }

    /**
     * Undo last applied change
     */
    private fun undo() {
        if (undoStack.isEmpty()) return

        viewModelScope.launch {
            try {
                val command = undoStack.removeAt(undoStack.lastIndex)

                // Restore previous state and video
                currentWorkingVideoPath = command.previousVideoPath

                _uiState.update {
                    it.copy(
                        editState = command.previousState.copy(
                            videoUri = command.previousVideoPath
                        ),
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = true
                    )
                }

                // Move to redo stack
                redoStack.add(command)

                _events.emit(VideoEditorEvent.UndoCompleted)
            } catch (e: Exception) {
                Timber.e(e, "Error during undo")
                _events.emit(VideoEditorEvent.Error("Undo failed: ${e.message}"))
            }
        }
    }

    /**
     * Redo previously undone change
     */
    private fun redo() {
        if (redoStack.isEmpty()) return

        viewModelScope.launch {
            try {
                val command = redoStack.removeAt(redoStack.lastIndex)

                // Restore new state and video
                currentWorkingVideoPath = command.newVideoPath

                _uiState.update {
                    it.copy(
                        editState = command.newState.copy(
                            videoUri = command.newVideoPath
                        ),
                        canUndo = true,
                        canRedo = redoStack.isNotEmpty()
                    )
                }

                // Move back to undo stack
                undoStack.add(command)

                _events.emit(VideoEditorEvent.RedoCompleted)
            } catch (e: Exception) {
                Timber.e(e, "Error during redo")
                _events.emit(VideoEditorEvent.Error("Redo failed: ${e.message}"))
            }
        }
    }

    private fun takePersistableUriPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Permission might already be taken or not available
        }
    }

    private fun isUriAccessible(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun updateTrimRange(startMs: Long, endMs: Long) {
        _uiState.update {
            it.copy(
                editState = it.editState.copy(
                    trimRange = TrimRange(startMs, endMs)
                )
            )
        }
    }

    private fun updateCropRatio(aspectRatio: AspectRatio) {
        _uiState.update {
            it.copy(
                editState = it.editState.copy(cropAspectRatio = aspectRatio)
            )
        }
    }

    private fun addFilter(filter: VideoFilter) {
        _uiState.update {
            it.copy(
                editState = it.editState.copy(
                    filters = it.editState.filters + filter
                )
            )
        }
    }

    private fun removeFilter(filter: VideoFilter) {
        _uiState.update {
            it.copy(
                editState = it.editState.copy(
                    filters = it.editState.filters.filterNot { it == filter }
                )
            )
        }
    }

    private fun updateFilter(index: Int, filter: VideoFilter) {
        _uiState.update {
            val filters = it.editState.filters.toMutableList()
            if (index in filters.indices) {
                filters[index] = filter
            }
            it.copy(editState = it.editState.copy(filters = filters))
        }
    }

    private fun addAudioTrack(track: AudioTrack) {
        _uiState.update {
            it.copy(
                editState = it.editState.copy(
                    audioTracks = it.editState.audioTracks + track
                )
            )
        }
    }

    private fun removeAudioTrack(trackId: String) {
        _uiState.update {
            it.copy(
                editState = it.editState.copy(
                    audioTracks = it.editState.audioTracks.filterNot { it.id == trackId }
                )
            )
        }
    }

    private fun updateAudioVolume(trackId: String, volume: Float) {
        _uiState.update {
            val tracks = it.editState.audioTracks.map { track ->
                if (track.id == trackId) track.copy(volume = volume)
                else track
            }
            it.copy(editState = it.editState.copy(audioTracks = tracks))
        }
    }

    private fun addTextOverlay(overlay: TextOverlay) {
        val newOverlay = overlay.copy(id = UUID.randomUUID().toString())
        _uiState.update {
            it.copy(
                editState = it.editState.copy(
                    textOverlays = it.editState.textOverlays + newOverlay
                )
            )
        }
    }

    private fun updateTextOverlay(overlay: TextOverlay) {
        _uiState.update {
            val overlays = it.editState.textOverlays.map { existing ->
                if (existing.id == overlay.id) overlay else existing
            }
            it.copy(editState = it.editState.copy(textOverlays = overlays))
        }
    }

    private fun removeTextOverlay(overlayId: String) {
        _uiState.update {
            it.copy(
                editState = it.editState.copy(
                    textOverlays = it.editState.textOverlays.filterNot { it.id == overlayId }
                )
            )
        }
    }

    private fun addStickerOverlay(overlay: StickerOverlay) {
        val newOverlay = overlay.copy(id = UUID.randomUUID().toString())
        _uiState.update {
            it.copy(
                editState = it.editState.copy(
                    stickerOverlays = it.editState.stickerOverlays + newOverlay
                )
            )
        }
    }

    private fun updateStickerOverlay(overlay: StickerOverlay) {
        _uiState.update {
            val overlays = it.editState.stickerOverlays.map { existing ->
                if (existing.id == overlay.id) overlay else existing
            }
            it.copy(editState = it.editState.copy(stickerOverlays = overlays))
        }
    }

    private fun removeStickerOverlay(overlayId: String) {
        _uiState.update {
            it.copy(
                editState = it.editState.copy(
                    stickerOverlays = it.editState.stickerOverlays.filterNot { it.id == overlayId }
                )
            )
        }
    }

    private fun updatePlaybackSpeed(speed: Float) {
        _uiState.update {
            it.copy(editState = it.editState.copy(playbackSpeed = speed))
        }
    }

    private fun toggleReverse() {
        _uiState.update {
            it.copy(editState = it.editState.copy(isReversed = !it.editState.isReversed))
        }
    }

    private fun updateCompressionLevel(level: CompressionLevel) {
        _uiState.update {
            it.copy(editState = it.editState.copy(compressionLevel = level))
        }
    }

    private fun updateOutputFormat(format: OutputFormat) {
        _uiState.update {
            it.copy(editState = it.editState.copy(outputFormat = format))
        }
    }

    private fun quickTrim(startMs: Long, endMs: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            try {
                val outputPath = videoProcessor.quickTrim(
                    _uiState.value.editState.videoUri,
                    startMs,
                    endMs
                )

                if (outputPath.isNotEmpty()) {
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.emit(VideoEditorEvent.ExportCompleted(outputPath))
                } else {
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.emit(VideoEditorEvent.Error("Quick trim failed"))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false) }
                _events.emit(VideoEditorEvent.Error("Quick trim failed: ${e.message}"))
            }
        }
    }

    private fun exportVideo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            videoProcessor.processVideo(_uiState.value.editState) { progress, operation ->
                Timber.d("Export progress: $progress, operation: $operation")
                _uiState.update {
                    it.copy(
                        processingProgress = ProcessingProgress(
                            currentOperation = operation,
                            progress = progress / 100f
                        )
                    )
                }
            }.collect { result ->
                when (result) {
                    is ExportResult.Success -> {
                        Timber.d("Export completed: ${result.outputPath}")
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                processingProgress = ProcessingProgress(
                                    currentOperation = ProcessingOperation.COMPLETED,
                                    progress = 1f,
                                    isCompleted = true
                                )
                            )
                        }
                        _events.emit(VideoEditorEvent.ExportCompleted(result.outputPath))
                    }
                    is ExportResult.Error -> {
                        Timber.e("Export error: ${result.message}")
                        _uiState.update { it.copy(isProcessing = false) }
                        _events.emit(VideoEditorEvent.Error(result.message))
                    }
                    is ExportResult.Progress -> {
                        Timber.d("Export progress: ${result.percentage}")
                        _uiState.update {
                            it.copy(
                                processingProgress = ProcessingProgress(
                                    currentOperation = result.operation,
                                    progress = result.percentage / 100f
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// Command pattern for undo/redo
data class EditCommand(
    val previousState: VideoEditState,
    val previousVideoPath: String,
    val newState: VideoEditState,
    var newVideoPath: String = ""
)

// UI State
data class VideoEditorUiState(
    val editState: VideoEditState = VideoEditState(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val processingProgress: ProcessingProgress = ProcessingProgress(),
    val isPlaying: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

// Intents
sealed class VideoEditorIntent {
    data class LoadVideo(val uri: String) : VideoEditorIntent()
    data class UpdateTrimRange(val startMs: Long, val endMs: Long) : VideoEditorIntent()
    data class UpdateCropRatio(val aspectRatio: AspectRatio) : VideoEditorIntent()
    data class AddFilter(val filter: VideoFilter) : VideoEditorIntent()
    data class RemoveFilter(val filter: VideoFilter) : VideoEditorIntent()
    data class UpdateFilter(val index: Int, val filter: VideoFilter) : VideoEditorIntent()
    data class AddAudioTrack(val track: AudioTrack) : VideoEditorIntent()
    data class RemoveAudioTrack(val trackId: String) : VideoEditorIntent()
    data class UpdateAudioVolume(val trackId: String, val volume: Float) : VideoEditorIntent()
    data class AddTextOverlay(val overlay: TextOverlay) : VideoEditorIntent()
    data class UpdateTextOverlay(val overlay: TextOverlay) : VideoEditorIntent()
    data class RemoveTextOverlay(val overlayId: String) : VideoEditorIntent()
    data class AddStickerOverlay(val overlay: StickerOverlay) : VideoEditorIntent()
    data class UpdateStickerOverlay(val overlay: StickerOverlay) : VideoEditorIntent()
    data class RemoveStickerOverlay(val overlayId: String) : VideoEditorIntent()
    data class UpdatePlaybackSpeed(val speed: Float) : VideoEditorIntent()
    data object ToggleReverse : VideoEditorIntent()
    data class UpdateCompressionLevel(val level: CompressionLevel) : VideoEditorIntent()
    data class UpdateOutputFormat(val format: OutputFormat) : VideoEditorIntent()
    data object ExportVideo : VideoEditorIntent()
    data class UpdatePlaybackPosition(val positionMs: Long) : VideoEditorIntent()
    data class QuickTrim(val startMs: Long, val endMs: Long) : VideoEditorIntent()
    data object ApplyChangesDirectly : VideoEditorIntent()
    data object Undo : VideoEditorIntent()
    data object Redo : VideoEditorIntent()
}

// Events
sealed class VideoEditorEvent {
    data class VideoLoaded(val uri: String) : VideoEditorEvent()
    data class ExportCompleted(val outputPath: String) : VideoEditorEvent()
    data class ChangesApplied(val outputPath: String) : VideoEditorEvent()
    data object UndoCompleted : VideoEditorEvent()
    data object RedoCompleted : VideoEditorEvent()
    data class Error(val message: String) : VideoEditorEvent()
}