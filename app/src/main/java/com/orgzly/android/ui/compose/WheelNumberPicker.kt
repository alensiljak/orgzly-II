package com.orgzly.android.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/**
 * A drum-roll style picker using a snapping LazyColumn.
 * Shows 3 items at once; the centred one is the selected item.
 * [selectedIndex] sets the initial scroll position only.
 * [onSelectionChanged] fires whenever the scroll settles on a new item.
 */
@Composable
fun WheelNumberPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Tracks the centred item in real time for the highlight — no allocation per frame.
    val highlightedIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // Report confirmed selection only after the scroll fully settles.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .drop(1) // skip the initial "not scrolling" emission
            .collect {
                onSelectionChanged(listState.firstVisibleItemIndex.coerceIn(0, items.lastIndex))
            }
    }

    Box(modifier = modifier) {
        // Selection highlight band behind the centre row
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            // Padding equal to one item height so the first/last items can centre
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.height(itemHeight * 3),
        ) {
            itemsIndexed(items) { index, item ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(itemHeight)
                        .fillParentMaxWidth(),
                ) {
                    val isSelected = index == highlightedIndex
                    Text(
                        text = item,
                        style = if (isSelected) {
                            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        },
                    )
                }
            }
        }
    }
}
