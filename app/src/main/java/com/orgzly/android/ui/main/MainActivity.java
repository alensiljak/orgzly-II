package com.orgzly.android.ui.main;

import androidx.activity.EdgeToEdge;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.navigation.NavigationView;
import cc.alensiljak.orgzly.BuildConfig;
import cc.alensiljak.orgzly.R;
import com.orgzly.android.App;
import com.orgzly.android.AppEventBus;
import com.orgzly.android.AppIntent;
import com.orgzly.android.SharingShortcutsManager;
import com.orgzly.android.db.NotesClipboard;
import com.orgzly.android.db.entity.Book;
import com.orgzly.android.db.entity.Note;
import com.orgzly.android.db.entity.SavedSearch;
import com.orgzly.android.prefs.AppPreferences;
import com.orgzly.android.reminders.AlarmSoundService;
import com.orgzly.android.sync.AutoSync;
import com.orgzly.android.ui.AppSnackbarUtils;
import com.orgzly.android.ui.CommonActivity;
import com.orgzly.android.ui.DisplayManager;
import com.orgzly.android.ui.NotePlace;
import com.orgzly.android.ui.Place;
import com.orgzly.android.ui.books.BooksFragmentCompose;
import com.orgzly.android.ui.drawer.DrawerNavigationView;
import com.orgzly.android.ui.note.NoteFragmentCompose;
import com.orgzly.android.ui.notes.book.BookFragment;
import com.orgzly.android.ui.notes.book.BookPrefaceFragment;
import com.orgzly.android.ui.notifications.Notifications;
import com.orgzly.android.ui.savedsearch.SavedSearchFragment;
import com.orgzly.android.ui.savedsearches.SavedSearchesFragmentCompose;
import com.orgzly.android.ui.settings.SettingsActivity;
import com.orgzly.android.ui.sync.SyncComposeFragment;
import com.orgzly.android.ui.util.KeyboardUtils;
import com.orgzly.android.usecase.BookExport;
import com.orgzly.android.usecase.BookImportGettingStarted;
import com.orgzly.android.usecase.BookSparseTreeForNote;
import com.orgzly.android.usecase.BookUpdatePreface;
import com.orgzly.android.usecase.NoteCopy;
import com.orgzly.android.usecase.NoteCut;
import com.orgzly.android.usecase.NoteDelete;
import com.orgzly.android.usecase.NoteDemote;
import com.orgzly.android.usecase.NoteMove;
import com.orgzly.android.usecase.NotePaste;
import com.orgzly.android.usecase.NotePromote;
import com.orgzly.android.usecase.NoteUpdateDeadlineTime;
import com.orgzly.android.usecase.NoteUpdateScheduledTime;
import com.orgzly.android.usecase.NoteUpdateState;
import com.orgzly.android.usecase.NoteUpdateStateToggle;
import com.orgzly.android.usecase.SavedSearchCreate;
import com.orgzly.android.usecase.SavedSearchDelete;
import com.orgzly.android.usecase.SavedSearchMoveDown;
import com.orgzly.android.usecase.SavedSearchMoveUp;
import com.orgzly.android.usecase.SavedSearchUpdate;
import com.orgzly.android.usecase.UseCase;
import com.orgzly.android.usecase.UseCaseResult;
import com.orgzly.android.usecase.UseCaseRunner;
import com.orgzly.android.usecase.UseCaseWorker;
import com.orgzly.android.util.AppPermissions;
import com.orgzly.android.util.LogUtils;
import com.orgzly.org.datetime.OrgDateTime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

