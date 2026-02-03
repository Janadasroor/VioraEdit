package com.janad.vioraedit.data.models

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject

import dagger.hilt.android.qualifiers.ApplicationContext

class DraftRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val draftsDir by lazy { File(context.filesDir, "drafts").apply { mkdirs() } }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun saveDraft(state: VideoEditState, name: String = "Draft_${System.currentTimeMillis()}"): Boolean {
        return try {
            val file = File(draftsDir, "$name.json")
            val jsonString = json.encodeToString(state)
            file.writeText(jsonString)
            Timber.d("Draft saved: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save draft")
            false
        }
    }

    fun loadDrafts(): List<Pair<String, VideoEditState>> {
        return try {
            draftsDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        val state = json.decodeFromString<VideoEditState>(file.readText())
                        file.nameWithoutExtension to state
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load draft: ${file.name}")
                        null
                    }
                } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to list drafts")
            emptyList()
        }
    }
    
    fun deleteDraft(name: String) {
        try {
             File(draftsDir, "$name.json").delete()
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete draft")
        }
    }
}
