package com.orgzly.android.ui.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cc.alensiljak.orgzly.R
import com.orgzly.android.ui.NoteStates

object NoteStateDialog {
    @JvmStatic
    fun show(context: Context, currentState: String?, onSelection: (String) -> Unit, onClear: () -> Unit): AlertDialog {
        val states = NoteStates.fromPreferences(context)

        val currentStateIndex = if (currentState != null) states.indexOf(currentState) else -1

        return MaterialAlertDialogBuilder(context)
                .setTitle(R.string.state)
                .setSingleChoiceItems(states.array, currentStateIndex) { d, which ->
                    onSelection(states[which])
                    d.dismiss()
                }
                .setNeutralButton(R.string.clear) { _, _ ->
                    onClear()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }
}

@Composable
fun NoteStateDialog(
    currentState: String?,
    onSelection: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val states = remember { NoteStates.fromPreferences(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.state)) },
        text = {
            Column {
                states.array.forEach { state ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelection(state); onDismiss() }
                    ) {
                        RadioButton(
                            selected = state == currentState,
                            onClick = { onSelection(state); onDismiss() }
                        )
                        Text(state)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                TextButton(onClick = { onClear(); onDismiss() }) {
                    Text(stringResource(R.string.clear))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}
