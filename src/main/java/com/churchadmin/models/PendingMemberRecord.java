package com.churchadmin.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a member record where the incoming snapshot version differs from the
 * local version on at least one business field.
 *
 * Not a JPA entity — never persisted to the database.
 *
 * The admin must choose per-record: <em>Keep Local</em> or <em>Accept Incoming</em>.
 * Once a decision is made, {@code resolved} is set to {@code true} and the row is
 * removed from the UI table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingMemberRecord {

    /** Raw field map from the local database (same shape as SnapshotPayload rows). */
    private Map<String, Object> localRecord;

    /** Raw field map from the incoming snapshot file. */
    private Map<String, Object> incomingRecord;

    /**
     * Business-field names whose values differ between {@link #localRecord}
     * and {@link #incomingRecord}.
     * Metadata fields (id, created_at, updated_at, version, checksum) are excluded.
     */
    private List<String> changedFields;

    /**
     * {@code false} until the admin makes a decision.
     * Set to {@code true} by
     * {@code MergeImportService.applyPendingMember()} or
     * {@code MergeImportService.discardPendingMember()}.
     */
    @Builder.Default
    private boolean resolved = false;
}
