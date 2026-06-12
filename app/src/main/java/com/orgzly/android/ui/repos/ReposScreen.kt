package com.orgzly.android.ui.repos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.alensiljak.orgzly.R
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReposScreen(
    viewModel: ReposViewModel,
    isDropboxEnabled: Boolean,
    isGitEnabled: Boolean,
    onNavigateUp: () -> Unit,
    onNewDropbox: () -> Unit,
    onNewGit: () -> Unit,
    onNewWebdav: () -> Unit,
    onNewDirectory: () -> Unit,
) {
    val repos by viewModel.repos.observeAsState(emptyList())

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ReposTopBar(
                hasRepos = repos.isNotEmpty(),
                isDropboxEnabled = isDropboxEnabled,
                isGitEnabled = isGitEnabled,
                onNavigateUp = onNavigateUp,
                onNewDropbox = onNewDropbox,
                onNewGit = onNewGit,
                onNewWebdav = onNewWebdav,
                onNewDirectory = onNewDirectory,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (repos.isEmpty()) {
            EmptyReposContent(
                modifier = Modifier.padding(innerPadding),
                isDropboxEnabled = isDropboxEnabled,
                isGitEnabled = isGitEnabled,
                onNewDropbox = onNewDropbox,
                onNewGit = onNewGit,
                onNewWebdav = onNewWebdav,
                onNewDirectory = onNewDirectory,
            )
        } else {
            ReposList(
                modifier = Modifier.padding(innerPadding),
                repos = repos,
                onRepoClick = { viewModel.openRepo(it.id) },
                onRepoDelete = { viewModel.deleteRepo(it.id) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReposTopBar(
    hasRepos: Boolean,
    isDropboxEnabled: Boolean,
    isGitEnabled: Boolean,
    onNavigateUp: () -> Unit,
    onNewDropbox: () -> Unit,
    onNewGit: () -> Unit,
    onNewWebdav: () -> Unit,
    onNewDirectory: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    OrgzlyTopAppBar(
        title = stringResource(R.string.repositories),
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.close),
                )
            }
        },
        actions = {
            if (hasRepos) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.repos_options_menu_new_repo),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (isDropboxEnabled) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dropbox)) },
                            onClick = { menuExpanded = false; onNewDropbox() },
                        )
                    }
                    if (isGitEnabled) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git)) },
                            onClick = { menuExpanded = false; onNewGit() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.webdav)) },
                        onClick = { menuExpanded = false; onNewWebdav() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.directory)) },
                        onClick = { menuExpanded = false; onNewDirectory() },
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun EmptyReposContent(
    modifier: Modifier = Modifier,
    isDropboxEnabled: Boolean,
    isGitEnabled: Boolean,
    onNewDropbox: () -> Unit,
    onNewGit: () -> Unit,
    onNewWebdav: () -> Unit,
    onNewDirectory: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.create_new_repository_for_syncing),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        if (isDropboxEnabled) {
            RepoTypeButton(
                label = stringResource(R.string.dropbox),
                icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                onClick = onNewDropbox,
            )
        }

        if (isGitEnabled) {
            RepoTypeButton(
                label = stringResource(R.string.git),
                icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                onClick = onNewGit,
            )
        }

        RepoTypeButton(
            label = stringResource(R.string.webdav),
            icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
            onClick = onNewWebdav,
        )

        RepoTypeButton(
            label = stringResource(R.string.directory),
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
            onClick = onNewDirectory,
        )
    }
}

@Composable
private fun RepoTypeButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Box(modifier = Modifier.padding(end = 8.dp)) { icon() }
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReposList(
    modifier: Modifier = Modifier,
    repos: List<Repo>,
    onRepoClick: (Repo) -> Unit,
    onRepoDelete: (Repo) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Repo?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(repos, key = { it.id }) { repo ->
            RepoRow(
                repo = repo,
                onClick = { onRepoClick(repo) },
                onDeleteRequest = { pendingDelete = repo },
            )
        }
    }

    pendingDelete?.let { repo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(repo.url) },
            confirmButton = {
                TextButton(onClick = {
                    onRepoDelete(repo)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoRow(
    repo: Repo,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDeleteRequest()
            }
            false // keep row in place; the dialog confirms the deletion
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        ListItem(
            headlineContent = { Text(repo.url) },
            supportingContent = {
                Text(repo.type.name.lowercase().replaceFirstChar { it.uppercase() })
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        )
    }
}
