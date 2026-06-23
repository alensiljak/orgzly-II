package com.orgzly.android.ui.repo.webdav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cc.alensiljak.orgzly.R
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebdavRepoScreen(
    url: String,
    onUrlChange: (String) -> Unit,
    urlError: String?,
    username: String,
    onUsernameChange: (String) -> Unit,
    usernameError: String?,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordError: String?,
    certificates: String?,
    onCertificatesChange: (String?) -> Unit,
    connectionResult: WebdavRepoViewModel.ConnectionResult?,
    snackbarMessage: String?,
    onSnackbarShown: () -> Unit,
    showCleartextDialog: Boolean,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onSaveConfirmed: () -> Unit,
    onCleartextDismiss: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    var showCertificatesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onSnackbarShown()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            OrgzlyTopAppBar(
                title = stringResource(R.string.webdav),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onSave) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.done),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.webdav_url_hint)) },
                isError = urlError != null,
                supportingText = urlError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = false,
                maxLines = 3,
            )

            OutlinedButton(
                onClick = { showCertificatesDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (certificates.isNullOrEmpty()) R.string.add_trusted_certificates_optional
                        else R.string.edit_trusted_certificates
                    )
                )
            }

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.webdav_auth_username_hint)) },
                isError = usernameError != null,
                supportingText = usernameError?.let { { Text(it) } },
                singleLine = true,
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.webdav_auth_password_hint)) },
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it) } },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )

            Button(
                onClick = onTestConnection,
                modifier = Modifier.fillMaxWidth(),
                enabled = connectionResult !is WebdavRepoViewModel.ConnectionResult.InProgress,
            ) {
                Text(stringResource(R.string.test_connection))
            }

            connectionResult?.let { result ->
                val text = when (result) {
                    is WebdavRepoViewModel.ConnectionResult.InProgress ->
                        stringResource(result.msg)

                    is WebdavRepoViewModel.ConnectionResult.Success -> {
                        val countMsg = pluralStringResource(
                            R.plurals.found_number_of_notebooks,
                            result.bookCount,
                            result.bookCount,
                        )
                        "${stringResource(R.string.connection_successful)}\n$countMsg"
                    }

                    is WebdavRepoViewModel.ConnectionResult.Error -> when (result.msg) {
                        is Int -> stringResource(result.msg)
                        is String -> result.msg
                        else -> ""
                    }
                }
                if (text.isNotEmpty()) {
                    Text(text = text)
                }
            }
        }
    }

    if (showCertificatesDialog) {
        CertificatesDialog(
            initialValue = certificates ?: "",
            onSet = { value ->
                onCertificatesChange(value.ifBlank { null })
                showCertificatesDialog = false
            },
            onClear = {
                onCertificatesChange(null)
                showCertificatesDialog = false
            },
            onDismiss = { showCertificatesDialog = false },
        )
    }

    if (showCleartextDialog) {
        AlertDialog(
            onDismissRequest = onCleartextDismiss,
            title = { Text(stringResource(R.string.cleartext_traffic)) },
            text = { Text(stringResource(R.string.cleartext_traffic_message)) },
            confirmButton = {
                TextButton(onClick = onSaveConfirmed) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onCleartextDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CertificatesDialog(
    initialValue: String,
    onSet: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trusted_certificates)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.certificates_hint)) },
                singleLine = false,
                minLines = 4,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSet(text) }) {
                Text(stringResource(R.string.set))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.clear))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}
