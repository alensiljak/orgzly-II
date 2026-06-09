package com.orgzly.android.ui.dialogs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.DialogFragment
import cc.alensiljak.orgzly.R
import com.orgzly.android.git.SshKey.sshPublicKey
import com.orgzly.android.ui.SshKeygenActivity
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap

class ShowSshKeyDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OrgzlyBootstrap {
                    ShowSshKeyDialog(
                        publicKey = sshPublicKey ?: "",
                        onShare = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, sshPublicKey)
                            }
                            startActivity(Intent.createChooser(sendIntent, null))
                            (activity as? SshKeygenActivity)?.finish()
                        },
                        onDismiss = {
                            (activity as? SshKeygenActivity)?.finish()
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
}

@Composable
fun ShowSshKeyDialog(
    publicKey: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.your_public_key)) },
        text = { Text(publicKey) },
        confirmButton = {
            TextButton(onClick = onShare) {
                Text(stringResource(R.string.ssh_keygen_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.not_now))
            }
        },
    )
}