public class MainActivity extends CommonActivity
        implements
        SavedSearchFragment.Listener,
        SavedSearchesFragmentCompose.Listener,
        BooksFragmentCompose.Listener,
        BookFragment.Listener,
        NoteFragmentCompose.Listener,
        BookPrefaceFragment.Listener {

    public static final String TAG = MainActivity.class.getName();

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    protected DrawerNavigationView drawerNavigationView;

    private static final Set<String> EVENT_ACTIONS = Set.of(
            AppIntent.ACTION_OPEN_NOTE,
            AppIntent.ACTION_FOLLOW_LINK_TO_NOTE_OR_BOOK_WITH_PROPERTY,
            AppIntent.ACTION_FOLLOW_LINK_TO_FILE,
            AppIntent.ACTION_OPEN_SAVED_SEARCHES,
            AppIntent.ACTION_OPEN_QUERY,
            AppIntent.ACTION_OPEN_BOOKS,
            AppIntent.ACTION_OPEN_BOOK,
            AppIntent.ACTION_OPEN_SETTINGS,
            AppIntent.ACTION_SHOW_PENDING_REMINDERS
    );

    private boolean mPromoteDemoteOrMoveRequested = false;

    private Runnable runnableOnResumeFragments;

    private AlertDialog dialog;

    private SharedMainActivityViewModel sharedMainActivityViewModel;

    private MainActivityViewModel viewModel;

    private ActivityForResult activityForResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        App.appComponent.inject(this);

        super.onCreate(savedInstanceState);

        if (BuildConfig.LOG_DEBUG)
            LogUtils.d(TAG, getIntent(), savedInstanceState);

        // Edge-to-edge is enforced on Android 15+ (targetSdk 35+). Enable it explicitly so all
        // API levels lay out the window the same way and IME insets (imePadding in Compose)
        // are the single keyboard mechanism everywhere.
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_main);

        sharedMainActivityViewModel = new ViewModelProvider(this)
                .get(SharedMainActivityViewModel.class);

        ViewModelProvider.Factory factory =
                MainActivityViewModelFactory.Companion.getInstance(dataRepository);

        viewModel = new ViewModelProvider(this, factory).get(MainActivityViewModel.class);

        AppEventBus.observe(this, EVENT_ACTIONS, this::handleIntent, Lifecycle.State.RESUMED);

        setupDrawer();

        setupViewModel();

        setupDisplay(savedInstanceState);

        if (AppPreferences.anyNotificationsEnabled(this)) {
            if (Build.VERSION.SDK_INT >= 33) {
                // Ensure we have the POST_NOTIFICATIONS permission
                AppPermissions.isGrantedOrRequest(this, AppPermissions.Usage.POST_NOTIFICATIONS);
            }
        }

        if (AppPreferences.newNoteNotification(this)) {
            Notifications.showOngoingNotification(this);
        }

        activityForResult = new ActivityForResult(this) {
            @Override
            public void onSearchQueriesImport(@NotNull Uri uri) {
                viewModel.importSavedSearches(uri);
            }

            @Override
            public void onSearchQueriesExport(@NotNull Uri uri) {
                viewModel.exportSavedSearches(uri);
            }
        };

        new SharingShortcutsManager().replaceDynamicShortcuts(this);
    }

    @NotNull
    private OutputStream getOutputStream(@NotNull Uri uri) throws IOException {
        OutputStream stream = getContentResolver().openOutputStream(uri);

        if (stream == null) {
            throw new IOException("Failed opening output stream for " + uri);
        }

        return stream;
    }

    /**
     * Adds initial set of fragments, depending on intent extras
     */
    private void setupDisplay(Bundle savedInstanceState) {
        if (savedInstanceState == null) { // Not a configuration change.
            long bookId = getIntent().getLongExtra(AppIntent.EXTRA_BOOK_ID, 0L);
            long noteId = getIntent().getLongExtra(AppIntent.EXTRA_NOTE_ID, 0L);
            String queryString = getIntent().getStringExtra(AppIntent.EXTRA_QUERY_STRING);

            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, bookId, noteId, queryString);

            DisplayManager.displayBooks(getSupportFragmentManager(), false);

            /* Display requested book and note. */
            if (bookId > 0) {
                DisplayManager.displayBook(getSupportFragmentManager(), bookId, noteId);
                if (noteId > 0) {
                    DisplayManager.displayExistingNote(getSupportFragmentManager(), bookId, noteId);
                }
            } else if (queryString != null) {
                DisplayManager.displayQuery(getSupportFragmentManager(), queryString, null);

            } else {
                handleOrgProtocolIntent(getIntent());
                handleDeepLinkIntent(getIntent());
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOrgProtocolIntent(intent);
        handleDeepLinkIntent(intent);
    }

    private void handleDeepLinkIntent(Intent intent) {
        Uri data = intent.getData();
        if (data != null) {
            boolean isOrgzlyScheme = "orgzly".equals(data.getScheme()) && "note".equals(data.getHost());
            boolean isHttpScheme = ("https".equals(data.getScheme()) || "http".equals(data.getScheme()))
                    && "orgzly.com".equals(data.getHost())
                    && data.getPath() != null && data.getPath().startsWith("/note/");

            if (isOrgzlyScheme || isHttpScheme) {
                String path = data.getPath(); // "/note/123" or "/123"
                if (path != null) {
                    String idStr = data.getLastPathSegment();
                    try {
                        long noteId = Long.parseLong(idStr);
                        viewModel.openNote(noteId);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
        }
    }

    private void handleOrgProtocolIntent(Intent intent) {
        OrgProtocol.handleOrgProtocol(intent, new OrgProtocol.Listener() {
            @Override
            public void onNoteOrBookWithId(@NonNull String id) {
                viewModel.followLinkToNoteOrBookWithProperty("ID", id);
            }

            @Override
            public void onQuery(@NonNull String query) {
                viewModel.displayQuery(query);
            }

            @Override
            public void onError(@NonNull String str) {
                AppSnackbarUtils.showSnackbar(MainActivity.this, str);
            }
        });
    }

    private void setupDrawer() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        mDrawerLayout = findViewById(R.id.drawer_layout);

        NavigationView navigationView = mDrawerLayout.findViewById(R.id.drawer_navigation_view);

        // Handle edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (view, insets) -> {
            Insets innerPadding = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );
            navigationView.setPadding(
                    innerPadding.left,
                    innerPadding.top,
                    innerPadding.right,
                    innerPadding.bottom
            );
            return insets;
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            Intent intent = item.getIntent();

            if (intent != null) {
                mDrawerLayout.closeDrawer(GravityCompat.START);

                // Avoid jerky drawer close by displaying new fragment with a delay
                new Handler().postDelayed(() -> AppEventBus.send(intent), 200);
            }

            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, item, intent);

            return true;
        });

        drawerNavigationView = new DrawerNavigationView(this, viewModel, navigationView);

        if (mDrawerLayout != null) {
            // Set the drawer toggle as the DrawerListener
            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {
                private int state = -1;

                /*
                 * onDrawerOpened and onDrawerClosed are not called fast enough.
                 * So state is determined using onDrawerSlide callback and checking the slide offset.
                 */
                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    super.onDrawerSlide(drawerView, slideOffset);

                    switch (state) {
                        case -1: // Unknown
                            if (slideOffset == 0) {
                                state = 0;
                                drawerClosed();

                            } else if (slideOffset > 0) {
                                state = 1;
                                drawerOpened();
                            }
                            break;

                        case 0: // Starting to open the drawer
                            if (slideOffset > 0) {
                                state = 1;
                                drawerOpened();
                            }
                            break;

                        case 1: // Starting to close the drawer
                            if (slideOffset == 0) {
                                state = 0;
                                drawerClosed();
                            }
                            break;
                    }
                }
            };

            // No flipping burgers
            mDrawerToggle.setDrawerSlideAnimationEnabled(false);

            mDrawerLayout.addDrawerListener(mDrawerToggle);
        }

        addSyncFragment();
    }

    private void setupViewModel() {
        sharedMainActivityViewModel.getDrawerLockState().observe(this, isLocked -> {
            DrawerLayout layout = mDrawerLayout;
            if (layout != null) {
                if (isLocked == null || !isLocked) {
                    layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                } else {
                    layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                }
            }
        });

        sharedMainActivityViewModel.getOpenDrawerRequest().observeSingle(this, value -> {
            if (value != null && value) {
                mDrawerLayout.openDrawer(GravityCompat.START);
            } else {
                mDrawerLayout.closeDrawer(GravityCompat.START);
            }
        });

        sharedMainActivityViewModel.getCurrentFragmentState().observe(this, state -> {
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Observed fragment state: " + state);

            if (state != null) {
                drawerNavigationView.updateActiveFragment(state.getTag());
            }
        });

        viewModel.getNavigationActions().observeSingle(this, action -> {
            if (action instanceof MainNavigationAction.OpenBook) {
                MainNavigationAction.OpenBook thisAction =
                        (MainNavigationAction.OpenBook) action;

                DisplayManager.displayBook(
                        getSupportFragmentManager(),
                        thisAction.getBookId(),
                        0);

            } else if (action instanceof MainNavigationAction.OpenBookFocusNote) {
                MainNavigationAction.OpenBookFocusNote thisAction =
                        (MainNavigationAction.OpenBookFocusNote) action;

                DisplayManager.displayBook(
                        getSupportFragmentManager(),
                        thisAction.getBookId(),
                        thisAction.getNoteId());

            } else if (action instanceof MainNavigationAction.OpenNote) {
                MainNavigationAction.OpenNote thisAction =
                        (MainNavigationAction.OpenNote) action;

                DisplayManager.displayExistingNote(
                        getSupportFragmentManager(),
                        thisAction.getBookId(),
                        thisAction.getNoteId());

            } else if (action instanceof MainNavigationAction.OpenFile) {
                MainNavigationAction.OpenFile thisAction =
                        (MainNavigationAction.OpenFile) action;

                openFileIfExists(thisAction.getFile());

            } else if (action instanceof MainNavigationAction.DisplayQuery) {
                MainNavigationAction.DisplayQuery thisAction =
                        (MainNavigationAction.DisplayQuery) action;

                DisplayManager.displayQuery(
                        getSupportFragmentManager(),
                        thisAction.getQuery(),
                        null);
            }
        });

        viewModel.getSavedSearchedExportEvent().observeSingle(this, count -> {
            AppSnackbarUtils.showSnackbar(this, getResources().getQuantityString(R.plurals.exported_searches, count, count));
        });

        viewModel.getSavedSearchedImportEvent().observeSingle(this, count -> {
            AppSnackbarUtils.showSnackbar(this, getResources().getQuantityString(R.plurals.imported_searches, count, count));

        });
        viewModel.getErrorEvent().observeSingle(this, error -> {
            if (error != null) {
                AppSnackbarUtils.showSnackbar(this, error.getLocalizedMessage());
            }
        });
    }

    private void drawerOpened() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        KeyboardUtils.closeSoftKeyboard(this);
    }

    private void drawerClosed() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);
    }

    private void addSyncFragment() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        if (getSupportFragmentManager().findFragmentByTag(SyncComposeFragment.FRAGMENT_TAG) == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.drawer_sync_container, SyncComposeFragment.newInstance(), SyncComposeFragment.FRAGMENT_TAG)
                    .commit();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState);

        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, newConfig);

        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        if (clearFragmentBackstack) {
            DisplayManager.clear(getSupportFragmentManager());
            clearFragmentBackstack = false;
        }

        performIntros();

        viewModel.refresh(AppPreferences.notebooksSortOrder(this));

        autoSync.trigger(AutoSync.Type.APP_RESUMED);

        stopService(new Intent(this, AlarmSoundService.class));
    }

    private void performIntros() {
        int currentVersion = AppPreferences.lastUsedVersionCode(this);
        boolean isNewVersion = checkIfNewAndUpdateVersion();

        if (isNewVersion) {
            /* Import Getting Started notebook. */
            if (!AppPreferences.isGettingStartedNotebookLoaded(this)) {
                UseCaseWorker.schedule(this, new BookImportGettingStarted());
            }

            /* Open drawer for the first time user. */
            if (currentVersion == 0 && mDrawerLayout != null) {
                mDrawerLayout.openDrawer(GravityCompat.START);
            }

            // Display what's new
            displayWhatsNewDialog();

            // Clear clipboard (due to possible internal format changes across app versions)
            NotesClipboard.clear();
        }
    }

    private boolean checkIfNewAndUpdateVersion() {
        boolean isNewVersion = false;


        if (BuildConfig.VERSION_CODE > AppPreferences.lastUsedVersionCode(this)) {
            isNewVersion = true;
        }

        AppPreferences.lastUsedVersionCode(this, BuildConfig.VERSION_CODE);

        return isNewVersion;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        autoSync.trigger(AutoSync.Type.APP_SUSPENDED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        if (mDrawerLayout != null && mDrawerToggle != null) {
            mDrawerLayout.removeDrawerListener(mDrawerToggle);
        }
    }

    @Override
    public void onBackPressed() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        // Close drawer if opened
        if (mDrawerLayout != null) {
            if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.closeDrawer(GravityCompat.START);
                return;
            }
        }

        // Collapse search view if expanded
        Toolbar toolbar = findViewById(R.id.top_toolbar);
        if (toolbar != null) {
            MenuItem menuItem = toolbar.getMenu().findItem(R.id.search_view);
            if (menuItem != null) {
                if (menuItem.isActionViewExpanded()) {
                    menuItem.collapseActionView();
                    return;
                }
            }
        }

        super.onBackPressed();
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        /* Showing dialog in onResume() fails with:
         *   Can not perform this action after onSaveInstanceState
         */
        if (runnableOnResumeFragments != null) {
            runnableOnResumeFragments.run();
            runnableOnResumeFragments = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        activityForResult.onResult(requestCode, resultCode, data);
    }

    @Override
    public void onNoteFocusInBookRequest(long noteId) {
        runAction(new BookSparseTreeForNote(noteId));
    }

    /* Open note fragment to create a new note. */
    @Override
    public void onNoteNewRequest(NotePlace target) {
        DisplayManager.displayNewNote(getSupportFragmentManager(), target);
    }

    @Override
    public void onNoteCreated(Note note) {
        popBackStackAndCloseKeyboard();

        // FIXME: Gives time for backstack pop to avoid displaying the snackbar on top of FAB
        new Handler(getMainLooper()).postDelayed(() -> {
            // Display Snackbar with an action (create new note below just created one)
            AppSnackbarUtils.showSnackbar(MainActivity.this, R.string.message_note_created, R.string.new_below, () -> {
                NotePlace notePlace = new NotePlace(
                        note.getPosition().getBookId(),
                        note.getId(),
                        Place.BELOW);

                DisplayManager.displayNewNote(getSupportFragmentManager(), notePlace);

                return null;
            });
        }, 100);
    }

    @Override
    public void onNoteUpdated(Note note) {
        popBackStackAndCloseKeyboard();
    }

    @Override
    public void onNoteCanceled() {
        popBackStackAndCloseKeyboard();
    }

    @Override
    public void onStateChangeRequest(Set<Long> noteIds, @Nullable String state) {
        runAction(new NoteUpdateState(noteIds, state));
    }

    @Override
    public void onStateToggleRequest(@NotNull Set<Long> noteIds) {
        runAction(new NoteUpdateStateToggle(noteIds));
    }

    @Override
    public void onScheduledTimeUpdateRequest(Set<Long> noteIds, OrgDateTime time) {
        runAction(new NoteUpdateScheduledTime(noteIds, time));
    }

    @Override
    public void onDeadlineTimeUpdateRequest(Set<Long> noteIds, OrgDateTime time) {
        runAction(new NoteUpdateDeadlineTime(noteIds, time));
    }

    @Override /* BookFragment */
    public void onBookPrefaceEditRequest(Book book) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG);

        DisplayManager.displayEditor(getSupportFragmentManager(), book);
    }

    @Override
    public void onBookPrefaceUpdate(long bookId, String preface) {
        runAction(new BookUpdatePreface(bookId, preface));
    }

    @Override
    public void onNotesDeleteRequest(final long bookId, final Set<Long> noteIds) {
        runAction(new NoteDelete(bookId, noteIds));
    }

    @Override
    public void onNotesCutRequest(long bookId, Set<Long> noteIds) {
        runAction(new NoteCut(bookId, noteIds));
    }

    @Override
    public void onNotesCopyRequest(long bookId, Set<Long> noteIds) {
        runAction(new NoteCopy(bookId, noteIds));
    }

    @Override
    public void onNotesPasteRequest(long bookId, long noteId, Place place) {
        runAction(new NotePaste(bookId, noteId, place));
    }

    @Override
    public void onNotesPromoteRequest(Set<Long> noteIds) {
        mPromoteDemoteOrMoveRequested = true;
        runAction(new NotePromote(noteIds));
    }

    @Override
    public void onNotesDemoteRequest(Set<Long> noteIds) {
        mPromoteDemoteOrMoveRequested = true;
        runAction(new NoteDemote(noteIds));
    }

    @Override
    public void onNotesMoveRequest(long bookId, Set<Long> noteIds, int offset) {
        mPromoteDemoteOrMoveRequested = true;
        runAction(new NoteMove(bookId, noteIds, offset));
    }

    @Override
    public void onBookClicked(long bookId) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, bookId);

        /* Attempt to avoid occasional rare IllegalStateException (state loss related).
         * Consider removing BooksFragment.Listener and using broadcasts for all actions instead.
         */
        // DisplayManager.displayBook(bookId, 0);
        Intent intent = new Intent(AppIntent.ACTION_OPEN_BOOK);
        intent.putExtra(AppIntent.EXTRA_BOOK_ID, bookId);
        AppEventBus.send(intent);
    }

