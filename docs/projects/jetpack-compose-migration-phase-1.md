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
  - [ ] Export book (selection → export) — not yet covered
  - [ ] Import book (overflow → import) — not yet covered
  - [ ] Search bar open/close and filtering — not yet covered
  - [ ] Pull-to-refresh triggers sync — not yet covered
  - [x] Back press clears selection
- [x] Verify existing `BooksTest.java` and `BooksSortOrderTest.kt` still pass or rewrite them as Compose tests
- [x] Delete `BooksFragment.kt` and its XML layout(s) (`fragment_books.xml`, `item_book.xml`, `BooksAdapter.kt`)
- [x] Remove `inject(arg: BooksFragment)` from `AppComponent`

### 2. Note editor screen

- [ ] Audit feature parity between `NoteFragment` (1260 lines) and `NoteFragmentCompose` (351 lines) — identify any features present in the legacy fragment that are missing from the Compose version
- [ ] Implement any missing features in `NoteFragmentCompose` / its composable
- [ ] Write or migrate Compose UI tests (`NoteScreenTest.kt`) covering:
  - [ ] Open existing note
  - [ ] Edit title and body
  - [ ] Set/clear state (TODO/DONE/etc.)
  - [ ] Set scheduled and deadline dates
  - [ ] Add/remove tags
  - [ ] Set priority
  - [ ] Save note
  - [ ] Discard changes (back press)
  - [ ] Create new note
- [ ] Verify existing `NoteFragmentTest.kt` (648 lines) still passes or rewrite as Compose tests
- [ ] Delete `NoteFragment.kt` and its XML layout(s)

### 3. Saved searches list screen

- [ ] Build `SavedSearchesScreen.kt` composable (to replace `SavedSearchesFragment`)
- [ ] Wire it into a new `SavedSearchesFragmentCompose` and update `DisplayManager`
- [ ] Write Compose UI tests covering:
  - [ ] List of saved searches displayed
  - [ ] Create new saved search
  - [ ] Edit existing saved search (tap → opens `SavedSearchFragment`)
  - [ ] Delete saved search
  - [ ] Reorder (move up/down)
  - [ ] Import / export
- [ ] Verify existing `SavedSearchFragmentTest.kt` (278 lines) still passes or rewrite as Compose tests
- [ ] Delete `SavedSearchesFragment.kt` and its XML layout(s)

### 4. Cleanup

- [ ] Remove any UI-version preference toggles (if any exist that switch between legacy and Compose screens)
- [x] Remove `BooksFragment.Listener` interface — moved into `BooksFragmentCompose`; `MainActivity` and `BookChooserActivity` now implement `BooksFragmentCompose.Listener`
- [ ] Remove `SavedSearchesFragment.Listener` and `SavedSearchFragment.Listener` from `MainActivity` once legacy fragments are deleted
- [ ] Confirm `GanttFragment` has no legacy counterpart requiring deletion
- [x] Deprecated `EspressoUtils.onBook(int, int)` helper methods
- [x] Added `ids_legacy.xml` stub IDs for test compilation of references to deleted view IDs
- [ ] Migrate sync icon assertions in `SyncingTest.java` (currently commented out) to Compose ContentDescription-based checks
- [ ] Migrate encoding detail assertions in `SyncingTest.java` (currently commented out) to text-based checks

---

## Definition of Done for Phase 1

- Zero references to `BooksFragment`, `NoteFragment`, or `SavedSearchesFragment` in production code
- All Phase 1 screens have Compose UI tests that cover the major features listed above
- No regressions in existing test suites
- `AppComponent` injection entries cleaned up
