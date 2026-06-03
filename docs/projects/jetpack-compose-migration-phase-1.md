# Jetpack Compose Migration — Phase 1: Stabilize Existing Compose Screens

> Part of the [Jetpack Compose Migration Plan](jetpack-compose-migration.md).

**Risk:** Low  
**Goal:** Validate the four already-migrated screens (Books, Note, Gantt, SavedSearch), ensure full test coverage, then delete legacy counterparts.

---

## Current State

| Screen              | Compose fragment                          | Legacy fragment         | Active in main flow? |
|---------------------|-------------------------------------------|-------------------------|----------------------|
| Books list          | `BooksFragmentCompose` → `BooksScreen.kt` | ~~`BooksFragment`~~ ✅ deleted | ✅ (`DisplayManager`) |
| Note editor         | `NoteFragmentCompose`                     | `NoteFragment`          | Partial              |
| Saved searches list | ❌ none                                    | `SavedSearchesFragment` | ✅ (`DisplayManager`) |
| Saved search editor | `SavedSearchFragment` (Compose)           | —                       | ✅ (`DisplayManager`) |
| Gantt               | `GanttFragment` (Compose)                 | —                       | ✅                    |

---

## Blockers Before Deletion

- [x] **`BookChooserActivity`** still instantiates the legacy `BooksFragment` (refile/link flow). Must be migrated to `BooksFragmentCompose` (or a standalone Compose chooser) before `BooksFragment` can be deleted.
- [ ] **`SavedSearchesFragment`** (legacy, 353 lines) is still active in `DisplayManager` — no Compose equivalent exists yet. Must be built before the legacy fragment can be deleted.
- [x] **`AppComponent`** has separate `inject(arg: BooksFragment)` and `inject(arg: BooksFragmentCompose)` entries — the legacy entry can only be removed once `BooksFragment` is gone.

---

## Action Items

### 1. Books screen ✅ DONE

- [x] Migrate `BookChooserActivity` to use `BooksFragmentCompose` instead of `BooksFragment`
- [x] Write Compose UI tests (`BooksScreenTest.kt`) covering:
  - [x] Book list displayed on launch
  - [x] Create new book (FAB → dialog → confirm)
  - [x] Rename book (long-press → selection toolbar → rename)
  - [x] Delete book (selection → delete → snackbar)
  - [x] Export book — covered in `BooksTest.java` (`testCancelExportFileSelection`, `testExportWithFakeResponse`)
  - [ ] Import book — no test has ever existed for this; future work
  - [x] Search bar open/close and filtering — `books_searchBar_openAndClose`, `books_searchBar_filtersByName` in `BooksScreenTest.kt`
  - [ ] Pull-to-refresh triggers sync — no test has ever existed for this; future work
  - [x] Back press clears selection
- [x] Verify existing `BooksTest.java` and `BooksSortOrderTest.kt` still pass or rewrite them as Compose tests
- [x] Delete `BooksFragment.kt` and its XML layout(s) (`fragment_books.xml`, `item_book.xml`, `BooksAdapter.kt`)
- [x] Remove `inject(arg: BooksFragment)` from `AppComponent`

### 2. Note editor screen

- [x] Audit feature parity between `NoteFragment` (1260 lines) and `NoteFragmentCompose` (351 lines) — full parity confirmed; no missing features
- [x] Move `NoteFragment.Listener` → `NoteFragmentCompose.Listener`; update `MainActivity.java` and `ShareActivity.java`
- [x] Remove `inject(arg: NoteFragment)` from `AppComponent`
- [x] Delete `NoteFragment.kt`, `NotePropertySuggestionAdapter.kt`, `fragment_note.xml`, `property.xml`
- [x] Add legacy view IDs to `ids_legacy.xml` so `NoteFragmentTest.kt` still compiles
- [x] Add `contentDescription` to overflow (MoreVert) icon in `NoteScreen.kt` for testability
- [x] Write `NoteScreenTest.kt` covering:
  - [x] Open existing note
  - [x] Edit title and body
  - [x] Set/clear state (TODO/DONE/etc.)
  - [x] Set scheduled and deadline dates
  - [x] Add/remove tags
  - [x] Set priority
  - [x] Save note
  - [x] Discard changes (back press)
  - [x] Create new note
  - [x] Delete note (overflow menu)
  - [x] Existing properties displayed
- [x] Migrate `NoteFragmentTest.kt` to Compose-compatible selectors — all legacy view IDs replaced; added `semantics { contentDescription }` to PropertyItem TextFields in `NoteScreen.kt` for testability

### 3. Saved searches list screen ✅ DONE

- [x] Build `SavedSearchesScreen.kt` composable (to replace `SavedSearchesFragment`)
- [x] Wire it into a new `SavedSearchesFragmentCompose` and update `DisplayManager`
- [x] Write `SavedSearchesScreenTest.kt` covering:
  - [x] List of saved searches displayed
  - [x] FAB visible on launch
  - [x] Long-press enters selection mode
  - [x] Back press clears selection
  - [x] Move up/down buttons visible for single selection
  - [x] Move up / move down actions
  - [x] Cancel selection via back button in toolbar
  - [ ] Create new saved search — opens `SavedSearchFragment` (requires further Espresso test)
  - [ ] Delete saved search — via overflow menu in selection toolbar
  - [ ] Import / export — requires activity result stubs
- [x] Delete `SavedSearchesFragment.kt`, `SavedSearchesAdapter.kt`, `fragment_saved_searches.xml`, `item_saved_search.xml`
- [x] Migrate `SavedSearchesFragmentTest.java` — `onSavedSearch(0)` → `withText("Agenda")`, `saved_searches_cab_move_up/down` → `withContentDescription(R.string.up/down)`, export via overflow menu

### 4. Cleanup ✅ DONE

- [x] Remove any UI-version preference toggles — none exist; N/A
- [x] Remove `BooksFragment.Listener` interface — moved into `BooksFragmentCompose`; `MainActivity` and `BookChooserActivity` now implement `BooksFragmentCompose.Listener`
- [x] Remove `SavedSearchesFragment.Listener` from `MainActivity` — moved into `SavedSearchesFragmentCompose.Listener`; `MainActivity` now implements the new interface
- [x] `SavedSearchFragment.Listener` — `SavedSearchFragment` was already a Compose fragment before this migration; its `Listener` interface is still the active communication mechanism with `MainActivity` and is not a legacy artifact to remove
- [x] Confirmed `GanttFragment` has no legacy counterpart — it extends `ComposeFragment` with no accompanying legacy View fragment
- [x] Deprecated `EspressoUtils.onBook(int, int)` helper methods
- [x] Added `ids_legacy.xml` stub IDs for test compilation of references to deleted view IDs
- [x] Migrated sync icon assertions in `SyncingTest.java` — added `R.string.sync_needed` content description to the out-of-sync `Icon` in `BooksScreen.kt`; tests now use `withContentDescription(R.string.sync_needed)` + `doesNotExist()` / `matches(isDisplayed())`
- [x] Migrated encoding detail assertions in `SyncingTest.java` — tests now enable encoding prefs via `AppPreferences.displayedBookDetails()` before launching and assert with `withText(context.getString(R.string.argument_used/detected, "UTF-8"))`

---

## Definition of Done for Phase 1

- Zero references to `BooksFragment`, `NoteFragment`, or `SavedSearchesFragment` in production code
- All Phase 1 screens have Compose UI tests that cover the major features listed above
- No regressions in existing test suites
- `AppComponent` injection entries cleaned up
