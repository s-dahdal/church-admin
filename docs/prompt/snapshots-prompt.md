# Task: Implement Snapshots

## Context

This is a JavaFX + Spring Boot + SQLite desktop application for church administration.
The project structure, entity model, and conventions are defined in `ADR-2026-001-Church-Admin-App.docx`.
Phase 2 (Members) is already complete. The Spring+JavaFX bridge is in place —
controllers are loaded via `loader.setControllerFactory(springContext::getBean)`.

## What to build

Implement the full Snapshots feature. A snapshot is a complete export of the entire
database (all tables) saved as a structured JSON file on disk.

---

## Models

### `SnapshotEntry` (not a JPA entity)
A lightweight descriptor for a snapshot file on disk. Fields:
- `String filename` — e.g. `2026-06-19_08-30-00_MANUAL.json`
- `LocalDateTime createdAt`
- `SnapshotType type` — enum: `AUTO_DAILY`, `PRE_IMPORT`, `MANUAL`
- `long sizeBytes`
- Helper methods: `getFormattedSize()` (returns "42 KB" / "1.2 MB"), `getTypeLabel()` (human-readable type string)

### `SnapshotPayload`
The JSON envelope written to / read from snapshot files. Fields:
- `int schemaVersion` — current value: `1`; increment when the format changes
- `LocalDateTime exportedAt`
- `String exportedBy` — admin name or machine identifier
- `String snapshotType`
- `String appVersion`
- `List<Map<String, Object>> members`
- `List<Map<String, Object>> transactions`
- `List<Map<String, Object>> transactionCategories`

Use `Map<String, Object>` per row (raw JDBC result) so the payload stays decoupled
from entity field changes. Use Jackson with `JavaTimeModule` for serialization.

---

## Service: `SnapshotService`

Package: `com.churchadmin.services`

### Snapshot directory
Configurable via `snapshots.dir` in `application.properties` (default: `snapshots`).
Create the directory on `@PostConstruct` if it does not exist.

### Filename format
`yyyy-MM-dd_HH-mm-ss_<TYPE>.json`
Example: `2026-06-19_14-22-00_MANUAL.json`
Imported external files get an `IMPORTED_` prefix.

### Methods to implement

**`createSnapshot(SnapshotType type, String exportedBy) → Path`**
- Dump all rows from all managed tables using `JdbcTemplate` (`SELECT * FROM <table>`)
- Tables and their FK-safe insert order: `transaction_categories` → `members` → `transactions`
- Wrap in `SnapshotPayload`, serialize to JSON with `ObjectMapper` (pretty-print)
- Save to `<snapshotsDir>/<timestamp>_<TYPE>.json`
- Call `enforceRetention(type)` after saving
- Return the created file path

**`listSnapshots() → List<SnapshotEntry>`**
- Scan the snapshots directory for `*.json` files
- Parse each filename into a `SnapshotEntry` (timestamp + type from filename)
- Return sorted newest-first
- Skip and log files that don't match the naming convention

**`exportSnapshot(String filename, Path destination)`**
- Copy the named snapshot file from the snapshots directory to `destination`
- Throw `IOException` if the file does not exist

**`importExternalSnapshot(Path externalFile) → SnapshotEntry`**
- Parse the file as `SnapshotPayload` and validate `schemaVersion`
- Throw `IOException` if `schemaVersion > CURRENT_SCHEMA_VERSION`
- Copy to snapshots directory with `IMPORTED_` prefix (overwrite if exists)
- Return a `SnapshotEntry` for the imported file
- Do NOT auto-restore — the admin must explicitly activate it

**`restoreSnapshot(String filename, String adminName)`**
- Annotated `@Transactional`
- Parse and validate schema version; throw if incompatible
- Call `createSnapshot(PRE_IMPORT, adminName + " [pre-restore]")` before touching any data
- Delete all rows from managed tables in reverse FK order: `transactions` → `members` → `transaction_categories`
- Re-insert rows from the snapshot in FK-safe order using dynamic `INSERT INTO` SQL
- All deletes and inserts run inside the same transaction (all-or-nothing)

**`deleteSnapshot(String filename)`**
- Delete the file; throw `IOException` if not found

**`enforceRetention(SnapshotType type)`**
- `AUTO_DAILY`: keep latest 30, delete the rest (configurable via `snapshots.retention.auto-daily`)
- `PRE_IMPORT`: keep latest 10, delete the rest (configurable via `snapshots.retention.pre-import`)
- `MANUAL`: never auto-delete

---

## Service: `StartupSnapshotJob`

Package: `com.churchadmin.services`
Annotated `@Component`.

On `@PostConstruct`, start a daemon thread that:
1. Calls `listSnapshots()` and checks whether an `AUTO_DAILY` snapshot already exists for today's date
2. If not, calls `createSnapshot(AUTO_DAILY, <system username>)`
3. Never throws — log warnings only so a failed snapshot never crashes startup

---

## Controller: `SnapshotsController`

Package: `com.churchadmin.controllers`
Annotated `@Component` (Spring bean, loaded by JavaFX via `setControllerFactory`).

### FXML fields (`fx:id`)
- `btnCreateSnapshot` — Button
- `btnImportExternal` — Button
- `snapshotsTable` — TableView\<SnapshotEntry\>
- `colDateTime` — TableColumn\<SnapshotEntry, String\>
- `colType` — TableColumn\<SnapshotEntry, String\>
- `colSize` — TableColumn\<SnapshotEntry, String\>
- `colActions` — TableColumn\<SnapshotEntry, Void\>
- `statusLabel` — Label
- `progressIndicator` — ProgressIndicator

