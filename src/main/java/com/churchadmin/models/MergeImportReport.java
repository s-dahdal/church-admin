package com.churchadmin.models;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary of a merge import analysis produced by
 * {@link com.churchadmin.services.MergeImportService#analyze}.
 *
 * Not a JPA entity — never persisted to the database.
 *
 * <ul>
 *   <li><b>Members</b> — classified as new (auto-insert), identical (skip), or
 *       pending (admin review).</li>
 *   <li><b>Transactions</b> — deduplicated by composite business-field key;
 *       either new (auto-insert) or identical (skip).
 *       New transactions whose linked member is pending are deferred until
 *       that member is resolved.</li>
 *   <li><b>Categories</b> — completely ignored in Merge Import.</li>
 * </ul>
 */
@Data
@Builder
public class MergeImportReport {

    // ── Member counts ─────────────────────────────────────────────────────────

    /** Members whose UUID was not found locally — will be auto-inserted. */
    private int newMembers;

    /** Members whose UUID was found and checksum matched — skipped silently. */
    private int skippedMembers;

    /**
     * Members whose UUID was found but at least one business field differs —
     * awaiting admin decision (Keep Local or Accept Incoming).
     */
    @Builder.Default
    private List<PendingMemberRecord> pendingMembers = new ArrayList<>();

    // ── Transaction counts ────────────────────────────────────────────────────

    /** Transactions with no matching record by business fields — will be auto-inserted. */
    private int newTransactions;

    /** Transactions already present by business fields — skipped silently. */
    private int skippedTransactions;

    // ── Helper methods ────────────────────────────────────────────────────────

    /** Number of members awaiting a manual decision. */
    public int totalPending() {
        return pendingMembers.size();
    }

    /** Total records that will be auto-inserted (new members + new transactions). */
    public int totalNew() {
        return newMembers + newTransactions;
    }

    /** Total records skipped as identical (members + transactions). */
    public int totalSkipped() {
        return skippedMembers + skippedTransactions;
    }
}
