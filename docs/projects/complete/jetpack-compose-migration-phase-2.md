# Jetpack Compose Migration — Phase 2: Dialogs and Small Components

## Goal

Convert all `DialogFragment` subclasses and modal alert dialogs to Jetpack Compose. These are self-contained, have no navigation dependencies, and are low-risk to migrate incrementally.

## Pattern

**DialogFragment → Compose hosting:**
Override `onCreateView` to return a `ComposeView` wrapping an `OrgzlyBootstrap { AlertDialog(...) }`. Set the dialog window background to transparent and layout to `MATCH_PARENT` in `onStart` so the Compose `AlertDialog` shape renders correctly.

```kotlin
override fun onCreateView(inflater, container, savedInstanceState): View {
    return ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            OrgzlyBootstrap {
                MyDialog(onDismiss = { dismiss() }, ...)
            }
        }
    }
}

override fun onStart() {
    super.onStart()
    dialog?.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
```

**Object/factory dialogs (`AlertDialog` factories):**
The legacy `show()` / `create()` factories stay untouched until their callers are migrated. A standalone `@Composable fun FooDialog(...)` is added to the same file for use in future Compose screens.

---

## Status

### ✅ Done

#### `ShowSshKeyDialogFragment`

- `onCreateDialog` replaced with `onCreateView` + `ComposeView`
- Standalone `@Composable fun ShowSshKeyDialog(publicKey, onShare, onDismiss)` added
- No XML layout to delete (was using `setMessage`)
- Callers: `SettingsFragment`, `SshKeygenActivity` — no changes needed

#### `SimpleOneLinerDialog`

- `onCreateDialog` + XML inflation replaced with `onCreateView` + `ComposeView`
- Compose `AlertDialog` + `OutlinedTextField` with auto-focus and IME Done action
- `dialog_simple_one_liner.xml` deleted
- Standalone `@Composable fun SimpleOneLinerDialog(title, hint, initialValue, ...)` added
- Public API unchanged: `getInstance()`, `FRAGMENT_TAG`, Fragment Result API — callers need no changes
- Callers: `BrowserActivity`

#### `WhatsNewDialog` (companion)

- Legacy `create(context): AlertDialog` factory kept for `CommonActivity` / `MainActivity` / `SettingsActivity`
- `@Composable fun WhatsNewDialog(versionName, onDismiss)` added — uses `AndroidView(TextView)` for HTML link content
- Will replace legacy factory when `MainActivity` / `SettingsActivity` migrate (Phase 4/6)

#### `NoteStateDialog` (companion)

- Legacy `show(context, currentState, onSelection, onClear)` kept for `NotesFragment` / `BookFragment`
- `@Composable fun NoteStateDialog(currentState, onSelection, onClear, onDismiss)` added with `RadioButton` rows
- Will replace legacy factory when `NotesFragment` migrates (Phase 3)

#### `PeriodWithTypePickerDialog` family

Three concrete `DialogFragment` subclasses sharing an abstract base, migrated alongside `TimestampDialogFragment`.

| Class                       | Type spinner                  | Used by                   |
|-----------------------------|-------------------------------|---------------------------|
| `WarningPeriodPickerDialog` | None                          | `TimestampDialogFragment` |
| `DelayPickerDialog`         | ALL / FIRST_ONLY              | `TimestampDialogFragment` |
| `RepeaterPickerDialog`      | CUMULATE / CATCH_UP / RESTART | `TimestampDialogFragment` |

- `onCreateDialog` replaced with `onCreateView` + `ComposeView` in each subclass
- `WheelNumberPicker.kt` added as a custom Compose snap-scroll number picker (replaces `NumberPicker` view)
- `dialog_period_with_type.xml` deleted

#### `TimestampDialogFragment`

The most complex dialog in the app.

- Inline active/inactive toggle, date picker, time picker, end-time picker
- Buttons: Today, Tomorrow, Next Week, Set, Clear, Cancel
- Sub-dialogs: `RepeaterPickerDialog`, `DelayPickerDialog`, `WarningPeriodPickerDialog` (all migrated)
- `onCreateDialog` replaced with `onCreateView` + `ComposeView`
- `dialog_timestamp.xml` and `dialog_timestamp_title.xml` deleted

---

#### `RefileFragment`

Full-screen `DialogFragment` with a `LazyColumn` list of refile targets backed by a `ViewModel`.

- `onCreateDialog` replaced with `onCreateView` + `ComposeView`
- `RefileScreen` composable with `TopAppBar`, breadcrumb bar, and `LazyColumn` item list
- `BreadcrumbsBar` with horizontal scroll and refile-here `IconButton`
- `RefileItem` composable uses `AndroidView(TextView)` for styled note titles (via `NoteItemViewBinder`)
- `RefileAdapter.kt` deleted (replaced by `LazyColumn`)
- `dialog_refile.xml` and `item_refile.xml` deleted
- Public API unchanged: `getInstance()`, `FRAGMENT_TAG` — callers need no changes

### 🔲 Remaining

#### Settings dialogs *(deferred to Phase 6)*

`SettingsExportFragment` and `SettingsImportFragment` are modal dialogs tied to the Settings screen. Migrate them as part of the Settings screen rewrite.

---

## Files to Delete When Complete

| File                                     | Blocked by                                  |
|------------------------------------------|---------------------------------------------|
| `res/layout/dialog_simple_one_liner.xml` | ✅ Deleted                                  |
| `res/layout/dialog_period_with_type.xml` | ✅ Deleted                                  |
| `res/layout/dialog_timestamp.xml`        | ✅ Deleted                                  |
| `res/layout/dialog_timestamp_title.xml`  | ✅ Deleted                                  |
| `res/layout/dialog_refile.xml`           | ✅ Deleted                                  |
| `res/layout/dialog_whats_new.xml`        | `WhatsNewDialog` legacy factory (Phase 4/6) |

---

## Suggested Order for Remaining Work

1. ✅ `PeriodWithTypePickerDialog` family (`WarningPeriodPickerDialog`, `DelayPickerDialog`, `RepeaterPickerDialog`)
2. ✅ `TimestampDialogFragment`
3. ✅ `RefileFragment`
4. Settings dialogs (deferred, part of Phase 6)
