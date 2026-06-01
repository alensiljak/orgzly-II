package com.orgzly.android.ui.notes.gantt

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.ui.compose.base.ComposeFragment
import javax.inject.Inject

class GanttFragment : ComposeFragment() {

    @Inject
    lateinit var dataRepository: DataRepository

    private lateinit var viewModel: GanttViewModel

    private var bookId: Long = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookId = requireArguments().getLong(ARG_BOOK_ID)
        val factory = GanttViewModelFactory(dataRepository, bookId)
        viewModel = ViewModelProvider(this, factory)[GanttViewModel::class.java]
    }

    @Composable
    override fun Content() {
        GanttScreen(
            viewModel = viewModel,
            onNavigateUp = { requireActivity().onBackPressedDispatcher.onBackPressed() },
        )
    }

    companion object {
        @JvmField
        val FRAGMENT_TAG: String = GanttFragment::class.java.name

        private const val ARG_BOOK_ID = "bookId"

        @JvmStatic
        fun getInstance(bookId: Long): GanttFragment {
            val fragment = GanttFragment()
            fragment.arguments = Bundle().apply {
                putLong(ARG_BOOK_ID, bookId)
            }
            return fragment
        }
    }
}
