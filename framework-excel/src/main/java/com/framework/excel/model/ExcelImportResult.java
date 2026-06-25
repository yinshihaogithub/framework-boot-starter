package com.framework.excel.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel import result with row data and row-level errors.
 */
@Data
public class ExcelImportResult<T> {

    private final List<T> rows = new ArrayList<>();
    private final List<ExcelRowError> errors = new ArrayList<>();

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public int successCount() {
        return rows.size();
    }

    public int errorCount() {
        return errors.size();
    }
}
