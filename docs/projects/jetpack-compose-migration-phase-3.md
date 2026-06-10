# Jetpack Compose Migration — Phase 3: Notes List Screen

## Goal

Migrate the notes-list screens (`NotesFragment` and its concrete subclasses `BookFragment`,
`SearchFragment`, `AgendaFragment`) from XML/RecyclerView to Jetpack Compose. This is the
most-used and most complex screen: nested-set tree display, selection/CAB, swipe actions, folding,
narrowing, and rich Org-formatted note rendering.

**Scope decision:** migrate `BookFragment` (the notebook tree view) first to validate the
approach, then `SearchFragment` / `AgendaFragment` in a follow-up (they share the note-row
rendering stack).

## Main risk and the chosen approach

The danger in Phase 3 is **not** the list — a `LazyColumn` replacing a `RecyclerView` is routine —
it's the **note row**. The row's title and content are produced by the existing Org rendering
pipeline (`TitleGenerator` + `OrgFormatter`) as Android `Spanned` text carrying state colors,
priorities, tags, links, checkboxes and drawers. The content cell additionally supports three
in-row interactions: **checkbox toggle** (persists via `NoteUpdateContent`), **drawer fold**
(visual), and **link follow**. (The tree view is read-only for free text — `item_head.xml` had
`editable="false"` — so there is no inline text editing to reproduce.)

**Decision: full pure-Compose row, reusing the span pipeline.** Rather than reimplement
`OrgFormatter`, we keep it: it still produces the `Spanned`, and a converter maps the resulting
Android spans onto a Compose `AnnotatedString`, recording the clickable spans so taps can be
dispatched. This gives pixel-faithful formatting with no fork of the `RichText` editor stack
(which `NoteFragment` still uses).

## What was built

### New files

