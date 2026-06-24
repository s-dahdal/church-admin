# Implementation Prompt — Smart Snapshot Merge Import

## Context

Church Admin is a standalone offline-first JavaFX + Spring Boot desktop application.
The existing Snapshots screen already supports:
- **Export** — save a snapshot JSON to disk
- **Restore** — full replace of the local DB from a snapshot (destructive)
- **Import External** — load a snapshot file from disk, then Restore

We are now adding a **Merge Import** mode: import a snapshot file and apply only
what is new or changed, skip what is identical, and surface conflicts for manual
resolution — without touching conflicting records automatically.

---

## Existing Code to Understand First

Before writing anything, read and fully understand:

- `SnapshotPayload` — the Jackson JSON envelope (schemaVersion, exportedAt, exportedBy,
  snapshotType, appVersion, and `List<Map<String, Object>>` per table)
- `SnapshotEntry` — disk descriptor (filename, createdAt, type, sizeBytes)
- `SnapshotService` — especially `restoreSnapshot()` and `createSnapshot()`
- `SnapshotsController` + `Snapshots.fxml` — existing toolbar and table layout
- `BaseEntity` — id (UUID), createdAt, updatedAt, version, checksum fields
- `Member` entity — all 24 business fields
- `Transaction` entity — including nullable `memberId` FK
- `TransactionCategory` entity
- `app.css` — existing CSS variables and component classes
- All three `messages_*.properties` files — existing key naming conventions

---

## What to Build

### 1. `MergeOutcome` enum
**Package:** `com.churchadmin.models.enums`

```
NEW          // UUID not found locally → safe to insert
IDENTICAL    // UUID found + checksum matches → skip, no action needed
UPDATABLE    // UUID found + incoming updatedAt is newer + checksum differs → safe to update
CONFLICT     // UUID found + local is equally or more recent + checksums differ → hold for manual review
```

---

### 2. `ConflictRecord` (plain class, not a JPA entity)
**Package:** `com.churchadmin.models`

Fields:
- `String entityType` — "member", "transaction", "category"
- `Map<String, Object> localRecord` — raw field map from DB (same shape as SnapshotPayload rows)
- `Map<String, Object> incomingRecord` — raw field map from the snapshot file
- `List<String> changedFields` — field names whose values differ between the two maps
- `MergeOutcome outcome` — always `CONFLICT`

---

### 3. `MergeImportReport` (plain class, not a JPA entity)
**Package:** `com.churchadmin.models`

Fields:
- `int newMembers`, `int updatedMembers`, `int skippedMembers`, `int conflictMembers`
- `int newTransactions`, `int updatedTransactions`, `int skippedTransactions`, `int conflictTransactions`
- `int newCategories`, `int updatedCategories`, `int skippedCategories`, `int conflictCategories`
- `List<ConflictRecord> conflicts`

Helper methods:
- `int totalNew()`, `int totalUpdated()`, `int totalSkipped()`, `int totalConflicts()`
- `List<ConflictRecord> conflictsForType(String entityType)`

---

### 4. `MergeImportService`
**Package:** `com.churchadmin.services`
**Annotations:** `@Service`
**Dependencies:** `JdbcTemplate`, `SnapshotService`, `ObjectMapper`

#### 4.1 `MergeImportReport analyze(SnapshotPayload payload)`

Reads all three entity tables from the local DB into memory as
`Map<String, Map<String, Object>>` keyed by UUID string. Then iterates every
record in the payload and classifies it:

```
For each incoming record R:
  localRecord = localMap.get(R.id)

  if localRecord == null:
    → NEW

  if localRecord.checksum == R.checksum:
    → IDENTICAL

  incomingUpdatedAt = parse R.updatedAt
  localUpdatedAt    = parse localRecord.updatedAt

  if incomingUpdatedAt > localUpdatedAt:
    → UPDATABLE

  else:
    → CONFLICT  (local is same age or newer, but checksums differ)
```

Build and return a `MergeImportReport` with all classified records.

**Important — FK safety rule:**
If a transaction's `memberId` is non-null and that member UUID is classified as
`CONFLICT` (i.e. not yet applied), classify that transaction as `CONFLICT` too,
regardless of its own timestamps. Add `"memberId"` to its `changedFields` with a
note value `"member_is_conflict"` so the UI can explain why.

#### 4.2 `void applyNonConflicts(SnapshotPayload payload, MergeImportReport report)`

- First: call `snapshotService.createSnapshot(SnapshotType.PRE_IMPORT, "pre-merge-import")`
- Then in a single `@Transactional` block:
  - INSERT all `NEW` records (categories first, then members, then transactions — FK order)
  - UPDATE all `UPDATABLE` records
  - Do NOT touch `CONFLICT` or `IDENTICAL` records
- Return — do not throw on partial success; the report already captures what was done

#### 4.3 `void applySingleConflict(ConflictRecord conflict)`

Applies one conflict record the admin has manually accepted.
Runs in its own `@Transactional`. Updates only the single row identified by
`conflict.incomingRecord.get("id")`.

---

### 5. `MergeImportReviewController`
**Package:** `com.churchadmin.controllers`
**Annotation:** `@Controller` (Spring-managed, loaded via FXMLLoaderFactory)

