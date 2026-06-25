package com.framework.excel.service;

import com.alibaba.excel.annotation.ExcelProperty;
import com.framework.excel.config.ExcelProperties;
import com.framework.excel.model.ExcelImportResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelImportServiceTest {

    @Test
    void returnsErrorResultForInvalidInputStream() {
        ExcelImportService importService = new ExcelImportService(new ExcelProperties());

        ExcelImportResult<ImportRow> result = importService.importExcel(
                new ByteArrayInputStream("not an excel file".getBytes(StandardCharsets.UTF_8)),
                ImportRow.class
        );

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errorCount()).isGreaterThan(0);
        assertThat(result.successCount()).isZero();
    }

    @Test
    void returnsErrorResultForNullInputStream() {
        ExcelImportService importService = new ExcelImportService(new ExcelProperties());

        ExcelImportResult<ImportRow> result = importService.importExcel(null, ImportRow.class);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().get(0).getMessage()).contains("inputStream must not be null");
    }

    static class ImportRow {
        @ExcelProperty("Name")
        private String name;
    }
}
