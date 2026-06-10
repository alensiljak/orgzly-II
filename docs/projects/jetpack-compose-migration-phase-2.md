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

---

### 🔲 Remaining

#### `PeriodWithTypePickerDialog` family

Three concrete `DialogFragment` subclasses sharing an abstract base. Each wraps a `NumberPicker` compound layout (`dialog_period_with_type.xml`). Best migrated together.

| Class                       | Type spinner                  | Used by                   |
|-----------------------------|-------------------------------|---------------------------|
| `WarningPeriodPickerDialog` | None                          | `TimestampDialogFragment` |
| `DelayPickerDialog`         | ALL / FIRST_ONLY              | `TimestampDialogFragment` |
| `RepeaterPickerDialog`      | CUMULATE / CATCH_UP / RESTART | `TimestampDialogFragment` |

**Notes:**

- Compose has no `NumberPicker` equivalent — use `LazyColumn` with snap scroll or a custom vertically-scrolling picker
- `dialog_period_with_type.xml` can be deleted after all three are migrated
- All three are only called from `TimestampDialogFragment`, so migrating them alongside the timestamp dialog makes sense

#### `TimestampDialogFragment`

The most complex dialog in the app. Contains:

- Inline active/inactive checkbox
- Date picker (`DatePicker` view)
- Time picker (`TimePicker` view)
- End-time picker
- Buttons: Today, Tomorrow, Next Week, Set, Clear, Cancel
- Sub-dialogs: `RepeaterPickerDialog`, `DelayPickerDialog`, `WarningPeriodPickerDialog`

**Notes:**

- Compose `DatePicker` / `TimePicker` (Material3 `DatePickerDialog`, `TimeInput`) are available but have different UX from the legacy views — verify with design
- `dialog_timestamp.xml` and `dialog_timestamp_title.xml` can be deleted after migration
- Recommend migrating this after the `PeriodWithTypePicker` family is done

#### `RefileFragment`

Bottom-sheet style `DialogFragment` with a full `RecyclerView` list of refile targets backed by a `ViewModel`. More like a screen than a dialog.

**Notes:**

- Migrate to a Compose `ModalBottomSheet` or `AlertDialog` with a `LazyColumn`
- `dialog_refile.xml` can be deleted after migration
- Requires `RefileViewModel` LiveData → StateFlow migration (recommended but optional)

#### Settings dialogs *(deferred to Phase 6)*

`SettingsExportFragment` and `SettingsImportFragment` are modal dialogs tied to the Settings screen. Migrate them as part of the Settings screen rewrite.

---

## Files to Delete When Complete

| File                                     | Blocked by                                  |
|------------------------------------------|---------------------------------------------|
| `res/layout/dialog_simple_one_liner.xml` | ✅ Deleted                                   |
| `res/layout/dialog_period_with_type.xml` | `PeriodWithTypePickerDialog` family         |
| `res/layout/dialog_timestamp.xml`        | `TimestampDialogFragment`                   |
| `res/layout/dialog_timestamp_title.xml`  | `TimestampDialogFragment`                   |
| `res/layout/dialog_refile.xml`           | `RefileFragment`                            |
| `res/layout/dialog_whats_new.xml`        | `WhatsNewDialog` legacy factory (Phase 4/6) |

---

## Suggested Order for Remaining Work

1. `PeriodWithTypePickerDialog` → `WarningPeriodPickerDialog` → `DelayPickerDialog` → `RepeaterPickerDialog`
2. `TimestampDialogFragment` (depends on the period pickers being Compose)
3. `RefileFragment`
4. Settings dialogs (deferred, part of Phase 6)