#### Initialization
Receives `SnapshotPayload` and `MergeImportReport` injected after load via a
`public void initData(SnapshotPayload payload, MergeImportReport report)` method.
Non-conflict records are **not yet applied** when this screen opens. Apply happens
when the admin clicks **Apply Changes**.

#### Layout (`MergeImportReview.fxml`)

**Header bar:**
- Screen title (i18n key: `merge.review.title`)
- Subtitle showing the source filename

**Summary bar (HBox, 4 stat cards):**
```
[ ✔ X New ]  [ ↑ X Updated ]  [ — X Skipped ]  [ ⚠ X Conflicts ]
```
Each card uses `.summary-card` CSS class. Conflict card uses `.summary-card-warn`.

**Tab pane — 3 tabs:** Members | Transactions | Categories
Each tab shows a `TableView` of **conflict records only** for that entity type.

Columns per conflict row:
- One column per changed field (show only the fields listed in `changedFields`)
  - Cell shows: `local value → incoming value`
  - Cell background: gold highlight (`.conflict-cell`)
- Actions column: **Keep Local** button | **Accept Incoming** button

Unchanged fields are not shown in the table to keep it readable.

**Bottom action bar:**
- **Apply Changes** — calls `mergeImportService.applyNonConflicts(...)`, disables
  itself after success, shows count in status bar
- **Accept All Incoming** — confirmation dialog, then applies all remaining conflicts
  via `applySingleConflict()` in a loop
- **Keep All Local** — dismisses all conflicts (no writes), marks them resolved in UI
- **Done** — navigates back to Snapshots screen

All service calls run on background `Task` threads. Toolbar buttons disabled during
any in-progress operation (double-submit guard).

Status bar at bottom: `ProgressIndicator` + status label (`.status-ok` / `.status-error`).

---

### 6. Changes to `SnapshotsController`

Add a **Merge Import** toolbar button next to the existing Import External button.

Flow:
1. Open `FileChooser` (same filter as Import External: `*.json`)
2. Parse the selected file into `SnapshotPayload` via `SnapshotService` (reuse existing parse logic)
3. Run `mergeImportService.analyze(payload)` on a background `Task`
4. On success: push `MergeImportReview.fxml` via `mainController.showView()` and call
   `controller.initData(payload, report)`

No restore happens at this point — analysis only.

---

### 7. CSS additions to `app.css`

Add these classes (follow existing CSS variable naming from `.root`):

```css
.summary-card          /* stat card base: border, padding, rounded */
.summary-card-warn     /* extends summary-card: gold/amber border and icon color */
.conflict-cell         /* TableCell background: pale gold, e.g. rgba(201,168,76,0.18) */
.conflict-cell-label   /* text inside conflict cell: deep brown, monospace if possible */
```

---

### 8. i18n keys

Add to all three locale files (`messages_en.properties`, `messages_nl.properties`,
`messages_ar.properties`):

```
merge.review.title
merge.review.subtitle
merge.summary.new
merge.summary.updated
merge.summary.skipped
merge.summary.conflicts
merge.tab.members
merge.tab.transactions
merge.tab.categories
merge.action.apply
merge.action.accept.all
merge.action.keep.all
merge.action.done
merge.confirm.accept.all
merge.status.applying
merge.status.applied
merge.status.conflict.accepted
merge.status.conflict.kept
merge.button.keep.local
merge.button.accept.incoming
merge.no.conflicts
snapshots.button.merge.import
```

Provide natural translations for Dutch and Arabic. For Arabic, apply the same
right-to-left phrasing conventions used in the existing Arabic locale file.

---

## Constraints & Conventions to Follow

- All service calls from controllers must run on background `javafx.concurrent.Task`
  threads; UI updates delivered via `Platform.runLater()` or `Task.setOnSucceeded()`
- No hardcoded strings in FXML or controllers — all text via `ResourceBundle` keys
- FXML files must use `VBox.vgrow="ALWAYS"` on the TableView so the screen fills
  available height
- Follow the existing screen layout pattern: `.screen-root` → `.screen-header` →
  content → `.status-bar`
- CSS changes must be direction-neutral; RTL implications (button order in action bar)
  handled by `NodeOrientation` automatically — do not hardcode margins for direction
- `MergeImportService` must never call `restoreSnapshot()` — it is a separate code path
- Do not modify `SnapshotService.restoreSnapshot()` in any way
- Do not add a git commit at the end

---

## Acceptance Criteria

- [ ] Selecting a snapshot JSON and clicking Merge Import opens the review screen
      without modifying the DB
- [ ] Summary bar shows correct counts for new / updated / skipped / conflicts
- [ ] Conflicts tab shows only conflicting records; changed fields are highlighted
- [ ] Keep Local / Accept Incoming work per-row and update the UI immediately
- [ ] Apply Changes inserts/updates only NEW + UPDATABLE records in one transaction,
      preceded by a PRE_IMPORT snapshot
- [ ] Accept All Incoming applies all remaining conflicts after confirmation
- [ ] CONFLICT transactions whose member is also a conflict are held and explained
- [ ] All text is translated in EN, NL, and AR
- [ ] No regressions to existing Restore or Export flows
