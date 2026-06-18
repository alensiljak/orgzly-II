package com.orgzly.android.ui.repo.directory

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cc.alensiljak.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.repos.DocumentRepo
import com.orgzly.android.repos.RepoFactory
import com.orgzly.android.repos.RepoType
import com.orgzly.android.ui.compose.base.ComposeActivity
import com.orgzly.android.ui.repo.RepoViewModel
import com.orgzly.android.ui.repo.RepoViewModelFactory
import com.orgzly.android.util.AppPermissions
import com.orgzly.android.util.LogUtils
import cc.alensiljak.orgzly.BuildConfig
import javax.inject.Inject

class DirectoryRepoActivity : ComposeActivity() {

    @Inject
    lateinit var repoFactory: RepoFactory

    private lateinit var viewModel: RepoViewModel

    private var currentUrl by mutableStateOf("")
    private var urlError by mutableStateOf<String?>(null)
    private var snackbarMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        App.appComponent.inject(this)

        super.onCreate(savedInstanceState)

        val repoId = intent.getLongExtra(ARG_REPO_ID, 0)

        val factory = RepoViewModelFactory.getInstance(dataRepository, repoId)
        viewModel = ViewModelProvider(this, factory)[RepoViewModel::class.java]

        if (viewModel.repoId != 0L) {
            viewModel.loadRepoProperties()?.let { repoWithProps ->
                currentUrl = repoWithProps.repo.url
            }
        }

        viewModel.finishEvent.observeSingle(this, Observer {
            finish()
        })

        viewModel.alreadyExistsEvent.observeSingle(this, Observer {
            snackbarMessage = getString(R.string.repository_url_already_exists)
        })

        viewModel.errorEvent.observeSingle(this, Observer { error ->
            if (error != null) {
                snackbarMessage = (error.cause ?: error).localizedMessage
            }
        })
    }

    @Composable
    override fun Content() {
        DirectoryRepoScreen(
            url = currentUrl,
            onUrlChange = {
                currentUrl = it
                urlError = null
            },
            urlError = urlError,
            snackbarMessage = snackbarMessage,
            onSnackbarShown = { snackbarMessage = null },
            onBrowse = { openDocumentTree.launch(null) },
            onSave = { saveAndFinish() },
            onNavigateUp = { finish() },
        )
    }

    private val openDocumentTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                persistPermissions(uri)
                currentUrl = uri.toString()
            }
        }

    private fun persistPermissions(uri: Uri) {
        if (DocumentRepo.SCHEME == uri.scheme) {
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
    }

    private fun saveAndFinish() {
        val url = currentUrl.trim()

        if (TextUtils.isEmpty(url)) {
            urlError = getString(R.string.can_not_be_empty)
            return
        }

        val repoType = when {
            url.startsWith("file:") -> RepoType.DIRECTORY
            url.startsWith("content:") -> RepoType.DOCUMENT
            else -> {
                urlError = getString(R.string.invalid_repo_url, url)
                return
            }
        }

        val repo = try {
            viewModel.validate(repoType, url)
        } catch (e: Exception) {
            e.printStackTrace()
            urlError = getString(R.string.repository_not_valid_with_reason, e.message)
            return
        }

        val finalize = Runnable {
            viewModel.saveRepo(repoType, repo.uri.toString())
        }

        if (repoType == RepoType.DIRECTORY) {
            runWithPermission(AppPermissions.Usage.LOCAL_REPO, finalize)
        } else {
            finalize.run()
        }
    }

    companion object {
        private val TAG = DirectoryRepoActivity::class.java.name

        private const val ARG_REPO_ID = "repo_id"

        @JvmStatic
        @JvmOverloads
        fun start(activity: Activity, repoId: Long = 0) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setClass(activity, DirectoryRepoActivity::class.java)
                .putExtra(ARG_REPO_ID, repoId)

            activity.startActivity(intent)
        }
    }
}
