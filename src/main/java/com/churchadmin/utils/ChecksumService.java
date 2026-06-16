package com.churchadmin.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Produces deterministic SHA-256 checksums for entity integrity verification.
 *
 * Used in two places:
 *  1. BaseEntity.computeChecksum()  — stored with every record on save.
 *  2. ImportService                 — verifies incoming records before merge.
 *
 * If a checksum in an incoming export file differs from what we would compute
 * for the same field values, we flag a CONFLICT (silent corruption or clock skew).
 */
public final class ChecksumService {

    private ChecksumService() {}

    /**
     * SHA-256 of all provided field values concatenated with a pipe delimiter.
     *
     * @param fields business field values in a fixed, agreed order
     * @return 64-character lowercase hex string
     */
    public static String sha256(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = String.join("|", fields);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present in every JVM
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
