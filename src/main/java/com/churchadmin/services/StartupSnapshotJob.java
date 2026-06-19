package com.churchadmin.services;

import com.churchadmin.models.SnapshotEntry;
import com.churchadmin.models.enums.SnapshotType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Creates an AUTO_DAILY snapshot on application startup (once per calendar day).
 *
 * Runs on a daemon thread so a slow or failing snapshot never delays UI launch
 * and never prevents the JVM from shutting down.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupSnapshotJob {

    private final SnapshotService snapshotService;

    @PostConstruct
    public void scheduleStartupSnapshot() {
        Thread thread = new Thread(() -> {
            try {
                List<SnapshotEntry> existing = snapshotService.listSnapshots();
                LocalDate today = LocalDate.now();

                boolean alreadyExists = existing.stream()
                        .filter(e -> e.getType() == SnapshotType.AUTO_DAILY)
                        .anyMatch(e -> e.getCreatedAt() != null
                                && today.equals(e.getCreatedAt().toLocalDate()));

                if (alreadyExists) {
                    log.info("Auto-daily snapshot already exists for {}", today);
                } else {
                    snapshotService.createSnapshot(SnapshotType.AUTO_DAILY,
                            System.getProperty("user.name", "system"));
                    log.info("Auto-daily snapshot created for {}", today);
                }
            } catch (Exception e) {
                log.warn("Startup snapshot failed (non-fatal): {}", e.getMessage());
            }
        }, "snapshot-startup");
        thread.setDaemon(true);
        thread.start();
    }
}