package com.conduit.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.conduit.app.data.CustomView
import com.conduit.app.performHapticTick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomViewChipsBar(
    views: List<CustomView>,
    activeViewId: String?,
    onSelectView: (String?) -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (views.isEmpty()) return

    val context = LocalContext.current
    val lazyListState = rememberLazyListState()

    // Auto-scroll to active chip when in collapsed mode
    LaunchedEffect(activeViewId, isExpanded) {
        if (!isExpanded && activeViewId != null) {
            val index = views.indexOfFirst { it.id == activeViewId }
            if (index >= 0) {
                lazyListState.animateScrollToItem(index)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize()
    ) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified
        ) {
            SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
                val maxAvailableWidth = constraints.maxWidth
                val spacingPx = 8.dp.roundToPx()

                // Step 1: Measure chip widths unconstrained horizontally to detect if they overflow a single line
                val measurePlaceables = subcompose("measure_chips") {
                    views.forEach { view ->
                        CustomViewChipItem(
                            view = view,
                            isSelected = activeViewId == view.id,
                            onClick = {}
                        )
                    }
                }.map { it.measure(Constraints()) }

                val totalChipsWidth = measurePlaceables.sumOf { it.width } +
                    if (measurePlaceables.size > 1) (measurePlaceables.size - 1) * spacingPx else 0
                val overflows = totalChipsWidth > maxAvailableWidth

                // Step 2: Compose and measure the actual UI based on overflow and expansion state
                val actualContent = subcompose("actual_content") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = if (isExpanded) Alignment.Top else Alignment.CenterVertically
                    ) {
                        if (overflows) {
                            if (isExpanded) {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    views.forEach { view ->
                                        val isSelected = activeViewId == view.id
                                        CustomViewChipItem(
                                            view = view,
                                            isSelected = isSelected,
                                            onClick = {
                                                performHapticTick(context)
                                                onSelectView(if (isSelected) null else view.id)
                                            }
                                        )
                                    }
                                }
                            } else {
                                LazyRow(
                                    state = lazyListState,
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(views, key = { it.id }) { view ->
                                        val isSelected = activeViewId == view.id
                                        CustomViewChipItem(
                                            view = view,
                                            isSelected = isSelected,
                                            onClick = {
                                                performHapticTick(context)
                                                onSelectView(if (isSelected) null else view.id)
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedIconButton(
                                onClick = {
                                    performHapticTick(context)
                                    onToggleExpanded()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse custom views" else "Expand custom views",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Does not overflow: single row, no button needed
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                views.forEach { view ->
                                    val isSelected = activeViewId == view.id
                                    CustomViewChipItem(
                                        view = view,
                                        isSelected = isSelected,
                                        onClick = {
                                            performHapticTick(context)
                                            onSelectView(if (isSelected) null else view.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                val placeable = actualContent.first().measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
        }
    }
}

@Composable
private fun CustomViewChipItem(
    view: CustomView,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(view.name) },
        leadingIcon = if (isSelected) {
            { Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp)) }
        } else null
    )
}
