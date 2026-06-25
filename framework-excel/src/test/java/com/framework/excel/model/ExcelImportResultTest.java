package com.framework.excel.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelImportResultTest {

    @Test
    void countsSuccessAndErrorRows() {
        ExcelImportResult<String> result = new ExcelImportResult<>();

        result.getRows().add("row1");
        result.getRows().add("row2");
        result.getErrors().add(new ExcelRowError(3, "invalid"));

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.hasErrors()).isTrue();
    }
}
