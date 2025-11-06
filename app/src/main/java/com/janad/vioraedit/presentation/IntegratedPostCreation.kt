package com.janad.vioraedit.presentation

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.navigation.NavController

/**
 * Integration example for existing social media app
 */
//@Composable
//fun IntegratedPostCreation(
//    navController: NavController,
//    selectedMediaUri: Uri?
//) {
//    var showVideoEditor by remember { mutableStateOf(false) }
//    var editedVideoPath by remember { mutableStateOf<String?>(null) }
//
//    if (selectedMediaUri != null && showVideoEditor) {
//        VideoEditorScreen(
//            videoUri = selectedMediaUri.toString(),
//            onClose = {
//                showVideoEditor = false
//            },
//            onExportComplete = { outputPath ->
//                editedVideoPath = outputPath
//                showVideoEditor = false
//                // Navigate to post creation screen with edited video
//                navController.navigate("create_post?video=$outputPath")
//            }
//        )
//    }
//}