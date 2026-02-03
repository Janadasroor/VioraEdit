package com.janad.vioraedit.domain

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import timber.log.Timber
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentOutputFile: String? = null

    fun startRecording(): String? {
        val outputDir = context.cacheDir
        val outputFile = File.createTempFile("voiceover_", ".aac", outputDir)
        currentOutputFile = outputFile.absolutePath

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
                Timber.d("Recording started: ${outputFile.absolutePath}")
            } catch (e: IOException) {
                Timber.e(e, "prepare() failed")
                return null
            } catch (e: Exception) {
                Timber.e(e, "start() failed")
                return null
            }
        }
        
        return currentOutputFile
    }

    fun stopRecording(): String? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            Timber.d("Recording stopped: $currentOutputFile")
            currentOutputFile
        } catch (e: Exception) {
            Timber.e(e, "stop() failed")
            null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.release()
            recorder = null
            currentOutputFile?.let {
                File(it).delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "cancel() failed")
        }
    }
}