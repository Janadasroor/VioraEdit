package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janad.vioraedit.data.models.CompressionLevel
import com.janad.vioraedit.data.models.OutputFormat
import com.janad.vioraedit.data.models.VideoEditState

@Composable
fun ExportDialog(
    currentSettings: VideoEditState,
    onDismiss: () -> Unit,
    onExport: (CompressionLevel, OutputFormat) -> Unit
) {
    var selectedCompression by remember { mutableStateOf(currentSettings.compressionLevel) }
    var selectedFormat by remember { mutableStateOf(currentSettings.outputFormat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Compression Quality", style = MaterialTheme.typography.titleSmall)
                CompressionLevel.values().forEach { level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedCompression == level,
                            onClick = { selectedCompression = level }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(level.name)
                    }
                }

                HorizontalDivider()

                Text("Output Format", style = MaterialTheme.typography.titleSmall)
                OutputFormat.values().forEach { format ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedFormat == format,
                            onClick = { selectedFormat = format }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(format.extension.uppercase())
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onExport(selectedCompression, selectedFormat) }) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
