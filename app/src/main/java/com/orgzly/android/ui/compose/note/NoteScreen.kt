package com.orgzly.android.ui.compose.note

import android.content.Intent
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cc.alensiljak.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.ui.NotePriorities
import com.orgzly.android.ui.NoteStates
import com.orgzly.android.ui.TimeType
import com.orgzly.android.ui.note.NotePayload
import com.orgzly.android.ui.note.NoteViewModel
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.ui.views.richtext.RichText
import com.orgzly.android.util.OrgFormatter
import com.orgzly.org.OrgProperties
import com.orgzly.org.datetime.OrgDateTime
import com.orgzly.org.datetime.OrgRange
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    viewModel: NoteViewModel,
    onBack: () -> Unit,
    onShowTimestampDialog: (originViewId: Int, timeType: TimeType, initialTime: OrgDateTime?) -> Unit,
    onShare: () -> Unit,
    onBookBreadcrumbClick: () -> Unit,
    onNoteBreadcrumbClick: (Note) -> Unit
) {
    val noteDetails by viewModel.noteDetailsDataEvent.observeAsState()
    val payload by viewModel.notePayloadLiveData.observeAsState()

    val richTextViews = remember { mutableMapOf<Int, RichText>() }
    var showMenu by remember { mutableStateOf(false) }
    var showStateDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showKeepScreenOnDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = remember { context as? android.app.Activity }

    // Metadata visibility (persisted to preferences)
    var metadataVisibility by remember { mutableStateOf(AppPreferences.noteMetadataVisibility(context)) }
    var alwaysShowSet by remember { mutableStateOf(AppPreferences.alwaysShowSetNoteMetadata(context)) }
    val selectedMetadata = remember { AppPreferences.selectedNoteMetadata(context) }

    val showKeepScreenOnItem = remember { AppPreferences.keepScreenOnMenuItem(context) }
    var isKeepingScreenOn by remember {
        mutableStateOf(
            activity?.window?.attributes?.flags
                ?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.note)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveNote() }) {
                        Icon(Icons.Default.Done, contentDescription = stringResource(R.string.done))
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clock_in)) },
                            onClick = {
                                showMenu = false
                                viewModel.updatePayload(
                                    title = payload?.title ?: "",
                                    content = OrgFormatter.clockIn(payload?.content),
                                    state = payload?.state,
                                    priority = payload?.priority,
                                    tags = payload?.tags ?: emptyList(),
                                    properties = payload?.properties ?: OrgProperties()
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clock_out)) },
                            onClick = {
                                showMenu = false
                                viewModel.updatePayload(
                                    title = payload?.title ?: "",
                                    content = OrgFormatter.clockOut(payload?.content),
                                    state = payload?.state,
                                    priority = payload?.priority,
                                    tags = payload?.tags ?: emptyList(),
                                    properties = payload?.properties ?: OrgProperties()
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.clock_cancel)) },
                            onClick = {
                                showMenu = false
                                viewModel.updatePayload(
                                    title = payload?.title ?: "",
                                    content = OrgFormatter.clockCancel(payload?.content),
                                    state = payload?.state,
                                    priority = payload?.priority,
                                    tags = payload?.tags ?: emptyList(),
                                    properties = payload?.properties ?: OrgProperties()
                                )
                            }
                        )
                        HorizontalDivider()
                        // Metadata visibility
                        DropdownMenuItem(
                            leadingIcon = {
                                RadioButton(
                                    selected = metadataVisibility == "all",
                                    onClick = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = { Text(stringResource(R.string.show_all)) },
                            onClick = {
                                showMenu = false
                                metadataVisibility = "all"
                                AppPreferences.noteMetadataVisibility(context, "all")
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                RadioButton(
                                    selected = metadataVisibility == "selected",
                                    onClick = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = { Text(stringResource(R.string.show_selected)) },
                            onClick = {
                                showMenu = false
                                metadataVisibility = "selected"
                                AppPreferences.noteMetadataVisibility(context, "selected")
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Checkbox(
                                    checked = alwaysShowSet,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = { Text(stringResource(R.string.always_show_set)) },
                            onClick = {
                                showMenu = false
                                val newValue = !alwaysShowSet
                                alwaysShowSet = newValue
                                AppPreferences.alwaysShowSetNoteMetadata(context, newValue)
                            }
                        )
                        HorizontalDivider()
                        if (showKeepScreenOnItem) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Checkbox(
                                        checked = isKeepingScreenOn,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = { Text(stringResource(R.string.keep_screen_on)) },
                                onClick = {
                                    showMenu = false
                                    if (isKeepingScreenOn) {
                                        activity?.window?.clearFlags(
                                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                        )
                                        isKeepingScreenOn = false
                                    } else {
                                        showKeepScreenOnDialog = true
                                    }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share)) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                showMenu = false
                                viewModel.requestNoteDelete()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sync)) },
                            onClick = {
                                showMenu = false
                                SyncRunner.startSync()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            onClick = {
                                showMenu = false
                                context.startActivity(
                                    Intent(context, SettingsActivity::class.java)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sortnote)) },
                            onClick = {
                                showMenu = false
                                val contentLines = payload?.content?.split("\n")?.toMutableList()
                                if (contentLines != null) {
                                    Collections.sort(contentLines)
                                    viewModel.updatePayload(
                                        title = payload?.title ?: "",
                                        content = contentLines.joinToString("\n"),
                                        state = payload?.state,
                                        priority = payload?.priority,
                                        tags = payload?.tags ?: emptyList(),
                                        properties = payload?.properties ?: OrgProperties()
                                    )
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                noteDetails == null -> { /* Still loading */ }
                noteDetails!!.note == null && payload == null && !viewModel.isNew() -> {
                    Text(
                        text = stringResource(R.string.note_does_not_exist_anymore),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    noteDetails?.let { details ->
                        NoteContent(
                            viewModel = viewModel,
                            details = details,
                            payload = payload,
                            onShowTimestampDialog = onShowTimestampDialog,
                            richTextViews = richTextViews,
                            onStateClick = { showStateDialog = true },
                            onPriorityClick = { showPriorityDialog = true },
                            onTagsClick = { showTagsDialog = true },
                            onInsertTimestamp = {
                                val focusedViewId =
                                    richTextViews.values.find { it.isBeingEdited() }?.id
                                        ?: R.id.content_edit
                                onShowTimestampDialog(focusedViewId, TimeType.EVENT, null)
                            },
                            metadataVisibility = metadataVisibility,
                            alwaysShowSet = alwaysShowSet,
                            selectedMetadata = selectedMetadata,
                            onBookBreadcrumbClick = onBookBreadcrumbClick,
                            onNoteBreadcrumbClick = onNoteBreadcrumbClick
                        )
                    }
                }
            }
        }
    }

    if (showStateDialog) {
        val states = NoteStates.fromPreferences(context)
        AlertDialog(
            onDismissRequest = { showStateDialog = false },
            title = { Text(stringResource(R.string.state)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    states.array.forEach { state ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updatePayloadState(state)
                                    showStateDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = state == payload?.state, onClick = null)
                            Text(text = state, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    viewModel.updatePayloadState(null)
                    showStateDialog = false
                }) {
                    Text(stringResource(R.string.clear))
                }
            }
        )
    }

    if (showPriorityDialog) {
        val priorities = NotePriorities.fromPreferences(context)
        AlertDialog(
            onDismissRequest = { showPriorityDialog = false },
            title = { Text(stringResource(R.string.priority)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    priorities.array.forEach { priority ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updatePayload(
                                        title = payload?.title ?: "",
                                        content = payload?.content ?: "",
                                        state = payload?.state,
                                        priority = priority,
                                        tags = payload?.tags ?: emptyList(),
                                        properties = payload?.properties ?: OrgProperties()
                                    )
                                    showPriorityDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = priority == payload?.priority, onClick = null)
                            Text(text = priority, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    viewModel.updatePayload(
                        title = payload?.title ?: "",
                        content = payload?.content ?: "",
                        state = payload?.state,
                        priority = null,
                        tags = payload?.tags ?: emptyList(),
                        properties = payload?.properties ?: OrgProperties()
                    )
                    showPriorityDialog = false
                }) {
                    Text(stringResource(R.string.clear))
                }
            }
        )
    }

    if (showTagsDialog) {
        var tagsText by remember { mutableStateOf(payload?.tags?.joinToString(" ") ?: "") }
        val allTags by viewModel.tags.observeAsState(emptyList())

        // Suggest completions for the current (last space-separated) token
        val lastWord = tagsText.substringAfterLast(' ', tagsText)
        val existingTags = tagsText.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
        val tagSuggestions = if (lastWord.isNotBlank()) {
            allTags.filter { it.startsWith(lastWord, ignoreCase = true) && it !in existingTags }
        } else emptyList()

        AlertDialog(
            onDismissRequest = { showTagsDialog = false },
            title = { Text(stringResource(R.string.tags)) },
            text = {
                Column {
                    TextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.tags)) }
                    )
                    if (tagSuggestions.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            items(tagSuggestions) { tag ->
                                SuggestionChip(
                                    onClick = {
                                        val base = if (tagsText.contains(' ')) {
                                            tagsText.substringBeforeLast(' ') + ' '
                                        } else ""
                                        tagsText = "$base$tag "
                                    },
                                    label = { Text(tag) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newTags = tagsText.split(Regex("\\s+")).filter { it.isNotBlank() }
                    viewModel.updatePayload(
                        title = payload?.title ?: "",
                        content = payload?.content ?: "",
                        state = payload?.state,
                        priority = payload?.priority,
                        tags = newTags,
                        properties = payload?.properties ?: OrgProperties()
                    )
                    showTagsDialog = false
                }) {
                    Text(stringResource(R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showKeepScreenOnDialog) {
        AlertDialog(
            onDismissRequest = { showKeepScreenOnDialog = false },
            title = { Text(stringResource(R.string.keep_screen_on)) },
            text = { Text(stringResource(R.string.keep_screen_on_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    isKeepingScreenOn = true
                    showKeepScreenOnDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeepScreenOnDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun NoteContent(
    viewModel: NoteViewModel,
    details: NoteViewModel.NoteDetailsData,
    payload: NotePayload?,
    onShowTimestampDialog: (originViewId: Int, timeType: TimeType, initialTime: OrgDateTime?) -> Unit,
    richTextViews: MutableMap<Int, RichText>,
    onStateClick: () -> Unit,
    onPriorityClick: () -> Unit,
    onTagsClick: () -> Unit,
    onInsertTimestamp: () -> Unit,
    metadataVisibility: String,
    alwaysShowSet: Boolean,
    selectedMetadata: Set<String>,
    onBookBreadcrumbClick: () -> Unit,
    onNoteBreadcrumbClick: (Note) -> Unit
) {
    val context = LocalContext.current
    val fontLargePx = remember {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.font_large, typedValue, true)
        typedValue.getDimension(context.resources.displayMetrics).toInt()
    }
    val contentTextSizePx = remember {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.fragment_note_content_text_size, typedValue, true)
        typedValue.getDimension(context.resources.displayMetrics).toInt()
    }
    val useMonospaceFont = remember { AppPreferences.isFontMonospaced(context) }
    val autoFocusTitle = remember { viewModel.isNew() && !viewModel.hasInitialTitleData() }

    val propertyNames by viewModel.propertyNames.observeAsState(emptyList())

    // Persist fold state to preferences
    var isMetadataFolded by remember { mutableStateOf(AppPreferences.noteMetadataFolded(context)) }
    var isContentFolded by remember { mutableStateOf(AppPreferences.isNoteContentFolded(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        BreadcrumbsView(
            details = details,
            onBookBreadcrumbClick = onBookBreadcrumbClick,
            onNoteBreadcrumbClick = onNoteBreadcrumbClick
        )

        RichTextComposable(
            sourceText = payload?.title ?: "",
            onSourceTextChange = { newTitle ->
                viewModel.updatePayload(
                    title = newTitle,
                    content = payload?.content ?: "",
                    state = payload?.state,
                    priority = payload?.priority,
                    tags = payload?.tags ?: emptyList(),
                    properties = payload?.properties ?: OrgProperties()
                )
            },
            attributes = RichText.Attributes(
                hint = stringResource(R.string.fragment_note_title_hint),
                textSize = fontLargePx,
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN,
                editId = R.id.title_edit
            ),
            viewId = R.id.title_edit,
            autoFocus = autoFocusTitle,
            onViewCreated = { richTextViews[R.id.title_edit] = it }
        )

        HorizontalDivider()

        FoldableHeader(
            painter = painterResource(R.drawable.ic_info_outline),
            label = stringResource(R.string.metadata),
            isFolded = isMetadataFolded,
            onFoldClick = {
                val newFolded = !isMetadataFolded
                isMetadataFolded = newFolded
                AppPreferences.noteMetadataFolded(context, newFolded)
            }
        )

        if (!isMetadataFolded) {
            MetadataSection(
                viewModel = viewModel,
                payload = payload,
                onShowTimestampDialog = onShowTimestampDialog,
                onStateClick = onStateClick,
                onPriorityClick = onPriorityClick,
                onTagsClick = onTagsClick,
                metadataVisibility = metadataVisibility,
                alwaysShowSet = alwaysShowSet,
                selectedMetadata = selectedMetadata,
                propertyNames = propertyNames
            )
        }

        HorizontalDivider()

        FoldableHeader(
            painter = painterResource(R.drawable.ic_notes),
            label = stringResource(R.string.content),
            isFolded = isContentFolded,
            onFoldClick = {
                val newFolded = !isContentFolded
                isContentFolded = newFolded
                AppPreferences.isNoteContentFolded(context, newFolded)
            }
        )

        if (!isContentFolded) {
            ContentToolbar(
                onInsertTimestamp = onInsertTimestamp,
                onInsertFormatting = { prefix, suffix ->
                    richTextViews[R.id.content_edit]?.insertFormattingAtCursor(prefix, suffix)
                }
            )
            RichTextComposable(
                sourceText = payload?.content ?: "",
                onSourceTextChange = { newContent ->
                    viewModel.updatePayload(
                        title = payload?.title ?: "",
                        content = newContent,
                        state = payload?.state,
                        priority = payload?.priority,
                        tags = payload?.tags ?: emptyList(),
                        properties = payload?.properties ?: OrgProperties()
                    )
                },
                attributes = RichText.Attributes(
                    hint = stringResource(R.string.content),
                    textSize = contentTextSizePx,
                    inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                    imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN,
                    editId = R.id.content_edit
                ),
                viewId = R.id.content_edit,
                useMonospaceFont = useMonospaceFont,
                onViewCreated = { richTextViews[R.id.content_edit] = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
            )
        }
    }
}

@Composable
fun ContentToolbar(
    onInsertTimestamp: () -> Unit,
    onInsertFormatting: (prefix: String, suffix: String) -> Unit = { _, _ -> }
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    data class FormatButton(val iconRes: Int, val contentDesc: String, val prefix: String, val suffix: String)
    val formattingButtons = listOf(
        FormatButton(R.drawable.ic_format_bold,          "Bold",          "*",              "*"),
        FormatButton(R.drawable.ic_format_italic,        "Italic",        "/",              "/"),
        FormatButton(R.drawable.ic_format_underline,     "Underline",     "_",              "_"),
        FormatButton(R.drawable.ic_format_strikethrough, "Strikethrough", "+",              "+"),
        FormatButton(R.drawable.ic_format_code,          "Code",          "~",              "~"),
        FormatButton(R.drawable.ic_format_verbatim,      "Verbatim",      "=",              "="),
        FormatButton(R.drawable.ic_format_quote,         "Quote block",   "#+BEGIN_QUOTE\n", "\n#+END_QUOTE"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        IconButton(onClick = onInsertTimestamp) {
            Icon(
                painter = painterResource(R.drawable.ic_today),
                contentDescription = stringResource(R.string.insert_timestamp),
                tint = tint
            )
        }
        formattingButtons.forEach { btn ->
            IconButton(onClick = { onInsertFormatting(btn.prefix, btn.suffix) }) {
                Icon(
                    painter = painterResource(btn.iconRes),
                    contentDescription = btn.contentDesc,
                    tint = tint
                )
            }
        }
    }
}

@Composable
fun BreadcrumbsView(
    details: NoteViewModel.NoteDetailsData,
    onBookBreadcrumbClick: () -> Unit,
    onNoteBreadcrumbClick: (Note) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        val bookTitle = details.book?.book?.name ?: ""
        Text(
            text = bookTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onBookBreadcrumbClick)
        )
        details.ancestors.forEach { ancestor ->
            Text(text = " > ", style = MaterialTheme.typography.bodySmall)
            Text(
                text = ancestor.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onNoteBreadcrumbClick(ancestor) }
            )
        }
    }
}

@Composable
fun FoldableHeader(
    painter: Painter,
    label: String,
    isFolded: Boolean,
    onFoldClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onFoldClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        )
        Icon(
            painter = if (isFolded) {
                painterResource(R.drawable.ic_keyboard_arrow_down)
            } else {
                painterResource(R.drawable.ic_keyboard_arrow_up)
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun isMetadataFieldVisible(
    name: String,
    isSet: Boolean,
    visibility: String,
    selectedMetadata: Set<String>,
    alwaysShowSet: Boolean
): Boolean {
    return visibility == "all"
        || (visibility == "selected" && selectedMetadata.contains(name))
        || (alwaysShowSet && isSet)
}

@Composable
fun MetadataSection(
    viewModel: NoteViewModel,
    payload: NotePayload?,
    onShowTimestampDialog: (originViewId: Int, timeType: TimeType, initialTime: OrgDateTime?) -> Unit,
    onStateClick: () -> Unit,
    onPriorityClick: () -> Unit,
    onTagsClick: () -> Unit,
    metadataVisibility: String,
    alwaysShowSet: Boolean,
    selectedMetadata: Set<String>,
    propertyNames: List<String>
) {
    Column {
        // Location (always visible — required to know which book the note is in)
        MetadataItem(
            painter = painterResource(R.drawable.ic_library_books),
            label = stringResource(R.string.notebook),
            value = viewModel.bookView.value?.book?.name ?: "",
            onValueClick = { viewModel.requestNoteBookChange() }
        )

        val tagsValue = payload?.tags?.joinToString(" ") ?: ""
        if (isMetadataFieldVisible("tags", tagsValue.isNotEmpty(), metadataVisibility, selectedMetadata, alwaysShowSet)) {
            MetadataItem(
                painter = painterResource(R.drawable.ic_label_outline),
                label = stringResource(R.string.tags),
                value = tagsValue,
                onValueClick = onTagsClick,
                onClearClick = {
                    viewModel.updatePayload(
                        title = payload?.title ?: "",
                        content = payload?.content ?: "",
                        state = payload?.state,
                        priority = payload?.priority,
                        tags = emptyList(),
                        properties = payload?.properties ?: OrgProperties()
                    )
                }
            )
        }

        val stateValue = payload?.state ?: ""
        if (isMetadataFieldVisible("state", stateValue.isNotEmpty(), metadataVisibility, selectedMetadata, alwaysShowSet)) {
            MetadataItem(
                painter = painterResource(R.drawable.ic_flag),
                label = stringResource(R.string.state),
                value = stateValue,
                onValueClick = onStateClick,
                onClearClick = { viewModel.updatePayloadState(null) }
            )
        }

        val priorityValue = payload?.priority ?: ""
        if (isMetadataFieldVisible("priority", priorityValue.isNotEmpty(), metadataVisibility, selectedMetadata, alwaysShowSet)) {
            MetadataItem(
                painter = painterResource(R.drawable.ic_star_border),
                label = stringResource(R.string.priority),
                value = priorityValue,
                onValueClick = onPriorityClick,
                onClearClick = {
                    viewModel.updatePayload(
                        title = payload?.title ?: "",
                        content = payload?.content ?: "",
                        state = payload?.state,
                        priority = null,
                        tags = payload?.tags ?: emptyList(),
                        properties = payload?.properties ?: OrgProperties()
                    )
                }
            )
        }

        val scheduledValue = payload?.scheduled ?: ""
        if (isMetadataFieldVisible("scheduled_time", scheduledValue.isNotEmpty(), metadataVisibility, selectedMetadata, alwaysShowSet)) {
            MetadataItem(
                painter = painterResource(R.drawable.ic_today),
                label = stringResource(R.string.scheduled),
                value = scheduledValue,
                onValueClick = {
                    onShowTimestampDialog(
                        R.id.scheduled_button,
                        TimeType.SCHEDULED,
                        OrgRange.parseOrNull(scheduledValue)?.startTime
                    )
                },
                onClearClick = { viewModel.updatePayloadScheduledTime(null) }
            )
        }

        val deadlineValue = payload?.deadline ?: ""
        if (isMetadataFieldVisible("deadline_time", deadlineValue.isNotEmpty(), metadataVisibility, selectedMetadata, alwaysShowSet)) {
            MetadataItem(
                painter = painterResource(R.drawable.ic_alarm),
                label = stringResource(R.string.deadline),
                value = deadlineValue,
                onValueClick = {
                    onShowTimestampDialog(
                        R.id.deadline_button,
                        TimeType.DEADLINE,
                        OrgRange.parseOrNull(deadlineValue)?.startTime
                    )
                },
                onClearClick = { viewModel.updatePayloadDeadlineTime(null) }
            )
        }

        // Closed is only shown when it has a value
        payload?.closed?.let { closedValue ->
            MetadataItem(
                painter = painterResource(R.drawable.ic_check_circle_outline),
                label = stringResource(R.string.closed),
                value = closedValue,
                onValueClick = {
                    onShowTimestampDialog(
                        R.id.closed_button,
                        TimeType.CLOSED,
                        OrgRange.parseOrNull(closedValue)?.startTime
                    )
                },
                onClearClick = { viewModel.updatePayloadClosedTime(null) }
            )
        }

        val hasProperties = (payload?.properties?.size() ?: 0) > 0
        if (isMetadataFieldVisible("properties", hasProperties, metadataVisibility, selectedMetadata, alwaysShowSet)) {
            payload?.properties?.all?.forEach { property ->
                PropertyItem(
                    name = property.name,
                    value = property.value,
                    propertyNameSuggestions = propertyNames,
                    onNameChange = { newName ->
                        val newProperties = OrgProperties(payload.properties)
                        newProperties.remove(property.name)
                        if (newName.isNotEmpty()) {
                            newProperties.put(newName, property.value)
                        }
                        viewModel.updatePayload(
                            title = payload.title,
                            content = payload.content ?: "",
                            state = payload.state,
                            priority = payload.priority,
                            tags = payload.tags,
                            properties = newProperties
                        )
                    },
                    onValueChange = { newValue ->
                        val newProperties = OrgProperties(payload.properties)
                        newProperties.put(property.name, newValue)
                        viewModel.updatePayload(
                            title = payload.title,
                            content = payload.content ?: "",
                            state = payload.state,
                            priority = payload.priority,
                            tags = payload.tags,
                            properties = newProperties
                        )
                    },
                    onRemove = {
                        val newProperties = OrgProperties(payload.properties)
                        newProperties.remove(property.name)
                        viewModel.updatePayload(
                            title = payload.title,
                            content = payload.content ?: "",
                            state = payload.state,
                            priority = payload.priority,
                            tags = payload.tags,
                            properties = newProperties
                        )
                    }
                )
            }
            IconButton(onClick = {
                val newProperties = payload?.properties?.let { OrgProperties(it) } ?: OrgProperties()
                newProperties.put("", "")
                viewModel.updatePayload(
                    title = payload?.title ?: "",
                    content = payload?.content ?: "",
                    state = payload?.state,
                    priority = payload?.priority,
                    tags = payload?.tags ?: emptyList(),
                    properties = newProperties
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_property))
            }
        }
    }
}

@Composable
fun PropertyItem(
    name: String,
    value: String,
    propertyNameSuggestions: List<String> = emptyList(),
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val nameSuggestions = if (name.isNotEmpty()) {
        propertyNameSuggestions.filter { it.startsWith(name, ignoreCase = true) && it != name }
    } else emptyList()
    var showNameDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TextField(
                value = name,
                onValueChange = { newName ->
                    onNameChange(newName)
                    showNameDropdown = newName.isNotEmpty()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.property_name)) }
            )
            DropdownMenu(
                expanded = showNameDropdown && nameSuggestions.isNotEmpty(),
                onDismissRequest = { showNameDropdown = false }
            ) {
                nameSuggestions.take(6).forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            onNameChange(suggestion)
                            showNameDropdown = false
                        }
                    )
                }
            }
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.property_value)) }
        )
        if (name.isNotEmpty()) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
            }
        }
    }
}

@Composable
fun MetadataItem(
    painter: Painter,
    label: String,
    value: String,
    onValueClick: () -> Unit,
    onClearClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(
            text = if (value.isEmpty()) label else value,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
                .clickable(onClick = onValueClick),
            color = if (value.isEmpty()) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        if (onClearClick != null && value.isNotEmpty()) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconButton(onClick = onClearClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RichTextComposable(
    sourceText: String,
    onSourceTextChange: (String) -> Unit,
    attributes: RichText.Attributes,
    viewId: Int = View.NO_ID,
    useMonospaceFont: Boolean = false,
    autoFocus: Boolean = false,
    onModeChange: ((Boolean) -> Unit)? = null,
    onViewCreated: (RichText) -> Unit = {},
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    AndroidView(
        factory = { context ->
            RichText(context, attributes).apply {
                id = viewId
                if (useMonospaceFont) {
                    setTypeface(Typeface.MONOSPACE)
                }
                setOnUserTextChangeListener { str ->
                    onSourceTextChange(str)
                }
                if (onModeChange != null) {
                    setOnModeChangeListener(object : RichText.OnModeChangeListener {
                        override fun onEditMode() { onModeChange(true) }
                        override fun onViewMode() { onModeChange(false) }
                    })
                }
                if (autoFocus) {
                    post { toEditMode(0) }
                }
                onViewCreated(this)
            }
        },
        update = { view ->
            if (view.getSourceText()?.toString() != sourceText) {
                view.setSourceText(sourceText)
            }
        },
        modifier = modifier
    )
}
