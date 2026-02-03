package com.janad.vioraedit.presentation

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.navigation.NavController
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize

/**
 * Integration example for existing social media app
 */
enum class PostCreationScreen {
    HOME, EDITOR, DRAFTS
}

@Composable
fun IntegratedPostCreation(
    navController: NavController,
    selectedMediaUri: Uri?
) {
    var currentScreen by remember { mutableStateOf(PostCreationScreen.HOME) }
    var editorVideoUri by remember { mutableStateOf<String?>(null) }
    var loadedDraftState by remember { mutableStateOf<com.janad.vioraedit.data.models.VideoEditState?>(null) }
    var editedVideoPath by remember { mutableStateOf<String?>(null) }
    
    // Auto-open editor when media is passed from outside
    LaunchedEffect(selectedMediaUri) {
        if (selectedMediaUri != null && editorVideoUri == null) {
            editorVideoUri = selectedMediaUri.toString()
            currentScreen = PostCreationScreen.EDITOR
        }
    }

    // ViewModel setup
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<VideoEditorViewModel>(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                // In real app, use Hilt
                val repository = com.janad.vioraedit.data.models.DraftRepository(context)
                return VideoEditorViewModel(context, repository) as T
            }
        }
    )

    when (currentScreen) {
        PostCreationScreen.HOME -> {
             androidx.compose.foundation.layout.Box(
                 modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                 contentAlignment = androidx.compose.ui.Alignment.Center
             ) {
                 androidx.compose.foundation.layout.Column(
                     horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                     verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                 ) {
                     androidx.compose.material3.Text("Create New Post", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                     
                     androidx.compose.material3.Button(
                         onClick = { /* In real app, trigger media picker here */ }
                     ) {
                         androidx.compose.material3.Text("Select Video from Gallery")
                     }
                     
                     androidx.compose.material3.OutlinedButton(
                         onClick = { currentScreen = PostCreationScreen.DRAFTS }
                     ) {
                         androidx.compose.material3.Text("My Drafts")
                     }
                 }
             }
        }
        
        PostCreationScreen.DRAFTS -> {
            DraftsScreen(
                onDraftSelected = { state ->
                    loadedDraftState = state
                    editorVideoUri = state.videoUri
                    currentScreen = PostCreationScreen.EDITOR
                },
                onBack = { currentScreen = PostCreationScreen.HOME }
            )
        }
        
        PostCreationScreen.EDITOR -> {
            if (editorVideoUri != null) {
                // If loading a draft, inject state
                LaunchedEffect(loadedDraftState) {
                    loadedDraftState?.let {
                        viewModel.onIntent(VideoEditorIntent.LoadDraft(it))
                    }
                }

                VideoEditorScreen(
                    videoUri = editorVideoUri!!,
                    onClose = {
                        currentScreen = PostCreationScreen.HOME
                        editorVideoUri = null
                        loadedDraftState = null
                    },
                    onExportComplete = { outputPath ->
                        editedVideoPath = outputPath
                        currentScreen = PostCreationScreen.HOME
                        // In real app: navController.navigate("create_post?video=$outputPath")
                    },
                    viewModel = viewModel
                )
            }
        }
    }
}