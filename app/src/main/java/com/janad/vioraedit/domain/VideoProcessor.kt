// File: domain/VideoProcessor.kt
package com.janad.vioraedit.domain

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.Color
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.janad.vioraedit.data.models.ProcessingOperation
import com.janad.vioraedit.data.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

/**
 * Main video processing engine using FFmpegKit
 */
class VideoProcessor(private val context: Context) {

    private val outputDir: File by lazy {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(downloads, "VideoEditor")

        if (!folder.exists()) {
            folder.mkdirs()
        }

        folder
    }
    private val cacheDir = File(context.cacheDir, "video_processing")

    init {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    /**
     * Process video with all applied edits
     */
    fun processVideo(
        editState: VideoEditState,
        onProgress: (Float, ProcessingOperation) -> Unit
    ): Flow<ExportResult> = flow {
        try {
            emit(ExportResult.Progress(0f, ProcessingOperation.LOADING))

            // Fix: Properly resolve URI to file path
            val inputPath = getPathFromUri(Uri.parse(editState.videoUri))
            val outputPath = generateOutputPath(editState.outputFormat)

            Timber.d("Input path: $inputPath")
            Timber.d("Output path: $outputPath")

            // Verify input file exists
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                emit(ExportResult.Error("Input file not found: $inputPath"))
                return@flow
            }

            Timber.d("Input file size: ${inputFile.length()} bytes")

            // Build FFmpeg command
            val commandString = buildFFmpegCommand(inputPath, outputPath, editState)
            Timber.d("FFmpegKit command: $commandString")

            emit(ExportResult.Progress(10f, ProcessingOperation.APPLYING_FILTERS))



// Simple copy command
            val commandTe = "-i $inputPath -c:v copy -c:a aac -b:a 64k -metadata:s:v:0 rotate=0 $outputPath"


            val result = executeFFmpegCommand(commandString, editState.videoDurationMs) { progress ->
                onProgress(10f + (progress * 80f), ProcessingOperation.EXPORTING)
            }

            Timber.d("FFmpeg result: $result")
            if (result) {
                val outputFile = File(outputPath)
                if (outputFile.exists() && outputFile.length() > 0) {
                    val fileSize = outputFile.length() / (1024f * 1024f)
                    Timber.d("Export completed. File size: $fileSize MB")
                    emit(ExportResult.Progress(100f, ProcessingOperation.COMPLETED))
                    emit(ExportResult.Success(outputPath, fileSize))
                } else {
                    emit(ExportResult.Error("Output file is empty or not created"))
                }
            } else {
                Timber.e("Failed to process video")
                emit(ExportResult.Error("Failed to process video"))
            }

            // Cleanup cache files
            cleanupCacheFiles()

        } catch (e: Exception) {
            Timber.e(e, "Error processing video")
            emit(ExportResult.Error("Error processing video: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Build complete FFmpeg command with all edits
     */
    private fun buildFFmpegCommand(
        inputPath: String,
        outputPath: String,
        editState: VideoEditState
    ): String {
        val cmdParts = mutableListOf<String>()

        // Input
        cmdParts.add("-i")
        cmdParts.add("\"$inputPath\"")

        // Trimming
        if (editState.trimRange.startMs > 0) {
            cmdParts.add("-ss")
            cmdParts.add((editState.trimRange.startMs / 1000.0).toString())
        }
        if (editState.trimRange.endMs > 0) {
            cmdParts.add("-to")
            cmdParts.add((editState.trimRange.endMs / 1000.0).toString())
        }

        // Video filters
        val videoFilters = mutableListOf<String>()
        if (editState.cropAspectRatio != AspectRatio.ORIGINAL) {
            buildCropFilter(editState.cropAspectRatio)?.let {
                if (it.isNotEmpty()) videoFilters.add(it)
            }
        }
        videoFilters.addAll(buildVideoFilters(editState.filters))
        Timber.d("Video filters: ${videoFilters.joinToString(", ")}")
        if (editState.playbackSpeed != 1.0f) {
            videoFilters.add("setpts=${1.0f / editState.playbackSpeed}*PTS")
        }
        if (editState.isReversed) {
            videoFilters.add("reverse")
        }
        videoFilters.addAll(buildTextOverlays(editState.textOverlays))

        if (videoFilters.isNotEmpty()) {
            cmdParts.add("-vf")
            cmdParts.add("\"${videoFilters.joinToString(",")}\"")
        }

        // Audio filters
        val audioFilters = mutableListOf<String>()
        if (editState.playbackSpeed != 1.0f) {
            audioFilters.add("atempo=${editState.playbackSpeed}")
        }
        if (editState.isReversed) {
            audioFilters.add("areverse")
        }
        if (audioFilters.isNotEmpty()) {
            cmdParts.add("-af")
            cmdParts.add("\"${audioFilters.joinToString(",")}\"")
        }

        // Video codec - Use hardware encoder for Android
        cmdParts.add("-c:v")
        cmdParts.add("h264_mediacodec")  // Changed from libx264
        cmdParts.add("-b:v")
        cmdParts.add(getBitrate(editState.compressionLevel))  // Use bitrate instead of CRF

        // Audio codec
        cmdParts.add("-c:a")
        cmdParts.add("aac")
        cmdParts.add("-b:a")
        cmdParts.add("128k")

        // Overwrite output
        cmdParts.add("-y")

        // Output
        cmdParts.add("\"$outputPath\"")

        return cmdParts.joinToString(" ")
    }

    private fun getBitrate(compressionLevel: CompressionLevel): String {
        // Convert CRF concept to bitrate (lower CRF = higher quality = higher bitrate)
        return when (compressionLevel.crf) {
            in 0..18 -> "8000k"   // Very high quality
            in 19..23 -> "4000k"  // High quality
            in 24..28 -> "2000k"  // Medium quality
            else -> "1000k"       // Lower quality
        }
    }



    private fun buildCropFilter(aspectRatio: AspectRatio): String {
        return when (aspectRatio) {
            AspectRatio.SQUARE -> "crop=min(iw\\,ih):min(iw\\,ih)"
            AspectRatio.LANDSCAPE -> "crop=ih*16/9:ih"
            AspectRatio.PORTRAIT -> "crop=iw:iw*16/9"
            AspectRatio.STORY -> "crop=ih*9/16:ih"
            else -> ""
        }
    }

    private fun buildVideoFilters(filters: List<VideoFilter>): List<String> {
        return filters.mapNotNull { filter ->
            when (filter) {
                is VideoFilter.Brightness -> "eq=brightness=${filter.value}"
                is VideoFilter.Contrast -> "eq=contrast=${filter.value}"
                is VideoFilter.Saturation -> "eq=saturation=${filter.value}"
                is VideoFilter.Blur -> "boxblur=${filter.radius}:1"
                is VideoFilter.Grayscale -> "hue=s=0"
                is VideoFilter.Sepia -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
                is VideoFilter.Vignette -> "vignette=angle=PI/4"
                is VideoFilter.Vintage -> "curves=vintage,eq=contrast=1.1:brightness=-0.1"
            }
        }
    }

    private fun buildTextOverlays(overlays: List<TextOverlay>): List<String> {
        return overlays.mapIndexed { index, overlay ->
            val x = overlay.position.x.toInt()
            val y = overlay.position.y.toInt()
            val color = colorToHex(overlay.color)
            val strokeColor = colorToHex(overlay.strokeColor)

            buildString {
                append("drawtext=")
                append("text='${overlay.text.replace("'", "\\\\\\\\\\\\'")}':") // Escape single quotes
                append("fontsize=${overlay.fontSize.toInt()}:")
                append("fontcolor=$color:")
                append("x=$x:y=$y")

                if (overlay.strokeWidth > 0) {
                    append(":borderw=${overlay.strokeWidth.toInt()}")
                    append(":bordercolor=$strokeColor")
                }

                if (overlay.startTimeMs > 0 || overlay.endTimeMs > 0) {
                    val startSec = overlay.startTimeMs / 1000.0
                    val endSec = overlay.endTimeMs / 1000.0
                    append(":enable='between(t,$startSec,$endSec)'")
                }
            }
        }
    }

    private suspend fun executeFFmpegCommand(
        command: String,
        durationMs: Long,
        onProgress: suspend (Float) -> Unit
    ): Boolean = suspendCancellableCoroutine { continuation ->
        Timber.d("Executing FFmpeg command: $command")

        val session = FFmpegKit.executeAsync(
            command,
            { session ->
                val returnCode = session.returnCode
                if (ReturnCode.isSuccess(returnCode)) {
                    Timber.d("FFmpeg execution successful")
                    continuation.resume(true)
                } else {
                    Timber.e("FFmpeg execution failed")
                    Timber.e("Return code: $returnCode")
                    Timber.e("Fail stack trace: ${session.failStackTrace}")
                    Timber.e("Output: ${session.output}")
                    Timber.e("All logs: ${session.allLogsAsString}")
                    continuation.resume(false)
                }
            },
            { log ->
                Timber.d("FFmpeg: ${log.message}")
            },
            { statistics ->
                val progress = if (durationMs > 0) {
                    (statistics.time.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                // Update progress in IO coroutine
                CoroutineScope(Dispatchers.IO).launch {
                    onProgress(progress)
                }
            }
        )

        continuation.invokeOnCancellation {
            Timber.d("FFmpeg execution cancelled")
            session.cancel()
        }
    }

    /**
     * Quick trim operation (no re-encoding)
     */
    suspend fun quickTrim(
        inputUri: String,
        startMs: Long,
        endMs: Long
    ): String = suspendCancellableCoroutine { continuation ->
        val inputPath = getPathFromUri(Uri.parse(inputUri))
        val outputPath = generateOutputPath(OutputFormat.MP4)

        val cmdParts = mutableListOf<String>()
        cmdParts.add("-i")
        cmdParts.add(inputPath)
        cmdParts.add("-ss")
        cmdParts.add((startMs / 1000.0).toString())
        cmdParts.add("-to")
        cmdParts.add((endMs / 1000.0).toString())
        cmdParts.add("-c")
        cmdParts.add("copy")
        cmdParts.add("-y")
        cmdParts.add(outputPath)

        val command = cmdParts.joinToString(" ")

        FFmpegKit.executeAsync(command) { session ->
            if (ReturnCode.isSuccess(session.returnCode)) {
                continuation.resume(outputPath)
            } else {
                Timber.e("Quick trim failed: ${session.output}")
                continuation.resume("")
            }
        }
    }

    /**
     * Extract video information
     */
    suspend fun getVideoInfo(uri: String): VideoInfo? {
        // Implementation would use FFprobe to get video metadata
        return null
    }

    /**
     * Convert content:// URI to file path
     * Handles both file:// URIs and content:// URIs
     */
    private fun getPathFromUri(uri: Uri): String {
        return when (uri.scheme) {
            "file" -> {
                // Direct file path
                uri.path ?: throw IllegalArgumentException("Invalid file URI")
            }
            "content" -> {
                // Content URI - copy to cache
                try {
                    copyUriToCache(uri)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to copy URI to cache")
                    throw IllegalArgumentException("Cannot access content URI: ${e.message}")
                }
            }
            else -> {
                throw IllegalArgumentException("Unsupported URI scheme: ${uri.scheme}")
            }
        }
    }

    /**
     * Copy content URI to cache directory
     */
    private fun copyUriToCache(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open input stream for URI")

        // Get file extension
        val extension = getFileExtension(uri)
        val fileName = "temp_video_${System.currentTimeMillis()}.$extension"
        val cacheFile = File(cacheDir, fileName)

        inputStream.use { input ->
            cacheFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }

        Timber.d("Copied URI to cache: ${cacheFile.absolutePath}")
        return cacheFile.absolutePath
    }

    /**
     * Get file extension from URI
     */
    private fun getFileExtension(uri: Uri): String {
        // Try to get from MIME type
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null) {
            return when {
                mimeType.contains("mp4") -> "mp4"
                mimeType.contains("quicktime") || mimeType.contains("mov") -> "mov"
                mimeType.contains("matroska") || mimeType.contains("mkv") -> "mkv"
                mimeType.contains("webm") -> "webm"
                mimeType.contains("avi") -> "avi"
                else -> "mp4"
            }
        }

        // Try to get from file name
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                val fileName = cursor.getString(nameIndex)
                val ext = fileName.substringAfterLast('.', "")
                if (ext.isNotEmpty()) return ext
            }
        }

        // Default to mp4
        return "mp4"
    }

    /**
     * Clean up old cache files
     */
    private fun cleanupCacheFiles() {
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_video_")) {
                    // Delete files older than 1 hour
                    val age = System.currentTimeMillis() - file.lastModified()
                    if (age > 3600000) { // 1 hour
                        file.delete()
                        Timber.d("Deleted old cache file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up cache files")
        }
    }

    private fun generateOutputPath(format: OutputFormat): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(outputDir, "VID_$timestamp.${format.extension}").absolutePath
    }

    private fun colorToHex(color: Color): String {
        val red = (color.red * 255).toInt()
        val green = (color.green * 255).toInt()
        val blue = (color.blue * 255).toInt()
        return String.format("#%02x%02x%02x", red, green, blue)
    }
}

data class VideoInfo(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bitrate: Long,
    val fps: Float,
    val codec: String
)