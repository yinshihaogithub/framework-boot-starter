package com.framework.excel.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Excel module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.excel")
public class ExcelProperties implements InitializingBean {

    private static final int MAX_SHEET_NAME_LENGTH = 31;
    private static final String INVALID_SHEET_NAME_CHARS = "[]:*?/\\";

    private boolean enabled = true;
    private String defaultSheetName = "Sheet1";
    private int maxRows = 100000;

    @Override
    public void afterPropertiesSet() {
        defaultSheetName = requireSheetName(defaultSheetName, "framework.excel.default-sheet-name");
        if (maxRows <= 0) {
            throw new IllegalArgumentException("framework.excel.max-rows must be greater than 0");
        }
    }

    private static String requireSheetName(String sheetName, String name) {
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
