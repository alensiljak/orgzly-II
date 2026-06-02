# Jetpack Compose Migration — Phase 1: Stabilize Existing Compose Screens

> Part of the [Jetpack Compose Migration Plan](jetpack-compose-migration.md).

**Risk:** Low  
**Goal:** Validate the four already-migrated screens (Books, Note, Gantt, SavedSearch), ensure full test coverage, then delete legacy counterparts.

---

## Current State

| Screen              | Compose fragment                          | Legacy fragment         | Active in main flow? |
|---------------------|-------------------------------------------|-------------------------|----------------------|
| Books list          | `BooksFragmentCompose` → `BooksScreen.kt` | `BooksFragment`         | ✅ (`DisplayManager`) |
| Note editor         | `NoteFragmentCompose`                     | `NoteFragment`          | Partial              |
| Saved searches list | ❌ none                                    | `SavedSearchesFragment` | ✅ (`DisplayManager`) |
| Saved search editor | `SavedSearchFragment` (Compose)           | —                       | ✅ (`DisplayManager`) |
| Gantt               | `GanttFragment` (Compose)                 | —                       | ✅                    |

---

## Blockers Before Deletion

- [ ] **`BookChooserActivity`** still instantiates the legacy `BooksFragment` (refile/link flow). Must be migrated to `BooksFragmentCompose` (or a standalone Compose chooser) before `BooksFragment` can be deleted.
- [ ] **`SavedSearchesFragment`** (legacy, 353 lines) is still active in `DisplayManager` — no Compose equivalent exists yet. Must be built before the legacy fragment can be deleted.
- [ ] **`AppComponent`** has separate `inject(arg: BooksFragment)` and `inject(arg: BooksFragmentCompose)` entries — the legacy entry can only be removed once `BooksFragment` is gone.

---

## Action Items

### 1. Books screen

- [ ] Migrate `BookChooserActivity` to use `BooksFragmentCompose` instead of `BooksFragment`
- [ ] Write Compose UI tests (`BooksScreenTest.kt`) covering:
  - [ ] Book list displayed on launch
  - [ ] Create new book (FAB → dialog → confirm)
  - [ ] Rename book (long-press → selection toolbar → rename)
  - [ ] Delete book (selection → delete → snackbar)
  - [ ] Export book (selection → export)
  - [ ] Import book (overflow → import)
  - [ ] Search bar open/close and filtering
  - [ ] Pull-to-refresh triggers sync
  - [ ] Back press clears selection
- [ ] Verify existing `BooksTest.java` and `BooksSortOrderTest.kt` still pass or rewrite them as Compose tests
- [ ] Delete `BooksFragment.kt` and its XML layout(s)
- [ ] Remove `inject(arg: BooksFragment)` from `AppComponent`

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
- [ ] Remove `BooksFragment.Listener` interface from `MainActivity` once `BooksFragment` is deleted
- [ ] Remove `SavedSearchesFragment.Listener` and `SavedSearchFragment.Listener` from `MainActivity` once legacy fragments are deleted
- [ ] Confirm `GanttFragment` has no legacy counterpart requiring deletion

---

## Definition of Done for Phase 1

- Zero references to `BooksFragment`, `NoteFragment`, or `SavedSearchesFragment` in production code
- All Phase 1 screens have Compose UI tests that cover the major features listed above
- No regressions in existing test suites
- `AppComponent` injection entries cleaned up
