package com.churchadmin.models.enums;

/**
 * Classification of an incoming snapshot record during a merge import.
 *
 * <ul>
 *   <li>{@link #NEW} — Members: UUID not found locally → inserted automatically.
 *       Transactions: no matching record found by business fields → inserted automatically.</li>
 *   <li>{@link #IDENTICAL} — Members: UUID found + checksum matches → skipped silently.
 *       Transactions: matching record already exists by business fields → skipped silently.</li>
 *   <li>{@link #PENDING} — Members only: UUID found + at least one field differs →
 *       shown to admin for manual decision.</li>
 * </ul>
 *
 * Used by {@link com.churchadmin.services.MergeImportService}.
 */
public enum MergeOutcome {

    /** UUID / business key not found locally — safe to insert automatically. */
    NEW,

    /** UUID / business key found + content matches — skip, no action needed. */
    IDENTICAL,

    /**
     * Member UUID found + at least one business field differs —
     * hold for manual review by the admin.
     * Used for members only; transactions use composite-key matching with no pending state.
     */
    PENDING
}
