// File: domain/AdvancedVideoProcessor.kt
package com.janad.vioraedit.domain;

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Advanced video processing features
 */
class AdvancedVideoProcessor(private val context: Context) {

    /**
     * Merge multiple video clips into one
     */
    suspend fun mergeVideos(
        inputVideos: List<String>,
        outputPath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create concat file
            val concatFile = createConcatFile(inputVideos)

            val command = "-f concat -safe 0 -i \"$concatFile\" " +
                    "-c copy -y \"$outputPath\""

            var success = false
            FFmpegKit.executeAsync(command, { session ->
                success = com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)
            }, null, { stats ->
                // Calculate progress
                onProgress(0.5f) // Simplified progress
            })

            success
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create video collage (2x2 grid)
     */
    suspend fun createCollage(
        videos: List<String>, // Max 4 videos
        outputPath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        require(videos.size in 2..4) { "Collage requires 2-4 videos" }

        val command = when (videos.size) {
            2 -> buildString {
                append("-i \"${videos[0]}\" -i \"${videos[1]}\" ")
                append("-filter_complex \"")
                append("[0:v]scale=iw/2:ih[v0];")
                append("[1:v]scale=iw/2:ih[v1];")
                append("[v0][v1]hstack=inputs=2[v]\" ")
                append("-map \"[v]\" -y \"$outputPath\"")
            }
            3 -> buildString {
                append("-i \"${videos[0]}\" -i \"${videos[1]}\" -i \"${videos[2]}\" ")
                append("-filter_complex \"")
                append("[0:v]scale=iw/2:ih/2[v0];")
                append("[1:v]scale=iw/2:ih/2[v1];")
                append("[2:v]scale=iw/2:ih/2[v2];")
                append("[v0][v1]hstack[top];")
                append("[top][v2]vstack[v]\" ")
                append("-map \"[v]\" -y \"$outputPath\"")
            }
            4 -> buildString {
                append("-i \"${videos[0]}\" -i \"${videos[1]}\" ")
                append("-i \"${videos[2]}\" -i \"${videos[3]}\" ")
                append("-filter_complex \"")
                append("[0:v]scale=iw/2:ih/2[v0];")
                append("[1:v]scale=iw/2:ih/2[v1];")
                append("[2:v]scale=iw/2:ih/2[v2];")
                append("[3:v]scale=iw/2:ih/2[v3];")
                append("[v0][v1]hstack[top];")
                append("[v2][v3]hstack[bottom];")
                append("[top][bottom]vstack[v]\" ")
                append("-map \"[v]\" -y \"$outputPath\"")
            }
            else -> ""
        }

        var success = false
        FFmpegKit.executeAsync(command, { session ->
            success = com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)
        }, null, { stats ->
            onProgress(0.5f)
        })

