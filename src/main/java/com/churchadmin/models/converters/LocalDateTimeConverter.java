package com.churchadmin.models.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Stores LocalDateTime as an ISO-8601 text string in SQLite.
 *
 * autoApply = true means this converter is applied to every LocalDateTime
 * field in every entity without any extra annotation.
 *
 * Read-side handles three formats:
 *  1. ISO-8601 "2026-06-16T19:37:03.256"  — written by this converter
 *  2. SQLite datetime() "2026-06-16 19:37:03" — written by V1 seed SQL
 *  3. Epoch-millis "1781631638877"          — written by Hibernate before
 *                                             this fix was in place
 */
@Converter(autoApply = true)
public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter ISO   = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]");

    @Override
    public String convertToDatabaseColumn(LocalDateTime dt) {
        return dt == null ? null : dt.format(ISO);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String s) {
        if (s == null || s.isBlank()) return null;

        // Epoch-millis fallback (data written before this converter existed)
        try {
            long millis = Long.parseLong(s.trim());
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        } catch (NumberFormatException ignored) { /* not a number */ }

        // Standard ISO with 'T' separator
        try {
            return LocalDateTime.parse(s, ISO);
        } catch (DateTimeParseException ignored) { /* try next */ }

        // SQLite datetime() with space separator
        return LocalDateTime.parse(s, SPACE);
    }
}
