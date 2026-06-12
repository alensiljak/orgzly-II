package com.orgzly.android.ui.notes.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.alensiljak.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.compose.notelist.NoteItemContent
import com.orgzly.android.ui.compose.notelist.SwipeableNoteRow
import com.orgzly.android.ui.compose.notelist.orgSpannedToAnnotatedString
import com.orgzly.android.ui.compose.widgets.OrgzlyFloatingActionButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar
import com.orgzly.android.ui.notes.book.BookViewModel.Companion.APP_BAR_DEFAULT_MODE
import com.orgzly.android.ui.notes.book.BookViewModel.Companion.APP_BAR_SELECTION_MODE
import com.orgzly.android.ui.notes.book.BookViewModel.Companion.APP_BAR_SELECTION_MOVE_MODE
import com.orgzly.android.ui.views.style.CheckboxSpan
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.util.OrgFormatter

/**
 * Compose implementation of the notebook (book) view. Presentational: it renders the toolbars,
 * note list and dialogs, and routes menu / CAB / popup actions back to [BookFragment] by their
 * existing menu item ids (so the fragment's action handlers are reused unchanged).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(
    viewModel: BookViewModel,
    bookTitle: String?,
    isRefreshing: Boolean,
    pasteCount: Int,
    currentFiletags: String,
    tagSuggestions: List<String>,
    onRefresh: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: (String) -> Unit,
    onNoteClick: (NoteView) -> Unit,
    onNoteLongClick: (NoteView) -> Unit,
    onNewNoteFab: () -> Unit,
    onPrefaceClick: () -> Unit,
    onToggleFold: (Long) -> Unit,
    onToggleFoldSubtree: (Long) -> Unit,
    onCheckboxToggle: (Note, CheckboxSpan) -> Unit,
    onLinkClick: (Any) -> Unit,
    onSwipe: (NoteView, Int, Int, Int) -> Unit,
    onSelectionAction: (Int) -> Unit,
    onBookAction: (Int) -> Unit,
    onApplyFiletags: (String) -> Unit,
) {
    val data by viewModel.data.observeAsState()
    val mode by viewModel.appBar.currentMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val narrowedNoteId by viewModel.narrowedNoteId.observeAsState()
    val flipperChild by viewModel.flipperDisplayedChild.observeAsState(
        BookViewModel.FlipperDisplayedChild.LOADING)
    val filetagsVisible by viewModel.filetagsDialogVisible.collectAsStateWithLifecycle()

    val book = data?.book
    val notes = data?.notes
    val levelOffset = viewModel.levelOffset(notes)

    val visibleNotes = remember(notes) {
        notes?.filter { it.note.position.foldedUnderId == 0L } ?: emptyList()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefaceHidden = AppPreferences.prefaceDisplay(context) ==
        context.getString(R.string.pref_value_preface_in_book_hide)
    val prefaceVisible = book?.preface?.isNotBlank() == true && !viewModel.isNarrowed() && !prefaceHidden

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            when {
                searchActive -> SearchTopBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {
                        searchActive = false
                        onSearch(searchQuery.trim())
                        searchQuery = ""
                    },
                    onClose = { searchActive = false; searchQuery = "" },
                )

                mode == APP_BAR_SELECTION_MODE -> SelectionTopBar(
                    count = selectedIds.size,
                    onBack = { viewModel.appBar.handleOnBackPressed() },
                    onAction = onSelectionAction,
                )

                mode == APP_BAR_SELECTION_MOVE_MODE -> MoveTopBar(
                    count = selectedIds.size,
                    onBack = { viewModel.appBar.handleOnBackPressed() },
                    onAction = onSelectionAction,
                )

                else -> DefaultTopBar(
                    title = bookTitle ?: stringResource(R.string.notebook),
                    isNarrowed = viewModel.isNarrowed(),
                    pasteCount = pasteCount,
                    onOpenDrawer = onOpenDrawer,
                    onSearchOpen = { searchActive = true },
                    onAction = onBookAction,
                )
            }
        },
        bottomBar = {
            if (mode == APP_BAR_SELECTION_MODE || mode == APP_BAR_SELECTION_MOVE_MODE) {
                SelectionBottomBar(
                    isSingleSelection = selectedIds.size == 1,
                    onAction = onSelectionAction,
                )
            }
        },
        floatingActionButton = {
            if (mode == APP_BAR_DEFAULT_MODE && book != null && !searchActive) {
                OrgzlyFloatingActionButton(onClick = onNewNoteFab) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.new_note),
                    )
                }
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
                when (flipperChild) {
                    BookViewModel.FlipperDisplayedChild.LOADING ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    BookViewModel.FlipperDisplayedChild.EMPTY ->
                        CenteredMessage(stringResource(R.string.no_notes))

                    BookViewModel.FlipperDisplayedChild.DOES_NOT_EXIST ->
                        CenteredMessage(stringResource(R.string.book_does_not_exist_anymore))

                    else -> NoteList(
                        notes = visibleNotes,
                        prefaceText = if (prefaceVisible) book?.preface else null,
                        levelOffset = levelOffset,
                        selectedIds = selectedIds,
                        onPrefaceClick = onPrefaceClick,
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

    if (filetagsVisible) {
        FiletagsDialog(
            initialValue = currentFiletags,
            onConfirm = {
                onApplyFiletags(it)
                viewModel.dismissFiletagsDialog()
            },
            onDismiss = { viewModel.dismissFiletagsDialog() },
        )
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = text,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoteList(
    notes: List<NoteView>,
    prefaceText: String?,
    levelOffset: Int?,
    selectedIds: Set<Long>,
    onPrefaceClick: () -> Unit,
    onNoteClick: (NoteView) -> Unit,
    onNoteLongClick: (NoteView) -> Unit,
    onToggleFold: (Long) -> Unit,
    onToggleFoldSubtree: (Long) -> Unit,
    onCheckboxToggle: (Note, CheckboxSpan) -> Unit,
    onLinkClick: (Any) -> Unit,
    onSwipe: (NoteView, Int, Int, Int) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("book_note_list")) {
        if (prefaceText != null) {
            item(key = "preface") {
                Preface(text = prefaceText, onClick = onPrefaceClick)
                HorizontalDivider()
            }
        }
        items(notes, key = { it.note.id }) { noteView ->
            SwipeableNoteRow(
                onSwipe = { direction, x, y -> onSwipe(noteView, direction, x, y) },
            ) {
                NoteItemContent(
                    noteView = noteView,
                    levelOffset = levelOffset,
                    isSelected = noteView.note.id in selectedIds,
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
private fun Preface(text: String, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val linkColor = MaterialTheme.colorScheme.primary
    val formatted = remember(text) {
        orgSpannedToAnnotatedString(OrgFormatter.parse(text, context), density, linkColor)
    }
    Text(
        text = formatted.text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preface_row")
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ── Top bars ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopBar(
    title: String,
    isNarrowed: Boolean,
    pasteCount: Int,
    onOpenDrawer: () -> Unit,
    onSearchOpen: () -> Unit,
    onAction: (Int) -> Unit,
) {
    var overflow by remember { mutableStateOf(false) }

    OrgzlyTopAppBar(
        title = title,
        navigationIcon = {
            if (isNarrowed) {
                IconButton(onClick = { onAction(R.id.books_options_menu_item_widen_view) }) {
                    Icon(painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.widen_view))
                }
            } else {
                IconButton(onClick = onOpenDrawer) {
                    Icon(painterResource(R.drawable.ic_menu),
                        contentDescription = stringResource(R.string.drawer_open))
                }
            }
        },
        actions = {
            IconButton(onClick = { onAction(R.id.books_options_menu_item_cycle_visibility) }) {
                Icon(painterResource(R.drawable.ic_unfold_more),
                    contentDescription = stringResource(R.string.cycle_visibility))
            }
            IconButton(onClick = onSearchOpen) {
                Icon(painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.search))
            }
            IconButton(onClick = { overflow = true }) {
                Icon(painterResource(R.drawable.ic_more_horiz),
                    contentDescription = stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                MenuItem(R.string.edit_book_preface) { overflow = false; onAction(R.id.books_options_menu_book_preface) }
                MenuItem(R.string.file_tags) { overflow = false; onAction(R.id.books_options_menu_book_filetags) }
                if (pasteCount > 0) {
                    MenuItem(R.string.paste) { overflow = false; onAction(R.id.book_actions_paste) }
                }
                MenuItem(R.string.gantt_chart) { overflow = false; onAction(R.id.book_actions_gantt) }
                MenuItem(R.string.keep_screen_on) { overflow = false; onAction(R.id.keep_screen_on) }
                MenuItem(R.string.sync) { overflow = false; onAction(R.id.sync) }
                MenuItem(R.string.settings) { overflow = false; onAction(R.id.activity_action_settings) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onBack: () -> Unit, onAction: (Int) -> Unit) {
    var overflow by remember { mutableStateOf(false) }
    var pasteMenu by remember { mutableStateOf(false) }

    OrgzlyTopAppBar(
        title = count.toString(),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.cancel))
            }
        },
        actions = {
            IconButton(onClick = { onAction(R.id.cut) }) {
                Icon(painterResource(R.drawable.ic_content_cut), stringResource(R.string.cut))
            }
            IconButton(onClick = { onAction(R.id.copy) }) {
                Icon(painterResource(R.drawable.ic_content_copy), stringResource(R.string.copy))
            }
            IconButton(onClick = { pasteMenu = true }) {
                Icon(painterResource(R.drawable.ic_content_paste), stringResource(R.string.paste))
            }
            DropdownMenu(expanded = pasteMenu, onDismissRequest = { pasteMenu = false }) {
                MenuItem(R.string.heads_action_menu_item_paste_above) { pasteMenu = false; onAction(R.id.paste_above) }
                MenuItem(R.string.heads_action_menu_item_paste_under) { pasteMenu = false; onAction(R.id.paste_under) }
                MenuItem(R.string.heads_action_menu_item_paste_below) { pasteMenu = false; onAction(R.id.paste_below) }
            }
            IconButton(onClick = { onAction(R.id.move) }) {
                Icon(painterResource(R.drawable.ic_swap_vert), stringResource(R.string.move))
            }
            IconButton(onClick = { overflow = true }) {
                Icon(painterResource(R.drawable.ic_more_horiz), stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                MenuItem(R.string.clock_in) { overflow = false; onAction(R.id.clock_in) }
                MenuItem(R.string.clock_out) { overflow = false; onAction(R.id.clock_out) }
                MenuItem(R.string.clock_cancel) { overflow = false; onAction(R.id.clock_cancel) }
                MenuItem(R.string.share) { overflow = false; onAction(R.id.share) }
                MenuItem(R.string.delete) { overflow = false; onAction(R.id.delete_note) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveTopBar(count: Int, onBack: () -> Unit, onAction: (Int) -> Unit) {
    OrgzlyTopAppBar(
        title = count.toString(),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.cancel))
            }
        },
        actions = {
            IconButton(onClick = { onAction(R.id.notes_action_move_left) }) {
                Icon(painterResource(R.drawable.ic_arrow_back),
                    stringResource(R.string.heads_moving_action_menu_item_promote))
            }
            IconButton(onClick = { onAction(R.id.notes_action_move_down) }) {
                Icon(painterResource(R.drawable.ic_arrow_downward),
                    stringResource(R.string.heads_moving_action_menu_item_down))
            }
            IconButton(onClick = { onAction(R.id.notes_action_move_up) }) {
                Icon(painterResource(R.drawable.ic_arrow_upward),
                    stringResource(R.string.heads_moving_action_menu_item_up))
            }
            IconButton(onClick = { onAction(R.id.notes_action_move_right) }) {
                Icon(painterResource(R.drawable.ic_arrow_forward),
                    stringResource(R.string.heads_moving_action_menu_item_demote))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    OrgzlyTopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.cancel))
            }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBottomBar(isSingleSelection: Boolean, onAction: (Int) -> Unit) {
    var newNoteMenu by remember { mutableStateOf(false) }

    BottomAppBar {
        IconButton(onClick = { onAction(R.id.refile) }) {
            Icon(painterResource(R.drawable.ic_move_to_inbox), stringResource(R.string.refile))
        }
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
            IconButton(onClick = { newNoteMenu = true }) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_note))
            }
            DropdownMenu(expanded = newNoteMenu, onDismissRequest = { newNoteMenu = false }) {
                MenuItem(R.string.new_above) { newNoteMenu = false; onAction(R.id.new_note_above) }
                MenuItem(R.string.new_under) { newNoteMenu = false; onAction(R.id.new_note_under) }
                MenuItem(R.string.new_below) { newNoteMenu = false; onAction(R.id.new_note_below) }
            }
        }
    }
}

@Composable
private fun MenuItem(textRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(textRes)) }, onClick = onClick)
}

@Composable
private fun FiletagsDialog(
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.file_tags)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) { Text(stringResource(R.string.set)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
