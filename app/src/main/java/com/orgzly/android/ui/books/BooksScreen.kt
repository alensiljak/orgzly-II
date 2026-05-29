package com.orgzly.android.ui.books

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.alensiljak.orgzly.R
import com.orgzly.android.db.entity.BookAction
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.compose.providers.appPreference
import com.orgzly.android.ui.compose.widgets.OrgzlyFloatingActionButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    viewModel: BooksViewModel,
    withActionBar: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onBookClick: (Long) -> Unit,
    onOpenDrawer: () -> Unit,
    onNewBook: () -> Unit,
    onImportBook: () -> Unit,
    onSearch: (String) -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onPendingReminders: () -> Unit,
) {
    val books by viewModel.data.observeAsState(emptyList())
    val viewState by viewModel.viewState.observeAsState(BooksViewModel.ViewState.LOADING)
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val renameDialogBook by viewModel.renameDialogBook.collectAsStateWithLifecycle()
    val deleteDialogBooks by viewModel.deleteDialogBooks.collectAsStateWithLifecycle()
    val linkDialogOptions by viewModel.linkDialogOptions.collectAsStateWithLifecycle()
    val displayedDetails by appPreference { AppPreferences.displayedBookDetails(it) }

    val inSelectionMode = selectedIds.isNotEmpty()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                inSelectionMode -> SelectionTopBar(
                    selectedCount = selectedIds.size,
                    isSingleSelection = selectedIds.size == 1,
                    onBack = { viewModel.clearSelection() },
                    onRename = { viewModel.renameBookRequest(selectedIds.first()) },
                    onSetLink = { viewModel.setBookLinksRequest(selectedIds) },
                    onForceSave = { viewModel.forceSaveBookRequest(selectedIds) },
                    onForceLoad = { viewModel.forceLoadBookRequest(selectedIds) },
                    onExport = { viewModel.exportBookRequest(selectedIds.first(), com.orgzly.android.BookFormat.ORG) },
                    onDelete = { viewModel.deleteBooksRequest(selectedIds) },
                    scrollBehavior = scrollBehavior,
                )
                else -> DefaultTopBar(
                    withActionBar = withActionBar,
                    onOpenDrawer = onOpenDrawer,
                    onImportBook = onImportBook,
                    onSearchOpen = { searchActive = true },
                    onSync = onSync,
                    onSettings = onSettings,
                    onPendingReminders = onPendingReminders,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = {
            if (withActionBar && !inSelectionMode) {
                OrgzlyFloatingActionButton(onClick = onNewBook) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.new_notebook),
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
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                when (viewState) {
                    BooksViewModel.ViewState.LOADING -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    BooksViewModel.ViewState.EMPTY -> {
                        Text(
                            text = stringResource(R.string.no_notebooks),
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BooksViewModel.ViewState.LOADED -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(books, key = { it.book.id }) { bookView ->
                                BookItem(
                                    bookView = bookView,
                                    isSelected = bookView.book.id in selectedIds,
                                    displayedDetails = displayedDetails,
                                    onClick = {
                                        if (inSelectionMode) {
                                            viewModel.toggleSelection(bookView.book.id)
                                        } else {
                                            onBookClick(bookView.book.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!withActionBar) {
                                            onBookClick(bookView.book.id)
                                        } else {
                                            viewModel.toggleSelection(bookView.book.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renameDialogBook?.let { book ->
        RenameBookDialog(
            bookName = book.book.name,
            onConfirm = { name ->
                viewModel.renameBook(book, name)
                viewModel.dismissRenameDialog()
            },
            onDismiss = { viewModel.dismissRenameDialog() },
        )
    }

    deleteDialogBooks?.let { booksToDelete ->
        DeleteBooksDialog(
            books = booksToDelete,
            onConfirm = { bookIds, deleteLinked ->
                viewModel.deleteBooks(bookIds, deleteLinked)
                viewModel.dismissDeleteDialog()
            },
            onDismiss = { viewModel.dismissDeleteDialog() },
        )
    }

    linkDialogOptions?.let { options ->
        SetLinkDialog(
            options = options,
            onSelect = { repo ->
                viewModel.setBookLinks(options.bookIds, repo)
                viewModel.dismissLinkDialog()
            },
            onRemove = {
                viewModel.setBookLinks(options.bookIds)
                viewModel.dismissLinkDialog()
            },
            onDismiss = { viewModel.dismissLinkDialog() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopBar(
    withActionBar: Boolean,
    onOpenDrawer: () -> Unit,
    onImportBook: () -> Unit,
    onSearchOpen: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onPendingReminders: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    OrgzlyTopAppBar(
        title = if (withActionBar) {
            stringResource(R.string.notebooks)
        } else {
            stringResource(R.string.select_notebook)
        },
        navigationIcon = {
            if (withActionBar) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu),
                        contentDescription = stringResource(R.string.drawer_open),
                    )
                }
            }
        },
        actions = {
            if (withActionBar) {
                IconButton(onClick = onSearchOpen) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.search),
                    )
                }
                IconButton(onClick = onSync) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sync),
                        contentDescription = stringResource(R.string.sync),
                    )
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
                        text = { Text(stringResource(R.string.import_org_file)) },
                        onClick = { overflowExpanded = false; onImportBook() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings)) },
                        onClick = { overflowExpanded = false; onSettings() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.show_pending_reminders)) },
                        onClick = { overflowExpanded = false; onPendingReminders() },
                    )
                }
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
    onRename: () -> Unit,
    onSetLink: () -> Unit,
    onForceSave: () -> Unit,
    onForceLoad: () -> Unit,
    onExport: () -> Unit,
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
            IconButton(onClick = onForceLoad) {
                Icon(
                    painter = painterResource(R.drawable.ic_cloud_download_outline),
                    contentDescription = stringResource(R.string.books_context_menu_item_force_load),
                )
            }
            IconButton(onClick = onForceSave) {
                Icon(
                    painter = painterResource(R.drawable.ic_cloud_upload_outline),
                    contentDescription = stringResource(R.string.books_context_menu_item_force_save),
                )
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
                if (isSingleSelection) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.books_context_menu_item_rename)) },
                        onClick = { overflowExpanded = false; onRename() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.books_context_menu_item_set_link)) },
                    onClick = { overflowExpanded = false; onSetLink() },
                )
                if (isSingleSelection) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export)) },
                        onClick = { overflowExpanded = false; onExport() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { overflowExpanded = false; onDelete() },
                )
            }
        },
        scrollBehavior = scrollBehavior,
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
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onSearch) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.search),
                    )
                }
            }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookItem(
    bookView: BookView,
    isSelected: Boolean,
    displayedDetails: Collection<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val book = bookView.book

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .graphicsLayer { alpha = if (book.isDummy) 0.4f else 1f },
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.elevatedCardColors()
        },
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title ?: book.name,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (book.title != null) {
                        Text(
                            text = book.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                when {
                    book.lastAction?.type == BookAction.Type.ERROR ->
                        Icon(
                            painter = painterResource(R.drawable.ic_sync_problem),
                            contentDescription = stringResource(R.string.sync),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    bookView.isOutOfSync() ->
                        Icon(
                            painter = painterResource(R.drawable.ic_sync),
                            contentDescription = stringResource(R.string.sync),
                            modifier = Modifier.size(20.dp),
                        )
                }
            }

            val errorColor = MaterialTheme.colorScheme.error
            val detailRows = buildDetailRows(context, bookView, displayedDetails, errorColor)
            if (detailRows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                detailRows.forEach { (iconRes, text) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 1.dp),
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 0.dp),
                            tint = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp),
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

private data class DetailRow(val iconRes: Int, val text: AnnotatedString)

private fun buildDetailRows(
    context: Context,
    bookView: BookView,
    displayedDetails: Collection<String>,
    errorColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Red,
): List<DetailRow> {
    val book = bookView.book
    val rows = mutableListOf<DetailRow>()

    fun isEnabled(prefKey: Int) = displayedDetails.contains(context.getString(prefKey))

    if (isEnabled(R.string.pref_value_book_details_mtime)) {
        val text = if (book.mtime != null && book.mtime > 0) {
            formatTime(context, book.mtime) ?: context.getString(R.string.not_modified)
        } else {
            context.getString(R.string.not_modified)
        }
        rows += DetailRow(R.drawable.ic_access_time, AnnotatedString(text))
    }

    if (isEnabled(R.string.pref_value_book_details_notes_count)) {
        val text = if (bookView.noteCount > 0) {
            context.resources.getQuantityString(
                R.plurals.notes_count_nonzero, bookView.noteCount, bookView.noteCount,
            )
        } else {
            context.getString(R.string.notes_count_zero)
        }
        rows += DetailRow(R.drawable.ic_format_list_bulleted, AnnotatedString(text))
    }

    if (bookView.hasLink() && isEnabled(R.string.pref_value_book_details_link_url)) {
        rows += DetailRow(R.drawable.ic_link, AnnotatedString(bookView.linkRepo?.url ?: ""))
    }

    if (bookView.hasSync() && isEnabled(R.string.pref_value_book_details_sync_url)) {
        rows += DetailRow(
            R.drawable.ic_sync,
            AnnotatedString(bookView.syncedTo?.uri.toString()),
        )
    }

    if (bookView.hasSync() && isEnabled(R.string.pref_value_book_details_sync_mtime)) {
        rows += DetailRow(
            R.drawable.ic_sync,
            AnnotatedString(formatTime(context, bookView.syncedTo?.mtime) ?: "N/A"),
        )
    }

    if (bookView.hasSync() && isEnabled(R.string.pref_value_book_details_sync_revision)) {
        rows += DetailRow(
            R.drawable.ic_sync,
            AnnotatedString(bookView.syncedTo?.revision ?: "N/A"),
        )
    }

    if (book.selectedEncoding != null && isEnabled(R.string.pref_value_book_details_encoding_selected)) {
        rows += DetailRow(
            R.drawable.ic_language,
            AnnotatedString(context.getString(R.string.argument_selected, book.selectedEncoding)),
        )
    }

    if (book.detectedEncoding != null && isEnabled(R.string.pref_value_book_details_encoding_detected)) {
        rows += DetailRow(
            R.drawable.ic_language,
            AnnotatedString(context.getString(R.string.argument_detected, book.detectedEncoding)),
        )
    }

    if (book.usedEncoding != null && isEnabled(R.string.pref_value_book_details_encoding_used)) {
        rows += DetailRow(
            R.drawable.ic_language,
            AnnotatedString(context.getString(R.string.argument_used, book.usedEncoding)),
        )
    }

    val lastAction = book.lastAction
    if (lastAction != null) {
        val alwaysShow = lastAction.type != BookAction.Type.INFO
        if (alwaysShow || isEnabled(R.string.pref_value_book_details_last_action)) {
            val text = buildAnnotatedString {
                append(formatTime(context, lastAction.timestamp) ?: "")
                append(": ")
                when (lastAction.type) {
                    BookAction.Type.ERROR -> withStyle(SpanStyle(color = errorColor)) {
                        append(lastAction.message ?: "")
                    }
                    BookAction.Type.PROGRESS -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(lastAction.message ?: "")
                    }
                    else -> append(lastAction.message ?: "")
                }
            }
            rows += DetailRow(R.drawable.ic_info_outline, text)
        }
    }

    return rows
}

