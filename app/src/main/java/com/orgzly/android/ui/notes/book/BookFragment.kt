package com.orgzly.android.ui.notes.book

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cc.alensiljak.orgzly.BuildConfig
import cc.alensiljak.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.NotesOrgExporter
import com.orgzly.android.db.NotesClipboard
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.ui.DisplayManager
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.Place
import com.orgzly.android.ui.SelectableItemAdapter
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.dialogs.TimestampDialogFragment
import com.orgzly.android.ui.drawer.DrawerItem
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.android.ui.main.SharedMainActivityViewModel
import com.orgzly.android.ui.notes.NotesFragment
import com.orgzly.android.ui.notes.book.BookViewModel.Companion.APP_BAR_DEFAULT_MODE
import com.orgzly.android.ui.notes.book.BookViewModel.Companion.APP_BAR_SELECTION_MODE
import com.orgzly.android.ui.notes.book.BookViewModel.Companion.APP_BAR_SELECTION_MOVE_MODE
import com.orgzly.android.ui.refile.RefileFragment
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.ui.views.style.CheckboxSpan
import com.orgzly.android.ui.views.style.CustomIdLinkSpan
import com.orgzly.android.ui.views.style.FileLinkSpan
import com.orgzly.android.ui.views.style.FileOrNotLinkSpan
import com.orgzly.android.ui.views.style.IdLinkSpan
import com.orgzly.android.ui.views.style.UrlLinkSpan
import com.orgzly.android.usecase.NoteToggleFolding
import com.orgzly.android.usecase.NoteToggleFoldingSubtree
import com.orgzly.android.usecase.NoteUpdateContent
import com.orgzly.android.usecase.UseCaseRunner
import com.orgzly.android.BookUtils
import com.orgzly.android.util.LogUtils

/**
 * Displays all notes from the notebook, in Jetpack Compose.
 * Allows moving, cutting, pasting etc.
 */