//    private void animateNotesAfterEdit(Set<Long> noteIds) {
//        Fragment f;
//
//        f = getSupportFragmentManager().findFragmentByTag(BookFragment.FRAGMENT_TAG);
//        if (f != null && f.isVisible()) {
//            BookFragment heads = (BookFragment) f;
//            heads.animateNotes(noteIds, HeadAnimator.ANIMATE_FOR_HEAD_MODIFIED);
//        }
//
//        f = getSupportFragmentManager().findFragmentByTag(QueryFragment.FRAGMENT_TAG);
//        if (f != null && f.isVisible()) {
//            QueryFragment heads = (QueryFragment) f;
//            heads.animateNotes(noteIds, HeadAnimator.ANIMATE_FOR_HEAD_MODIFIED);
//        }
//    }

    @Override /* EditorFragment */
    public void onBookPrefaceEditSaveRequest(@NotNull Book book, @NotNull String preface) {
        popBackStackAndCloseKeyboard();
        runAction(new BookUpdatePreface(book.getId(), preface));
    }

    @Override
    public void onBookPrefaceEditCancelRequest() {
        popBackStackAndCloseKeyboard();
    }

    // TODO: Sync when action mode is destroyed
    // autoSync.trigger(AutoSync.Type.DATA_MODIFIED);
    // TODO: When action mode is destroyed
