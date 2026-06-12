package com.orgzly.android.ui.sync

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.ui.compose.base.ComposeFragment

class SyncComposeFragment : ComposeFragment() {
    private lateinit var viewModel: SyncViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[SyncViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.state.observe(viewLifecycleOwner) { state ->
            if (state == null || state.isRunning()) {
                viewModel.allowSnackbarOnFailure = true
            }
            if (state != null && !state.isRunning()) {
                if (viewModel.allowSnackbarOnFailure && state.isFailure()) {
                    (activity as? FragmentActivity)?.let {
                        SyncRunner.showSyncFailedSnackBar(it, state)
                    }
                }
                viewModel.allowSnackbarOnFailure = false
            }
        }
    }

    @Composable
    override fun Content() {
        SyncScreen(viewModel)
    }

    companion object {
        @JvmField
        val FRAGMENT_TAG: String = SyncComposeFragment::class.java.name

        @JvmStatic
        fun newInstance(): SyncComposeFragment = SyncComposeFragment()
    }
}
