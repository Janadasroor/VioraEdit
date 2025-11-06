// File: presentation/components/FiltersPanel.kt
package com.janad.vioraedit.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.janad.vioraedit.data.models.VideoFilter

/**
 * Panel for selecting and adjusting video filters
 */
@Composable
fun FiltersPanel(
    appliedFilters: List<VideoFilter>,
    onFilterAdded: (VideoFilter) -> Unit,
    onFilterRemoved: (VideoFilter) -> Unit,
    onFilterUpdated: (Int, VideoFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilterCategory by remember { mutableStateOf<FilterCategory?>(null) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Filter categories
        Text(
            text = "Filters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(FilterCategory.values()) { category ->
                FilterCategoryChip(
                    category = category,
                    isSelected = selectedFilterCategory == category,
                    onClick = {
                        selectedFilterCategory = if (selectedFilterCategory == category) null else category
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Show filter options when category is selected
        selectedFilterCategory?.let { category ->
            FilterOptionsPanel(
                category = category,
                appliedFilters = appliedFilters,
                onFilterAdded = onFilterAdded,
                onFilterRemoved = onFilterRemoved,
                onFilterUpdated = onFilterUpdated
            )
        }
        
        // Show applied filters
        if (appliedFilters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            AppliedFiltersSection(
                filters = appliedFilters,
                onFilterRemoved = onFilterRemoved,
                onFilterUpdated = onFilterUpdated
            )
        }
    }
}

@Composable
fun FilterCategoryChip(
    category: FilterCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = category.name,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FilterOptionsPanel(
    category: FilterCategory,
    appliedFilters: List<VideoFilter>,
    onFilterAdded: (VideoFilter) -> Unit,
    onFilterRemoved: (VideoFilter) -> Unit,
    onFilterUpdated: (Int, VideoFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        when (category) {
            FilterCategory.ADJUST -> {
                AdjustmentFilters(appliedFilters, onFilterAdded, onFilterUpdated)
            }
            FilterCategory.EFFECTS -> {
                EffectsFilters(appliedFilters, onFilterAdded, onFilterRemoved)
            }
            FilterCategory.COLOR -> {
                ColorFilters(appliedFilters, onFilterAdded, onFilterRemoved)
            }
        }
    }
}

@Composable
fun AdjustmentFilters(
    appliedFilters: List<VideoFilter>,
    onFilterAdded: (VideoFilter) -> Unit,
    onFilterUpdated: (Int, VideoFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Brightness
        val brightnessFilter = appliedFilters.find { it is VideoFilter.Brightness } as? VideoFilter.Brightness
        FilterSlider(
            label = "Brightness",
            value = brightnessFilter?.value ?: 0f,
            valueRange = -1f..1f,
            onValueChange = { value ->
                val filter = VideoFilter.Brightness(value)
                val index = appliedFilters.indexOfFirst { it is VideoFilter.Brightness }
                if (index >= 0) {
                    onFilterUpdated(index, filter)
                } else {
                    onFilterAdded(filter)
                }
            }
        )
        
        // Contrast
        val contrastFilter = appliedFilters.find { it is VideoFilter.Contrast } as? VideoFilter.Contrast
        FilterSlider(
            label = "Contrast",
            value = contrastFilter?.value ?: 1f,
            valueRange = 0f..2f,
            onValueChange = { value ->
                val filter = VideoFilter.Contrast(value)
                val index = appliedFilters.indexOfFirst { it is VideoFilter.Contrast }
                if (index >= 0) {
                    onFilterUpdated(index, filter)
                } else {
                    onFilterAdded(filter)
                }
            }
        )
        
        // Saturation
        val saturationFilter = appliedFilters.find { it is VideoFilter.Saturation } as? VideoFilter.Saturation
        FilterSlider(
            label = "Saturation",
            value = saturationFilter?.value ?: 1f,
            valueRange = 0f..2f,
            onValueChange = { value ->
                val filter = VideoFilter.Saturation(value)
                val index = appliedFilters.indexOfFirst { it is VideoFilter.Saturation }
                if (index >= 0) {
                    onFilterUpdated(index, filter)
                } else {
                    onFilterAdded(filter)
                }
            }
        )
    }
}

@Composable
fun EffectsFilters(
    appliedFilters: List<VideoFilter>,
    onFilterAdded: (VideoFilter) -> Unit,
    onFilterRemoved: (VideoFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Blur
        val blurFilter = appliedFilters.find { it is VideoFilter.Blur } as? VideoFilter.Blur
        FilterSlider(
            label = "Blur",
            value = blurFilter?.radius ?: 0f,
            valueRange = 0f..20f,
            onValueChange = { value ->
                if (value > 0) {
                    onFilterAdded(VideoFilter.Blur(value))
                } else {
                    blurFilter?.let { onFilterRemoved(it) }
                }
            }
        )
        
        // Vignette
        val vignetteFilter = appliedFilters.find { it is VideoFilter.Vignette } as? VideoFilter.Vignette
        FilterSlider(
            label = "Vignette",
            value = vignetteFilter?.amount ?: 0f,
            valueRange = 0f..1f,
            onValueChange = { value ->
                if (value > 0) {
                    onFilterAdded(VideoFilter.Vignette(value))
                } else {
                    vignetteFilter?.let { onFilterRemoved(it) }
                }
            }
        )
    }
}

@Composable
fun ColorFilters(
    appliedFilters: List<VideoFilter>,
    onFilterAdded: (VideoFilter) -> Unit,
    onFilterRemoved: (VideoFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FilterPresetCard(
                name = "Grayscale",
                isApplied = appliedFilters.any { it is VideoFilter.Grayscale },
                onClick = {
                    val existing = appliedFilters.find { it is VideoFilter.Grayscale }
                    if (existing != null) {
                        onFilterRemoved(existing)
                    } else {
                        onFilterAdded(VideoFilter.Grayscale())
                    }
                }
            )
        }
        
        item {
            FilterPresetCard(
                name = "Sepia",
                isApplied = appliedFilters.any { it is VideoFilter.Sepia },
                onClick = {
                    val existing = appliedFilters.find { it is VideoFilter.Sepia }
                    if (existing != null) {
                        onFilterRemoved(existing)
                    } else {
                        onFilterAdded(VideoFilter.Sepia())
                    }
                }
            )
        }
        
        item {
            FilterPresetCard(
                name = "Vintage",
                isApplied = appliedFilters.any { it is VideoFilter.Vintage },
                onClick = {
                    val existing = appliedFilters.find { it is VideoFilter.Vintage }
                    if (existing != null) {
                        onFilterRemoved(existing)
                    } else {
                        onFilterAdded(VideoFilter.Vintage())
                    }
                }
            )
        }
    }
}

@Composable
fun FilterSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun FilterPresetCard(
    name: String,
    isApplied: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(100.dp, 120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isApplied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isApplied) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isApplied) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AppliedFiltersSection(
    filters: List<VideoFilter>,
    onFilterRemoved: (VideoFilter) -> Unit,
    onFilterUpdated: (Int, VideoFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Applied Filters (${filters.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                AppliedFilterChip(
                    filter = filter,
                    onRemove = { onFilterRemoved(filter) }
                )
            }
        }
    }
}

@Composable
fun AppliedFilterChip(
    filter: VideoFilter,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = filter.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove filter",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

enum class FilterCategory(val icon: ImageVector) {
    ADJUST(Icons.Default.Tune),
    EFFECTS(Icons.Default.AutoFixHigh),
    COLOR(Icons.Default.Palette)
}