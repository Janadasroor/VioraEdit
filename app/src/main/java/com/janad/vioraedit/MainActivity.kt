package com.janad.vioraedit

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.janad.vioraedit.presentation.VideoEditorScreen
import com.janad.vioraedit.presentation.VideoEditorViewModel
import com.janad.vioraedit.ui.theme.VioraEditTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel=hiltViewModel<VideoEditorViewModel>()
            VioraEditTheme(
                darkTheme = true
            ) {
                  VideoEditorApp(viewModel)
              }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VideoEditorApp(viewModel: VideoEditorViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var selectedVideoUri by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    // Request permissions
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    )

    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedVideoUri = it.toString()
            Log.d("VideoEditor", "Selected video URI: $it")
            showEditor = true
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    if (showEditor && selectedVideoUri != null) {
        VideoEditorScreen(
            videoUri = selectedVideoUri!!,
            onClose = {
                showEditor = false
                selectedVideoUri = null
            },
            onExportComplete = { outputPath ->
                Toast.makeText(
                    context,
                    "Video exported to: $outputPath",
                    Toast.LENGTH_LONG
                ).show()
                showEditor = false
                selectedVideoUri = null
            },
            viewModel = viewModel
        )
    } else {
        // Landing screen
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Video Editor",
                    style = MaterialTheme.typography.headlineMedium
                )

                Button(
                    onClick = {
                        videoPickerLauncher.launch("video/*")
                        if (permissionsState.allPermissionsGranted) {

                        } else {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    }
                ) {
                    Text("Select Video")
                }

                if (!permissionsState.allPermissionsGranted) {
                    Text(
                        text = "Please grant permissions to use video editor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}