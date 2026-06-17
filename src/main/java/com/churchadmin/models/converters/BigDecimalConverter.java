package com.churchadmin.models.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

/**
 * Stores BigDecimal as a plain decimal string ("1234.56") in SQLite TEXT columns.
 *
 * Motivation: SQLite's REAL type uses IEEE 754 floating-point, which loses
 * precision for financial amounts (e.g., 0.1 + 0.2 ≠ 0.3 exactly).
 * Storing as TEXT guarantees exact round-trip for any decimal value.
 *
 * autoApply = true covers Transaction.amount and any future BigDecimal fields.
 */
@Converter(autoApply = true)
public class BigDecimalConverter implements AttributeConverter<BigDecimal, String> {

    @Override
    public String convertToDatabaseColumn(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    @Override
    public BigDecimal convertToEntityAttribute(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            // Graceful fallback — should never happen for well-formed data
            return BigDecimal.ZERO;
        }
    }
}