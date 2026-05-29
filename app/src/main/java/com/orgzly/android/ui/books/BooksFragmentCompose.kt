package com.orgzly.android.ui.books

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.BundleCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.orgzly.android.App
import com.orgzly.android.AppIntent
import com.orgzly.android.BookName
import com.orgzly.android.data.DataRepository
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.DisplayManager
import com.orgzly.android.ui.compose.base.ComposeFragment
import com.orgzly.android.ui.dialogs.SimpleOneLinerDialog
import com.orgzly.android.ui.main.SharedMainActivityViewModel
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.ui.showSnackbar
import com.orgzly.android.usecase.BookDelete
import cc.alensiljak.orgzly.R
import javax.inject.Inject

class BooksFragmentCompose : ComposeFragment() {

    @Inject
    lateinit var dataRepository: DataRepository

    private lateinit var viewModel: BooksViewModel
    private lateinit var sharedMainActivityViewModel: SharedMainActivityViewModel

    private var listener: BooksFragment.Listener? = null

    private val selectionBackPressHandler = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.clearSelection()
        }
    }

    private val pickFileForBookImport =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val guessedName = guessBookNameFromUri(uri)
                SimpleOneLinerDialog
                    .getInstance("name-imported-book", R.string.import_as, R.string.import_, guessedName, Bundle().apply { putParcelable("uri", uri) })
                    .show(childFragmentManager, SimpleOneLinerDialog.FRAGMENT_TAG)
            } else {
                Log.w(TAG, "Import file not selected")
            }
        }

    private val pickFileForBookExport =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                viewModel.exportBook(uri)
            } else {
                Log.w(TAG, "Export file not selected")
            }
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
        listener = activity as? BooksFragment.Listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedMainActivityViewModel = ViewModelProvider(requireActivity())[SharedMainActivityViewModel::class.java]

        childFragmentManager.setFragmentResultListener("name-new-book", this) { _, result ->
            viewModel.createBook(result.getString("value", ""))
        }

        childFragmentManager.setFragmentResultListener("name-imported-book", this) { _, result ->
            val bookName = result.getString("value", "")
            val userData = result.getBundle("user-data")
            val uri = userData?.let { BundleCompat.getParcelable(it, "uri", Uri::class.java) }!!
            viewModel.importBook(uri, bookName)
        }

        val factory = BooksViewModelFactory.getInstance(dataRepository)
        viewModel = ViewModelProvider(this, factory)[BooksViewModel::class.java]

        requireActivity().onBackPressedDispatcher.addCallback(this, selectionBackPressHandler)

        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        sharedMainActivityViewModel.setCurrentFragment(FRAGMENT_TAG)
        viewModel.refresh(AppPreferences.notebooksSortOrder(context))
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedIds.collect { ids ->
                    selectionBackPressHandler.isEnabled = ids.isNotEmpty()
                    if (ids.isNotEmpty()) {
                        sharedMainActivityViewModel.lockDrawer()
                    } else {
                        sharedMainActivityViewModel.unlockDrawer()
                    }
                }
            }
        }

        viewModel.bookToExportEvent.observeSingle(this) { (book, format) ->
            pickFileForBookExport.launch(BookName.lastPathSegment(book.name, format))
        }

        viewModel.bookExportedEvent.observeSingle(this) { location ->
            activity?.showSnackbar(resources.getString(R.string.book_exported, location))
        }

        viewModel.bookDeletedEvent.observeSingle(this) {
            viewModel.clearSelection()
            activity?.showSnackbar(R.string.message_book_deleted)
        }

        viewModel.errorEvent.observeSingle(this) { error ->
            if (error is BookDelete.NotFound) {
                activity?.showSnackbar(
                    resources.getString(R.string.message_deleting_book_failed, error.localizedMessage)
                )
            } else if (error != null) {
                activity?.showSnackbar((error.cause ?: error).localizedMessage)
            }
        }
    }

    @Composable
    override fun Content() {
        val withActionBar = arguments?.getBoolean(ARG_WITH_ACTION_BAR) ?: true
        val syncState by syncProgressViewModel.syncState.collectAsState(initial = null)
        val isRefreshing = syncState?.isRunning() == true

        BooksScreen(
            viewModel = viewModel,
            withActionBar = withActionBar,
            isRefreshing = isRefreshing,
            onRefresh = { com.orgzly.android.sync.SyncRunner.startSync() },
            onBookClick = { bookId -> listener?.onBookClicked(bookId) },
            onOpenDrawer = { sharedMainActivityViewModel.openDrawer() },
            onNewBook = {
                SimpleOneLinerDialog
                    .getInstance("name-new-book", R.string.new_notebook, R.string.create, null)
                    .show(childFragmentManager, SimpleOneLinerDialog.FRAGMENT_TAG)
            },
            onImportBook = { pickFileForBookImport.launch("*/*") },
            onSearch = { query ->
                DisplayManager.displayQuery(parentFragmentManager, query)
            },
            onSync = { com.orgzly.android.sync.SyncRunner.startSync() },
            onSettings = { startActivity(Intent(context, SettingsActivity::class.java)) },
            onPendingReminders = {
                requireContext().sendBroadcast(
                    Intent(requireContext(), com.orgzly.android.reminders.RemindersBroadcastReceiver::class.java)
                        .setAction(AppIntent.ACTION_SHOW_PENDING_REMINDERS)
                )
            },
        )
    }

    private fun guessBookNameFromUri(uri: Uri): String? {
        val fileName = BookName.getFileName(requireContext(), uri)
        return if (BookName.isSupportedFormatFileName(fileName)) {
            BookName.fromRepoRelativePath(fileName).name
        } else {
            null
        }
    }

    companion object {
        private val TAG = BooksFragmentCompose::class.java.name

        @JvmField
        val FRAGMENT_TAG: String = BooksFragment.FRAGMENT_TAG

        private const val ARG_WITH_ACTION_BAR = "with_action_bar"

        @JvmStatic
        @JvmOverloads
        fun getInstance(withActionBar: Boolean = true): BooksFragmentCompose {
            return BooksFragmentCompose().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_WITH_ACTION_BAR, withActionBar)
                }
            }
        }
    }
}
