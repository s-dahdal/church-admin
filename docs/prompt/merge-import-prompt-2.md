# Implementation Prompt — Smart Snapshot Merge Import

## Context

Church Admin is a standalone offline-first JavaFX + Spring Boot desktop application.
The existing Snapshots screen already supports:
- **Export** — save a snapshot JSON to disk
- **Restore** — full replace of the local DB from a snapshot (destructive)
- **Import External** — load a snapshot file from disk, then Restore

We are now adding a **Merge Import** mode with entity-specific rules:
- **Members** — show all records where fields differ and let the admin choose per-record
- **Transactions** — deduplicate by business fields, insert only what does not exist
- **Categories** — ignored entirely in Merge Import

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
// Members
NEW           // UUID not found locally → inserted automatically
IDENTICAL     // UUID found + checksum matches → skipped silently
PENDING       // UUID found + at least one field differs → shown to admin for manual decision

// Transactions
NEW           // no matching transaction found by business fields → inserted automatically
IDENTICAL     // matching transaction already exists → skipped silently
```

No CONFLICT, no UPDATABLE — the old timestamp-based classification is removed entirely.

---

### 2. `PendingMemberRecord` (plain class, not a JPA entity)
**Package:** `com.churchadmin.models`

Replaces the old `ConflictRecord`. Represents a member record where the incoming
version differs from the local version and the admin must decide.

Fields:
- `Map<String, Object> localRecord` — raw field map from DB
- `Map<String, Object> incomingRecord` — raw field map from the snapshot file
- `List<String> changedFields` — field names whose values differ between the two maps
- `boolean resolved` — false by default; set to true when admin makes a decision

---

### 3. `MergeImportReport` (plain class, not a JPA entity)
**Package:** `com.churchadmin.models`

Fields:
- `int newMembers` — inserted automatically
- `int skippedMembers` — checksum identical, no action
- `List<PendingMemberRecord> pendingMembers` — differ on at least one field, awaiting admin decision
- `int newTransactions` — inserted automatically
- `int skippedTransactions` — matched by business fields, no action

Helper methods:
- `int totalPending()` — `pendingMembers.size()`
- `int totalNew()` — `newMembers + newTransactions`
- `int totalSkipped()` — `skippedMembers + skippedTransactions`

No categories fields — categories are not imported in Merge Import.

---

### 4. `MergeImportService`
**Package:** `com.churchadmin.services`
**Annotations:** `@Service`
**Dependencies:** `JdbcTemplate`, `SnapshotService`, `ObjectMapper`

#### 4.1 `MergeImportReport analyze(SnapshotPayload payload)`

**Members analysis:**

Load all local members into memory as `Map<String, Map<String, Object>>` keyed by UUID string.

```
For each incoming member M:
  localMember = localMap.get(M.id)

  if localMember == null:
    → NEW (add to newMembers count)

  else if localMember.checksum == M.checksum:
    → IDENTICAL (add to skippedMembers count)

  else:
    compute changedFields = fields where localMember.value != M.value
    → PENDING (add PendingMemberRecord to pendingMembers list)
```

Do not classify or import categories — skip the categories array entirely.

**Transactions analysis:**

Transactions are matched by business fields, not UUID. Load all local transactions
into memory as a `Set<String>` of composite keys built from:

```
date + "|" + amount + "|" + type + "|" + category + "|" + description + "|" + memberId
```

Use empty string for null `memberId` and null `description` when building the key.

```
For each incoming transaction T:
  key = buildCompositeKey(T)

  if key exists in localKeySet:
    → IDENTICAL (add to skippedTransactions count)
  else:
    → NEW (add to newTransactions count)
