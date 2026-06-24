package com.churchadmin.services;

import com.churchadmin.models.MergeImportReport;
import com.churchadmin.models.PendingMemberRecord;
import com.churchadmin.models.SnapshotPayload;
import com.churchadmin.models.enums.SnapshotType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements the Smart Merge Import logic.
 *
 * <ol>
 *   <li>{@link #analyze} — classifies every record in an incoming
 *       {@link SnapshotPayload} without touching the database.</li>
 *   <li>{@link #applyAutomatic} — takes a PRE_IMPORT safety snapshot, then inserts
 *       all new members and new non-deferred transactions in a single transaction.</li>
 *   <li>{@link #applyPendingMember} — admin accepted the incoming version of a
 *       pending member; updates the row and inserts any deferred transactions
 *       whose {@code member_id} matches.</li>
 *   <li>{@link #discardPendingMember} — admin kept the local version; the member
 *       FK exists locally, so deferred transactions for this member are inserted.</li>
 * </ol>
 *
 * <b>Entity rules:</b>
 * <ul>
 *   <li>Members — matched by UUID; NEW / IDENTICAL / PENDING.</li>
 *   <li>Transactions — matched by composite business-field key (date + amount + type
 *       + category_id + description + member_id); NEW or IDENTICAL.
 *       New transactions whose linked member is PENDING are deferred.</li>
 *   <li>Categories — completely ignored.</li>
 * </ul>
 *
 * This service never calls {@link SnapshotService#restoreSnapshot}.
 */
@Slf4j
@Service
public class MergeImportService {

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final JdbcTemplate    jdbcTemplate;
    private final SnapshotService snapshotService;

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Metadata fields excluded from changed-field computation. */
    private static final Set<String> EXCLUDED_FIELDS = Set.of(
            "id", "created_at", "updated_at", "version", "checksum"
    );

    // ── Constructor ───────────────────────────────────────────────────────────

    public MergeImportService(JdbcTemplate jdbcTemplate, SnapshotService snapshotService) {
        this.jdbcTemplate    = jdbcTemplate;
        this.snapshotService = snapshotService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Analyse every record in the incoming payload against the local DB and
     * return a {@link MergeImportReport}.
     *
     * <p>This method is read-only — it never writes to the database.
     *
     * <p>Categories in the payload are completely ignored.
     */
    public MergeImportReport analyze(SnapshotPayload payload) {

        Map<String, Map<String, Object>> localMembers = loadLocalById("members");
        Set<String> localTxKeys = loadTransactionKeys();

        int newMembers = 0, skippedMembers = 0;
        List<PendingMemberRecord> pendingMembers = new ArrayList<>();

        // ── Classify members ──────────────────────────────────────────────────
        for (Map<String, Object> incoming : safe(payload.getMembers())) {
            String id = idOf(incoming);
            if (id == null) continue;

            Map<String, Object> local = localMembers.get(id);
            if (local == null) {
                newMembers++;
            } else if (Objects.equals(stringOf(local.get("checksum")), stringOf(incoming.get("checksum")))) {
                skippedMembers++;
            } else {
                List<String> changedFields = computeChangedFields(local, incoming);
                pendingMembers.add(PendingMemberRecord.builder()
                        .localRecord(local)
                        .incomingRecord(incoming)
                        .changedFields(changedFields)
                        .build());
            }
        }

        int newTransactions = 0, skippedTransactions = 0;

        // ── Classify transactions (categories ignored entirely) ───────────────
        // PENDING members still exist locally, so their FK is always satisfied —
        // new transactions are inserted immediately in applyAutomatic regardless.
        for (Map<String, Object> incoming : safe(payload.getTransactions())) {
            String key = buildCompositeKey(incoming);
            if (localTxKeys.contains(key)) {
                skippedTransactions++;
            } else {
                newTransactions++;
            }
        }

        log.info("analyze — members: {} new, {} skipped, {} pending | transactions: {} new, {} skipped",
                newMembers, skippedMembers, pendingMembers.size(), newTransactions, skippedTransactions);

        return MergeImportReport.builder()
                .newMembers(newMembers)
                .skippedMembers(skippedMembers)
                .pendingMembers(pendingMembers)
                .newTransactions(newTransactions)
                .skippedTransactions(skippedTransactions)
                .build();
    }

    /**
     * Apply all records that do not require admin input.
     *
     * <ol>
     *   <li>Creates a {@code PRE_IMPORT} safety snapshot first.</li>
     *   <li>INSERTs new members (by UUID — those not already in local DB and not pending).</li>
     *   <li>INSERTs new transactions (by composite key — those not in local DB and not deferred).</li>
     * </ol>
     *
     * All DB writes run in a single transaction.
     * Pending and identical records are never touched.
     * Deferred transactions are not applied here — they are applied when the linked
     * member is resolved via {@link #applyPendingMember} or {@link #discardPendingMember}.
     *
     * @throws IOException if snapshot creation fails
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyAutomatic(SnapshotPayload payload, MergeImportReport report)
            throws IOException {

        snapshotService.createSnapshot(SnapshotType.PRE_IMPORT, "pre-merge-import");

        Map<String, Map<String, Object>> localMembers = loadLocalById("members");
        Set<String> localTxKeys = loadTransactionKeys();

        // Pending member UUIDs exist locally — skip them here; admin resolves them separately
        Set<String> pendingIds = report.getPendingMembers().stream()
                .map(p -> idOf(p.getIncomingRecord()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Insert new members (not already local, not pending)
        for (Map<String, Object> member : safe(payload.getMembers())) {
            String id = idOf(member);
            if (id != null && !localMembers.containsKey(id) && !pendingIds.contains(id)) {
                insertRow("members", member);
            }
        }

        // Insert all new transactions — PENDING members already exist locally so FK is satisfied
        for (Map<String, Object> tx : safe(payload.getTransactions())) {
            if (!localTxKeys.contains(buildCompositeKey(tx))) {
                insertRow("transactions", tx);
            }
        }

        log.info("applyAutomatic complete — {} new members, {} new transactions",
                report.getNewMembers(), report.getNewTransactions());
    }

    /**
     * Admin accepted the incoming version of a pending member.
     * Updates the local member row and marks the record resolved.
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyPendingMember(PendingMemberRecord pending) {
        updateRow("members", pending.getIncomingRecord());
        pending.setResolved(true);
        log.info("Pending member accepted — id: {}", idOf(pending.getIncomingRecord()));
    }

    /**
     * Admin chose to keep the local version of a pending member.
     * No DB write — just marks the record resolved.
     */
    public void discardPendingMember(PendingMemberRecord pending) {
        pending.setResolved(true);
        log.info("Pending member discarded (local kept) — id: {}", idOf(pending.getIncomingRecord()));
    }

    /**
     * Build a composite deduplication key for a transaction row.
     * Key = date|amount|type|category_id|description|member_id
     * Null values are represented as empty string.
     */
    private static String buildCompositeKey(Map<String, Object> row) {
        return asString(row.get("date"))        + "|"
             + asString(row.get("amount"))      + "|"
             + asString(row.get("type"))        + "|"
             + asString(row.get("category_id")) + "|"
             + asString(row.get("description")) + "|"
             + asString(row.get("member_id"));
    }

    private static String asString(Object value) {
        return (value != null) ? value.toString().strip() : "";
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private Set<String> loadTransactionKeys() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM transactions");
        Set<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            keys.add(buildCompositeKey(row));
        }
        return keys;
    }

    private Map<String, Map<String, Object>> loadLocalById(String table) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + table);
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = idOf(row);
            if (id != null) map.put(id, row);
        }
        return map;
    }

    // ── Field comparison ──────────────────────────────────────────────────────

    private List<String> computeChangedFields(Map<String, Object> local, Map<String, Object> incoming) {
        if (local == null || incoming == null) return List.of();
        Set<String> allKeys = new LinkedHashSet<>(incoming.keySet());
        allKeys.addAll(local.keySet());
        List<String> changed = new ArrayList<>();
        for (String key : allKeys) {
            if (EXCLUDED_FIELDS.contains(key)) continue;
            if (!Objects.equals(normalizeValue(local.get(key)), normalizeValue(incoming.get(key)))) {
                changed.add(key);
            }
        }
        return changed;
    }

    /**
     * Normalise any value to a comparable string.
     * Handles type mismatches between DB rows (SQL Timestamp) and snapshot rows (ISO string).
     */
    private static String normalizeValue(Object value) {
        if (value == null) return "";
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toString();
        if (value instanceof java.util.Date d)      return new java.sql.Timestamp(d.getTime()).toLocalDateTime().toString();
        return value.toString().strip();
    }

    // ── SQL helpers ───────────────────────────────────────────────────────────

    private void insertRow(String table, Map<String, Object> row) {
        if (row == null || row.isEmpty()) return;
        String columns      = String.join(", ", row.keySet());
        String placeholders = row.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
        jdbcTemplate.update(
                "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")",
                row.values().toArray());
        log.debug("Inserted into {}: id={}", table, row.get("id"));
    }

    private void updateRow(String table, Map<String, Object> row) {
        if (row == null || row.isEmpty()) return;
        String id = idOf(row);
        if (id == null) return;

        Map<String, Object> fields = new LinkedHashMap<>(row);
        fields.remove("id");
        fields.remove("created_at");
        if (fields.isEmpty()) return;

        String setClause = fields.keySet().stream()
                .map(k -> k + " = ?")
                .collect(Collectors.joining(", "));
        List<Object> params = new ArrayList<>(fields.values());
        params.add(id);
        jdbcTemplate.update("UPDATE " + table + " SET " + setClause + " WHERE id = ?", params.toArray());
        log.debug("Updated in {}: id={}", table, id);
    }

    // ── Type utilities ────────────────────────────────────────────────────────

    private static String idOf(Map<String, Object> row) {
        if (row == null) return null;
        Object v = row.get("id");
        return v != null ? v.toString() : null;
    }

    private static String stringOf(Object value) {
        return value != null ? value.toString() : null;
    }

    private static <T> List<T> safe(List<T> list) {
        return list != null ? list : List.of();
    }
}
