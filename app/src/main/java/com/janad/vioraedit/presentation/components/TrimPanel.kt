package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janad.vioraedit.data.models.AspectRatio

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimPanel(
    aspectRatio: AspectRatio,
    onAspectRatioChanged: (AspectRatio) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Crop Aspect Ratio",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectRatio.values().forEach { ratio ->
                FilterChip(
                    selected = aspectRatio == ratio,
                    onClick = { onAspectRatioChanged(ratio) },
                    label = { Text(ratio.ratio) },
                    leadingIcon = if (aspectRatio == ratio) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }
    }
}
