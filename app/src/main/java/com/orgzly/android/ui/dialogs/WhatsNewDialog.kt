package com.orgzly.android.ui.dialogs

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cc.alensiljak.orgzly.BuildConfig
import cc.alensiljak.orgzly.R
import com.orgzly.android.ui.util.getLayoutInflater
import com.orgzly.android.util.MiscUtils

object WhatsNewDialog {
    fun create(context: Context): AlertDialog {
        val layoutView = context.getLayoutInflater().inflate(R.layout.dialog_whats_new, null, false)

        layoutView.findViewById<TextView>(R.id.dialog_whats_new_message).apply {
            text = MiscUtils.fromHtml(
                context.getString(R.string.whats_new_message, BuildConfig.VERSION_NAME)
            )
            movementMethod = LinkMovementMethod.getInstance()
        }

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.whats_new_title)
            .setPositiveButton(R.string.ok, null)
            .setView(layoutView)
            .create()
    }
}

@Composable
fun WhatsNewDialog(
    versionName: String = BuildConfig.VERSION_NAME,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_title)) },
        text = {
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                },
                update = { view ->
                    view.text = MiscUtils.fromHtml(
                        context.getString(R.string.whats_new_message, versionName)
                    )
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}
