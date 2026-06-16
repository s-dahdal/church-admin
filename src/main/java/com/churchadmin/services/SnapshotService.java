package com.churchadmin.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Snapshot system (ADR §6.3).
 *
 * Snapshot types:
 *  - AUTO_DAILY    : on startup, once per day         → retain last 30
 *  - PRE_IMPORT    : automatically before every import → retain last 10
 *  - MANUAL        : admin clicks "Backup Now"         → kept indefinitely
 *
 * Snapshots are full database file copies saved in the snapshots directory.
 * Restore replaces the current SQLite DB file with the snapshot contents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String PREF_LAST_DAILY = "snapshot.lastDaily";

    @Value("${spring.datasource.url}")
    private String datasourceUrl; // jdbc:sqlite:/path/to/db

    @Value("${app.snapshots.dir}")
    private String snapshotsDirPath;

    @Value("${app.snapshots.retention.daily:30}")
    private int retentionDaily;

    @Value("${app.snapshots.retention.pre-import:10}")
    private int retentionPreImport;

    private final Preferences prefs = Preferences.userNodeForPackage(SnapshotService.class);

    public enum SnapshotType {
        AUTO_DAILY, PRE_IMPORT, MANUAL
    }

    /** Called on app startup — creates a snapshot at most once per day. */
    public void createDailySnapshotIfNeeded() {
        String today = LocalDate.now().toString();
        String lastDaily = prefs.get(PREF_LAST_DAILY, "");
        if (!today.equals(lastDaily)) {
            createSnapshot(SnapshotType.AUTO_DAILY);
            prefs.put(PREF_LAST_DAILY, today);
        }
    }

    /** Always called before any import operation. */
    public void createPreImportSnapshot() {
        createSnapshot(SnapshotType.PRE_IMPORT);
    }

    /** Triggered by admin clicking "Backup Now". */
    public void createManualSnapshot() {
        createSnapshot(SnapshotType.MANUAL);
    }

    public void createSnapshot(SnapshotType type) {
        try {
            File dbFile = getDbFile();
            if (!dbFile.exists()) {
                log.warn("Database file not found, skipping snapshot: {}", dbFile);
                return;
            }

            Path snapshotsDir = ensureSnapshotsDir();
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
            String filename = type.name().toLowerCase() + "_" + timestamp + ".db";
            Path destination = snapshotsDir.resolve(filename);

            Files.copy(dbFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            log.info("Snapshot created [{}]: {}", type, destination);

            applyRetentionPolicy(type);

        } catch (IOException e) {
            log.error("Failed to create snapshot [{}]: {}", type, e.getMessage(), e);
        }
    }

    public List<Path> listSnapshots() throws IOException {
        Path dir = ensureSnapshotsDir();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".db"))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
    }

    public void restoreSnapshot(Path snapshotPath) throws IOException {
        File dbFile = getDbFile();
        log.warn("Restoring database from snapshot: {}", snapshotPath);
        Files.copy(snapshotPath, dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("Restore complete. Application restart required to reload data.");
    }

    public void deleteSnapshot(Path snapshotPath) throws IOException {
        Files.deleteIfExists(snapshotPath);
        log.info("Snapshot deleted: {}", snapshotPath);
    }

    // ---- Private ----

    private void applyRetentionPolicy(SnapshotType type) throws IOException {
        if (type == SnapshotType.MANUAL) return; // manual snapshots kept indefinitely

        int limit = (type == SnapshotType.AUTO_DAILY) ? retentionDaily : retentionPreImport;
        String prefix = type.name().toLowerCase() + "_";
        Path dir = ensureSnapshotsDir();

        try (var stream = Files.list(dir)) {
            List<Path> typed = stream
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .sorted(Comparator.reverseOrder())
                    .toList();

            if (typed.size() > limit) {
                for (Path old : typed.subList(limit, typed.size())) {
                    Files.deleteIfExists(old);
                    log.info("Retention policy: removed old snapshot {}", old.getFileName());
                }
            }
        }
    }

    private File getDbFile() {
        // Strips "jdbc:sqlite:" prefix to get the file path
        String path = datasourceUrl.replace("jdbc:sqlite:", "");
        // Expand ${user.home} if present (Spring doesn't expand in @Value for this pattern)
        path = path.replace("${user.home}", System.getProperty("user.home"));
        return new File(path);
    }

    private Path ensureSnapshotsDir() throws IOException {
        String resolved = snapshotsDirPath.replace("${user.home}", System.getProperty("user.home"));
        Path dir = Path.of(resolved);
        Files.createDirectories(dir);
        return dir;
    }
}
