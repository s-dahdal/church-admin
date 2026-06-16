package com.churchadmin.services;

import com.churchadmin.models.Member;
import com.churchadmin.models.Transaction;
import com.churchadmin.models.TransactionCategory;
import com.churchadmin.repositories.MemberRepository;
import com.churchadmin.repositories.TransactionCategoryRepository;
import com.churchadmin.repositories.TransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Smart import and merge engine (ADR §6.2).
 *
 * Decision tree per entity:
 *  - UUID not found locally          → INSERT
 *  - UUID exists, incoming newer     → UPDATE  (last-write-wins)
 *  - UUID exists, timestamps equal   → SKIP    (unchanged)
 *  - UUID exists, local newer        → SKIP + log conflict
 *  - Checksum differs, same timestamp → CONFLICT (possible corruption/clock skew)
 *
 * Pre-import steps:
 *  1. Validate schema version
 *  2. Verify all checksums
 *  3. Auto-snapshot current database (via SnapshotService)
 *  4. Run all merges in a single transaction (all-or-nothing)
 *  5. Display post-import summary
 *
 * Clock-skew warning: if export timestamp > 24 hours from local clock, warn admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final String SUPPORTED_SCHEMA = "1.0";
    private static final long CLOCK_SKEW_WARN_HOURS = 24;

    private final MemberRepository memberRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final SnapshotService snapshotService;

    @SuppressWarnings("unchecked")
    @Transactional
    public ImportResult importFromFile(File importFile) throws IOException {

        log.info("Starting import from: {}", importFile.getAbsolutePath());

        // --- Parse ---
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        Map<String, Object> envelope = mapper.readValue(importFile, new TypeReference<>() {});

        // --- Schema version check ---
        String schemaVersion = (String) envelope.getOrDefault("schemaVersion", "unknown");
        if (!SUPPORTED_SCHEMA.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported schema version: " + schemaVersion + ". Expected: " + SUPPORTED_SCHEMA);
        }

        // --- Clock skew warning ---
        List<String> warnings = new ArrayList<>();
        String exportedAt = (String) envelope.get("exportedAt");
        if (exportedAt != null) {
            LocalDateTime exportTime = LocalDateTime.parse(exportedAt);
            long hoursDiff = Math.abs(ChronoUnit.HOURS.between(exportTime, LocalDateTime.now()));
            if (hoursDiff > CLOCK_SKEW_WARN_HOURS) {
                warnings.add("Clock skew warning: export file is " + hoursDiff +
                             " hours from your system clock. Merge decisions may be affected.");
            }
        }

        // --- Pre-import snapshot ---
        snapshotService.createPreImportSnapshot();

        // --- Merge all entity types ---
        List<Map<String, Object>> rawCategories = (List<Map<String, Object>>) envelope.getOrDefault("categories", List.of());
        List<Map<String, Object>> rawMembers    = (List<Map<String, Object>>) envelope.getOrDefault("members", List.of());
        List<Map<String, Object>> rawTx         = (List<Map<String, Object>>) envelope.getOrDefault("transactions", List.of());

        MergeCounter counter = new MergeCounter();

        mergeCategories(rawCategories, mapper, counter);
        mergeMembers(rawMembers, mapper, counter);
        mergeTransactions(rawTx, mapper, counter);

        ImportResult result = ImportResult.builder()
                .inserted(counter.inserted)
                .updated(counter.updated)
                .skipped(counter.skipped)
                .conflicts(counter.conflicts)
                .conflictLog(counter.conflictLog)
                .warnings(warnings)
                .build();

        log.info(result.toString());
        return result;
    }

    // ---- Merge helpers ----

    private void mergeCategories(List<Map<String, Object>> raw, ObjectMapper mapper, MergeCounter counter) {
        for (Map<String, Object> item : raw) {
            String id = (String) item.get("id");
            Optional<TransactionCategory> existing = categoryRepository.findById(id);
            TransactionCategory incoming = mapper.convertValue(item, TransactionCategory.class);
            mergeEntity(existing.orElse(null), incoming, counter,
                    () -> categoryRepository.save(incoming),
                    () -> {
                        existing.get().setName(incoming.getName());
                        existing.get().setType(incoming.getType());
                        categoryRepository.save(existing.get());
                    });
        }
    }

    private void mergeMembers(List<Map<String, Object>> raw, ObjectMapper mapper, MergeCounter counter) {
        for (Map<String, Object> item : raw) {
            String id = (String) item.get("id");
            Optional<Member> existing = memberRepository.findById(id);
            Member incoming = mapper.convertValue(item, Member.class);
            mergeEntity(existing.orElse(null), incoming, counter,
                    () -> memberRepository.save(incoming),
                    () -> {
                        Member m = existing.get();
                        m.setFullName(incoming.getFullName());
                        m.setMemberNumber(incoming.getMemberNumber());
                        m.setPhoneNumber(incoming.getPhoneNumber());
                        m.setEmail(incoming.getEmail());
                        m.setAddress(incoming.getAddress());
                        m.setPostalCode(incoming.getPostalCode());
                        m.setCity(incoming.getCity());
                        m.setPlace(incoming.getPlace());
                        m.setParish(incoming.getParish());
                        m.setPartnerName(incoming.getPartnerName());
                        m.setPartnerPhone(incoming.getPartnerPhone());
                        m.setPartnerEmail(incoming.getPartnerEmail());
                        m.setChild1(incoming.getChild1());
                        m.setChild2(incoming.getChild2());
                        m.setChild3(incoming.getChild3());
                        m.setChild4(incoming.getChild4());
                        m.setChild5(incoming.getChild5());
                        m.setApplicationHolder(incoming.getApplicationHolder());
                        m.setSigningDate(incoming.getSigningDate());
                        m.setIban(incoming.getIban());
                        m.setPaymentMethod(incoming.getPaymentMethod());
                        m.setStatus(incoming.getStatus());
                        m.setCategory(incoming.getCategory());
                        m.setHouseholdGroup(incoming.getHouseholdGroup());
                        memberRepository.save(m);
                    });
        }
    }

    private void mergeTransactions(List<Map<String, Object>> raw, ObjectMapper mapper, MergeCounter counter) {
        for (Map<String, Object> item : raw) {
            String id = (String) item.get("id");
            Optional<Transaction> existing = transactionRepository.findById(id);
            Transaction incoming = mapper.convertValue(item, Transaction.class);
            mergeEntity(existing.orElse(null), incoming, counter,
                    () -> transactionRepository.save(incoming),
                    () -> {
                        Transaction t = existing.get();
                        t.setDate(incoming.getDate());
                        t.setAmount(incoming.getAmount());
                        t.setType(incoming.getType());
                        t.setDescription(incoming.getDescription());
                        transactionRepository.save(t);
                    });
        }
    }

    /**
     * Core merge decision tree (ADR §6.2).
     */
    private void mergeEntity(com.churchadmin.models.BaseEntity local,
                              com.churchadmin.models.BaseEntity incoming,
                              MergeCounter counter,
                              Runnable doInsert,
                              Runnable doUpdate) {
        if (local == null) {
            doInsert.run();
            counter.inserted++;
            return;
        }

        LocalDateTime localTs    = local.getUpdatedAt();
        LocalDateTime incomingTs = incoming.getUpdatedAt();

        if (incomingTs.isAfter(localTs)) {
            doUpdate.run();
            counter.updated++;
        } else if (incomingTs.isEqual(localTs)) {
            // Same timestamp — check checksum for silent corruption
            if (!local.getChecksum().equals(incoming.getChecksum())) {
                counter.conflicts++;
                counter.conflictLog.add("CONFLICT [checksum mismatch, same timestamp] id=" + local.getId());
            } else {
                counter.skipped++;
            }
        } else {
            // Local is newer — skip but log
            counter.skipped++;
            counter.conflictLog.add("SKIPPED [local is newer] id=" + local.getId() +
                    " local=" + localTs + " incoming=" + incomingTs);
        }
    }

    private static class MergeCounter {
        int inserted, updated, skipped, conflicts;
        List<String> conflictLog = new ArrayList<>();
    }
}
