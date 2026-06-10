package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.alensiljak.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.compose.notelist.NoteItemContent
import com.orgzly.android.ui.compose.notelist.SwipeableNoteRow
import com.orgzly.android.ui.notes.query.QueryViewModel
import com.orgzly.android.ui.notes.query.QueryViewModel.Companion.APP_BAR_SELECTION_MODE
import com.orgzly.android.ui.notes.query.search.QueryDefaultTopBar
import com.orgzly.android.ui.notes.query.search.QueryEmptyMessage
import com.orgzly.android.ui.notes.query.search.QuerySelectionBottomBar
import com.orgzly.android.ui.notes.query.search.QuerySelectionTopBar
import com.orgzly.android.util.UserTimeFormatter
import com.orgzly.android.ui.views.style.CheckboxSpan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    viewModel: QueryViewModel,
    agendaItems: List<AgendaItem>,
    item2databaseIds: Map<Long, Long>,
    queryTitle: String,
    querySubtitle: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenDrawer: () -> Unit,
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
                    else -> AgendaItemList(
                        items = agendaItems,
                        item2databaseIds = item2databaseIds,
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
private fun AgendaItemList(
    items: List<AgendaItem>,
    item2databaseIds: Map<Long, Long>,
    selectedIds: Set<Long>,
    onNoteClick: (NoteView) -> Unit,
    onNoteLongClick: (NoteView) -> Unit,
    onToggleFold: (Long) -> Unit,
    onToggleFoldSubtree: (Long) -> Unit,
    onCheckboxToggle: (Note, CheckboxSpan) -> Unit,
    onLinkClick: (Any) -> Unit,
    onSwipe: (NoteView, Int, Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val formatter = UserTimeFormatter(context)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            when (item) {
                is AgendaItem.Overdue -> AgendaDivider(text = stringResource(R.string.overdue))
                is AgendaItem.Day -> AgendaDivider(text = formatter.formatDate(item.day))
                is AgendaItem.Note -> {
                    val noteDbId = item2databaseIds[item.id] ?: item.note.note.id
                    val isSelected = noteDbId in selectedIds
                    SwipeableNoteRow(
                        onSwipe = { direction, x, y -> onSwipe(item.note, direction, x, y) },
                    ) {
                        NoteItemContent(
                            noteView = item.note,
                            levelOffset = null,
                            isSelected = isSelected,
                            inBook = false,
                            onClick = { onNoteClick(item.note) },
                            onLongClick = { onNoteLongClick(item.note) },
                            onToggleFold = onToggleFold,
                            onToggleFoldSubtree = onToggleFoldSubtree,
                            onCheckboxToggle = onCheckboxToggle,
                            onLinkClick = onLinkClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaDivider(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}
