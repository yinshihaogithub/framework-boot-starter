package com.framework.excel.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Row-level Excel import error.
 */
@Data
@AllArgsConstructor
public class ExcelRowError {

    private int rowIndex;
    private String message;
}
