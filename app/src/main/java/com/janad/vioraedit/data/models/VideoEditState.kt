// File: data/models/VideoEditorModels.kt
package com.janad.vioraedit.data.models

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Represents the complete state of video editing
 */
data class VideoEditState(
    val videoUri: String = "",
    val videoDurationMs: Long = 0L,
    val trimRange: TrimRange = TrimRange(),
    val cropAspectRatio: AspectRatio = AspectRatio.ORIGINAL,
    val filters: List<VideoFilter> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val stickerOverlays: List<StickerOverlay> = emptyList(),
    val playbackSpeed: Float = 1.0f,
    val isReversed: Boolean = false,
    val compressionLevel: CompressionLevel = CompressionLevel.MEDIUM,
    val outputFormat: OutputFormat = OutputFormat.MP4
)

data class TrimRange(
    val startMs: Long = 0L,
    val endMs: Long = 0L
)

enum class AspectRatio(val ratio: String, val width: Int, val height: Int) {
    ORIGINAL("Original", 0, 0),
    SQUARE("1:1", 1, 1),
    LANDSCAPE("16:9", 16, 9),
    PORTRAIT("9:16", 9, 16),
    STORY("4:5", 4, 5)
}

sealed class VideoFilter(val name: String, val intensity: Float = 1.0f) {
    data class Brightness(val value: Float = 0.0f) : VideoFilter("Brightness", value)
    data class Contrast(val value: Float = 1.0f) : VideoFilter("Contrast", value)
    data class Saturation(val value: Float = 1.0f) : VideoFilter("Saturation", value)
    data class Blur(val radius: Float = 5.0f) : VideoFilter("Blur", radius)
    data class Vintage(val strength: Float = 1.0f) : VideoFilter("Vintage", strength)
    data class Grayscale(val amount: Float = 1.0f) : VideoFilter("Grayscale", amount)
    data class Sepia(val amount: Float = 1.0f) : VideoFilter("Sepia", amount)
    data class Vignette(val amount: Float = 0.5f) : VideoFilter("Vignette", amount)
}

data class AudioTrack(
    val id: String,
    val uri: String,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val volume: Float = 1.0f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val isOriginalAudio: Boolean = false
)

data class TextOverlay(
    val id: String,
    val text: String,
    val position: Offset,
    val fontSize: Float = 24f,
    val color: Color = Color.White,
    val backgroundColor: Color? = null,
    val fontFamily: String = "default",
    val rotation: Float = 0f,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val strokeWidth: Float = 0f,
    val strokeColor: Color = Color.Black
)

data class StickerOverlay(
    val id: String,
    val imageUri: String,
    val position: Offset,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L
)

enum class CompressionLevel(val crf: Int, val preset: String ) {
    LOW(28, "ultrafast"),
    MEDIUM(23, "medium"),
    HIGH(18, "slow")
}

enum class OutputFormat(val extension: String, val mimeType: String) {
    MP4("mp4", "video/mp4"),
    MOV("mov", "video/quicktime")
}

/**
 * Represents the progress of video processing
 */
data class ProcessingProgress(
    val currentOperation: ProcessingOperation = ProcessingOperation.IDLE,
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val error: String? = null
)

enum class ProcessingOperation {
    IDLE,
    LOADING,
    TRIMMING,
    APPLYING_FILTERS,
    ADDING_AUDIO,
    ADDING_OVERLAYS,
    COMPRESSING,
    EXPORTING,
    COMPLETED
}

/**
 * Result of video export operation
 */
sealed class ExportResult {
    data class Success(val outputPath: String, val fileSizeMb: Float) : ExportResult()
    data class Error(val message: String, val exception: Throwable? = null) : ExportResult()
    data class Progress(val percentage: Float, val operation: ProcessingOperation) : ExportResult()
}