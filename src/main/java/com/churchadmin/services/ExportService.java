package com.churchadmin.services;

import com.churchadmin.models.Member;
import com.churchadmin.models.Transaction;
import com.churchadmin.models.TransactionCategory;
import com.churchadmin.repositories.MemberRepository;
import com.churchadmin.repositories.TransactionCategoryRepository;
import com.churchadmin.repositories.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Exports all entities to a single structured JSON file.
 *
 * The export envelope (ADR §6.1) includes:
 *   - exportedAt     : ISO-8601 timestamp
 *   - exportedBy     : machine hostname
 *   - schemaVersion  : format version for future migration logic
 *   - members        : full member records with all audit fields
 *   - transactions   : full transaction records
 *   - categories     : full category records
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    static final String SCHEMA_VERSION = "1.0";

    private final MemberRepository memberRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public void exportToFile(File outputFile) throws IOException {
        log.info("Starting export to: {}", outputFile.getAbsolutePath());

        List<Member> members = memberRepository.findAll();
        List<Transaction> transactions = transactionRepository.findAll();
        List<TransactionCategory> categories = categoryRepository.findAll();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        envelope.put("exportedBy", getHostname());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("members", members);
        envelope.put("transactions", transactions);
        envelope.put("categories", categories);

        ObjectMapper mapper = buildMapper();
        mapper.writeValue(outputFile, envelope);

        log.info("Export complete: {} members, {} transactions, {} categories",
                members.size(), transactions.size(), categories.size());
    }

    private ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
