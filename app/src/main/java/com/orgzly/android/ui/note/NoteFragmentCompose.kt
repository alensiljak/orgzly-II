package com.orgzly.android.ui.note

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.activity.OnBackPressedCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.android.App
import cc.alensiljak.orgzly.BuildConfig
import com.orgzly.android.NotesOrgExporter
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.db.entity.Note
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.Place
import com.orgzly.android.ui.TimeType
import com.orgzly.android.ui.compose.base.ComposeFragment
import com.orgzly.android.ui.compose.note.NoteScreen
import com.orgzly.android.ui.dialogs.TimestampDialogFragment
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.android.ui.showSnackbar
import com.orgzly.android.ui.util.ActivityUtils
import com.orgzly.android.ui.util.KeyboardUtils
import com.orgzly.android.ui.util.getAlarmManager
import com.orgzly.android.ui.views.richtext.RichText
import com.orgzly.org.datetime.OrgDateTime
import com.orgzly.org.datetime.OrgRange
import cc.alensiljak.orgzly.R
import java.util.TreeSet
import javax.inject.Inject

class NoteFragmentCompose : ComposeFragment(), TimestampDialogFragment.OnDateTimeSetListener {

    @Inject
    internal lateinit var dataRepository: DataRepository

    private lateinit var viewModel: NoteViewModel

    private var listener: NoteFragment.Listener? = null

    private val userCancelBackPressHandler = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            userCancel()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
        listener = activity as? NoteFragment.Listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val noteInitialData = noteInitialDataFromArguments()
        val factory = NoteViewModelFactory.getInstance(dataRepository, noteInitialData)
        viewModel = ViewModelProvider(this, factory)[NoteViewModel::class.java]

        if (savedInstanceState == null) {
            viewModel.loadData()
        } else {
            viewModel.restorePayloadFromBundle(savedInstanceState)
            viewModel.loadData()
        }

        setupObservers()