- `ui/compose/notelist/OrgFormattedText.kt`
  - `orgSpannedToAnnotatedString(...)` — converts a `Spanned` (from `TitleGenerator`/`OrgFormatter`)
    to a Compose `AnnotatedString`, mapping `ForegroundColorSpan`, `StyleSpan` (bold/italic),
    `AbsoluteSizeSpan`, `Underline`/`Strikethrough`, monospace `TypefaceSpan`, and link styling;
    collects `CheckboxSpan` / `DrawerMarkerSpan` / `LinkSpan` ranges as clickables.
  - `ClickableOrgText(...)` — renders the `AnnotatedString` and dispatches taps on clickable spans
    (falling through to the row's own click/long-press otherwise) via `TextLayoutResult`.
- `ui/compose/notelist/NoteItemContent.kt` — the pure-Compose note row: indentation guides, bullet
  (drawn with `Canvas` — the legacy bullet drawables are `layer-list`/`shape` XML), styled title,
  planning times (scheduled/deadline/event/closed), foldable content, fold button, and
  done/archived alpha. Reuses `NoteItemViewBinder.generateTitle()` / `shouldDisplayContent()`.
- `ui/notes/book/BookScreen.kt` — the screen: `Scaffold` with three top-bar modes
  (default / selection / move) + search bar, bottom CAB, `LazyColumn`, preface item, FAB, flipper
  states (loading/empty/does-not-exist), pull-to-refresh, filetags dialog, and a
  `SwipeableNoteRow` wrapper that detects horizontal swipes.

### Changed files

- `ui/notes/book/BookViewModel.kt` — selection moved out of the adapter into the ViewModel as a
  `StateFlow<Set<Long>>` (`toggleSelection` / `clearSelection` / `retainSelection`), plus filetags
  dialog visibility state.
- `ui/notes/book/BookFragment.kt` — now hosts `BookScreen` via a `ComposeView` (still extends
  `NotesFragment` to keep its dialog helpers). All action handlers, the `Listener` contract, DI,
  and the timestamp/state/refile/delete dialogs are preserved; menu/CAB/popup actions are routed
  back by their existing menu-item ids so the handler logic is reused unchanged.
- `ui/AppBar.kt` — `toModeFromSelectionCount` now checks the `currentMode` StateFlow (initialised
  to 0) instead of the `SingleLiveEvent` `mode` (null until first set); the latter meant the first
  selection never entered selection mode.
- `ui/notes/NotePopup.kt` — added a coordinate-based `showWindow(...)` overload (no `MotionEvent`)
  for Compose gesture handlers.
- `ui/notes/NotesFragment.kt` — added `showPopupWindowAt(...)` to anchor the swipe popup by absolute
  window coordinates.
- `ui/notes/NoteItemViewBinder.kt` — exposed `shouldDisplayContent(note)` (used by the Compose row).

### Deleted (book-only legacy)

- `res/layout/fragment_book.xml`
- `ui/notes/book/BookAdapter.kt`
- `res/layout/item_preface.xml`
- `ui/notes/book/PrefaceItemViewBinder.kt`
- `ui/notes/book/ListAdapterWithHeaders.kt`

**Kept (shared with Search/Agenda, still on the legacy stack):** `item_head.xml`,
`NoteItemViewBinder`, `NoteItemViewHolder`, `ItemGestureDetector`, `OnSwipeListener`, `NotePopup`.

## Verified on device

- Notebook opens and renders identically to the legacy screen (indentation, bullets, fold markers,
  state colors, tags, planning times).
- Long-press selection switches to the selection CAB; bottom action bar appears; actions work.
- Swipe on a row opens the action popup at the swipe location, respecting per-direction button
  preferences; buttons act on the correct note.
- Tappable checkboxes (persist), drawers (fold), and links work in titles and content.

## Deferred follow-ups (BookFragment)

- **Jump-to-end FAB** (fast-scroll affordance) — needs `LazyListState` wiring.
- **Scroll-to-note spotlight** on cross-book navigation — `BookFragment.scrollToNoteIfSet()` is
  currently a stub.
- **keep-screen-on** toggle — the old path needs a checkable `MenuItem`; left as a TODO in
  `handleBookActionItemClick`.

## SearchFragment / AgendaFragment migration (COMPLETE)

### New files (Search/Agenda)

- `ui/notes/query/search/SearchScreen.kt` — Compose screen: DefaultTopBar (hamburger,
  title/subtitle, overflow with sync/settings/keep-screen-on), SelectionTopBar (back, count,
  clock submenu, share), SelectionBottomBar (schedule, deadline, state, toggle, focus when
  single), PullToRefreshBox, LazyColumn of `NoteItemContent(inBook=false)` rows wrapped in
  `SwipeableNoteRow`.
- `ui/notes/query/agenda/AgendaScreen.kt` — Same toolbar/bar structure as SearchScreen; list
  renders `AgendaItem.Day` / `AgendaItem.Overdue` as sticky `Card` dividers and
  `AgendaItem.Note` as note rows. `AgendaItems.getList()` transformation kept intact in the
  Fragment (runs inside a `remember` keyed on the notes list).
- `ui/compose/notelist/SwipeableNoteRow.kt` — Extracted from `BookScreen.kt` (was private) into
  a shared file so Search/Agenda can reuse it.

### Changed files (Search/Agenda)

- `ui/compose/notelist/NoteItemContent.kt` — Added `inBook: Boolean = true` parameter. When
  `inBook=false`: no indentation, no bullet, book name shown above/below title per the
  `bookNameInSearchResults` preference (0=hide, 1=above, 2=below), fold button gated on
  `isSearchFoldable` preference.
- `ui/notes/query/QueryViewModel.kt` — Added `StateFlow<Set<Long>> selectedIds` with
  `toggleSelection` / `clearSelection` / `retainSelection` / `getSelectedIds()`, replacing
  adapter-based selection.
- `ui/notes/query/search/SearchFragment.kt` — Now hosts `SearchScreen` via `ComposeView`.
  All action handling, DI, and dialog helpers preserved; click logic (with reverse-click pref)
  moved from adapter callbacks to Fragment methods.
- `ui/notes/query/agenda/AgendaFragment.kt` — Now hosts `AgendaScreen` via `ComposeView`.
  `item2databaseIds` mapping kept in Fragment; ViewModel initialized in `onCreate` (was
  `onActivityCreated`).
- `ui/notes/NoteItemViewBinder.kt` — Removed `setupSpacingForDensitySetting(ItemAgendaDividerBinding)`
  overload (dead code after agenda adapter deletion) and its import.
- `res/values/ids.xml` — Added `bottom_toolbar` id (used by `AppSnackbar` anchor lookup).

### Deleted

- `ui/notes/query/search/SearchAdapter.kt`
- `ui/notes/query/agenda/AgendaAdapter.kt`
- `ui/notes/ItemGestureDetector.kt`
- `ui/notes/OnSwipeListener.kt`
- `res/layout/fragment_query_search.xml`
- `res/layout/fragment_query_agenda.xml`
- `res/layout/item_agenda_divider.xml`

### Still kept (NoteItemViewBinder depends on them)

`item_head.xml`, `NoteItemViewHolder` — `NoteItemViewBinder` still binds to `NoteItemViewHolder`
in its `bind()` method (dead code path, but still compiles). A future cleanup can split the
title-generation helpers out and delete the ViewHolder/layout entirely.

## Remaining Phase 3 work

1. **Espresso tests** — `androidTest` still references deleted layout ids
   (`fragment_book_recycler_view`, `fragment_book_view_flipper`, `item_preface_text_view`,
   `fragment_query_search_recycler_view`, etc.). These need to be rewritten against the Compose
   UI (or removed). Does not affect the main app build.
2. **NoteItemViewBinder cleanup** — Split title-generation helpers (`generateTitle`,
   `shouldDisplayContent`) out of the binder so `NoteItemViewHolder` and `item_head.xml` can be
   deleted.
