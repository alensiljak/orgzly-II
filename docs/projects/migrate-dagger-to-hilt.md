# Plan: Migrate Dagger 2 → Hilt

## Context

Orgzly II currently uses Dagger 2.59.2 with a single `AppComponent` and 3 modules. The injection surface is consistent but verbose: 32 manual `inject()` call sites, 15 hand-written `ViewModelFactory` classes, and custom component initialization in `App.java`. Hilt is Google's opinionated wrapper around Dagger 2 that eliminates most of this boilerplate. The question is whether the migration is worth the effort.

**Verdict: Yes, worth it.** The codebase has one component, three modules, no custom scopes, and consistent patterns — ideal Hilt migration conditions. The payoff: ~15 factory classes deleted, ~32 `App.appComponent.inject(this)` calls removed, and standard WorkManager integration gained.

---

## Is It Worth It?

| Factor | Assessment |
|--------|-----------|
| Boilerplate eliminated | ~15 ViewModelFactory classes, 1 AppComponent, 32 inject() calls |
| Complexity | Medium — 60+ files touched, but changes are mechanical and repetitive |
| Risk | Low-medium — single component, 3 modules, no custom scopes |
| Hilt limitations | BroadcastReceivers need entry-point pattern (slightly more verbose); Workers need HiltWorkerFactory setup |
| Already compatible | `SavedSearchViewModel` uses `@AssistedInject` — works unchanged under Hilt |
| App.java | Java class — can add `@HiltAndroidApp` in Java without converting to Kotlin |

---

## Migration Steps

### Step 1 — Add Hilt dependency

In `build.gradle` (project root), add Hilt plugin version alongside `versions.dagger`.  
In `app/build.gradle`:
- Replace `com.google.dagger:dagger` + `kapt dagger-compiler` with `com.google.dagger:hilt-android` + `kapt hilt-compiler`
- Add `id 'com.google.dagger.hilt.android'` plugin

### Step 2 — Annotate Application class

`app/src/main/java/com/orgzly/android/App.java`:
- Add `@HiltAndroidApp` annotation
- Remove manual `DaggerAppComponent.builder()...build()` initialization
- Remove the `public static AppComponent appComponent` field

### Step 3 — Convert Modules to Hilt modules

All 3 modules get `@InstallIn(SingletonComponent::class)` instead of being listed in `@Component(modules = [...])`:

- `di/module/ApplicationModule.kt` — add `@InstallIn(SingletonComponent::class)`, remove constructor params (Hilt provides `Application` and `Context` automatically via `@ApplicationContext`)
- `di/module/DatabaseModule.kt` — add `@InstallIn(SingletonComponent::class)`, inject `Application` via `@ApplicationContext Context` parameter; handle the `testing: Boolean` flag (see testing note below)
- `di/module/DataModule.kt` — add `@InstallIn(SingletonComponent::class)`

### Step 4 — Delete AppComponent

`di/AppComponent.kt` can be deleted entirely — Hilt generates its own component.

### Step 5 — Annotate Activities and Fragments

All Activities and Fragments that call `App.appComponent.inject(this)`:
- Add `@AndroidEntryPoint` to the class
- Remove the `App.appComponent.inject(this)` call
- Base classes (`CommonActivity`, `CommonFragment`, `ComposeFragment`) get `@AndroidEntryPoint` too, which covers subclasses

**Files (~13 Activities):** `MainActivity`, `ReposActivity`, `DropboxRepoActivity`, `DirectoryRepoActivity`, `WebdavRepoActivity`, `GitRepoActivity`, `BrowserActivity`, `SettingsActivity`, `ShareActivity`, `BookChooserActivity`, `TemplateChooserActivity`, `AppLogsActivity`, `ListWidgetSelectionActivity`

**Files (~18 Fragments):** `BooksFragment`, `NotesFragment`, `BookFragment`, `BookPrefaceFragment`, `SearchFragment`, `AgendaFragment`, `NoteFragment`, `SavedSearchesFragment`, `SavedSearchFragment`, `RefileFragment`, `SettingsExportFragment`, `SettingsImportFragment`, `SyncFragment`, `SettingsFragment`, plus any Preference fragments

### Step 6 — Convert ViewModels to @HiltViewModel

For each ViewModel that takes `DataRepository` (or other injected deps) via a factory:
- Add `@HiltViewModel` to the ViewModel class
- Add `@Inject` to the constructor
- Delete the corresponding `*ViewModelFactory` class
- In the Fragment/Activity, replace `ViewModelProvider(this, factory).get(Foo::class.java)` with `by viewModels()`

**Representative pattern:**
```kotlin
// Before
class BooksViewModel(private val dataRepository: DataRepository) : ViewModel()
class BooksViewModelFactory private constructor(private val dataRepository: DataRepository) : ViewModelProvider.Factory { ... }
// Fragment: val vm = ViewModelProvider(this, BooksViewModelFactory.getInstance(repo)).get(BooksViewModel::class.java)

// After
@HiltViewModel
class BooksViewModel @Inject constructor(private val dataRepository: DataRepository) : ViewModel()
// Fragment: private val viewModel: BooksViewModel by viewModels()
```

