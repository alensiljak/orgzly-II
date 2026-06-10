package com.orgzly.android.ui.notes.query.agenda

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import cc.alensiljak.orgzly.BuildConfig
import cc.alensiljak.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.ui.SelectableItemAdapter
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.notes.NotePopup
import com.orgzly.android.ui.notes.query.QueryFragment
import com.orgzly.android.ui.notes.query.QueryViewModel
import com.orgzly.android.ui.notes.query.QueryViewModel.Companion.APP_BAR_DEFAULT_MODE
import com.orgzly.android.ui.notes.query.QueryViewModel.Companion.APP_BAR_SELECTION_MODE
import com.orgzly.android.ui.notes.query.QueryViewModelFactory
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.ui.views.style.CheckboxSpan
import com.orgzly.android.usecase.NoteUpdateContent
import com.orgzly.android.usecase.UseCaseRunner
import com.orgzly.android.util.LogUtils

/**
 * Displays agenda results using Jetpack Compose.
 */
class AgendaFragment : QueryFragment() {

    private val item2databaseIds = hashMapOf<Long, Long>()

    private val appBarBackPressHandler = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.appBar.handleOnBackPressed()
        }
    }

    override fun getAdapter(): SelectableItemAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = QueryViewModelFactory.forQuery(dataRepository)
        viewModel = ViewModelProvider(this, factory).get(QueryViewModel::class.java)

        requireActivity().onBackPressedDispatcher.addCallback(this, appBarBackPressHandler)
        requireActivity().onBackPressedDispatcher.addCallback(this, notePopupDismissOnBackPress)
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
                    AgendaContent()
                }
            }
        }
    }

    @Composable
    private fun AgendaContent() {
        val syncState by syncProgressViewModel.syncState.collectAsState(initial = null)
        val notes by viewModel.data.observeAsState(emptyList())

        val hideEmptyDays = AppPreferences.hideEmptyDaysInAgenda(context)
        val groupScheduledWithToday = AppPreferences.groupScheduledWithTodayInAgenda(context)

        val agendaItems = remember(notes, hideEmptyDays, groupScheduledWithToday) {
            AgendaItems(hideEmptyDays, groupScheduledWithToday)
                .getList(notes, currentQuery, item2databaseIds)
        }

        AgendaScreen(
            viewModel = viewModel,
            agendaItems = agendaItems,
            item2databaseIds = item2databaseIds,
            queryTitle = currentQueryName ?: getString(R.string.agenda),
            querySubtitle = currentQuery,
            isRefreshing = syncState?.isRunning() == true,
            onRefresh = { SyncRunner.startSync() },
            onOpenDrawer = { sharedMainActivityViewModel.openDrawer() },
            onNoteClick = ::handleNoteClick,
            onNoteLongClick = ::handleNoteLongClick,
            onToggleFold = { /* agenda rows are flat — no folding */ },
            onToggleFoldSubtree = { /* agenda rows are flat — no folding */ },
            onCheckboxToggle = ::toggleCheckbox,
            onLinkClick = {},
            onSwipe = ::onNoteSwipe,
            onSelectionAction = { itemId ->
                handleActionItemClick(viewModel.getSelectedIds(), itemId)
            },
            onDefaultAction = ::handleDefaultAction,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.data.observe(viewLifecycleOwner) { notes ->
            val ids = notes.mapTo(hashSetOf()) { it.note.id }
            viewModel.retainSelection(ids)
        }

        viewModel.appBar.mode.observeSingle(viewLifecycleOwner) { mode ->
            when (mode) {
                APP_BAR_DEFAULT_MODE, null -> {
                    sharedMainActivityViewModel.unlockDrawer()
                    appBarBackPressHandler.isEnabled = false
                }
                APP_BAR_SELECTION_MODE -> {
                    sharedMainActivityViewModel.lockDrawer()
                    appBarBackPressHandler.isEnabled = true
                }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)
        viewModel.refresh(currentQuery, AppPreferences.defaultPriority(context))
    }

    override fun onResume() {
        super.onResume()
        sharedMainActivityViewModel.setCurrentFragment(FRAGMENT_TAG)
    }

    private fun handleNoteClick(noteView: NoteView) {
        val id = noteView.note.id
        if (!AppPreferences.isReverseNoteClickAction(context)) {
            if (viewModel.selectionCount > 0) {
                viewModel.toggleSelection(id)
            } else {
                listener?.onNoteOpen(id)
            }
        } else {
            viewModel.toggleSelection(id)
        }
    }

    private fun handleNoteLongClick(noteView: NoteView) {
        val id = noteView.note.id
        if (!AppPreferences.isReverseNoteClickAction(context)) {
            viewModel.toggleSelection(id)
        } else {
            listener?.onNoteOpen(id)
        }
    }

    private fun onNoteSwipe(noteView: NoteView, direction: Int, screenX: Int, screenY: Int) {
        val anchor = view ?: return
        val noteId = noteView.note.id
        // Resolve agenda item ID -> note DB ID via the map (notes can appear multiple times).
        val dbId = item2databaseIds.entries.firstOrNull { it.value == noteId }?.value ?: noteId
        showPopupWindowAt(
            dbId,
            NotePopup.Location.QUERY,
            direction,
            anchor,
            screenX,
            screenY,
        ) { resolvedId, buttonId ->
            handleActionItemClick(setOf(resolvedId), buttonId)
        }
    }

    private fun toggleCheckbox(note: Note, span: CheckboxSpan) {
        val content = note.content ?: return
        if (span.rawStart < 0 || span.rawEnd > content.length) return
        val replacement = if (span.getState() == CheckboxSpan.State.CHECKED) "[ ]" else "[X]"
        val newContent = content.replaceRange(span.rawStart, span.rawEnd, replacement)
        App.EXECUTORS.diskIO().execute { UseCaseRunner.run(NoteUpdateContent(note.id, newContent)) }
    }

    private fun handleDefaultAction(itemId: Int) {
        when (itemId) {
            R.id.sync -> SyncRunner.startSync()
            // TODO: keep_screen_on requires a checkable MenuItem; revisit in Compose.
            R.id.activity_action_settings ->
                startActivity(Intent(context, SettingsActivity::class.java))
        }
    }

    companion object {
        private val TAG = AgendaFragment::class.java.name

        @JvmField
        val FRAGMENT_TAG: String = AgendaFragment::class.java.name

        @JvmStatic
        fun getInstance(query: String): AgendaFragment = getInstance(query, null)

        @JvmStatic
        fun getInstance(query: String, queryName: String? = null): AgendaFragment {
            val fragment = AgendaFragment()
            val args = Bundle()
            args.putString(ARG_QUERY, query)
            if (queryName != null) args.putString(ARG_QUERY_NAME, queryName)
            fragment.arguments = args
            return fragment
        }
    }
}