//    mPromoteDemoteOrMoveRequested = false;
//        BottomActionBar.hideBottomBar(findViewById(R.id.bottom_action_bar));

    @Override
    public void onSavedSearchNewRequest() {
        DisplayManager.onSavedSearchNewRequest(getSupportFragmentManager());
    }

    @Override
    public void onSavedSearchEditRequest(long id) {
        DisplayManager.onSavedSearchEditRequest(getSupportFragmentManager(), id);
    }

    @Override
    public void onSavedSearchDeleteRequest(@NotNull Set<Long> ids) {
        runAction(new SavedSearchDelete(ids));
    }

    @Override
    public void onSavedSearchMoveUpRequest(long id) {
        runAction(new SavedSearchMoveUp(id));
    }

    @Override
    public void onSavedSearchMoveDownRequest(long id) {
        runAction(new SavedSearchMoveDown(id));
    }

    @Override
    public void onSavedSearchCreateRequest(SavedSearch savedSearch) {
        popBackStackAndCloseKeyboard();
        runAction(new SavedSearchCreate(savedSearch));
    }

    @Override
    public void onSavedSearchUpdateRequest(SavedSearch savedSearch) {
        popBackStackAndCloseKeyboard();
        runAction(new SavedSearchUpdate(savedSearch));
    }

    @Override
    public void onSavedSearchCancelRequest() {
        popBackStackAndCloseKeyboard();
    }

    @Override
    public void onSavedSearchesExportRequest() {
        activityForResult.startSavedSearchesExportFileChooser();
    }

    @Override
    public void onSavedSearchesImportRequest() {
        activityForResult.startSavedSearchesImportFileChooser();
    }

    private void openSettings() {
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
    }

    public void popBackStackAndCloseKeyboard() {
        getSupportFragmentManager().popBackStack();
        KeyboardUtils.closeSoftKeyboard(this);
    }

    private void runAction(UseCase action) {
        App.EXECUTORS.diskIO().execute(() -> {
            try {
                UseCaseResult result = UseCaseRunner.run(action);
                App.EXECUTORS.mainThread().execute(() -> onSuccess(action, result));
            } catch (Throwable e) {
                e.printStackTrace();
                App.EXECUTORS.mainThread().execute(() -> onError(action, e));
            }
        });
    }

    /**
     * User action succeeded.
     */
    public void onSuccess(UseCase action, UseCaseResult result) {
        if (action instanceof NoteCut) {
            NotesClipboard clipboard = (NotesClipboard) result.getUserData();

            if (clipboard != null) {
                int count = clipboard.getCount();

                String message;

                if (count == 0) {
                    message = getResources().getString(R.string.no_notes_cut);
                } else {
                    message = getResources().getQuantityString(R.plurals.notes_cut, count, count);
                }

                AppSnackbarUtils.showSnackbar(this, message);
            }

        } else if (action instanceof NoteCopy) {
            NotesClipboard clipboard = (NotesClipboard) result.getUserData();

            if (clipboard != null) {
                int count = clipboard.getCount();

                if (count > 0) {
                    String message = getResources().getQuantityString(R.plurals.notes_copied, count, count);
                    AppSnackbarUtils.showSnackbar(this, message);
                }
            }

        } else if (action instanceof NotePaste) {
            int count = (int) result.getUserData();

            String message;
            if (count > 0) {
                message = getResources().getQuantityString(R.plurals.notes_pasted, count, count);
            } else {
                message = getResources().getString(R.string.no_notes_pasted);
            }

            AppSnackbarUtils.showSnackbar(this, message);
        }
    }

    /**
     * User action failed.
     */
    public void onError(UseCase action, Throwable throwable) {
        if (action instanceof BookExport) {
            AppSnackbarUtils.showSnackbar(this, getString(
                    R.string.failed_exporting_book, throwable.getLocalizedMessage()));

        } else {
            if (throwable.getCause() != null) {
                AppSnackbarUtils.showSnackbar(this, throwable.getCause().getLocalizedMessage());
            } else {
                AppSnackbarUtils.showSnackbar(this, throwable.getLocalizedMessage());
            }
        }
    }

    @Override
    public void onNoteOpen(long noteId) {
        viewModel.openNote(noteId);
    }

    public static void openSpecificNote(long bookId, long noteId) {
        Intent intent = new Intent(AppIntent.ACTION_OPEN_NOTE);
        intent.putExtra(AppIntent.EXTRA_NOTE_ID, noteId);
        intent.putExtra(AppIntent.EXTRA_BOOK_ID, bookId);
        AppEventBus.send(intent);
    }

    public static void followLinkToFile(String path) {
        Intent intent = new Intent(AppIntent.ACTION_FOLLOW_LINK_TO_FILE);
        intent.putExtra(AppIntent.EXTRA_PATH, path);
        AppEventBus.send(intent);
    }

    public static void followLinkToNoteOrBookWithProperty(String name, String value) {
        Intent intent = new Intent(AppIntent.ACTION_FOLLOW_LINK_TO_NOTE_OR_BOOK_WITH_PROPERTY);
        intent.putExtra(AppIntent.EXTRA_PROPERTY_NAME, name);
        intent.putExtra(AppIntent.EXTRA_PROPERTY_VALUE, value);
        AppEventBus.send(intent);
    }

    @Override
    public void onClockIn(@NonNull Set<Long> noteIds) {
        viewModel.clockingUpdateRequest(noteIds, 0);
    }

    @Override
    public void onClockOut(@NonNull Set<Long> noteIds) {
        viewModel.clockingUpdateRequest(noteIds, 1);
    }

    @Override
    public void onClockCancel(@NonNull Set<Long> noteIds) {
        viewModel.clockingUpdateRequest(noteIds, 2);
    }

    private void handleIntent(@NonNull Intent intent) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, intent);

        String action = intent.getAction();
        if (action == null) {
            return;
        }

        switch (action) {
            case AppIntent.ACTION_OPEN_NOTE: {
                long bookId = intent.getLongExtra(AppIntent.EXTRA_BOOK_ID, 0);
                long noteId = intent.getLongExtra(AppIntent.EXTRA_NOTE_ID, 0);
                DisplayManager.displayExistingNote(getSupportFragmentManager(), bookId, noteId);
                break;
            }

            case AppIntent.ACTION_OPEN_SAVED_SEARCHES: {
                DisplayManager.displaySavedSearches(getSupportFragmentManager());
                break;
            }

            case AppIntent.ACTION_OPEN_QUERY: {
                String query = intent.getStringExtra(AppIntent.EXTRA_QUERY_STRING);
                String searchName = intent.getStringExtra(AppIntent.EXTRA_SEARCH_NAME);
                DisplayManager.displayQuery(getSupportFragmentManager(), query, searchName);
                break;
            }

            case AppIntent.ACTION_OPEN_BOOKS: {
                DisplayManager.displayBooks(getSupportFragmentManager(), true);
                break;
            }

            case AppIntent.ACTION_OPEN_BOOK: {
                long bookId = intent.getLongExtra(AppIntent.EXTRA_BOOK_ID, 0);
                long noteId = intent.getLongExtra(AppIntent.EXTRA_NOTE_ID, 0);
                DisplayManager.displayBook(getSupportFragmentManager(), bookId, noteId);
                break;
            }

            case AppIntent.ACTION_OPEN_SETTINGS: {
                openSettings();
                break;
            }

            case AppIntent.ACTION_SHOW_PENDING_REMINDERS: {
                sendBroadcast(new Intent(this, com.orgzly.android.reminders.RemindersBroadcastReceiver.class)
                        .setAction(AppIntent.ACTION_SHOW_PENDING_REMINDERS));
                break;
            }

            case AppIntent.ACTION_FOLLOW_LINK_TO_NOTE_OR_BOOK_WITH_PROPERTY: {
                String name = intent.getStringExtra(AppIntent.EXTRA_PROPERTY_NAME);
                String value = intent.getStringExtra(AppIntent.EXTRA_PROPERTY_VALUE);
                viewModel.followLinkToNoteOrBookWithProperty(name, value);
                break;
            }

            case AppIntent.ACTION_FOLLOW_LINK_TO_FILE: {
                String path = intent.getStringExtra(AppIntent.EXTRA_PATH);
                viewModel.followLinkToFile(path);
                break;
            }
        }
    }
}
