package com.framework.excel.service;

import com.alibaba.excel.annotation.ExcelProperty;
import com.framework.excel.config.ExcelProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelExportServiceTest {

    @Test
    void rejectsInvalidMaxRowsAtConstruction() {
        ExcelProperties properties = new ExcelProperties();
        properties.setMaxRows(0);

        assertThatThrownBy(() -> new ExcelExportService(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("framework.excel.max-rows must be greater than 0");
    }

    @Test
    void rejectsBlankDefaultSheetNameAtConstruction() {
        ExcelProperties properties = new ExcelProperties();
        properties.setDefaultSheetName(" ");

        assertThatThrownBy(() -> new ExcelExportService(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("framework.excel.default-sheet-name must not be blank");
    }

    @Test
    void rejectsInvalidDefaultSheetNameAtConstruction() {
        ExcelProperties properties = new ExcelProperties();
        properties.setDefaultSheetName("bad/name");

        assertThatThrownBy(() -> new ExcelExportService(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.excel.default-sheet-name")
                .hasMessageContaining("invalid character");
    }

    @Test
    void rejectsNullRowsWithClearMessage() {
        ExcelExportService exportService = new ExcelExportService(new ExcelProperties());

        assertThatThrownBy(() -> exportService.export(ExportRow.class, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows must not be null");
    }

    @Test
    void rejectsInvalidSheetNameBeforeWriting() {
        ExcelExportService exportService = new ExcelExportService(new ExcelProperties());

        assertThatThrownBy(() -> exportService.export("01234567890123456789012345678901", ExportRow.class, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sheetName")
                .hasMessageContaining("31");
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
