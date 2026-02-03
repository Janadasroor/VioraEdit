// File: data/models/VideoEditorModels.kt
package com.janad.vioraedit.data.models

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Represents the complete state of video editing
 */
@Serializable
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
    val outputFormat: OutputFormat = OutputFormat.MP4,
    val canvasConfig: CanvasConfig = CanvasConfig()
)

@Serializable
data class CanvasConfig(
    val mode: CanvasMode = CanvasMode.CROP,
    @Serializable(with = ColorSerializer::class)
    val color: Color = Color.Black,
    val blurRadius: Float = 20f
)

@Serializable
enum class CanvasMode {
    CROP, // Original behavior: Fill and crop excess
    FIT_BLUR, // Fit entire video, fill background with blurred copy
    FIT_COLOR // Fit entire video, fill background with color
}

@Serializable
data class TrimRange(
    val startMs: Long = 0L,
    val endMs: Long = 0L
)

@Serializable
enum class AspectRatio(val ratio: String, val width: Int, val height: Int) {
    ORIGINAL("Original", 0, 0),
    SQUARE("1:1", 1, 1),
    LANDSCAPE("16:9", 16, 9),
    PORTRAIT("9:16", 9, 16),
    STORY("4:5", 4, 5)
}

@Serializable
sealed class VideoFilter(val name: String, val intensity: Float = 1.0f) {
    @Serializable data class Brightness(val value: Float = 0.0f) : VideoFilter("Brightness", value)
    @Serializable data class Contrast(val value: Float = 1.0f) : VideoFilter("Contrast", value)
    @Serializable data class Saturation(val value: Float = 1.0f) : VideoFilter("Saturation", value)
    @Serializable data class Blur(val radius: Float = 5.0f) : VideoFilter("Blur", radius)
    @Serializable data class Vintage(val strength: Float = 1.0f) : VideoFilter("Vintage", strength)
    @Serializable data class Grayscale(val amount: Float = 1.0f) : VideoFilter("Grayscale", amount)
    @Serializable data class Sepia(val amount: Float = 1.0f) : VideoFilter("Sepia", amount)
    @Serializable data class Vignette(val amount: Float = 0.5f) : VideoFilter("Vignette", amount)
    @Serializable data class Vibrant(val strength: Float = 1.0f) : VideoFilter("Vibrant", strength)
}

@Serializable
data class AudioTrack(
    val id: String,
    val uri: String,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val sourceStartMs: Long = 0L, // Start position within the audio file
    val volume: Float = 1.0f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val isOriginalAudio: Boolean = false
)

@Serializable
data class TextOverlay(
    val id: String,
    val text: String,
    @Serializable(with = OffsetSerializer::class)
    val position: Offset,
    val fontSize: Float = 24f,
    @Serializable(with = ColorSerializer::class)
    val color: Color = Color.White,
    @Serializable(with = ColorSerializer::class)
    val backgroundColor: Color? = null,
    val fontFamily: String = "default",
    val rotation: Float = 0f,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val strokeWidth: Float = 0f,
    @Serializable(with = ColorSerializer::class)
    val strokeColor: Color = Color.Black,
    val animation: OverlayAnimation = OverlayAnimation.NONE,
    val animationDurationMs: Long = 500L
)

@Serializable
data class StickerOverlay(
    val id: String,
    val imageUri: String,
    @Serializable(with = OffsetSerializer::class)
    val position: Offset,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val animation: OverlayAnimation = OverlayAnimation.NONE,
    val animationDurationMs: Long = 500L
)

@Serializable
enum class OverlayAnimation {
    NONE,
    FADE_IN,
    SLIDE_IN_LEFT,
    SLIDE_IN_RIGHT,
    SLIDE_IN_UP,
    SLIDE_IN_DOWN,
    POP_IN // Zoom/Scale in
}

@Serializable
enum class CompressionLevel(val crf: Int, val preset: String ) {
    LOW(28, "ultrafast"),
    MEDIUM(23, "medium"),
    HIGH(18, "slow")
}

@Serializable
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