**15 factories to delete:** `BooksViewModelFactory`, `BookViewModelFactory`, `QueryViewModelFactory`, `RefileViewModelFactory`, `MainActivityViewModelFactory`, `NoteViewModelFactory`, `RepoViewModelFactory`, `WebdavRepoViewModelFactory`, `ReposViewModelFactory`, `SavedSearchesViewModelFactory`, `AppLogsViewModelFactory`, `TimestampDialogViewModelFactory`, `SettingsExportViewModelFactory`, `SettingsImportViewModelFactory`, and SyncViewModelFactory (if exists).

**SavedSearchViewModel** — already uses `@AssistedInject` + `@AssistedFactory`. Under Hilt, just add `@HiltViewModel` and it works unchanged. No factory class to delete here.

**No-arg ViewModels** (`CommonViewModel`, `SharedMainActivityViewModel`, `SyncProgressViewModel`) — no changes needed; they can be optionally annotated with `@HiltViewModel @Inject constructor()` for consistency.

### Step 7 — Migrate Workers to @HiltWorker

Hilt has a dedicated WorkManager integration. For each Worker:
- Add dependency: `implementation "androidx.hilt:hilt-work:1.x"` + `kapt "androidx.hilt:hilt-compiler:1.x"`
- Annotate: `@HiltWorker`
- Change constructor to `@AssistedInject constructor(@Assisted context, @Assisted params, /* injected deps */)`
- Remove `App.appComponent.inject(this)` from `doWork()`
- In `ApplicationModule`, provide `WorkerFactory` via `HiltWorkerFactory` and configure WorkManager to use it in `App.java`

**Workers affected:** `CalendarWorker`, `SyncWorker`, `ScheduledSyncWorker`, `UseCaseWorker`

### Step 8 — Migrate BroadcastReceivers

Hilt supports `@AndroidEntryPoint` on BroadcastReceivers since Hilt 2.28. The change is identical to Activities/Fragments: add annotation, remove manual call.

```kotlin
// Before
class RemindersBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var dataRepository: DataRepository
    override fun onReceive(context: Context, intent: Intent) {
        App.appComponent.inject(this)
        // ...
    }
}

// After
@AndroidEntryPoint
class RemindersBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var dataRepository: DataRepository
    override fun onReceive(context: Context, intent: Intent) {
        // no manual inject call needed
    }
}
```

**Receivers affected:** `RemindersBroadcastReceiver`, `NotificationBroadcastReceiver`, `TimeChangeBroadcastReceiver`, `NewNoteBroadcastReceiver`

### Step 9 — Services

`ListWidgetService` and `ListWidgetProvider` — add `@AndroidEntryPoint`, remove `App.appComponent.inject(this)`.

### Step 10 — Handle testing

`DatabaseModule` has a `testing: Boolean = false` constructor param for using an in-memory DB in tests. Under Hilt:
- Replace with a separate `@TestInstallIn` module in `shared-test` or `androidTest` that overrides the DB binding
- Use `HiltAndroidRule` + `@HiltAndroidTest` in instrumented tests

### Step 11 — Clean up

- Remove `di/AppComponent.kt`
- Remove `kapt "com.google.dagger:dagger-compiler"` (replaced by `hilt-compiler`)
- Remove `implementation "com.google.dagger:dagger"` (Hilt brings it transitively)

---

## Critical Files

| File | Change |
|------|--------|
| `build.gradle` (root) | Add Hilt plugin version |
| `app/build.gradle` | Swap Dagger deps → Hilt deps, add hilt-work |
| `App.java` | `@HiltAndroidApp`, remove component init |
| `di/AppComponent.kt` | **Delete** |
| `di/module/ApplicationModule.kt` | `@InstallIn(SingletonComponent::class)`, fix constructor |
| `di/module/DatabaseModule.kt` | `@InstallIn(SingletonComponent::class)`, testing fix |
| `di/module/DataModule.kt` | `@InstallIn(SingletonComponent::class)` |
| All Activities & Fragments | `@AndroidEntryPoint`, remove `inject(this)` |
| 15 `*ViewModelFactory` files | **Delete** |
| 15 ViewModels (not SavedSearch) | `@HiltViewModel @Inject constructor(...)` |
| 4 Workers | `@HiltWorker @AssistedInject constructor(...)` |
| 4 BroadcastReceivers | `@AndroidEntryPoint`, remove `inject(this)` |

---

## Verification

1. `./gradlew assembleFdroidDebug` — confirms build succeeds and Hilt codegen works
2. `./gradlew testFdroidDebugUnitTest` — unit tests pass
3. `./gradlew connectedFdroidDebugAndroidTest` — instrumented tests pass with `@HiltAndroidTest`
4. Manual smoke test: launch app, open a book, edit a note, trigger sync — confirms DI wiring is correct at runtime
