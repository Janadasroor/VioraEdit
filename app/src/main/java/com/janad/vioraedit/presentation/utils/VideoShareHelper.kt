package com.janad.vioraedit.presentation.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

class VideoShareHelper(private val context: Context) {

    fun shareVideo(videoPath: String) {
        try {
            val file = File(videoPath)
            if (!file.exists()) {
                Timber.e("Video file does not exist: $videoPath")
                return
            }

            // Get content URI using FileProvider
            // Note: Application ID needs to be correct. 
            // Usually ${applicationId}.provider in AndroidManifest
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Video")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            Timber.e(e, "Failed to share video")
        }
    }

    /**
     * Launch specific app if available, otherwise generic share
     */
    fun shareToSocialApp(videoPath: String, packageName: String) {
        try {
            val file = File(videoPath)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                setPackage(packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Check if app is installed
            if (intent.resolveActivity(context.packageManager) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                // Fallback to generic share
                shareVideo(videoPath)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to share to $packageName")
            shareVideo(videoPath)
        }
    }
}
