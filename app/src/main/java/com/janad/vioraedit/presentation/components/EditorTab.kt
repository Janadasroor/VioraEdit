package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class EditorTab(val title: String, val icon: ImageVector) {
    TRIM("Trim", Icons.Default.ContentCut),
    CANVAS("Canvas", Icons.Default.AspectRatio),
    FILTERS("Filters", Icons.Default.FilterAlt),
    AUDIO("Audio", Icons.Default.MusicNote),
    TEXT("Text", Icons.Default.TextFields),
    STICKERS("Stickers", Icons.Default.AddPhotoAlternate),
    SPEED("Speed", Icons.Default.Speed)
}

@Composable
fun EditorTabRow(
    selectedTab: EditorTab,
    onTabSelected: (EditorTab) -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier.fillMaxWidth(),
        edgePadding = 8.dp
    ) {
        EditorTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = { Text(tab.title) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}
