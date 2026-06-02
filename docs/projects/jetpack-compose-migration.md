# Jetpack Compose Migration Plan

## Current State

The app uses a two-track approach: legacy XML/Fragment screens run in parallel with Compose equivalents. The infrastructure is already in place — Material3 theme, `ComposeActivity`/`ComposeFragment` base classes, a custom component library, and composition-local providers. About 20% of UI surfaces have Compose implementations (Books list, Note editor, Gantt, Saved Search). All Activities remain legacy. Navigation still uses `FragmentManager` + `DisplayManager`, not Navigation Compose.

**Already migrated:** BooksFragmentCompose, NoteFragmentCompose, GanttFragment, SavedSearchFragment  
**Remaining legacy screens:** Notes list, Book preface, Sync status, Settings, Repos, all dialogs, all Activities

---

## Pros and Cons

### Pros

- Eliminates XML layout files and reduces boilerplate significantly
- Compose's declarative model matches the MVVM + LiveData/StateFlow architecture already in use
- Material3 design system already established — consistent visual output
- Easier to build complex, animated UI (e.g., note tree, collapsible panels)
- Compose testing tooling (`ui-test-junit4`) is more expressive than Espresso for UI-level tests
- Removes dependency on View binding and `ViewHolder` patterns
- Future-proof: Google's primary UI toolkit

### Cons

- Settings screen relies on `PreferenceFragmentCompat`, which has no Compose equivalent — requires a custom settings UI to replace it
- Interop cost: each Compose screen embedded in a Fragment adds overhead (`ComposeView` wrapper) until full Activity-level Compose is adopted
- Some third-party libraries (e.g., repo-specific UI) may assume View-based contexts
- Espresso tests targeting legacy Views must be rewritten or adapted
- Migration is inherently incremental — the two-track codebase adds complexity until legacy code is deleted

---

## Architectural Improvements to Include

These changes are not strictly required but will pay off during and after migration:

1. **Replace FragmentManager navigation with Navigation Compose** — the current `DisplayManager`/`Navigator`/`NavigationDestination` system is a hand-rolled nav graph. Replacing it with Navigation Compose eliminates deep linking fragility and enables type-safe routes. Do this after individual screens are migrated, as the last step.

2. **Migrate LiveData → StateFlow in ViewModels** — Compose works natively with `collectAsStateWithLifecycle()`. `runtime-livedata` bridge works but StateFlow gives better cancellation and testability. Migrate ViewModel by ViewModel alongside screen migration.

3. **Replace DialogFragments with Compose dialogs** — `AlertDialog` / `BasicAlertDialog` in Compose are simpler and composable. Migrate each dialog when its parent screen is migrated.

4. **Custom Settings screen** — `PreferenceFragmentCompat` cannot be replaced by Compose without a full rewrite. Budget this as a dedicated effort; the visual output should match the current screen.

5. **Delete legacy parallel fragments** — after a Compose screen is stable, delete the XML layout, the legacy Fragment, and all View binding code. The two-track approach is a transition tool, not a destination.

---

## Migration Order

Screens are ordered by impact, isolation, and risk. Finish each phase before starting the next.

### Phase 1 — Stabilize existing Compose screens *(low risk)*

The four already-migrated screens (Books, Note, Gantt, SavedSearch) have legacy counterparts still active. Validate them, delete the legacy versions, and remove the feature-flag/preference switch that selects between them.

- Delete `BooksFragment` (XML), keep `BooksFragmentCompose`
- Delete `NoteFragment` (XML), keep `NoteFragmentCompose`
- Delete unused legacy `SavedSearchesFragment` if superseded
- Remove any UI-version preference toggles

### Phase 2 — Dialogs and small components *(low risk, high volume)*

Migrate all `DialogFragment` subclasses to Compose dialogs. These are self-contained and have no navigation dependencies.

- `TimestampDialogFragment`
- `SimpleOneLinerDialog`
- `ShowSshKeyDialogFragment`
- `RefileFragment`
- Any remaining modal sheets

### Phase 3 — Notes list screen *(medium risk)*

`NotesFragment` (abstract) and its concrete implementations are the most-used screen. This requires the most care because the nested-set tree display, selection state, and swipe actions are complex.

- Implement `NotesScreen.kt` composable with `LazyColumn`
- Wire existing `NotesViewModel` (migrate LiveData → StateFlow)
- Replace swipe-to-action with Compose gesture handling
- Delete XML layouts and legacy Fragment

### Phase 4 — Repo and sync management screens *(medium risk)*

The repo Activities (`DirectoryRepoActivity`, `GitRepoActivity`, `WebdavRepoActivity`, `DropboxRepoActivity`, `ReposActivity`) are self-contained flows.

- Convert each to a `ComposeActivity` with a full Compose screen
- `SyncFragment` → Compose screen embedded in `MainActivity`

### Phase 5 — Book preface and secondary screens *(low risk)*

- `BookPrefaceFragment` → Compose screen
- `AppLogsActivity` → Compose screen
- `SshKeygenActivity` → Compose screen

### Phase 6 — Settings screen *(high risk, standalone effort)*

Write a custom Compose settings screen that reproduces all `PreferenceFragmentCompat` functionality. Group logically: appearance, sync, reminders, notifications, advanced. After validation, delete `SettingsFragment`, `SettingsActivity`, and all preference XML files.

### Phase 7 — Navigation Compose *(architectural, last step)*

Replace `DisplayManager` + `Navigator` with Navigation Compose. Define a `NavHost` at the `MainActivity` level (or move to a single `ComposeActivity`). Map existing `NavigationDestination` sealed interface entries to typed routes. Delete `DisplayManager`, `DefaultNavigator`, and `LocalFragmentManager`.

---

## Definition of Done

- Zero XML layout files in `res/layout/` (preference XML is also deleted)
- Zero `ComposeView` wrappers — all screens are top-level Compose content
- All `ViewModels` emit `StateFlow`, not `LiveData`
- Navigation handled entirely by Navigation Compose
- Legacy base classes (`CommonFragment`, `CommonActivity`) either deleted or reduced to non-UI concerns
- All existing Espresso tests either passing or replaced with Compose UI tests
