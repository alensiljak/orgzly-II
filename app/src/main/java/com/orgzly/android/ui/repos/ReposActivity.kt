package com.orgzly.android.ui.repos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import cc.alensiljak.orgzly.BuildConfig
import com.orgzly.android.App
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.compose.base.ComposeActivity
import com.orgzly.android.ui.repo.directory.DirectoryRepoActivity
import com.orgzly.android.ui.repo.dropbox.DropboxRepoActivity
import com.orgzly.android.ui.repo.git.GitRepoActivity
import com.orgzly.android.ui.repo.webdav.WebdavRepoActivity
import com.orgzly.android.ui.showSnackbar
import com.orgzly.android.repos.RepoType
import javax.inject.Inject
import com.orgzly.android.repos.RepoFactory

/**
 * List of user-configured repositories.
 */
class ReposActivity : ComposeActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    @Inject
    lateinit var repoFactory: RepoFactory

    private lateinit var viewModel: ReposViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        App.appComponent.inject(this)
        super.onCreate(savedInstanceState)

        val factory = ReposViewModelFactory.getInstance(dataRepository)
        viewModel = ViewModelProvider(this, factory)[ReposViewModel::class.java]

        viewModel.openRepoRequestEvent.observeSingle(this) { repo ->
            if (repo != null) {
                openRepo(repo)
            }
        }

        viewModel.errorEvent.observeSingle(this) { error ->
            if (error != null) {
                showSnackbar((error.cause ?: error).localizedMessage)
            }
        }
    }

    @Composable
    override fun Content() {
        ReposScreen(
            viewModel = viewModel,
            isDropboxEnabled = BuildConfig.IS_DROPBOX_ENABLED,
            isGitEnabled = AppPreferences.gitIsEnabled(this),
            onNavigateUp = { finish() },
            onNewDropbox = { startRepoActivity(RepoKind.DROPBOX) },
            onNewGit = { startRepoActivity(RepoKind.GIT) },
            onNewWebdav = { startRepoActivity(RepoKind.WEBDAV) },
            onNewDirectory = { startRepoActivity(RepoKind.DIRECTORY) },
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            val granted = grantResults.zip(permissions)
                .find { (_, perm) -> perm == Manifest.permission.WRITE_EXTERNAL_STORAGE }
                ?.let { (result, _) -> result == PackageManager.PERMISSION_GRANTED }
            if (granted == true) {
                GitRepoActivity.start(this)
            }
        }
    }

    private fun startRepoActivity(kind: RepoKind) {
        when (kind) {
            RepoKind.DROPBOX -> DropboxRepoActivity.start(this)

            RepoKind.GIT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    val uri = "package:${BuildConfig.APPLICATION_ID}".toUri()
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        REQUEST_CODE_STORAGE_PERMISSION,
                    )
                } else {
                    GitRepoActivity.start(this)
                }
            }

            RepoKind.WEBDAV -> WebdavRepoActivity.start(this)

            RepoKind.DIRECTORY -> DirectoryRepoActivity.start(this)
        }
    }

    private fun openRepo(repoEntity: com.orgzly.android.db.entity.Repo) {
        try {
            dataRepository.getRepoInstance(repoEntity.id, repoEntity.type, repoEntity.url)
        } catch (e: Exception) {
            e.printStackTrace()
            showSnackbar(getString(cc.alensiljak.orgzly.R.string.repository_not_valid_with_reason, e.message))
            return
        }

        when (repoEntity.type) {
            RepoType.MOCK -> DropboxRepoActivity.start(this, repoEntity.id)
            RepoType.DROPBOX -> DropboxRepoActivity.start(this, repoEntity.id)
            RepoType.DIRECTORY -> DirectoryRepoActivity.start(this, repoEntity.id)
            RepoType.DOCUMENT -> DirectoryRepoActivity.start(this, repoEntity.id)
            RepoType.WEBDAV -> WebdavRepoActivity.start(this, repoEntity.id)
            RepoType.GIT -> GitRepoActivity.start(this, repoEntity.id)
        }
    }

    private enum class RepoKind { DROPBOX, GIT, WEBDAV, DIRECTORY }

    companion object {
        val TAG: String = ReposActivity::class.java.name
        private const val REQUEST_CODE_STORAGE_PERMISSION = 0
    }
}
