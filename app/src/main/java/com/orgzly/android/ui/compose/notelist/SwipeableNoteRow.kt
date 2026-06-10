package com.orgzly.android.ui.compose.notelist

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Wraps a note row with horizontal-drag detection. On a sufficient swipe it reports the swipe
 * direction (-1 left, +1 right) and the swipe end x / start y in window coordinates.
 * Taps and vertical scrolling are unaffected.
 */
@Composable
fun SwipeableNoteRow(
    onSwipe: (direction: Int, x: Int, y: Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val thresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    var windowOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { windowOffset = it.positionInWindow() }
            .pointerInput(Unit) {
                var totalDx = 0f
                var lastPos = Offset.Zero
                detectHorizontalDragGestures(
                    onDragStart = { off -> totalDx = 0f; lastPos = off },
                    onHorizontalDrag = { change, delta -> totalDx += delta; lastPos = change.position },
                    onDragEnd = {
                        if (abs(totalDx) > thresholdPx) {
                            val direction = if (totalDx < 0) -1 else 1
                            onSwipe(
                                direction,
                                (windowOffset.x + lastPos.x).toInt(),
                                (windowOffset.y + lastPos.y).toInt(),
                            )
                        }
                    },
                )
            },
    ) {
        content()
    }
}
