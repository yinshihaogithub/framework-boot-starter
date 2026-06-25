package com.framework.excel.service;

import com.alibaba.excel.annotation.ExcelProperty;
import com.framework.excel.config.ExcelProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelExportServiceTest {

    @Test
    void rejectsNullRowsWithClearMessage() {
        ExcelExportService exportService = new ExcelExportService(new ExcelProperties());

        assertThatThrownBy(() -> exportService.export(ExportRow.class, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows must not be null");
    }

    @Test
    void rejectsRowsExceedingConfiguredMaxRows() {
        ExcelProperties properties = new ExcelProperties();
        properties.setMaxRows(1);
        ExcelExportService exportService = new ExcelExportService(properties);

        assertThatThrownBy(() -> exportService.export(ExportRow.class, List.of(
                        new ExportRow("a"),
                        new ExportRow("b")
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRows: 1");
    }

    static class ExportRow {
        @ExcelProperty("Name")
        private String name;

        ExportRow(String name) {
            this.name = name;
        }
    }
}