private fun formatTime(context: Context, ts: Long?): String? {
    ts ?: return null
    val flags = DateUtils.FORMAT_SHOW_DATE or
            DateUtils.FORMAT_SHOW_TIME or
            DateUtils.FORMAT_ABBREV_MONTH or
            DateUtils.FORMAT_SHOW_WEEKDAY or
            DateUtils.FORMAT_ABBREV_WEEKDAY
    return DateUtils.formatDateTime(context, ts, flags)
}

@Composable
private fun RenameBookDialog(
    bookName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(bookName) }
    val isValid = name.isNotEmpty() && name != bookName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_book, bookName)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                isError = name.isEmpty(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = isValid) {
                Text(stringResource(R.string.rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DeleteBooksDialog(
    books: Set<BookView>,
    onConfirm: (Set<Long>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val singleBook = if (books.size == 1) books.first() else null
    var deleteLinked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (singleBook != null) {
                    stringResource(R.string.delete_with_quoted_argument, singleBook.book.name)
                } else {
                    stringResource(R.string.delete_amount_of_books, books.size)
                }
            )
        },
        text = {
            if (singleBook?.syncedTo != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteLinked, onCheckedChange = { deleteLinked = it })
                    Column {
                        Text(stringResource(R.string.also_delete_linked_book))
                        if (deleteLinked) {
                            Text(
                                text = singleBook.syncedTo.uri.toString(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(books.map { it.book.id }.toSet(), deleteLinked) }) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetLinkDialog(
    options: BooksViewModel.BookLinkOptions,
    onSelect: (Repo) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.book_link)) },
        text = {
            Column {
                options.links.forEachIndexed { index, repo ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onSelect(repo) }),
                    ) {
                        RadioButton(
                            selected = index == options.selected,
                            onClick = { onSelect(repo) },
                        )
                        Text(
                            text = options.urls[index],
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.remove_notebook_link))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}
