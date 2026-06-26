package com.framework.excel.service;

import com.framework.excel.config.ExcelProperties;

import java.util.Objects;

final class ExcelSupport {

    private static final int MAX_SHEET_NAME_LENGTH = 31;
    private static final String INVALID_SHEET_NAME_CHARS = "[]:*?/\\";

    private ExcelSupport() {
    }

    static ExcelProperties requireProperties(ExcelProperties properties) {
        ExcelProperties safeProperties = Objects.requireNonNull(properties, "properties must not be null");
        if (safeProperties.getMaxRows() <= 0) {
            throw new IllegalArgumentException("framework.excel.max-rows must be greater than 0");
        }
        return safeProperties;
    }

    static ExcelProperties requireExportProperties(ExcelProperties properties) {
        ExcelProperties safeProperties = requireProperties(properties);
        safeProperties.setDefaultSheetName(requireSheetName(
                safeProperties.getDefaultSheetName(),
                "framework.excel.default-sheet-name"));
        return safeProperties;
    }

    static String requireSheetName(String sheetName, String name) {
        if (sheetName == null || sheetName.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = sheetName.trim();
        if (normalized.length() > MAX_SHEET_NAME_LENGTH) {
            throw new IllegalArgumentException(name + " length must not exceed " + MAX_SHEET_NAME_LENGTH);
        }
        if (normalized.startsWith("'") || normalized.endsWith("'")) {
            throw new IllegalArgumentException(name + " must not start or end with apostrophe");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isISOControl(ch) || INVALID_SHEET_NAME_CHARS.indexOf(ch) >= 0) {
                throw new IllegalArgumentException(name + " contains invalid character: " + printable(ch));
            }
        }
        return normalized;
    }

    private static String printable(char ch) {
        return switch (ch) {
            case '\r' -> "\\r";
            case '\n' -> "\\n";
            case '\t' -> "\\t";
            default -> Character.toString(ch);
        };
    }
}