### Table columns
- `colDateTime`: format `createdAt` as `yyyy-MM-dd  HH:mm:ss`
- `colType`: use `entry.getTypeLabel()`
- `colSize`: use `entry.getFormattedSize()`
- `colActions`: custom cell factory with three buttons per row — **Export**, **Restore**, **Delete**
    - Export: `btn-secondary` style
    - Restore: `btn-primary` style
    - Delete: `btn-danger` style

### Actions

**Toolbar — "Export New Snapshot"** (`onCreateSnapshot`)
- Disable both toolbar buttons while running
- Call `snapshotService.createSnapshot(MANUAL, adminName)` on a background thread
- On success: update status label, reload table
- On error: update status label with error message

**Toolbar — "Import External Snapshot"** (`onImportExternal`)
- Show `FileChooser` filtered to `*.json`
- Call `snapshotService.importExternalSnapshot(chosen)` on a background thread
- On success: update status label, reload table
- On error: update status label, show error `Alert`

**Row — Export**
- Show `FileChooser` (save dialog) pre-filled with the snapshot filename
- Call `snapshotService.exportSnapshot(filename, dest)` on a background thread
- Update status label on success/error

**Row — Restore**
- Show confirmation `Alert` explaining that all current data will be replaced and a backup will be created automatically
- Call `snapshotService.restoreSnapshot(filename, adminName)` on a background thread
- On success: show info `Alert`, reload table (a new PRE_IMPORT entry will have appeared)
- On error: show error `Alert`

**Row — Delete**
- Show confirmation `Alert`
- Call `snapshotService.deleteSnapshot(filename)` on a background thread
- Reload table on success

### Threading
All `SnapshotService` calls must run on a background daemon thread (use `javafx.concurrent.Task`).
Results and errors are delivered back on the JavaFX Application Thread.
Add a double-submit guard: disable action buttons at the start of each operation, re-enable on completion.

---

## FXML: `Snapshots.fxml`

Location: `src/main/resources/fxml/Snapshots.fxml`
Controller: `com.churchadmin.controllers.SnapshotsController`

Layout (top to bottom, `VBox` root):
1. **Header `HBox`**: title label (`%snapshots.title`), subtitle label (`%snapshots.subtitle`), spacer, `btnImportExternal`, `btnCreateSnapshot`
2. **`TableView`** (`VBox.vgrow="ALWAYS"`): the four columns defined above; placeholder label using `%snapshots.empty`
3. **Footer `HBox`**: `progressIndicator` (hidden by default) + `statusLabel`

All user-visible strings must use ResourceBundle keys (`%key` syntax). No hardcoded text.

---

## i18n

Add the following keys to all three locale files
(`messages_en.properties`, `messages_nl.properties`, `messages_ar.properties`):

```
snapshots.title
snapshots.subtitle
snapshots.btn.create
snapshots.btn.import
snapshots.col.datetime
snapshots.col.type
snapshots.col.size
snapshots.col.actions
snapshots.type.auto_daily
snapshots.type.pre_import
snapshots.type.manual
snapshots.action.export
snapshots.action.restore
snapshots.action.delete
snapshots.empty
snapshots.confirm.restore.title
snapshots.confirm.restore.body
snapshots.confirm.delete.title
snapshots.confirm.delete.body
snapshots.status.creating
snapshots.status.created
snapshots.status.importing
snapshots.status.imported
snapshots.status.restoring
snapshots.status.restored
snapshots.status.deleting
snapshots.status.deleted
snapshots.status.loaded
snapshots.status.exporting
snapshots.status.exported
snapshots.error.import_failed
snapshots.error.restore_failed
snapshots.info.restore_complete
snapshots.info.restore_complete.body
```

---

## CSS

Add the following style classes to `app.css` (do not create a separate file):

```
.screen-root, .screen-header, .screen-title, .screen-subtitle
.status-bar, .status-label, .status-label.status-error, .status-label.status-ok
.small-spinner
.data-table (column headers, alternating rows, selected row)
.table-placeholder
.btn-primary, .btn-secondary, .btn-danger (with :hover and :disabled variants)
```

Use the existing liturgical palette CSS variables already defined on `.root`:
gold `#C9A84C`, dark brown `#3B2A0E`, parchment `#F5EFE0`, danger red `#8B3A2A`, olive `#5C7A3E`.

`rtl.css` does not need changes — the screen header mirrors automatically via `NodeOrientation.RIGHT_TO_LEFT`.

---

## `application.properties` additions

```properties
snapshots.dir=snapshots
snapshots.retention.auto-daily=30
snapshots.retention.pre-import=10
```

---

## Navigation wiring

Add a "Snapshots" nav button to the sidebar in `MainLayout.fxml` and wire it to
`showView("fxml/Snapshots.fxml")` in `MainController`. Follow the same pattern used
for the existing Members nav button.

---

## Constraints & conventions

- Follow all existing package and naming conventions established in Phase 1 and Phase 2
- Controllers are Spring `@Component` beans — no `new` instantiation
- No hardcoded UI strings — all text via ResourceBundle keys
- Background threads must be daemon threads so they don't block JVM shutdown
- The restore operation must be atomic — if any insert fails, the entire restore rolls back
- A failed startup snapshot must never prevent the application from launching
