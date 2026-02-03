package com.janad.vioraedit.domain

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ThumbnailGenerator(private val context: Context) {

    suspend fun generateThumbnails(
        videoUri: String,
        count: Int,
        width: Int = 200,
        height: Int = 200
    ): List<String> = withContext(Dispatchers.IO) {
        val thumbnails = mutableListOf<String>()
        val retriever = MediaMetadataRetriever()
        
        try {
            retriever.setDataSource(context, Uri.parse(videoUri))
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            
            if (durationMs > 0) {
                val interval = durationMs / count
                
                for (i in 0 until count) {
                    val timeUs = i * interval * 1000
                    // OPTION_CLOSEST_SYNC is faster but less accurate. 
                    // OPTION_CLOSEST is accurate but slower.
                    val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    
                    if (bitmap != null) {
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                        val file = File(context.cacheDir, "thumb_${System.currentTimeMillis()}_$i.jpg")
                        FileOutputStream(file).use { out ->
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        }
                        thumbnails.add(file.absolutePath)
                        if (bitmap != scaledBitmap) {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
        
        thumbnails
    }
}