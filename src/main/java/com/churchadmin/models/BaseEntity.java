package com.churchadmin.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shared base for all persistent entities.
 *
 * Enforces consistent identity (UUID), auditing (createdAt / updatedAt),
 * optimistic locking (version), and integrity verification (checksum).
 *
 * UUID prevents ID collisions across admin machines (vs auto-increment Long).
 * updatedAt drives the last-write-wins merge strategy during import.
 * checksum (SHA-256) provides a secondary integrity check — detects silent
 * corruption or clock-skew cases where timestamps match but data differs.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA optimistic locking — prevents concurrent overwrites within the
     * same session. Incremented automatically on every merge/update.
     */
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    /**
     * SHA-256 hash of all business fields, computed before every save.
     * Used during import to detect data corruption or clock-skew conflicts.
     */
    @Column(nullable = false, length = 64)
    private String checksum;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        checksum = computeChecksum();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        checksum = computeChecksum();
    }

    /**
     * Subclasses implement this to return a deterministic SHA-256 hash of
     * their business fields.  Use {@link com.churchadmin.utils.ChecksumService}.
     */
    protected abstract String computeChecksum();
}
