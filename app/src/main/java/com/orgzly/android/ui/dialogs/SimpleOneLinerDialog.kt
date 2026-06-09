package com.orgzly.android.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import cc.alensiljak.orgzly.R
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap

class SimpleOneLinerDialog : DialogFragment() {
    private lateinit var requestKey: String

    private var title = 0
    private var hint = 0
    private var value: String? = null
    private var positiveButtonText = 0
    private var negativeButtonText = 0
    private var userData: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.apply {
            requestKey = getString(ARG_REQUEST_ID, "")
            title = getInt(ARG_TITLE)
            hint = getInt(ARG_HINT)
            value = getString(ARG_VALUE)
            positiveButtonText = getInt(ARG_POSITIVE_BUTTON_TEXT)
            negativeButtonText = getInt(ARG_NEGATIVE_BUTTON_TEXT)
            userData = getBundle(ARG_USER_DATA)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OrgzlyBootstrap {
                    SimpleOneLinerDialog(
                        title = stringResource(title),
                        hint = if (hint != 0) stringResource(hint) else null,
                        initialValue = value ?: "",
                        positiveButtonText = stringResource(positiveButtonText),
                        negativeButtonText = stringResource(negativeButtonText),
                        onConfirm = { result ->
                            parentFragmentManager.setFragmentResult(
                                requestKey, bundleOf("value" to result, "user-data" to userData)
                            )
                            dismiss()
                        },
                        onDismiss = { dismiss() },
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

    companion object {
        private const val ARG_REQUEST_ID = "id"
        private const val ARG_TITLE = "title"
        private const val ARG_HINT = "hint"
        private const val ARG_VALUE = "value"
        private const val ARG_POSITIVE_BUTTON_TEXT = "pos"
        private const val ARG_NEGATIVE_BUTTON_TEXT = "neg"
        private const val ARG_USER_DATA = "bundle"

        @JvmField
        val FRAGMENT_TAG: String = SimpleOneLinerDialog::class.java.name

        @JvmStatic
        fun getInstance(
            requestKey: String,
            @StringRes title: Int,
            @StringRes positiveButtonText: Int,
            defaultValue: String?,
            userData: Bundle? = null
        ): SimpleOneLinerDialog {
            val bundle = Bundle().apply {
                putString(ARG_REQUEST_ID, requestKey)
                putInt(ARG_TITLE, title)
                putInt(ARG_HINT, R.string.name)
                if (defaultValue != null) {
                    putString(ARG_VALUE, defaultValue)
                }
                putInt(ARG_POSITIVE_BUTTON_TEXT, positiveButtonText)
                putInt(ARG_NEGATIVE_BUTTON_TEXT, R.string.cancel)
                if (userData != null) {
                    putBundle(ARG_USER_DATA, userData)
                }
            }
            return SimpleOneLinerDialog().apply {
                arguments = bundle
            }
        }
    }
}

@Composable
fun SimpleOneLinerDialog(
    title: String,
    hint: String?,
    initialValue: String,
    positiveButtonText: String,
    negativeButtonText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = hint?.let { { Text(it) } },
                singleLine = true,
                isError = text.isEmpty(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotEmpty()) onConfirm(text.trim()) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotEmpty()) {
                Text(positiveButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(negativeButtonText) }
        },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
