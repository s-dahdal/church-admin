package com.churchadmin.services;

import com.churchadmin.models.SnapshotEntry;
import com.churchadmin.models.SnapshotPayload;
import com.churchadmin.models.enums.SnapshotType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Snapshot system — full JSON-based database exports.
 *
 * Snapshot types:
 *   AUTO_DAILY  — created on startup, once per calendar day  → retain last 30
 *   PRE_IMPORT  — created automatically before every restore → retain last 10
 *   MANUAL      — admin-triggered                            → kept indefinitely
 *
 * Filename format: {@code yyyy-MM-dd_HH-mm-ss_<TYPE>.json}
 * Imported files:  {@code IMPORTED_<original-filename>}
 */
@Slf4j
@Service
public class SnapshotService {

    // ── Constants ─────────────────────────────────────────────────────────────

    static final int CURRENT_SCHEMA_VERSION = 1;

    private static final DateTimeFormatter FILE_DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * Matches both standard and IMPORTED_ filenames.
     * Groups: 1=optional "IMPORTED_", 2=timestamp, 3=type
     */
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "^(IMPORTED_)?(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})_(AUTO_DAILY|PRE_IMPORT|MANUAL)\\.json$"
    );

    /** FK-safe insert order: parents before children. */
    private static final List<String> FK_INSERT_ORDER = List.of(
            "transaction_categories", "members", "transactions"
    );

    /** Reverse FK order for deletes: children before parents. */
    private static final List<String> FK_DELETE_ORDER = List.of(
            "transactions", "members", "transaction_categories"
    );

    // ── Spring dependencies ───────────────────────────────────────────────────

    private final JdbcTemplate jdbcTemplate;

    @Value("${snapshots.dir:snapshots}")
    private String snapshotsDirPath;

    @Value("${snapshots.retention.auto-daily:30}")
    private int retentionAutoDaily;

    @Value("${snapshots.retention.pre-import:10}")
    private int retentionPreImport;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    // ── Local state ───────────────────────────────────────────────────────────

    private Path snapshotsDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public SnapshotService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() throws IOException {
        snapshotsDir = resolveDir(snapshotsDirPath);
        Files.createDirectories(snapshotsDir);
        log.info("Snapshots directory: {}", snapshotsDir);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Dump all managed tables to a JSON file and return its path.
     * Calls {@link #enforceRetention} after saving.
     */
    public Path createSnapshot(SnapshotType type, String exportedBy) throws IOException {
        List<Map<String, Object>> categories = jdbcTemplate.queryForList(
                "SELECT * FROM transaction_categories");
        List<Map<String, Object>> members = jdbcTemplate.queryForList(
                "SELECT * FROM members");
        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(
                "SELECT * FROM transactions");

        SnapshotPayload payload = SnapshotPayload.builder()
                .schemaVersion(CURRENT_SCHEMA_VERSION)
                .exportedAt(LocalDateTime.now())
                .exportedBy(exportedBy)
                .snapshotType(type.name())
                .appVersion(appVersion)
                .members(members)
                .transactions(transactions)
                .transactionCategories(categories)
                .build();

        String timestamp = LocalDateTime.now().format(FILE_DT_FMT);
        String filename = timestamp + "_" + type.name() + ".json";
        Path dest = snapshotsDir.resolve(filename);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(dest.toFile(), payload);
        log.info("Snapshot created [{}]: {}", type, dest.getFileName());

        enforceRetention(type);
        return dest;
    }

    /**
     * Scan the snapshots directory, parse filenames, return entries sorted newest-first.
     * Files that do not match the naming convention are skipped with a warning.
     */
    public List<SnapshotEntry> listSnapshots() throws IOException {
        ensureDir();
        try (var stream = Files.list(snapshotsDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(this::parseEntry)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(SnapshotEntry::getCreatedAt).reversed())
                    .toList();
        }
    }

    /**
     * Copy a named snapshot to the given destination path.
     *
     * @throws IOException if the snapshot file does not exist
     */
    public void exportSnapshot(String filename, Path destination) throws IOException {
        Path source = snapshotsDir.resolve(filename);
        if (!Files.exists(source)) {
            throw new IOException("Snapshot not found: " + filename);
        }
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        log.info("Snapshot exported: {} → {}", filename, destination);
    }

    /**
     * Parse an external JSON file as a snapshot payload, validate its schema
     * version, copy it into the snapshots directory with an {@code IMPORTED_}
     * prefix, and return a {@link SnapshotEntry} for the new file.
     *
     * <p>Does NOT restore the data — the admin must call {@link #restoreSnapshot}
     * explicitly.
     *
     * @throws IOException if the file cannot be parsed or has an incompatible schema version
     */
    public SnapshotEntry importExternalSnapshot(Path externalFile) throws IOException {
        SnapshotPayload payload = objectMapper.readValue(externalFile.toFile(), SnapshotPayload.class);
        if (payload.getSchemaVersion() > CURRENT_SCHEMA_VERSION) {
            throw new IOException(
                    "Incompatible snapshot schema version " + payload.getSchemaVersion()
                    + " (current: " + CURRENT_SCHEMA_VERSION + ")");
        }

        String importedName = "IMPORTED_" + externalFile.getFileName().toString();
        Path dest = snapshotsDir.resolve(importedName);
        Files.copy(externalFile, dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("External snapshot imported as: {}", importedName);

        SnapshotType type = parseType(payload.getSnapshotType());
        return SnapshotEntry.builder()
                .filename(importedName)
                .createdAt(payload.getExportedAt() != null ? payload.getExportedAt() : LocalDateTime.now())
                .type(type)
                .sizeBytes(Files.size(dest))
                .build();
    }

    /**
     * Restore the database from a named snapshot.
     *
     * <p>Steps (all-or-nothing, inside a single transaction):
     * <ol>
     *   <li>Parse and validate schema version</li>
     *   <li>Create a PRE_IMPORT backup of the current state</li>
     *   <li>Delete all rows (reverse FK order)</li>
     *   <li>Re-insert rows from the snapshot (FK-safe order)</li>
     * </ol>
     *
     * @throws IOException if the file is missing or has an incompatible schema version
     */
    @Transactional
    public void restoreSnapshot(String filename, String adminName) throws IOException {
        Path source = snapshotsDir.resolve(filename);
        if (!Files.exists(source)) {
            throw new IOException("Snapshot not found: " + filename);
        }

        SnapshotPayload payload = objectMapper.readValue(source.toFile(), SnapshotPayload.class);
        if (payload.getSchemaVersion() > CURRENT_SCHEMA_VERSION) {
            throw new IOException(
                    "Incompatible snapshot schema version " + payload.getSchemaVersion()
                    + " (current: " + CURRENT_SCHEMA_VERSION + ")");
        }

        // Safety net: backup current state before touching any data
        createSnapshot(SnapshotType.PRE_IMPORT, adminName + " [pre-restore]");

        // Delete in reverse FK order
        for (String table : FK_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
            log.debug("Cleared table: {}", table);
        }

        // Re-insert in FK-safe order
        insertAll("transaction_categories", payload.getTransactionCategories());
        insertAll("members",                payload.getMembers());
        insertAll("transactions",           payload.getTransactions());

        log.info("Restore complete from: {}", filename);
    }

    /**
     * Delete a snapshot file.
     *
     * @throws IOException if the file does not exist
     */
    public void deleteSnapshot(String filename) throws IOException {
        Path path = snapshotsDir.resolve(filename);
        if (!Files.exists(path)) {
            throw new IOException("Snapshot not found: " + filename);
        }
        Files.delete(path);
        log.info("Snapshot deleted: {}", filename);
    }

    /**
     * Enforce retention policy for a given type.
     * MANUAL snapshots are never auto-deleted.
     */
    public void enforceRetention(SnapshotType type) throws IOException {
        if (type == SnapshotType.MANUAL) return;

        int limit = (type == SnapshotType.AUTO_DAILY) ? retentionAutoDaily : retentionPreImport;
        String typeSuffix = "_" + type.name() + ".json";

        try (var stream = Files.list(snapshotsDir)) {
            List<Path> typed = stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(typeSuffix) && !name.startsWith("IMPORTED_");
                    })
                    .sorted(Comparator.reverseOrder())
                    .toList();

            if (typed.size() > limit) {
                for (Path old : typed.subList(limit, typed.size())) {
                    Files.deleteIfExists(old);
                    log.info("Retention [{}]: removed {}", type, old.getFileName());
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void ensureDir() throws IOException {
        if (snapshotsDir == null) {
            snapshotsDir = resolveDir(snapshotsDirPath);
        }
        Files.createDirectories(snapshotsDir);
    }

    private SnapshotEntry parseEntry(Path path) {
        String name = path.getFileName().toString();
        Matcher m = FILENAME_PATTERN.matcher(name);
        if (!m.matches()) {
            log.warn("Skipping snapshot with non-standard name: {}", name);
            return null;
        }
        try {
            LocalDateTime createdAt = LocalDateTime.parse(m.group(2), FILE_DT_FMT);
            SnapshotType  type      = SnapshotType.valueOf(m.group(3));
            long          size      = Files.size(path);
            return SnapshotEntry.builder()
                    .filename(name)
                    .createdAt(createdAt)
                    .type(type)
                    .sizeBytes(size)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse snapshot entry '{}': {}", name, e.getMessage());
            return null;
        }
    }

    private SnapshotType parseType(String typeStr) {
        if (typeStr == null) return SnapshotType.MANUAL;
        try {
            return SnapshotType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SnapshotType.MANUAL;
        }
    }

    private void insertAll(String table, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (Map<String, Object> row : rows) {
            insertRow(table, row);
        }
    }

    private void insertRow(String table, Map<String, Object> row) {
        if (row == null || row.isEmpty()) return;
        String columns      = String.join(", ", row.keySet());
        String placeholders = row.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
        String sql          = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";
        jdbcTemplate.update(sql, row.values().toArray());
    }

    private static Path resolveDir(String raw) {
        String path = raw.replace("${user.home}", System.getProperty("user.home"));
        Path p = Path.of(path);
        if (!p.isAbsolute()) {
            // relative path → anchor under ~/.churchadmin/
            p = Path.of(System.getProperty("user.home"), ".churchadmin", path);
        }
        return p;
    }
}