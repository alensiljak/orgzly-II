package com.orgzly.android.ui.notes.query.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.alensiljak.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.compose.notelist.NoteItemContent
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar
import com.orgzly.android.ui.compose.notelist.SwipeableNoteRow
import com.orgzly.android.ui.notes.query.QueryViewModel
import com.orgzly.android.ui.notes.query.QueryViewModel.Companion.APP_BAR_DEFAULT_MODE
import com.orgzly.android.ui.notes.query.QueryViewModel.Companion.APP_BAR_SELECTION_MODE
import com.orgzly.android.ui.views.style.CheckboxSpan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: QueryViewModel,
    queryTitle: String,
    querySubtitle: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: (String) -> Unit,
    onNoteClick: (NoteView) -> Unit,
    onNoteLongClick: (NoteView) -> Unit,
    onToggleFold: (Long) -> Unit,
    onToggleFoldSubtree: (Long) -> Unit,
    onCheckboxToggle: (Note, CheckboxSpan) -> Unit,
    onLinkClick: (Any) -> Unit,
    onSwipe: (NoteView, Int, Int, Int) -> Unit,
    onSelectionAction: (Int) -> Unit,
    onDefaultAction: (Int) -> Unit,
) {
    val viewState by viewModel.viewState.observeAsState(QueryViewModel.ViewState.LOADING)
    val notes by viewModel.data.observeAsState(emptyList())
    val mode by viewModel.appBar.currentMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            when (mode) {
                APP_BAR_SELECTION_MODE -> QuerySelectionTopBar(
                    count = selectedIds.size,
                    onBack = { viewModel.appBar.handleOnBackPressed() },
                    onAction = onSelectionAction,
                )
                else -> QueryDefaultTopBar(
                    title = queryTitle,
                    subtitle = querySubtitle,
                    onOpenDrawer = onOpenDrawer,
                    onAction = onDefaultAction,
                )
            }
        },
        bottomBar = {
            if (mode == APP_BAR_SELECTION_MODE) {
                QuerySelectionBottomBar(
                    isSingleSelection = selectedIds.size == 1,
                    onAction = onSelectionAction,
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (viewState) {
                    QueryViewModel.ViewState.LOADING ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    QueryViewModel.ViewState.EMPTY ->
                        QueryEmptyMessage(modifier = Modifier.align(Alignment.Center))
                    else -> SearchNoteList(
                        notes = notes,
                        selectedIds = selectedIds,
                        onNoteClick = onNoteClick,
                        onNoteLongClick = onNoteLongClick,
                        onToggleFold = onToggleFold,
                        onToggleFoldSubtree = onToggleFoldSubtree,
                        onCheckboxToggle = onCheckboxToggle,
                        onLinkClick = onLinkClick,
                        onSwipe = onSwipe,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchNoteList(
    notes: List<NoteView>,
    selectedIds: Set<Long>,
    onNoteClick: (NoteView) -> Unit,
    onNoteLongClick: (NoteView) -> Unit,
    onToggleFold: (Long) -> Unit,
    onToggleFoldSubtree: (Long) -> Unit,
    onCheckboxToggle: (Note, CheckboxSpan) -> Unit,
    onLinkClick: (Any) -> Unit,
    onSwipe: (NoteView, Int, Int, Int) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(notes, key = { it.note.id }) { noteView ->
            SwipeableNoteRow(
                onSwipe = { direction, x, y -> onSwipe(noteView, direction, x, y) },
            ) {
                NoteItemContent(
                    noteView = noteView,
                    levelOffset = null,
                    isSelected = noteView.note.id in selectedIds,
                    inBook = false,
                    onClick = { onNoteClick(noteView) },
                    onLongClick = { onNoteLongClick(noteView) },
                    onToggleFold = onToggleFold,
                    onToggleFoldSubtree = onToggleFoldSubtree,
                    onCheckboxToggle = onCheckboxToggle,
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

@Composable
internal fun QueryEmptyMessage(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.no_notes),
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── Top bars ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueryDefaultTopBar(
    title: String,
    subtitle: String?,
    onOpenDrawer: () -> Unit,
    onAction: (Int) -> Unit,
) {
    var overflow by remember { mutableStateOf(false) }

    OrgzlyTopAppBar(
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(painterResource(R.drawable.ic_menu),
                    contentDescription = stringResource(R.string.drawer_open))
            }
        },
        actions = {
            IconButton(onClick = { overflow = true }) {
                Icon(painterResource(R.drawable.ic_more_horiz),
                    contentDescription = stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                QueryMenuItem(R.string.sync) { overflow = false; onAction(R.id.sync) }
                QueryMenuItem(R.string.keep_screen_on) { overflow = false; onAction(R.id.keep_screen_on) }
                QueryMenuItem(R.string.settings) { overflow = false; onAction(R.id.activity_action_settings) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuerySelectionTopBar(
    count: Int,
    onBack: () -> Unit,
    onAction: (Int) -> Unit,
) {
    var overflow by remember { mutableStateOf(false) }

    OrgzlyTopAppBar(
        title = count.toString(),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.cancel))
            }
        },
        actions = {
            IconButton(onClick = { overflow = true }) {
                Icon(painterResource(R.drawable.ic_more_horiz),
                    contentDescription = stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                QueryMenuItem(R.string.clock_in) { overflow = false; onAction(R.id.clock_in) }
                QueryMenuItem(R.string.clock_out) { overflow = false; onAction(R.id.clock_out) }
                QueryMenuItem(R.string.clock_cancel) { overflow = false; onAction(R.id.clock_cancel) }
                QueryMenuItem(R.string.share) { overflow = false; onAction(R.id.share) }
            }
        },
    )
}

@Composable
internal fun QuerySelectionBottomBar(isSingleSelection: Boolean, onAction: (Int) -> Unit) {
    BottomAppBar {
        IconButton(onClick = { onAction(R.id.schedule) }) {
            Icon(painterResource(R.drawable.ic_today), stringResource(R.string.schedule))
        }
        IconButton(onClick = { onAction(R.id.deadline) }) {
            Icon(painterResource(R.drawable.ic_alarm), stringResource(R.string.deadline))
        }
        IconButton(onClick = { onAction(R.id.state) }) {
            Icon(painterResource(R.drawable.ic_flag), stringResource(R.string.state))
        }
        IconButton(onClick = { onAction(R.id.toggle_state) }) {
            Icon(painterResource(R.drawable.ic_check_circle_outline), stringResource(R.string.done))
        }
        if (isSingleSelection) {
            IconButton(onClick = { onAction(R.id.focus) }) {
                Icon(painterResource(R.drawable.ic_center_focus_strong), stringResource(R.string.open))
            }
        }
    }
}

@Composable
private fun QueryMenuItem(textRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(textRes)) }, onClick = onClick)
}
