package com.orgzly.android.git;

import android.net.Uri;

import org.eclipse.jgit.api.TransportCommand;

import cc.alensiljak.orgzly.R;
import com.orgzly.android.prefs.AppPreferences;
import com.orgzly.android.prefs.RepoPreferences;

public class GitPreferencesFromRepoPrefs implements GitPreferences {
    private final RepoPreferences repoPreferences;

    public GitPreferencesFromRepoPrefs(RepoPreferences prefs) {
        repoPreferences = prefs;
    }

    @Override
    public GitTransportSetter createTransportSetter() {
        Uri uri = remoteUri();
        String scheme = uri != null ? uri.getScheme() : null;

        if ("https".equals(scheme)) {
            String username = repoPreferences.getStringValue(R.string.pref_key_git_https_username, "");
            String password = repoPreferences.getStringValue(R.string.pref_key_git_https_password, "");
            return new HTTPSTransportSetter(username, password);
        } else if ("file".equals(scheme)) {
            return new GitTransportSetter() {
                @Override
                public <C extends TransportCommand<?, ?>> C setTransport(C tc) {
                    return tc;
                }
            };
        }
        return new GitSshKeyTransportSetter();
    }

    @Override
    public String getAuthor() {
        return repoPreferences.getStringValueWithGlobalDefault(
                R.string.pref_key_git_author, "orgzly");
    }

    @Override
    public String getEmail() {
        return repoPreferences.getStringValueWithGlobalDefault(
                R.string.pref_key_git_email, "");
    }

    @Override
    public String repositoryFilepath() {
        return repoPreferences.getStringValueWithGlobalDefault(
                R.string.pref_key_git_repository_filepath,
                AppPreferences.repositoryStoragePathForUri(
                        repoPreferences.getContext(), remoteUri()));
    }

    @Override
    public String remoteName() {
        return repoPreferences.getStringValueWithGlobalDefault(
                R.string.pref_key_git_remote_name, "origin");
    }

    @Override
    public String branchName() {
        return repoPreferences.getStringValueWithGlobalDefault(
                R.string.pref_key_git_branch_name, "master");
    }

    @Override
    public Uri remoteUri() {
        return repoPreferences.getRepoUri();
    }
}
