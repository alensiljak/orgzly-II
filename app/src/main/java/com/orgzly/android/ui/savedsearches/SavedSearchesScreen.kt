package com.orgzly.android.ui.savedsearches

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.livedata.observeAsState
import cc.alensiljak.orgzly.R
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.ui.compose.widgets.OrgzlyFloatingActionButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSearchesScreen(
    viewModel: SavedSearchesViewModel,
    onNewSearch: () -> Unit,
    onEditSearch: (Long) -> Unit,
    onDeleteSearch: (Set<Long>) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val searches by viewModel.data.observeAsState(emptyList())
    val viewState by viewModel.viewState.observeAsState(SavedSearchesViewModel.ViewState.LOADING)
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    val inSelectionMode = selectedIds.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (inSelectionMode) {
                SelectionTopBar(
                    selectedCount = selectedIds.size,
                    isSingleSelection = selectedIds.size == 1,
                    onBack = { viewModel.clearSelection() },
                    onMoveUp = { selectedIds.firstOrNull()?.let { onMoveUp(it) } },
                    onMoveDown = { selectedIds.firstOrNull()?.let { onMoveDown(it) } },
                    onDelete = { onDeleteSearch(selectedIds); viewModel.clearSelection() },
                    scrollBehavior = scrollBehavior,
                )
            } else {
                DefaultTopBar(
                    onOpenDrawer = onOpenDrawer,
                    onImport = onImport,
                    onExport = onExport,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = {
            if (!inSelectionMode) {
                OrgzlyFloatingActionButton(onClick = onNewSearch) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.create),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when (viewState) {
                SavedSearchesViewModel.ViewState.LOADING -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                SavedSearchesViewModel.ViewState.EMPTY -> {
                    Text(
                        text = stringResource(R.string.no_filters),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SavedSearchesViewModel.ViewState.LOADED -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(searches, key = { it.id }) { search ->
                            SavedSearchItem(
                                search = search,
                                isSelected = search.id in selectedIds,
                                onClick = {
                                    if (inSelectionMode) {
                                        viewModel.toggleSelection(search.id)
                                    } else {
                                        onEditSearch(search.id)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(search.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopBar(
    onOpenDrawer: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    OrgzlyTopAppBar(
        title = stringResource(R.string.searches),
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = stringResource(R.string.drawer_open),
                )
            }
        },
        actions = {
            IconButton(onClick = { overflowExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_)) },
                    onClick = { overflowExpanded = false; onImport() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export)) },
                    onClick = { overflowExpanded = false; onExport() },
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    isSingleSelection: Boolean,
    onBack: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    OrgzlyTopAppBar(
        title = selectedCount.toString(),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        },
        actions = {
            if (isSingleSelection) {
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.testTag("saved_search_move_up"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_upward),
                        contentDescription = stringResource(R.string.up),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    modifier = Modifier.testTag("saved_search_move_down"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_downward),
                        contentDescription = stringResource(R.string.down),
                    )
                }
            }
            IconButton(onClick = { overflowExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { overflowExpanded = false; onDelete() },
                    modifier = Modifier.testTag("saved_search_delete"),
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedSearchItem(
    search: SavedSearch,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = search.name,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            Text(
                text = search.query,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = if (isSelected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_search_item_${search.name}")
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}
