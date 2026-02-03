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

    private val fontRepository = com.janad.vioraedit.data.models.FontRepository(context)

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
        var inputCount = 0

        // 0: Input Video
        cmdParts.add("-i")
        cmdParts.add("\"$inputPath\"")
        inputCount++

        // Audio Inputs
        val audioTracks = editState.audioTracks.filter { !it.isOriginalAudio }
        audioTracks.forEach { track ->
            try {
                val audioPath = getPathFromUri(Uri.parse(track.uri))
                cmdParts.add("-i")
                cmdParts.add("\"$audioPath\"")
                inputCount++
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve audio path: ${track.uri}")
            }
        }
        
        // Sticker Inputs
        val stickerOverlays = editState.stickerOverlays
        stickerOverlays.forEach { sticker ->
             try {
                val imagePath = getPathFromUri(Uri.parse(sticker.imageUri))
                cmdParts.add("-i")
                cmdParts.add("\"$imagePath\"")
                inputCount++
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve sticker path: ${sticker.imageUri}")
            }
        }

        // Trimming
        if (editState.trimRange.startMs > 0) {
            cmdParts.add("-ss")
            cmdParts.add((editState.trimRange.startMs / 1000.0).toString())
        }
        if (editState.trimRange.endMs > 0) {
            cmdParts.add("-to")
            cmdParts.add((editState.trimRange.endMs / 1000.0).toString())
        }

        // --- Filter Complex ---
        val filterComplex = mutableListOf<String>()
        var videoOutputLabel = "0:v"
        var audioOutputLabel = "0:a"

        // 1. Base Video Filters (Speed, Reverse)
        // We do NOT apply crop/canvas here yet, we prepare the base stream
        var currentVideoLabel = "[0:v]"
        val baseFilters = mutableListOf<String>()
        
        if (editState.playbackSpeed != 1.0f) {
            baseFilters.add("setpts=${1.0f / editState.playbackSpeed}*PTS")
        }
        if (editState.isReversed) {
            baseFilters.add("reverse")
        }
        
        if (baseFilters.isNotEmpty()) {
            filterComplex.add("$currentVideoLabel${baseFilters.joinToString(", ")}[v_base]")
            currentVideoLabel = "[v_base]"
        }

        // 2. Canvas / Aspect Ratio Logic
        val canvasConfig = editState.canvasConfig
        val aspectRatio = editState.cropAspectRatio
        
        // Determine target dimensions
        // Default to 1080p base
        val targetW: Int
        val targetH: Int
        
        if (aspectRatio == AspectRatio.ORIGINAL) {
            // If original, we can't easily do FIT_BLUR/COLOR without knowing dims.
            // Fallback to simple behavior or skip.
            // For now, if ORIGINAL, we skip canvas logic unless filters are needed
            targetW = -1
            targetH = -1
        } else {
            // Simple logic: Base 1080
            if (aspectRatio.width >= aspectRatio.height) {
                targetW = 1920
                targetH = (1920 * aspectRatio.height) / aspectRatio.width
            } else {
                targetW = 1080
                targetH = (1080 * aspectRatio.height) / aspectRatio.width
            }
        }

        if (aspectRatio == AspectRatio.ORIGINAL || canvasConfig.mode == CanvasMode.CROP) {
            // Legacy/Simple Crop behavior
            val cropFilters = mutableListOf<String>()
            if (aspectRatio != AspectRatio.ORIGINAL) {
                 buildCropFilter(aspectRatio)?.let { if (it.isNotEmpty()) cropFilters.add(it) }
            }
            cropFilters.addAll(buildVideoFilters(editState.filters)) // Color filters
            
            if (cropFilters.isNotEmpty()) {
                filterComplex.add("$currentVideoLabel${cropFilters.joinToString(", ")}[v_canvas]")
                currentVideoLabel = "[v_canvas]"
            }
        } else {
            // FIT Modes (Blur or Color)
            // We need to construct the canvas
            
            val padColor = colorToHex(canvasConfig.color)
            val blurRadius = canvasConfig.blurRadius
            
            // 2a. Prepare Background
            if (canvasConfig.mode == CanvasMode.FIT_BLUR) {
                // Split input: one for BG, one for FG
                filterComplex.add("${currentVideoLabel}split[v_bg_in][v_fg_in]")
                
                // BG: Scale to fill -> Blur
                // scale=targetW:targetH:force_original_aspect_ratio=increase,crop=targetW:targetH
                val bgFilter = "scale=$targetW:$targetH:force_original_aspect_ratio=increase,crop=$targetW:$targetH,boxblur=$blurRadius:5"
                filterComplex.add("[v_bg_in]$bgFilter[v_bg]")
                
                // FG: Scale to fit
                val fgFilter = "scale=$targetW:$targetH:force_original_aspect_ratio=decrease"
                filterComplex.add("[v_fg_in]$fgFilter[v_fg]")
                
                // Overlay FG on BG
                filterComplex.add("[v_bg][v_fg]overlay=(W-w)/2:(H-h)/2[v_sized]")
                
            } else {
                // FIT_COLOR
                // BG: Generate color source
                // Note: color source has infinite duration, need to trim to video duration? 
                // Easier to use pad filter on the video instead.
                
                // Scale video to fit
                // pad=targetW:targetH:(ow-iw)/2:(oh-ih)/2:color
                val scalePadFilter = "scale=$targetW:$targetH:force_original_aspect_ratio=decrease,pad=$targetW:$targetH:(ow-iw)/2:(oh-ih)/2:$padColor"
                filterComplex.add("$currentVideoLabel$scalePadFilter[v_sized]")
            }
            
            currentVideoLabel = "[v_sized]"
            
            // Apply color filters AFTER resizing/canvas
            val colorFilters = buildVideoFilters(editState.filters)
            if (colorFilters.isNotEmpty()) {
                filterComplex.add("$currentVideoLabel${colorFilters.joinToString(", ")}[v_canvas]")
                currentVideoLabel = "[v_canvas]"
            } else {
                // just rename for consistency
                filterComplex.add("${currentVideoLabel}null[v_canvas]")
                currentVideoLabel = "[v_canvas]"
            }
        }
        
        // 3. Text Overlays (Burn in)
        // Note: We apply text after canvas so it draws on the final frame
        val textFilters = buildTextOverlays(editState.textOverlays)
        if (textFilters.isNotEmpty()) {
            filterComplex.add("$currentVideoLabel${textFilters.joinToString(", ")}[v_text]")
            currentVideoLabel = "[v_text]"
        }
        
        // 4. Sticker Overlays (PIP)
        // Sticker inputs start after video (index 0) and all audio tracks
        var stickerInputStartIndex = 1 + audioTracks.size
        
        stickerOverlays.forEachIndexed { index, sticker ->
            val inputIndex = stickerInputStartIndex + index
            val scaledStickerLabel = "[sticker_scaled$index]"
            
            val scaleW = if(sticker.scale != 1.0f) "iw*${sticker.scale}" else "iw"
            val scaleH = if(sticker.scale != 1.0f) "ih*${sticker.scale}" else "ih"
            
            val stickerFilter = "scale=$scaleW:$scaleH"
            
            filterComplex.add("[$inputIndex:v]$stickerFilter$scaledStickerLabel")
            
            // Animation Expressions for Overlay x:y
            val targetX = sticker.position.x.toInt()
            val targetY = sticker.position.y.toInt()
            val animDuration = sticker.animationDurationMs / 1000.0
            val startTime = sticker.startTimeMs / 1000.0
            
            var xExpr = "$targetX"
            var yExpr = "$targetY"
            
            // Note: 'overlay' filter evaluates x/y expressions per frame
            when (sticker.animation) {
                OverlayAnimation.SLIDE_IN_LEFT -> {
                     // Start off-screen left (-w), move to targetX
                    xExpr = "if(lt(t,${startTime + animDuration}), -w + ($targetX - -w)*(t-$startTime)/$animDuration, $targetX)"
                }
                OverlayAnimation.SLIDE_IN_RIGHT -> {
                    // Start off-screen right (W), move to targetX (Requires knowing main W, usually 'main_w' or 'W' in overlay filter)
                    xExpr = "if(lt(t,${startTime + animDuration}), W + ($targetX - W)*(t-$startTime)/$animDuration, $targetX)"
                }
                OverlayAnimation.SLIDE_IN_UP -> {
                    // Start off-screen bottom (H), move to targetY
                    yExpr = "if(lt(t,${startTime + animDuration}), H + ($targetY - H)*(t-$startTime)/$animDuration, $targetY)"
                }
                OverlayAnimation.SLIDE_IN_DOWN -> {
                    // Start off-screen top (-h), move to targetY
                    yExpr = "if(lt(t,${startTime + animDuration}), -h + ($targetY - -h)*(t-$startTime)/$animDuration, $targetY)"
                }
                else -> {}
            }
            
            val enable = if (sticker.startTimeMs > 0 || sticker.endTimeMs < editState.videoDurationMs) {
                val startSec = sticker.startTimeMs / 1000.0
                val endSec = sticker.endTimeMs / 1000.0
                ":enable='between(t,$startSec,$endSec)'"
            } else ""
            
            val nextVideoLabel = "[v_sticker$index]"
            filterComplex.add("$currentVideoLabel$scaledStickerLabel overlay=x='$xExpr':y='$yExpr'$enable$nextVideoLabel")
            currentVideoLabel = nextVideoLabel
        }
        videoOutputLabel = currentVideoLabel

        // 5. Audio Filters & Mixing
        val audioMixParts = mutableListOf<String>()
        var audioInputIndex = 1 // Start after video input
        
        // Process Original Audio
        val originalTrack = editState.audioTracks.find { it.isOriginalAudio }
        val originalVolume = originalTrack?.volume ?: 1.0f
        
        var originalAudioLabel = "0:a"
        val originalAudioFilters = mutableListOf<String>()
        
        if (editState.playbackSpeed != 1.0f) originalAudioFilters.add("atempo=${editState.playbackSpeed}")
        if (editState.isReversed) originalAudioFilters.add("areverse")
        if (originalVolume != 1.0f) originalAudioFilters.add("volume=$originalVolume")
        
        if (originalAudioFilters.isNotEmpty()) {
            filterComplex.add("[$originalAudioLabel]${originalAudioFilters.joinToString(", ")}[a0]")
            originalAudioLabel = "[a0]"
        }
        audioMixParts.add(originalAudioLabel)

        // Process Added Audio Tracks
        audioTracks.forEach { track ->
            val trackDelayMs = track.startTimeMs
            val sourceStartMs = track.sourceStartMs
            val volume = track.volume
            
            val trackFilters = mutableListOf<String>()
            
            // 1. Trim source (skip intro)
            if (sourceStartMs > 0) {
                trackFilters.add("atrim=start=${sourceStartMs / 1000.0}")
                trackFilters.add("asetpts=PTS-STARTPTS")
            }
            
            // 2. Volume
            trackFilters.add("volume=$volume")

            // 3. Fades
            if (track.fadeInMs > 0) {
                trackFilters.add("afade=t=in:ss=0:d=${track.fadeInMs / 1000.0}")
            }
            if (track.fadeOutMs > 0) {
                val durationMs = track.endTimeMs - track.startTimeMs
                if (durationMs > track.fadeOutMs) {
                    val fadeOutStartSec = (durationMs - track.fadeOutMs) / 1000.0
                    trackFilters.add("afade=t=out:st=$fadeOutStartSec:d=${track.fadeOutMs / 1000.0}")
                }
            }
            
            // 4. Delay (Position in timeline)
            if (trackDelayMs > 0) {
                 trackFilters.add("adelay=${trackDelayMs}|${trackDelayMs}")
            }
            
            filterComplex.add("[$audioInputIndex:a]${trackFilters.joinToString(", ")}[a$audioInputIndex]")
            audioMixParts.add("[a$audioInputIndex]")
            audioInputIndex++
        }
        
        if (audioMixParts.size > 1) {
            filterComplex.add("${audioMixParts.joinToString("")}amix=inputs=${audioMixParts.size}:duration=first:dropout_transition=2[aout]")
            audioOutputLabel = "[aout]"
        } else if (audioMixParts.size == 1) {
             audioOutputLabel = audioMixParts[0]
        }

        if (filterComplex.isNotEmpty()) {
            cmdParts.add("-filter_complex")
            cmdParts.add("\"${filterComplex.joinToString(";")}\"")
        }

        // Map Outputs
        cmdParts.add("-map")
        cmdParts.add("\"$videoOutputLabel\"")
        
        cmdParts.add("-map")
        cmdParts.add("\"$audioOutputLabel\"")

        // Video codec
        cmdParts.add("-c:v")
        cmdParts.add("h264_mediacodec")
        cmdParts.add("-b:v")
        cmdParts.add(getBitrate(editState.compressionLevel))

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
                is VideoFilter.Vibrant -> "eq=saturation=1.5:contrast=1.2"
            }
        }
    }

    private fun buildTextOverlays(overlays: List<TextOverlay>): List<String> {
        return overlays.mapIndexed { index, overlay ->
            val targetX = overlay.position.x.toInt()
            val targetY = overlay.position.y.toInt()
            val color = colorToHex(overlay.color)
            val strokeColor = colorToHex(overlay.strokeColor)
            val fontPath = fontRepository.getFontPath(overlay.fontFamily)
            
            // Animation Parameters
            val animDuration = overlay.animationDurationMs / 1000.0
            val startTime = overlay.startTimeMs / 1000.0
            
            var xExpr = "$targetX"
            var yExpr = "$targetY"
            var alphaExpr = "1"
            
            when (overlay.animation) {
                OverlayAnimation.FADE_IN -> {
                    alphaExpr = "if(lt(t,${startTime + animDuration}),(t-$startTime)/$animDuration,1)"
                }
                OverlayAnimation.SLIDE_IN_LEFT -> {
                    // Start from left (-w), move to targetX
                    xExpr = "if(lt(t,${startTime + animDuration}), -tw + ($targetX - -tw)*(t-$startTime)/$animDuration, $targetX)"
                }
                OverlayAnimation.SLIDE_IN_RIGHT -> {
                    // Start from right (W), move to targetX
                    xExpr = "if(lt(t,${startTime + animDuration}), w + ($targetX - w)*(t-$startTime)/$animDuration, $targetX)"
                }
                OverlayAnimation.SLIDE_IN_UP -> {
                    // Start from bottom (H), move to targetY
                    yExpr = "if(lt(t,${startTime + animDuration}), h + ($targetY - h)*(t-$startTime)/$animDuration, $targetY)"
                }
                OverlayAnimation.SLIDE_IN_DOWN -> {
                    // Start from top (-h), move to targetY
                    yExpr = "if(lt(t,${startTime + animDuration}), -th + ($targetY - -th)*(t-$startTime)/$animDuration, $targetY)"
                }
                OverlayAnimation.POP_IN -> {
                    // Not easily supported in drawtext without complex zoompan, fallback to Fade
                     alphaExpr = "if(lt(t,${startTime + animDuration}),(t-$startTime)/$animDuration,1)"
                }
                else -> {}
            }

            buildString {
                append("drawtext=")
                
                if (fontPath != null) {
                    // Escape path for FFmpeg (colons and backslashes)
                    val escapedFontPath = fontPath.replace("\\", "\\\\").replace(":", "\\:")
                    append("fontfile='$escapedFontPath':")
                }
                
                append("text='${overlay.text.replace("'", "\\\\\\\\\\\\'")}':") // Escape single quotes
                append("fontsize=${overlay.fontSize.toInt()}:")
                append("fontcolor=$color:")
                
                // Use expressions for position
                append("x='$xExpr':y='$yExpr':")
                append("alpha='$alphaExpr'")

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