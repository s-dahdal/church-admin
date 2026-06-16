package com.churchadmin.services;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary of an import operation — displayed to the admin after every import.
 *
 * Gives full visibility into what changed without blocking the workflow
 * (ADR §6.2 — Conflict Resolution Strategy).
 */
@Getter
@Builder
public class ImportResult {

    private final int inserted;
    private final int updated;
    private final int skipped;
    private final int conflicts;

    @Builder.Default
    private final List<String> conflictLog = new ArrayList<>();

    @Builder.Default
    private final List<String> warnings = new ArrayList<>();

    public boolean hasConflicts() {
        return conflicts > 0;
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    @Override
    public String toString() {
        return String.format(
                "Import complete — Inserted: %d | Updated: %d | Skipped: %d | Conflicts: %d",
                inserted, updated, skipped, conflicts
        );
    }
}
