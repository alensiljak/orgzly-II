package com.orgzly.android.ui.sync

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.alensiljak.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.sync.SyncState
import com.orgzly.android.ui.util.copyPlainTextToClipboard

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SyncScreen(viewModel: SyncViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.observeAsState()
    var showDialog by remember { mutableStateOf(false) }

    val displayText = syncDisplayText(context, state)
    val isRunning = state?.isRunning() ?: false

    val infiniteTransition = rememberInfiniteTransition(label = "sync-icon")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .combinedClickable(
                    onClick = {
                        if (viewModel.isSyncRunning()) SyncRunner.stopSync()
                        else SyncRunner.startSync()
                    },
                    onLongClick = { showDialog = true },
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sync),
                contentDescription = stringResource(R.string.sync),
                modifier = if (isRunning) Modifier.rotate(rotation) else Modifier,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = displayText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            text = {
                SelectionContainer { Text(displayText) }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    context.copyPlainTextToClipboard("Sync output", displayText)
                    showDialog = false
                }) {
                    Text(stringResource(R.string.copy))
                }
            },
        )
    }
}

private fun syncDisplayText(context: Context, state: SyncState?): String {
    val desc = state?.getDescription(context)
    if (desc != null) return desc

    val time = AppPreferences.lastSuccessfulSyncTime(context)
    return if (time > 0) {
        val formatted = DateUtils.formatDateTime(
            context, time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME,
        )
        context.getString(R.string.last_sync_with_argument, formatted)
    } else {
        context.getString(R.string.sync)
    }
}