```

**FK safety rule for new transactions:**
If an incoming NEW transaction has a non-null `memberId` and that member UUID is
classified as `PENDING` (not yet applied), defer that transaction — do not insert it
automatically. Instead, track it as a `deferredTransactions` list on the report.
These transactions will be re-evaluated and inserted after the admin applies the
pending member.

Add to `MergeImportReport`:
- `List<Map<String, Object>> deferredTransactions`
- `int deferredTransactionCount()`

#### 4.2 `void applyAutomatic(SnapshotPayload payload, MergeImportReport report)`

Applies all records that do not require admin input: new members and new transactions.
Deferred transactions are NOT applied here.

- First: call `snapshotService.createSnapshot(SnapshotType.PRE_IMPORT, "pre-merge-import")`
- Then in a single `@Transactional` block:
  - INSERT new members (by UUID, in the order they appear in the report)
  - INSERT new transactions (by business fields match — not UUID)
  - Do NOT touch IDENTICAL or PENDING records
  - Do NOT insert deferred transactions

#### 4.3 `void applyPendingMember(PendingMemberRecord pending)`

Admin has accepted the incoming version of a pending member.
Runs in its own `@Transactional`.
Updates the local member row identified by `pending.incomingRecord.get("id")`.
After applying, re-evaluate `report.deferredTransactions`: any deferred transaction
whose `memberId` matches this member's UUID is now safe to insert — insert it and
remove it from the deferred list.

#### 4.4 `void discardPendingMember(PendingMemberRecord pending)`

Admin has chosen to keep the local version.
No DB write. Mark `pending.resolved = true`.
Deferred transactions linked to this member remain deferred permanently (they reference
a member the admin chose not to update, so inserting them as-is is still safe — the
memberId FK exists locally). Insert them now.

---

### 5. `MergeImportReviewController`
**Package:** `com.churchadmin.controllers`
**Annotation:** `@Controller` (Spring-managed, loaded via FXMLLoaderFactory)

#### Initialization

`public void initData(SnapshotPayload payload, MergeImportReport report)`

The automatic records (new members, new transactions) are **not yet applied** when
this screen opens. Apply happens when the admin clicks **Apply**.

#### Layout (`MergeImportReview.fxml`)

**Header bar:**
- Screen title (i18n: `merge.review.title`)
- Subtitle showing the source filename (i18n: `merge.review.subtitle`)

**Summary bar (HBox, 4 stat cards):**
```
[ + X New Members ]  [ + X New Transactions ]  [ — X Skipped ]  [ ✎ X Pending Review ]
```
Each card uses `.summary-card`. Pending card uses `.summary-card-warn` if count > 0.

**Members pending review — TableView:**

Show only `pendingMembers` from the report. Each row is one member where fields differ.

Columns:
- **Member name** — always shown (read from `localRecord` for identification)
- **One column per changed field** — show only fields in `changedFields`
  - Cell content: `local value → incoming value`
  - Cell style: `.conflict-cell` (pale gold background)
- **Actions column**: **Keep Local** button | **Accept Incoming** button

When the admin clicks either button:
- Call the appropriate service method on a background Task
- Remove the row from the TableView immediately on success
- Update the pending count in the summary bar
- If `deferredTransactionCount > 0`, update a deferred transactions notice label

If `pendingMembers` is empty, show a placeholder label (i18n: `merge.no.pending`).

**Deferred transactions notice** (shown below the table only when `deferredTransactionCount > 0`):
```
⚠ X transactions are waiting for their linked members to be resolved.
```
i18n key: `merge.deferred.notice`

This count decreases as pending members are resolved.

**Bottom action bar:**
- **Apply** — calls `applyAutomatic(...)`, disables itself after success, updates status bar.
  Label: i18n `merge.action.apply`
- **Accept All Incoming** — confirmation dialog, then calls `applyPendingMember()` for all
  remaining unresolved pending members in a loop. i18n: `merge.action.accept.all`
- **Keep All Local** — calls `discardPendingMember()` for all remaining unresolved pending
  members in a loop, no confirmation needed. i18n: `merge.action.keep.all`
- **Done** — navigates back to Snapshots screen. i18n: `merge.action.done`

All service calls run on background `Task` threads. Buttons disabled during any
in-progress operation (double-submit guard).

Status bar at bottom: `ProgressIndicator` + status label (`.status-ok` / `.status-error`).

---

### 6. Changes to `SnapshotsController`

Add a **Merge Import** toolbar button next to the existing Import External button.

Flow:
1. Open `FileChooser` (same filter: `*.json`)
2. Parse the selected file into `SnapshotPayload` via `SnapshotService`
3. Run `mergeImportService.analyze(payload)` on a background `Task`
4. On success: push `MergeImportReview.fxml` via `mainController.showView()` and call
   `controller.initData(payload, report)`

No DB writes at this point — analysis only.

---

### 7. CSS additions to `app.css`

```css
.summary-card          /* stat card base: border, padding, rounded corners */
.summary-card-warn     /* extends summary-card: gold/amber border and label color */
.conflict-cell         /* TableCell: pale gold background rgba(201,168,76,0.18) */
.conflict-cell-label   /* text inside conflict cell: deep brown, monospace if possible */
```

---

### 8. i18n keys

Add to all three locale files (`messages_en.properties`, `messages_nl.properties`,
`messages_ar.properties`):

```
merge.review.title
merge.review.subtitle
merge.summary.new.members
merge.summary.new.transactions
merge.summary.skipped
merge.summary.pending
merge.action.apply
merge.action.accept.all
merge.action.keep.all
merge.action.done
merge.confirm.accept.all
merge.status.applying
merge.status.applied
merge.status.member.accepted
merge.status.member.kept
merge.button.keep.local
merge.button.accept.incoming
merge.no.pending
merge.deferred.notice
snapshots.button.merge.import
```

Provide natural translations for Dutch and Arabic. For Arabic, apply the same
right-to-left phrasing conventions used in the existing Arabic locale file.

---

## Constraints & Conventions to Follow

- All service calls from controllers must run on background `javafx.concurrent.Task`
  threads; UI updates delivered via `Platform.runLater()` or `Task.setOnSucceeded()`
- No hardcoded strings in FXML or controllers — all text via `ResourceBundle` keys
- FXML files must use `VBox.vgrow="ALWAYS"` on the TableView
- Follow the existing screen layout pattern: `.screen-root` → `.screen-header` →
  content → `.status-bar`
- CSS changes must be direction-neutral; RTL handled by `NodeOrientation` automatically
- `MergeImportService` must never call `restoreSnapshot()` — it is a separate code path
- Do not modify `SnapshotService.restoreSnapshot()` in any way
- Categories array in the snapshot payload must be completely ignored — no reads, no writes
- Do not add a git commit at the end

---

## Acceptance Criteria

- [ ] Selecting a snapshot JSON and clicking Merge Import opens the review screen
      without modifying the DB
- [ ] Summary bar shows correct counts: new members, new transactions, skipped, pending
- [ ] Pending members table shows only records where at least one field differs
- [ ] Changed fields are highlighted per cell; unchanged fields are not shown
- [ ] Keep Local / Accept Incoming work per-row, row is removed from table on resolution
- [ ] Apply button inserts new members and new transactions in one transaction,
      preceded by a PRE_IMPORT snapshot
- [ ] Deferred transactions (linked to a pending member) are inserted when that member
      is resolved — regardless of whether admin kept local or accepted incoming
- [ ] Accept All Incoming and Keep All Local process all remaining pending rows in bulk
- [ ] Categories are never read, compared, or written during Merge Import
- [ ] All text is translated in EN, NL, and AR
- [ ] No regressions to existing Restore or Export flows
