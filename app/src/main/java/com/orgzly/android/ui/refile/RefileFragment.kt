package com.orgzly.android.ui.refile

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cc.alensiljak.orgzly.BuildConfig
import cc.alensiljak.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.notes.NoteItemViewBinder
import com.orgzly.android.ui.showSnackbar
import com.orgzly.android.usecase.NoteRefile
import com.orgzly.android.util.LogUtils
import javax.inject.Inject

class RefileFragment : DialogFragment() {

    @Inject
    lateinit var dataRepository: DataRepository

    lateinit var viewModel: RefileViewModel

    private val errorSubtitle: MutableState<String?> = mutableStateOf(null)

    override fun onAttach(context: Context) {
        super.onAttach(context)

        App.appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        val noteIds = arguments?.getLongArray(ARG_NOTE_IDS)?.toSet() ?: emptySet()
        val count = arguments?.getInt(ARG_COUNT) ?: 0

        val factory = RefileViewModelFactory.forNotes(dataRepository, noteIds, count)

        viewModel = ViewModelProvider(this, factory).get(RefileViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OrgzlyBootstrap {
                    RefileScreen(
                        title = resources.getQuantityString(
                            R.plurals.refile_notes, viewModel.count, viewModel.count),
                        subtitle = errorSubtitle.value,
                        data = viewModel.data.observeAsState().value,
                        onClose = { dismiss() },
                        onItemClick = { viewModel.open(it) },
                        onItemRefile = { viewModel.refile(it) },
                        onRefileHere = { viewModel.refileHere() },
                        onBreadcrumbClick = { viewModel.onBreadcrumbClick(it) },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.refiledEvent.observeSingle(viewLifecycleOwner, Observer { result ->
            dismiss()

            (result.userData as? Note)?.let { firstRefiledNote ->
                activity?.showSnackbar(firstRefiledNote.title, R.string.go_to) {
                    viewModel.goTo(firstRefiledNote.id)
                }
            }
        })

        viewModel.errorEvent.observeSingle(viewLifecycleOwner, Observer { error ->
            errorSubtitle.value =
                if (error is NoteRefile.TargetInNotesSubtree) {
                    getString(R.string.cannot_refile_to_the_same_subtree)
                } else {
                    (error.cause ?: error).localizedMessage
                }
        })

        viewModel.openForTheFirstTime()
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)

            val w = resources.displayMetrics.widthPixels
            val h = resources.displayMetrics.heightPixels

            if (h > w) { // Portrait
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (h * 0.90).toInt())
            } else {
                setLayout((w * 0.90).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }
    }

    companion object {
        fun getInstance(noteIds: Set<Long>, count: Int): RefileFragment {
            return RefileFragment().also { fragment ->
                fragment.arguments = Bundle().apply {
                    putLongArray(ARG_NOTE_IDS, noteIds.toLongArray())
                    putInt(ARG_COUNT, count)
                }
            }
        }

        private const val ARG_NOTE_IDS = "note_ids"
        private const val ARG_COUNT = "count"

        private val TAG = RefileFragment::class.java.name

        val FRAGMENT_TAG: String = RefileFragment::class.java.name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefileScreen(
    title: String,
    subtitle: String?,
    data: Pair<*, List<RefileViewModel.Item>>?,
    onClose: () -> Unit,
    onItemClick: (RefileViewModel.Item) -> Unit,
    onItemRefile: (RefileViewModel.Item) -> Unit,
    onRefileHere: () -> Unit,
    onBreadcrumbClick: (RefileViewModel.Item) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val breadcrumbs = (data?.first as? List<RefileViewModel.Item>) ?: emptyList()
    val items = data?.second ?: emptyList()

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1)
                        if (!subtitle.isNullOrEmpty()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
            )

            BreadcrumbsBar(
                breadcrumbs = breadcrumbs,
                onBreadcrumbClick = onBreadcrumbClick,
                onRefileHere = onRefileHere,
            )

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(items) { item ->
                    RefileItem(
                        item = item,
                        onClick = { onItemClick(item) },
                        onRefile = { onItemRefile(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbsBar(
    breadcrumbs: List<RefileViewModel.Item>,
    onBreadcrumbClick: (RefileViewModel.Item) -> Unit,
    onRefileHere: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scrollState = rememberScrollState()

            // Scroll breadcrumbs to the end whenever the path changes
            LaunchedEffect(breadcrumbs.size) {
                scrollState.scrollTo(scrollState.maxValue)
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                breadcrumbs.forEachIndexed { index, item ->
                    val isLast = index == breadcrumbs.size - 1

                    if (index > 0) {
                        Text(
                            "  •  ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val label = breadcrumbLabel(item)

                    Text(
                        text = label,
                        maxLines = 1,
                        modifier = if (!isLast) {
                            Modifier.clickable { onBreadcrumbClick(item) }
                        } else {
                            Modifier
                        },
                    )
                }
            }

            // Hide refile-here button in notebook list (only Home in breadcrumbs)
            if (breadcrumbs.size > 1) {
                IconButton(
                    onClick = onRefileHere,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_move_to_inbox),
                        contentDescription = stringResource(R.string.refile),
                    )
                }
            }
        }
    }
}

@Composable
private fun breadcrumbLabel(item: RefileViewModel.Item): String {
    return when (val payload = item.payload) {
        is RefileViewModel.Home -> stringResource(R.string.notebooks)
        is Book -> payload.title ?: payload.name
        is Note -> payload.title
        else -> item.name ?: ""
    }
}

@Composable
private fun Bullet(
    isFolded: Boolean,
    modifier: Modifier = Modifier
) {
    val innerSize = 8.dp
    val totalSize = 16.dp
    val colorOnSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val colorSecondaryContainer = MaterialTheme.colorScheme.secondaryContainer

    Box(
        modifier = modifier.size(totalSize),
        contentAlignment = Alignment.Center
    ) {
        if (isFolded) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = colorSecondaryContainer)
            }
        }
        Canvas(modifier = Modifier.size(innerSize)) {
            drawCircle(color = colorOnSecondaryContainer)
        }
    }
}

@Composable
private fun RefileItem(
    item: RefileViewModel.Item,
    onClick: () -> Unit,
    onRefile: () -> Unit,
) {
    val context = LocalContext.current
    val noteItemViewBinder = remember { NoteItemViewBinder(context, true) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (val payload = item.payload) {
                is Note -> {
                    Bullet(
                        isFolded = payload.position.descendantsCount > 0,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    val title = remember(payload) {
                        noteItemViewBinder.generateTitle(NoteView(note = payload, bookName = ""))
                    }
                    AndroidView(
                        factory = { ctx ->
                            TextView(ctx).apply {
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                                gravity = Gravity.CENTER_VERTICAL
                            }
                        },
                        update = { it.text = title },
                    )
                }

                is Book -> {
                    Text(
                        text = payload.title ?: payload.name,
                        maxLines = 1,
                    )
                }
            }
        }

        IconButton(onClick = onRefile) {
            Icon(
                painterResource(R.drawable.ic_move_to_inbox),
                contentDescription = stringResource(R.string.refile),
            )
        }
    }
}
