package com.churchadmin.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * JSON envelope written to / read from snapshot files.
 *
 * Uses {@code Map<String, Object>} per row (raw JDBC result) so the payload
 * stays decoupled from entity field changes.
 *
 * Current {@code schemaVersion}: 1.  Increment when the structure changes in a
 * backwards-incompatible way.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotPayload {

    private int                          schemaVersion;
    private LocalDateTime                exportedAt;
    private String                       exportedBy;
    private String                       snapshotType;
    private String                       appVersion;
    private List<Map<String, Object>>    members;
    private List<Map<String, Object>>    transactions;
    private List<Map<String, Object>>    transactionCategories;
}