        success
    }

    /**
     * Extract frames from video
     */
    suspend fun extractFrames(
        videoPath: String,
        outputDir: String,
        fps: Int = 1
    ): List<String> = withContext(Dispatchers.IO) {
        val command = "-i \"$videoPath\" -vf fps=$fps \"$outputDir/frame_%04d.jpg\""

        FFmpegKit.execute(command)

        // Return list of frame paths
        val frameFiles = java.io.File(outputDir).listFiles { file ->
            file.name.startsWith("frame_") && file.name.endsWith(".jpg")
        }?.map { it.absolutePath } ?: emptyList()

        frameFiles
    }

    /**
     * Add green screen effect (chroma key)
     */
    suspend fun addGreenScreenEffect(
        videoPath: String,
        backgroundPath: String,
        outputPath: String,
        keyColor: String = "0x00FF00", // Green
        similarity: Float = 0.3f
    ): Boolean = withContext(Dispatchers.IO) {
        val command = "-i \"$videoPath\" -i \"$backgroundPath\" " +
                "-filter_complex \"" +
                "[0:v]chromakey=$keyColor:$similarity:0.1[ckout];\" " +
                "[1:v][ckout]overlay[v]\" " +
                "-map \"[v]\" -c:v libx264 -crf 23 -preset medium " +
                "-y \"$outputPath\""

        var success = false
        FFmpegKit.executeAsync(command) { session ->
            success = com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)
        }

        success
    }

    /**
     * Add picture-in-picture effect
     */
    suspend fun addPictureInPicture(
        mainVideoPath: String,
        pipVideoPath: String,
        outputPath: String,
        position: PiPPosition = PiPPosition.TOP_RIGHT,
        scale: Float = 0.25f
    ): Boolean = withContext(Dispatchers.IO) {
        val overlayPosition = when (position) {
            PiPPosition.TOP_LEFT -> "10:10"
            PiPPosition.TOP_RIGHT -> "main_w-overlay_w-10:10"
            PiPPosition.BOTTOM_LEFT -> "10:main_h-overlay_h-10"
            PiPPosition.BOTTOM_RIGHT -> "main_w-overlay_w-10:main_h-overlay_h-10"
        }

        val command = "-i \"$mainVideoPath\" -i \"$pipVideoPath\" " +
                "-filter_complex \"" +
                "[1:v]scale=iw*$scale:ih*$scale[pip];\"" +
        "[0:v][pip]overlay=$overlayPosition[v]\" " +
                "-map \"[v]\" -map 0:a? -c:v libx264 -crf 23 -preset medium " +
                "-y \"$outputPath\""

        var success = false
        FFmpegKit.executeAsync(command) { session ->
            success = com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)
        }

        success
    }

    /**
     * Add transition between clips
     */
    suspend fun addTransition(
        video1Path: String,
        video2Path: String,
        outputPath: String,
        transitionType: TransitionType = TransitionType.FADE,
        duration: Float = 1.0f
    ): Boolean = withContext(Dispatchers.IO) {
        val transitionFilter = when (transitionType) {
            TransitionType.FADE -> "fade"
            TransitionType.WIPE_LEFT -> "wipeleft"
            TransitionType.WIPE_RIGHT -> "wiperight"
            TransitionType.SLIDE_LEFT -> "slideleft"
            TransitionType.SLIDE_RIGHT -> "slideright"
            TransitionType.DISSOLVE -> "dissolve"
        }

        val command = "-i \"$video1Path\" -i \"$video2Path\" " +
                "-filter_complex \"" +
                "[0][1]xfade=transition=$transitionFilter:duration=$duration:offset=0[v]\" " +
                "-map \"[v]\" -c:v libx264 -crf 23 -preset medium " +
                "-y \"$outputPath\""

        var success = false
        FFmpegKit.executeAsync(command) { session ->
            success = com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)
        }

        success
    }

    /**
     * Apply stabilization to shaky videos
     */
    suspend fun stabilizeVideo(
        inputPath: String,
        outputPath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        // Two-pass stabilization
        val transformsFile = "${context.cacheDir}/transforms.trf"

        // Pass 1: Analyze
        val analyzeCommand = "-i \"$inputPath\" " +
                "-vf vidstabdetect=shakiness=10:accuracy=15:result=\"$transformsFile\" " +
                "-f null -"

        FFmpegKit.execute(analyzeCommand)
        onProgress(0.5f)

        // Pass 2: Transform
        val transformCommand = "-i \"$inputPath\" " +
                "-vf vidstabtransform=input=\"$transformsFile\":smoothing=30 " +
                "-c:v libx264 -crf 23 -preset medium " +
                "-y \"$outputPath\""

        var success = false
        FFmpegKit.executeAsync(transformCommand) { session ->
            success = com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)
            onProgress(1.0f)
        }

        success
    }

    /**
     * Generate video thumbnail
     */
    suspend fun generateThumbnail(
        videoPath: String,
        outputPath: String,
        timeSeconds: Float = 1.0f
    ): Boolean = withContext(Dispatchers.IO) {
        val command = "-i \"$videoPath\" -ss $timeSeconds -vframes 1 " +
                "-vf scale=320:-1 -y \"$outputPath\""

        val session = FFmpegKit.execute(command)
        com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)
    }

    /**
     * Compress video with custom bitrate
     */
    suspend fun compressVideoCustom(
        inputPath: String,
        outputPath: String,
        targetBitrate: String = "1M",
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val command = "-i \"$inputPath\" " +
                "-c:v libx264 -b:v $targetBitrate -maxrate $targetBitrate " +
                "-bufsize ${targetBitrate.replace("M", "000k")} " +
                "-vf scale=-2:720 " +
                "-c:a aac -b:a 128k " +
                "-preset fast -movflags +faststart " +
                "-y \"$outputPath\""

        var success = false
        FFmpegKit.executeAsync(command, { session ->
            success = com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)
        }, null, { stats ->
            // Calculate progress based on time
            onProgress(0.5f)
        })

        success
    }

    private fun createConcatFile(videos: List<String>): String {
        val concatFile = java.io.File(context.cacheDir, "concat_list.txt")
        concatFile.writeText(videos.joinToString("\n") { "file '$it'" })
        return concatFile.absolutePath
    }
}

enum class PiPPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

enum class TransitionType {
    FADE,
    WIPE_LEFT,
    WIPE_RIGHT,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    DISSOLVE
}