class BookFragment :
        NotesFragment(),
        TimestampDialogFragment.OnDateTimeSetListener,
        DrawerItem {

    private var listener: Listener? = null

    private lateinit var sharedMainActivityViewModel: SharedMainActivityViewModel

    private lateinit var viewModel: BookViewModel

    var currentBook: Book? = null

    private var mBookId: Long = 0

    override fun getAdapter(): SelectableItemAdapter? = null

    override fun getCurrentListener(): NotesFragment.Listener? = listener

    private val appBarBackPressHandler = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (viewModel.isNarrowed()) {
                viewModel.widenView()
            } else {
                viewModel.appBar.handleOnBackPressed()
            }
        }
    }

    init {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, context)
        listener = activity as Listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        sharedMainActivityViewModel = ViewModelProvider(requireActivity())
                .get(SharedMainActivityViewModel::class.java)

        parseArguments()

        val factory = BookViewModelFactory.forBook(dataRepository, mBookId)
        viewModel = ViewModelProvider(this, factory).get(BookViewModel::class.java)

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
                    BookContent()
                }
            }
        }
    }

    @Composable
    private fun BookContent() {
        val data by viewModel.data.observeAsState()
        val tags by viewModel.tags.observeAsState(emptyList())
        val syncState by syncProgressViewModel.syncState.collectAsState(initial = null)

        BookScreen(
            viewModel = viewModel,
            bookTitle = BookUtils.getFragmentTitleForBook(data?.book),
            isRefreshing = syncState?.isRunning() == true,
            pasteCount = NotesClipboard.count(),
            currentFiletags = data?.book?.filetags?.toString().orEmpty(),
            tagSuggestions = tags,
            onRefresh = { SyncRunner.startSync() },
            onOpenDrawer = { sharedMainActivityViewModel.openDrawer() },
            onSearch = { query -> DisplayManager.displayQuery(parentFragmentManager, query) },
            onNoteClick = ::handleNoteClick,
            onNoteLongClick = ::handleNoteLongClick,
            onNewNoteFab = ::newNoteFromFab,
            onPrefaceClick = ::onPrefaceClick,
            onToggleFold = { id -> runUseCase(NoteToggleFolding(id)) },
            onToggleFoldSubtree = { id -> runUseCase(NoteToggleFoldingSubtree(id)) },
            onCheckboxToggle = ::toggleCheckbox,
            onLinkClick = ::followLink,
            onSwipe = ::onNoteSwipe,
            onSelectionAction = { itemId -> handleActionItemClick(viewModel.getSelectedIds(), itemId) },
            onBookAction = ::handleBookActionItemClick,
            onApplyFiletags = ::applyFiletags,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.data.observe(viewLifecycleOwner, Observer { data ->
            currentBook = data.book

            data.notes?.let { notes ->
                viewModel.retainSelection(notes.mapTo(hashSetOf()) { it.note.id })
            }

            setFlipperDisplayedChild(data.book, data.notes)
        })

        viewModel.refileRequestEvent.observeSingle(viewLifecycleOwner, Observer {
            RefileFragment.getInstance(it.selected, it.count)
                    .show(childFragmentManager, RefileFragment.FRAGMENT_TAG)
        })

        viewModel.notesDeleteRequest.observeSingle(viewLifecycleOwner, Observer { pair ->
            val ids = pair.first
            val count = pair.second

            val question = resources.getQuantityString(
                    R.plurals.delete_note_or_notes_with_count_question, count, count)

            dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(question)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        listener?.onNotesDeleteRequest(mBookId, ids)
                    }
                    .setNegativeButton(R.string.cancel) { _, _ -> }
                    .show()
        })

        viewModel.appBar.mode.observeSingle(viewLifecycleOwner) { mode ->
            when (mode) {
                APP_BAR_DEFAULT_MODE -> {
                    viewModel.clearSelection()
                    sharedMainActivityViewModel.unlockDrawer()
                    appBarBackPressHandler.isEnabled = viewModel.isNarrowed()
                }
                APP_BAR_SELECTION_MODE, APP_BAR_SELECTION_MOVE_MODE -> {
                    sharedMainActivityViewModel.lockDrawer()
                    appBarBackPressHandler.isEnabled = true
                }
            }
        }

        viewModel.narrowedNoteId.observe(viewLifecycleOwner) {
            if (viewModel.appBar.mode.value != APP_BAR_DEFAULT_MODE) return@observe
            appBarBackPressHandler.isEnabled = viewModel.isNarrowed()
        }
    }

    private fun setFlipperDisplayedChild(book: Book?, notes: List<NoteView>?) {
        val child = when {
            book == null -> BookViewModel.FlipperDisplayedChild.DOES_NOT_EXIST
            notes == null -> BookViewModel.FlipperDisplayedChild.LOADING
            notes.isNotEmpty() || isPrefaceDisplayed(book) -> BookViewModel.FlipperDisplayedChild.LOADED
            else -> BookViewModel.FlipperDisplayedChild.EMPTY
        }
        viewModel.setFlipperDisplayedChild(child)
    }

    private fun isPrefaceDisplayed(book: Book?): Boolean {
        val hidden = getString(R.string.pref_value_preface_in_book_hide) ==
                AppPreferences.prefaceDisplay(context)
        return !book?.preface.isNullOrBlank() && !hidden && !viewModel.isNarrowed()
    }

    override fun onResume() {
        super.onResume()
        sharedMainActivityViewModel.setCurrentFragment(FRAGMENT_TAG)
    }

    override fun onDetach() {
        super.onDetach()
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)
        listener = null
    }

    private fun parseArguments() {
        arguments?.let {
            require(it.containsKey(ARG_BOOK_ID)) { "No book id passed" }
            mBookId = it.getLong(ARG_BOOK_ID)
            require(mBookId > 0) { "Passed book id $mBookId is not valid" }
        } ?: throw IllegalArgumentException("No arguments passed")
    }

    /* Note interactions */

    private fun handleNoteClick(noteView: NoteView) {
        val id = noteView.note.id

        if (viewModel.isNarrowed() && id == viewModel.narrowedNoteId.value) {
            if (!AppPreferences.isReverseNoteClickAction(context) && viewModel.selectionCount == 0) {
                openNote(id)
            }
            return
        }

        if (!AppPreferences.isReverseNoteClickAction(context)) {
            if (viewModel.selectionCount > 0) {
                viewModel.toggleSelection(id)
            } else {
                openNote(id)
            }
        } else {
            viewModel.toggleSelection(id)
        }
    }

    private fun handleNoteLongClick(noteView: NoteView) {
        val id = noteView.note.id

        if (viewModel.isNarrowed() && id == viewModel.narrowedNoteId.value) {
            if (AppPreferences.isReverseNoteClickAction(context)) {
                openNote(id)
            }
            return
        }

        if (!AppPreferences.isReverseNoteClickAction(context)) {
            viewModel.toggleSelection(id)
        } else {
            openNote(id)
        }
    }

    private fun openNote(id: Long) {
        listener?.onNoteOpen(id)
    }

    private fun onNoteSwipe(noteView: NoteView, direction: Int, screenX: Int, screenY: Int) {
        // Disable swipe popup for the narrowed root note (matches legacy behaviour).
        if (viewModel.isNarrowed() && noteView.note.id == viewModel.narrowedNoteId.value) {
            return
        }

        val anchor = view ?: return

        showPopupWindowAt(
            noteView.note.id,
            com.orgzly.android.ui.notes.NotePopup.Location.BOOK,
            direction,
            anchor,
            screenX,
            screenY,
        ) { noteId, buttonId ->
            handleActionItemClick(setOf(noteId), buttonId)
        }
    }

    private fun newNoteFromFab() {
        val narrowedId = viewModel.narrowedNoteId.value
        val notePlace = if (narrowedId != null) {
            NotePlace(mBookId, narrowedId, Place.UNDER)
        } else {
            NotePlace(mBookId)
        }
        listener?.onNoteNewRequest(notePlace)
    }

    private fun onPrefaceClick() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)
        currentBook?.let { listener?.onBookPrefaceEditRequest(it) }
    }

    private fun runUseCase(useCase: com.orgzly.android.usecase.UseCase) {
        App.EXECUTORS.diskIO().execute { UseCaseRunner.run(useCase) }
    }

    private fun toggleCheckbox(note: Note, span: CheckboxSpan) {
        val content = note.content ?: return
        if (span.rawStart < 0 || span.rawEnd > content.length) return

        val replacement = if (span.getState() == CheckboxSpan.State.CHECKED) "[ ]" else "[X]"
        val newContent = content.replaceRange(span.rawStart, span.rawEnd, replacement)

        runUseCase(NoteUpdateContent(note.id, newContent))
    }

    private fun followLink(span: Any) {
        when (span) {
            is UrlLinkSpan ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(span.url)))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open url ${span.url}", e)
                }
            is FileLinkSpan -> MainActivity.followLinkToFile(span.path)
            is FileOrNotLinkSpan -> MainActivity.followLinkToFile(span.link)
            is IdLinkSpan ->
                MainActivity.followLinkToNoteOrBookWithProperty(
                    "ID", span.link.substring(IdLinkSpan.PREFIX.length))
            is CustomIdLinkSpan ->
                MainActivity.followLinkToNoteOrBookWithProperty(
                    "CUSTOM_ID", span.value.substring(CustomIdLinkSpan.PREFIX.length))
        }
    }

    /* Actions */

    private fun newNoteRelativeToSelection(place: Place, noteId: Long) {
        listener?.onNoteNewRequest(NotePlace(mBookId, noteId, place))
    }

    private fun moveNotes(offset: Int) {
        val ids = viewModel.getSelectedIds()
        if (ids.isEmpty()) {
            Log.e(TAG, "Trying to move notes while there are no notes selected")
            return
        }
        listener?.onNotesMoveRequest(mBookId, ids, offset)
    }

    private fun pasteNotes(place: Place, noteId: Long) {
        viewModel.clearSelection()
        listener?.onNotesPasteRequest(mBookId, noteId, place)
    }

    private fun delete(ids: Set<Long>) {
        viewModel.requestNotesDelete(ids)
    }

    private fun shareNotes(ids: Set<Long>) {
        try {
            val exporter = NotesOrgExporter(dataRepository)
            val exportedNotes = mutableListOf<String>()

            for (noteId in ids) {
                try {
                    exportedNotes.add(exporter.exportNote(noteId))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to export note $noteId", e)
                }
            }

            val content = exportedNotes.joinToString("")
            if (content.isNotEmpty()) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share notes", e)
        }
    }

    private fun applyFiletags(tagsText: String) {
        val book = currentBook ?: return
        val newPreface = updateFiletagsInPreface(book.preface, tagsText)
        listener?.onBookPrefaceUpdate(mBookId, newPreface)
    }

    private fun updateFiletagsInPreface(preface: String?, tagsText: String): String {
        val filetagsLine = if (tagsText.isNotBlank()) {
            val tags = tagsText.split("\\s+".toRegex()).filter { it.isNotBlank() }
            "#+FILETAGS: :${tags.joinToString(":")}:"
        } else {
            null
        }

        if (preface.isNullOrBlank()) {
            return filetagsLine ?: ""
        }

        val lines = preface.lines().toMutableList()
        val existingIndex = lines.indexOfFirst {
            it.trimStart().startsWith("#+FILETAGS:", ignoreCase = true)
        }

        if (existingIndex >= 0) {
            if (filetagsLine != null) {
                lines[existingIndex] = filetagsLine
            } else {
                lines.removeAt(existingIndex)
            }
        } else if (filetagsLine != null) {
            lines.add(0, filetagsLine)
        }

        return lines.joinToString("\n")
    }

    override fun getCurrentDrawerItemId(): String = getDrawerItemId(mBookId)

    /**
     * Called by [DisplayManager] when navigating to a specific note within an already-open book.
     * TODO: scroll to and spotlight the note in the Compose LazyColumn (deferred follow-up).
     */
    fun scrollToNoteIfSet(noteId: Long) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, noteId)
    }

    /** Handles selection-mode / move-mode / popup actions, keyed by menu item id. */
    private fun handleActionItemClick(ids: Set<Long>, itemId: Int) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, ids, itemId)

        if (ids.isEmpty()) {
            Log.e(TAG, "Cannot handle action when there are no items selected")
            viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            return
        }

        when (itemId) {
            R.id.note_popup_new_above, R.id.new_note_above -> {
                newNoteRelativeToSelection(Place.ABOVE, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }
            R.id.note_popup_new_under, R.id.new_note_under -> {
                newNoteRelativeToSelection(Place.UNDER, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }
            R.id.note_popup_new_below, R.id.new_note_below -> {
                newNoteRelativeToSelection(Place.BELOW, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }
            R.id.move -> viewModel.appBar.toMode(APP_BAR_SELECTION_MOVE_MODE)

            in scheduledTimeButtonIds(), in deadlineTimeButtonIds() ->
                displayTimestampDialog(itemId, ids)

            R.id.note_popup_delete, R.id.delete_note -> {
                delete(ids)
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }
            R.id.share -> {
                shareNotes(ids)
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }
            R.id.cut -> {
                listener?.onNotesCutRequest(mBookId, ids)
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }
            R.id.copy -> {
                listener?.onNotesCopyRequest(mBookId, ids)
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }
            R.id.paste_above -> {
                pasteNotes(Place.ABOVE, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }
            R.id.paste_under -> {
                pasteNotes(Place.UNDER, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }
            R.id.paste_below -> {
                pasteNotes(Place.BELOW, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }
            R.id.note_popup_refile, R.id.refile -> viewModel.refile(ids)

            R.id.notes_action_move_up -> moveNotes(-1)
            R.id.notes_action_move_down -> moveNotes(1)
            R.id.notes_action_move_left -> listener?.onNotesPromoteRequest(ids)
            R.id.notes_action_move_right -> listener?.onNotesDemoteRequest(ids)

            R.id.note_popup_set_state, R.id.state ->
                listener?.let { openNoteStateDialog(it, ids, null) }

            R.id.note_popup_toggle_state, R.id.toggle_state ->
                listener?.onStateToggleRequest(ids)

            R.id.note_popup_clock_in, R.id.clock_in -> listener?.onClockIn(ids)
            R.id.note_popup_clock_out, R.id.clock_out -> listener?.onClockOut(ids)
            R.id.note_popup_clock_cancel, R.id.clock_cancel -> listener?.onClockCancel(ids)

            R.id.note_popup_focus, R.id.focus ->
                listener?.onNoteFocusInBookRequest(ids.first())

            R.id.note_popup_narrow -> viewModel.narrowToSubtree(ids.first())

            R.id.note_popup_gantt ->
                DisplayManager.displayGanttForNote(parentFragmentManager, mBookId, ids.first())
        }
    }

    /** Handles book-level (default top bar) actions. */
    private fun handleBookActionItemClick(itemId: Int) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, itemId)

        when (itemId) {
            R.id.books_options_menu_item_cycle_visibility -> viewModel.cycleVisibility()
            R.id.books_options_menu_item_widen_view -> viewModel.widenView()
            R.id.book_actions_paste -> pasteNotes(Place.UNDER, 0)
            R.id.books_options_menu_book_preface -> onPrefaceClick()
            R.id.books_options_menu_book_filetags -> viewModel.showFiletagsDialog()
            R.id.sync -> SyncRunner.startSync()
            R.id.book_actions_gantt -> DisplayManager.displayGantt(parentFragmentManager, mBookId)
            R.id.activity_action_settings ->
                startActivity(Intent(context, SettingsActivity::class.java))
            // TODO: keep_screen_on requires a checkable MenuItem; revisit in Compose.
        }
    }

    interface Listener : NotesFragment.Listener {
        fun onBookPrefaceEditRequest(book: Book)
        fun onBookPrefaceUpdate(bookId: Long, preface: String)
        fun onNotesDeleteRequest(bookId: Long, noteIds: Set<Long>)
        fun onNotesCutRequest(bookId: Long, noteIds: Set<Long>)
        fun onNotesCopyRequest(bookId: Long, noteIds: Set<Long>)
        fun onNotesPasteRequest(bookId: Long, noteId: Long, place: Place)
        fun onNotesPromoteRequest(noteIds: Set<Long>)
        fun onNotesDemoteRequest(noteIds: Set<Long>)
        fun onNotesMoveRequest(bookId: Long, noteIds: Set<Long>, offset: Int)
    }

    companion object {
        private val TAG = BookFragment::class.java.name

        @JvmField
        val FRAGMENT_TAG: String = BookFragment::class.java.name

        private const val ARG_BOOK_ID = "bookId"
        private const val ARG_NOTE_ID = "noteId"

        @JvmStatic
        fun getInstance(bookId: Long, noteId: Long): BookFragment {
            val fragment = BookFragment()
            val args = Bundle()
            args.putLong(ARG_BOOK_ID, bookId)
            args.putLong(ARG_NOTE_ID, noteId)
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun getDrawerItemId(bookId: Long): String = "$TAG $bookId"
    }
}
