package com.churchadmin.models.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Stores LocalDate as an ISO-8601 text string ("yyyy-MM-dd") in SQLite.
 * autoApply = true covers Transaction.date, Member.signingDate, etc.
 *
 * Read-side handles:
 *  1. ISO-8601 "yyyy-MM-dd"      — written by this converter
 *  2. Epoch-millis "1699743600000" — written by Hibernate before this fix
 */
@Converter(autoApply = true)
public class LocalDateConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate date) {
        return date == null ? null : date.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String s) {
        if (s == null || s.isBlank()) return null;

        // Epoch-millis fallback (data written before this converter existed)
        try {
            long millis = Long.parseLong(s.trim());
            return LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        } catch (NumberFormatException ignored) { /* not a number */ }

        return LocalDate.parse(s);
    }
}
