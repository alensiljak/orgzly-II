# Jetpack Compose Migration — Phase 4: Repo and Sync Management Screens

## Status

| Work item                                         | Status  |
|---------------------------------------------------|---------|
| `ReposActivity` → `ReposScreen`                   | Done    |
| `DirectoryRepoActivity` → Compose                 | Done    |
| `GitRepoActivity` → Compose                       | Pending |
| `WebdavRepoActivity` → Compose                    | Pending |
| `DropboxRepoActivity` → Compose                   | Pending |
| `SyncFragment` → Compose screen in `MainActivity` | Done    |

---

## Goal

Migrate all repository management screens to Jetpack Compose. The repo Activities
(`DirectoryRepoActivity`, `GitRepoActivity`, `WebdavRepoActivity`, `DropboxRepoActivity`,
`ReposActivity`) are self-contained flows with no shared navigation dependencies, making them a
natural next phase after the notes list. `SyncFragment` is converted to a Compose screen embedded
in `MainActivity`.

Each Activity becomes a `ComposeActivity` subclass with a full Compose screen. The ViewModels are
preserved (with LiveData → StateFlow migration where it adds value).

---

## ReposActivity → ReposScreen

### What the screen does

- Shows a list of all configured repositories (URL + type)
- Empty state: full-screen prompt with buttons to create each repo type (Dropbox hidden when
  disabled; Git hidden when preference `gitIsEnabled` is false)
- Non-empty state: `LazyColumn` of repo rows; tap a row to open its edit screen; long-press (or
  swipe) to delete
- Top bar: back navigation + "New" (+) dropdown menu (Dropbox/Git/WebDAV/Directory) when repos exist
- Permission handling: Git requires `MANAGE_APP_ALL_FILES_ACCESS` (API 30+) or
  `WRITE_EXTERNAL_STORAGE` (API < 30) before launching `GitRepoActivity`

### Approach

- `ReposActivity` extends `ComposeActivity` (replaces `CommonActivity`)
- New `ReposScreen.kt` in `ui/repos/` — pure Compose screen passed the ViewModel
- `ReposViewModel` left mostly intact; `LiveData<List<Repo>>` observed via `observeAsState()`
  (StateFlow migration is deferred — no behaviour change needed here)
- Permission request delegated back to the Activity via a callback lambda (Compose has no direct
  equivalent of `ActivityCompat.requestPermissions`)
- Delete: swipe-to-dismiss on each row (replaces context menu long-press)
- `activity_repos.xml`, `item_repo.xml`, `repos_actions.xml`, `repos_cab.xml` deleted after
  migration

### New files

- `ui/repos/ReposScreen.kt` — `Scaffold` with `TopAppBar` (back + "+ New" dropdown), `LazyColumn`
  of `RepoRow` items with swipe-to-dismiss, and an empty-state column of "create" buttons.

### Changed files

- `ui/repos/ReposActivity.kt` — now extends `ComposeActivity`; `Content()` hosts `ReposScreen`;
  permission logic stays in the Activity

### Deleted files

- `res/layout/activity_repos.xml`
- `res/layout/item_repo.xml`
- `res/menu/repos_actions.xml`
- `res/menu/repos_cab.xml`

---

## SyncFragment → SyncScreen / SyncComposeFragment

### What the screen does

- Shows a sync button card at the bottom of the navigation drawer
- Tap: starts or cancels sync depending on current state
- Long-press: opens a dialog with the current status text and a Copy button
- Icon rotates counter-clockwise while sync is running
- Shows a snackbar when sync fails (once per sync cycle)

### Approach

- `SyncFragment` (View-based retained fragment) replaced by `SyncComposeFragment`
  extending `ComposeFragment`
- New `SyncScreen.kt` — pure Compose card with `combinedClickable`, infinite rotation
  animation via `rememberInfiniteTransition`, and an `AlertDialog` for long-press
- `SyncViewModel` unchanged; `LiveData<SyncState?>` observed in Compose via `observeAsState()`
- Snackbar-on-failure logic (which needs a `FragmentActivity` reference) kept in
  `SyncComposeFragment.onViewCreated` as a `LiveData` observer — not in Compose
- `SyncFragment.run()` use-case executor role moved to a private `runAction()` method
  in `MainActivity` (calls `App.EXECUTORS.diskIO()` + `UseCaseRunner.run()` directly)
- `SyncFragment.Listener` interface removed from both `MainActivity` and `ShareActivity`;
  `onSuccess`/`onError` become plain methods in `MainActivity`
- `ShareActivity` had a vestigial headless `SyncFragment` (never called `run()`) — removed

### Added

- `ui/sync/SyncScreen.kt` — Compose card with icon, text, click/long-click, dialog
- `ui/sync/SyncComposeFragment.kt` — `ComposeFragment` hosting `SyncScreen`; also owns
  the failure-snackbar observer

### Modified

- `ui/main/MainActivity.java` — drops `SyncFragment.Listener`, adds `runAction()`,
  replaces fragment transaction to use `SyncComposeFragment`
- `ui/share/ShareActivity.java` — drops `SyncFragment` entirely (unused there)
- `di/AppComponent.kt` — removes `inject(SyncFragment)`

### Removed

- `ui/sync/SyncFragment.kt`
- `res/layout/fragment_sync.xml`
