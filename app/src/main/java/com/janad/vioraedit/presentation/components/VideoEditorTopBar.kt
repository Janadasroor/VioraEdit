package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorTopBar(
    onClose: () -> Unit,
    onExport: () -> Unit,
    onApplyChanges: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveDraft: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    isProcessing: Boolean,
    hasUnappliedChanges: Boolean,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
           // Text("Video Editor")
                },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        },
        actions = {
            // Save Draft button
            IconButton(
                onClick = onSaveDraft,
                enabled = !isProcessing
            ) {
                 Icon(Icons.Default.Save, contentDescription = "Save Draft")
            }

            // Undo button
            IconButton(
                onClick = onUndo,
                enabled = canUndo && !isProcessing
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo && !isProcessing) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }

            // Redo button
            IconButton(
                onClick = onRedo,
                enabled = canRedo && !isProcessing
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo && !isProcessing) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }

            Spacer(Modifier.width(8.dp))

            // Apply changes button
            Button(
                onClick = onApplyChanges,
                enabled = !isProcessing && hasUnappliedChanges,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasUnappliedChanges) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Apply")
            }

            Spacer(Modifier.width(8.dp))

            // Export button
            Button(
                onClick = onExport,
                enabled = !isProcessing,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Export")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}