        requireActivity().onBackPressedDispatcher.addCallback(this, userCancelBackPressHandler)
    }

    override fun onPause() {
        super.onPause()
        ActivityUtils.keepScreenOnClear(activity)
    }

    private fun setupObservers() {
        viewModel.noteCreatedEvent.observe(this) { note ->
            listener?.onNoteCreated(note)
        }

        viewModel.noteUpdatedEvent.observe(this) { note ->
            listener?.onNoteUpdated(note)
        }

        viewModel.noteDeletedEvent.observeSingle(this) { count ->
            (activity as? MainActivity)?.popBackStackAndCloseKeyboard()

            val message = if (count == 0) {
                resources.getString(R.string.no_notes_deleted)
            } else {
                resources.getQuantityString(R.plurals.notes_deleted, count, count)
            }

            activity?.showSnackbar(message)
        }

        viewModel.noteDeleteRequest.observeSingle(this) { count ->
            val question = resources.getQuantityString(
                R.plurals.delete_note_or_notes_with_count_question, count, count
            )

            MaterialAlertDialogBuilder(requireContext()).setTitle(question)
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewModel.deleteNote()
                }.setNegativeButton(R.string.cancel) { _, _ -> }.show()
        }

        viewModel.bookChangeRequestEvent.observeSingle(this) { books ->
            if (books != null) {
                handleNoteBookChangeRequest(books)
            }
        }

        viewModel.errorEvent.observeSingle(this) { error ->
            activity?.showSnackbar((error.cause ?: error).localizedMessage)
        }

        viewModel.snackBarMessage.observeSingle(this) { resId ->
            activity?.showSnackbar(resId)
        }
    }

    private fun handleNoteBookChangeRequest(books: List<BookView>) {
        val bookNames = books.map { it.book.name }.toTypedArray()
        val selected = books.indexOfFirst { it.book.id == viewModel.bookId }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notebook)
            .setSingleChoiceItems(bookNames, selected) { dialog, which ->
                viewModel.setBook(books[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun userCancel() {
        KeyboardUtils.closeSoftKeyboard(activity)

        if (viewModel.isNoteModified()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_has_been_modified)
                .setMessage(R.string.discard_or_save_changes)
                .setPositiveButton(R.string.save) { _, _ ->
                    viewModel.saveNote {
                        listener?.onNoteCanceled()
                    }
                }
                .setNegativeButton(R.string.discard) { _, _ ->
                    listener?.onNoteCanceled()
                }
                .setNeutralButton(R.string.cancel, null)
                .show()
        } else {
            listener?.onNoteCanceled()
        }
    }

    private fun userFollowBookBreadcrumb() {
        KeyboardUtils.closeSoftKeyboard(activity)

        if (viewModel.isNoteModified()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_has_been_modified)
                .setMessage(R.string.discard_or_save_changes)
                .setPositiveButton(R.string.save) { _, _ ->
                    viewModel.saveNote { viewModel.followBookBreadcrumb() }
                }
                .setNegativeButton(R.string.discard) { _, _ ->
                    viewModel.followBookBreadcrumb()
                }
                .setNeutralButton(R.string.cancel, null)
                .show()
        } else {
            viewModel.followBookBreadcrumb()
        }
    }

    private fun userFollowNoteBreadcrumb(ancestor: Note) {
        KeyboardUtils.closeSoftKeyboard(activity)

        if (viewModel.isNoteModified()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_has_been_modified)
                .setMessage(R.string.discard_or_save_changes)
                .setPositiveButton(R.string.save) { _, _ ->
                    viewModel.saveNote { viewModel.followNoteBreadcrumb(ancestor) }
                }
                .setNegativeButton(R.string.discard) { _, _ ->
                    viewModel.followNoteBreadcrumb(ancestor)
                }
                .setNeutralButton(R.string.cancel, null)
                .show()
        } else {
            viewModel.followNoteBreadcrumb(ancestor)
        }
    }

    private fun noteInitialDataFromArguments(): NoteInitialData {
        val args = requireNotNull(arguments)
        val bookId = args.getLong(ARG_BOOK_ID)
        val noteId = args.getLong(ARG_NOTE_ID)
        val place = args.getString(ARG_PLACE)?.let { Place.valueOf(it) }
        val title = args.getString(ARG_TITLE)
        val content = args.getString(ARG_CONTENT)
        return NoteInitialData(bookId, noteId, place, title, content)
    }

    @Composable
    override fun Content() {
        NoteScreen(
            viewModel = viewModel,
            onBack = { userCancel() },
            onShowTimestampDialog = { originViewId, timeType, initialTime ->
                TimestampDialogFragment.getInstance(
                    originViewId,
                    timeType,
                    emptySet(),
                    initialTime
                ).show(childFragmentManager, TimestampDialogFragment.FRAGMENT_TAG)
            },
            onShare = { shareNote() },
            onBookBreadcrumbClick = { userFollowBookBreadcrumb() },
            onNoteBreadcrumbClick = { ancestor -> userFollowNoteBreadcrumb(ancestor) }
        )
    }

    private fun shareNote() {
        viewModel.noteId.let { noteId ->
            try {
                val exporter = NotesOrgExporter(dataRepository)
                val orgContent = exporter.exportNote(noteId)

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, orgContent)
                }

                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share note", e)
                activity?.showSnackbar(R.string.failed_sharing_note)
            }
        }
    }

    override fun onDateTimeSet(originViewId: Int, noteIds: TreeSet<Long>, time: OrgDateTime?) {
        val range = if (time != null) OrgRange(time) else null
        when (originViewId) {
            R.id.scheduled_button -> {
                ensureAlarmPermissions(time)
                viewModel.updatePayloadScheduledTime(range)
            }
            R.id.deadline_button -> {
                ensureAlarmPermissions(time)
                viewModel.updatePayloadDeadlineTime(range)
            }
            R.id.closed_button -> viewModel.updatePayloadClosedTime(range)
            R.id.content_edit, R.id.title_edit -> {
                if (time != null) {
                    val originView = view?.findViewById<RichText>(originViewId)
                    originView?.insertStringAtCursorPosition(time.toString())
                    ensureAlarmPermissions(time)
                }
            }
        }
    }

    private fun ensureAlarmPermissions(time: OrgDateTime?) {
        if ((time != null) && time.hasTime()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!requireContext().getAlarmManager().canScheduleExactAlarms()) {
                    val uri = ("package:" + BuildConfig.APPLICATION_ID).toUri()
                    activity?.startActivity(Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri))
                }
            }
        }
    }

    override fun onDateTimeAborted(originViewId: Int, noteIds: TreeSet<Long>) {
    }

    fun getNoteId(): Long {
        return viewModel.noteId
    }

    companion object {
        private val TAG = NoteFragmentCompose::class.java.simpleName
        const val FRAGMENT_TAG = "com.orgzly.android.ui.note.NoteFragmentCompose"
        private const val ARG_BOOK_ID = "book_id"
        private const val ARG_NOTE_ID = "note_id"
        private const val ARG_PLACE = "place"
        private const val ARG_TITLE = "title"
        private const val ARG_CONTENT = "content"

        fun forExistingNote(bookId: Long, noteId: Long): NoteFragmentCompose {
            return NoteFragmentCompose().apply {
                arguments = Bundle().apply {
                    putLong(ARG_BOOK_ID, bookId)
                    putLong(ARG_NOTE_ID, noteId)
                }
            }
        }

        fun forNewNote(notePlace: NotePlace, title: String? = null, content: String? = null): NoteFragmentCompose {
            return NoteFragmentCompose().apply {
                arguments = Bundle().apply {
                    putLong(ARG_BOOK_ID, notePlace.bookId)
                    putLong(ARG_NOTE_ID, notePlace.noteId)
                    if (notePlace.place != null) {
                        putString(ARG_PLACE, notePlace.place.toString())
                    }
                    if (title != null) {
                        putString(ARG_TITLE, title)
                    }
                    if (content != null) {
                        putString(ARG_CONTENT, content)
                    }
                }
            }
        }
    }
}
