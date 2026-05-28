# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Orgzly II is an Android app for managing Org-mode notes and to-do lists. It is a fork of the original Orgzly app. The app syncs Org files with external repositories (Dropbox, WebDAV, Git, local directories) and manages notes in a local SQLite Room database.

## Tools

### Text Search

Use ripgrep (`rg`) instead of grep.

### Code Intelligence (LSP)

Prefer LSP over text search.

## Build System

This is a standard Android Gradle project with three modules:

- **`:app`** — Main Android application
- **`:org-java`** — Pure Java library for parsing/writing Org-mode files
- **`:shared-test`** — Shared test utilities

### Product Flavors

Two flavors exist, both with `debug` and `release` build types:

- **`fdroid`** — Dropbox disabled (no API key), Git sync enabled
- **`premium`** — Dropbox enabled, Git sync removed (Play Store restrictions)

### Build Commands

```bash
# Build fdroid debug APK
./gradlew assembleFdroidDebug

# Build premium debug APK
./gradlew assemblePremiumDebug

# Run local JVM unit tests (fdroid flavor)
./gradlew testFdroidDebugUnitTest

# Run local JVM unit tests (premium flavor)
./gradlew testPremiumDebugUnitTest

# Run a single unit test class
./gradlew testFdroidDebugUnitTest --tests "com.orgzly.android.query.QueryTest"

# Run instrumented (on-device) tests
./gradlew connectedFdroidDebugAndroidTest

# Run a single instrumented test class
./gradlew connectedFdroidDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.orgzly.android.espresso.NoteFragmentTest
```

### app.properties

Dropbox credentials are loaded from `app.properties` in the project root (not committed). See `sample.app.properties` for the format. Without it, the fdroid flavor builds fine; premium flavor builds but Dropbox won't authenticate.

## Architecture

### Dependency Injection

Dagger 2 is used for DI. The single `AppComponent` (`di/AppComponent.kt`) wires together three modules:
- `ApplicationModule` — provides `Context`, `Resources`
- `DatabaseModule` — provides `OrgzlyDatabase`
- `DataModule` — provides `DataRepository`, `RepoFactory`, `LocalStorage`

All Fragments and Activities that need DI call `App.appComponent.inject(this)`.

### Use Case Pattern

All user actions go through `UseCase` subclasses in `usecase/`. Each `UseCase` implements `run(dataRepository: DataRepository): UseCaseResult`. `UseCaseRunner.run()` executes the use case, then triggers auto-sync, reminder rescheduling, and widget updates as needed based on `UseCaseResult` flags.

### Data Layer

`DataRepository` (`data/DataRepository.kt`) is the single source of truth for all data operations — it wraps Room DAOs, handles Org file parsing/writing, and orchestrates sync operations. It is `@Singleton` and injected everywhere.

Room database (`OrgzlyDatabase`) is at schema version 158, with entities in `db/entity/`.

Notes use a **nested set model** for tree hierarchy (`NotePosition.lft`/`rgt`), not a simple parent-id tree. This affects all insert/move/delete operations which must maintain the nested set invariants. `NoteAncestor` denormalizes ancestry for query performance.

### Org-mode Parsing (org-java module)

The `org-java` module (`com.orgzly.org.*`) is a standalone Java library. Key classes:
- `OrgParser` / `OrgNestedSetParser` — parse `.org` files into `OrgHead`/`OrgFile` objects
- `OrgParserWriter` — serialize back to Org format
- `OrgDateTime`, `OrgRange`, `OrgRepeater` — date/time handling

`OrgMapper` (`data/mappers/OrgMapper.kt`) bridges org-java types to Room entities.

### Sync Architecture

Sync is driven by `SyncWorker` (WorkManager), coordinated by `SyncRunner`. Each repository type implements either a simple repo interface or `TwoWaySyncRepo` for two-way sync (currently only `GitRepo`). Repo types: `MOCK`, `DROPBOX`, `DIRECTORY`, `DOCUMENT`, `WEBDAV`, `GIT`. `RepoFactory` creates the appropriate implementation based on `RepoType`.

Auto-sync is triggered via `AutoSync` after data-modifying use cases.

### Query System

The search query system has two layers:
- `query/user/` — user-facing query parsers (`DottedQueryParser`, `BasicQueryParser`) and builders
- `query/sql/` — `SqliteQueryBuilder` converts the parsed `Query` object to SQL for Room

Queries can filter notes by state, tag, scheduled/deadline date, priority, book, and text content, with configurable sort orders.

### UI Architecture

The app uses a mix of legacy Views and Jetpack Compose (being migrated):
- `MainActivity` hosts a navigation drawer and loads fragments
- Fragments follow MVVM with `ViewModel` + `LiveData`
- Compose screens use `ComposeActivity`/`ComposeFragment` base classes in `ui/compose/base/`
- `CommonViewModel` provides shared error/message signaling

### Reminders

`RemindersScheduler` schedules Android alarms for note timestamps. `NoteReminders` queries the DB for upcoming scheduled/deadline times and fires `AlarmManager` alarms that trigger `RemindersBroadcastReceiver`.

### External Access

`ExternalAccessReceiver` handles broadcasts from external apps (e.g., Tasker integrations) via action handlers in `external/actionhandlers/`.

## Key Conventions

- Kotlin for all new code in `:app`; `org-java` remains Java
- Room schema export files are in `app/schemas/` — new migrations must export a schema JSON
- `BuildConfig.LOG_DEBUG` gates verbose logging (`LogUtils`)
- `BuildConfig.IS_DROPBOX_ENABLED` and `IS_GIT_REMOVED` gate feature availability per flavor
