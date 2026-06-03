package com.orgzly.android.ui.savedsearches

import android.content.Context
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.ui.compose.base.ComposeFragment
import com.orgzly.android.ui.drawer.DrawerItem
import com.orgzly.android.ui.main.SharedMainActivityViewModel
import javax.inject.Inject

class SavedSearchesFragmentCompose : ComposeFragment(), DrawerItem {

    @Inject
    lateinit var dataRepository: DataRepository

    private lateinit var viewModel: SavedSearchesViewModel
    private lateinit var sharedMainActivityViewModel: SharedMainActivityViewModel

    private var listener: Listener? = null

    private val selectionBackPressHandler = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.clearSelection()
        }
    }

    override fun getCurrentDrawerItemId() = getDrawerItemId()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
        listener = activity as? Listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedMainActivityViewModel = ViewModelProvider(requireActivity())[SharedMainActivityViewModel::class.java]

        val factory = SavedSearchesViewModelFactory.getInstance(dataRepository)
        viewModel = ViewModelProvider(this, factory)[SavedSearchesViewModel::class.java]

        requireActivity().onBackPressedDispatcher.addCallback(this, selectionBackPressHandler)

        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        sharedMainActivityViewModel.setCurrentFragment(FRAGMENT_TAG)
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
    }

    @Composable
    override fun Content() {
        SavedSearchesScreen(
            viewModel = viewModel,
            onNewSearch = { listener?.onSavedSearchNewRequest() },
            onEditSearch = { id -> listener?.onSavedSearchEditRequest(id) },
            onDeleteSearch = { ids -> listener?.onSavedSearchDeleteRequest(ids) },
            onMoveUp = { id -> listener?.onSavedSearchMoveUpRequest(id) },
            onMoveDown = { id -> listener?.onSavedSearchMoveDownRequest(id) },
            onImport = { listener?.onSavedSearchesImportRequest() },
            onExport = { listener?.onSavedSearchesExportRequest() },
            onOpenDrawer = { sharedMainActivityViewModel.openDrawer() },
        )
    }

    interface Listener {
        fun onSavedSearchNewRequest()
        fun onSavedSearchDeleteRequest(ids: Set<Long>)
        fun onSavedSearchEditRequest(id: Long)
        fun onSavedSearchMoveUpRequest(id: Long)
        fun onSavedSearchMoveDownRequest(id: Long)
        fun onSavedSearchesExportRequest()
        fun onSavedSearchesImportRequest()
    }

    companion object {
        const val SEARCH_DOCUMENTATION_URL = "https://orgzly.alensiljak.cc/docs#search"

        private val TAG = SavedSearchesFragmentCompose::class.java.name

        @JvmField
        val FRAGMENT_TAG: String = SavedSearchesFragmentCompose::class.java.name

        @JvmStatic
        val instance: SavedSearchesFragmentCompose
            get() = SavedSearchesFragmentCompose()

        @JvmStatic
        fun getDrawerItemId(): String = TAG
    }
}
