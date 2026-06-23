package com.orgzly.android.ui.repo.webdav

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cc.alensiljak.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.repos.RepoType
import com.orgzly.android.repos.WebdavRepo.Companion.CERTIFICATES_PREF_KEY
import com.orgzly.android.repos.WebdavRepo.Companion.PASSWORD_PREF_KEY
import com.orgzly.android.repos.WebdavRepo.Companion.USERNAME_PREF_KEY
import com.orgzly.android.ui.compose.base.ComposeActivity
import com.orgzly.android.util.UriUtils
import javax.inject.Inject
import com.orgzly.android.repos.RepoFactory

class WebdavRepoActivity : ComposeActivity() {

    @Inject
    lateinit var repoFactory: RepoFactory

    private lateinit var viewModel: WebdavRepoViewModel

    private var url by mutableStateOf("")
    private var urlError by mutableStateOf<String?>(null)
    private var username by mutableStateOf("")
    private var usernameError by mutableStateOf<String?>(null)
    private var password by mutableStateOf("")
    private var passwordError by mutableStateOf<String?>(null)
    private var snackbarMessage by mutableStateOf<String?>(null)
    private var showCleartextDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        App.appComponent.inject(this)

        super.onCreate(savedInstanceState)

        val repoId = intent.getLongExtra(ARG_REPO_ID, 0)
        val factory = WebdavRepoViewModelFactory.getInstance(dataRepository, repoId)
        viewModel = ViewModelProvider(this, factory)[WebdavRepoViewModel::class.java]

        if (viewModel.repoId != 0L) {
            viewModel.loadRepoProperties()?.let { repoWithProps ->
                url = repoWithProps.repo.url
                username = repoWithProps.props[USERNAME_PREF_KEY] ?: ""
                password = repoWithProps.props[PASSWORD_PREF_KEY] ?: ""
                viewModel.certificates.value = repoWithProps.props[CERTIFICATES_PREF_KEY]
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
        val certificates by viewModel.certificates.observeAsState()
        val connectionResult by viewModel.connectionTestStatus.observeAsState()

        WebdavRepoScreen(
            url = url,
            onUrlChange = { url = it; urlError = null },
            urlError = urlError,
            username = username,
            onUsernameChange = { username = it; usernameError = null },
            usernameError = usernameError,
            password = password,
            onPasswordChange = { password = it; passwordError = null },
            passwordError = passwordError,
            certificates = certificates,
            onCertificatesChange = { viewModel.certificates.value = it },
            connectionResult = connectionResult,
            snackbarMessage = snackbarMessage,
            onSnackbarShown = { snackbarMessage = null },
            showCleartextDialog = showCleartextDialog,
            onTestConnection = { testConnection() },
            onSave = { saveAndFinish() },
            onSaveConfirmed = { doSave(); showCleartextDialog = false },
            onCleartextDismiss = { showCleartextDialog = false },
            onNavigateUp = { finish() },
        )
    }

    private fun saveAndFinish() {
        if (!isInputValid()) return

        if (UriUtils.isUrlSecure(url.trim())) {
            doSave()
        } else {
            showCleartextDialog = true
        }
    }

    private fun doSave() {
        val uriString = url.trim()
        val props = mutableMapOf(
            USERNAME_PREF_KEY to username.trim(),
            PASSWORD_PREF_KEY to password.trim(),
        )
        viewModel.certificates.value?.let { certs ->
            if (certs.isNotBlank()) props[CERTIFICATES_PREF_KEY] = certs
        }
        viewModel.saveRepo(RepoType.WEBDAV, uriString, props)
    }

    private fun testConnection() {
        if (!isInputValid()) return
        viewModel.testConnection(url.trim(), username.trim(), password.trim(), viewModel.certificates.value)
    }

    private fun isInputValid(): Boolean {
        urlError = when {
            TextUtils.isEmpty(url.trim()) -> getString(R.string.can_not_be_empty)
            !WEB_DAV_SCHEME_REGEX.matches(url.trim()) -> getString(R.string.invalid_url)
            UriUtils.containsUser(url.trim()) -> getString(R.string.credentials_in_url_not_supported)
            else -> null
        }

        usernameError = when {
            TextUtils.isEmpty(username.trim()) -> getString(R.string.can_not_be_empty)
            else -> null
        }

        passwordError = when {
            TextUtils.isEmpty(password.trim()) -> getString(R.string.can_not_be_empty)
            else -> null
        }

        return urlError == null && usernameError == null && passwordError == null
    }

    companion object {
        private const val ARG_REPO_ID = "repo_id"

        private val WEB_DAV_SCHEME_REGEX = Regex("^(webdav|dav|http)s?://.+\$")

        @JvmStatic
        @JvmOverloads
        fun start(activity: Activity, repoId: Long = 0) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setClass(activity, WebdavRepoActivity::class.java)
                .putExtra(ARG_REPO_ID, repoId)

            activity.startActivity(intent)
        }
    }
}
