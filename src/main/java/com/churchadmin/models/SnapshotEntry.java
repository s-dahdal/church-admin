package com.churchadmin.models;

import com.churchadmin.models.enums.SnapshotType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight descriptor for a snapshot file on disk.
 * Not a JPA entity — never persisted to the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotEntry {

    private String        filename;
    private LocalDateTime createdAt;
    private SnapshotType  type;
    private long          sizeBytes;

    /** Human-readable file size: "42 KB" or "1.2 MB". */
    public String getFormattedSize() {
        if (sizeBytes < 1024L * 1024L) {
            return (sizeBytes / 1024L) + " KB";
        }
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }

    /** Human-readable snapshot type (English fallback; the controller uses i18n keys). */
    public String getTypeLabel() {
        if (type == null) return "Unknown";
        return switch (type) {
            case AUTO_DAILY -> "Auto Daily";
            case PRE_IMPORT -> "Pre-Import";
            case MANUAL     -> "Manual";
        };
    }
}