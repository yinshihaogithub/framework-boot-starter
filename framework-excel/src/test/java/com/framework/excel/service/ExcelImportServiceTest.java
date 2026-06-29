package com.framework.excel.service;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.EasyExcel;
import com.framework.excel.config.ExcelProperties;
import com.framework.excel.model.ExcelImportResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelImportServiceTest {

    @Test
    void rejectsInvalidMaxRowsAtConstruction() {
        ExcelProperties properties = new ExcelProperties();
        properties.setMaxRows(0);

        assertThatThrownBy(() -> new ExcelImportService(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("framework.excel.max-rows must be greater than 0");
    }

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

    @Test
    void returnsErrorResultWhenHeaderDoesNotMatchRowClass() {
        ExcelImportService importService = new ExcelImportService(new ExcelProperties());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, WrongHeaderRow.class)
                .sheet("Sheet1")
                .doWrite(List.of(new WrongHeaderRow("alice")));

        ExcelImportResult<ImportRow> result = importService.importExcel(
                new ByteArrayInputStream(outputStream.toByteArray()),
                ImportRow.class
        );

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.successCount()).isZero();
        assertThat(result.getErrors().get(0).getMessage()).contains("header mismatch");
    }

    @Test
    void trimsHeaderCellsBeforeComparingWithRowClass() {
        ExcelImportService importService = new ExcelImportService(new ExcelProperties());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, HeaderWithWhitespaceRow.class)
                .sheet("Sheet1")
                .doWrite(List.of(new HeaderWithWhitespaceRow("alice")));

        ExcelImportResult<ImportRow> result = importService.importExcel(
                new ByteArrayInputStream(outputStream.toByteArray()),
                ImportRow.class
        );

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    void returnsErrorResultWhenRowClassHasNoExcelPropertyHeaders() {
        ExcelImportService importService = new ExcelImportService(new ExcelProperties());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, ImportRow.class)
                .sheet("Sheet1")
                .doWrite(List.of(importRow("alice")));

        ExcelImportResult<NoHeaderRow> result = importService.importExcel(
                new ByteArrayInputStream(outputStream.toByteArray()),
                NoHeaderRow.class
        );

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.successCount()).isZero();
        assertThat(result.getErrors().get(0).getMessage())
                .contains("rowClass must declare at least one @ExcelProperty header");
    }

    @Test
    void returnsErrorResultWhenRowClassHasBlankExcelPropertyHeader() {
        ExcelImportService importService = new ExcelImportService(new ExcelProperties());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, ImportRow.class)
                .sheet("Sheet1")
                .doWrite(List.of(importRow("alice")));

        ExcelImportResult<BlankHeaderRow> result = importService.importExcel(
                new ByteArrayInputStream(outputStream.toByteArray()),
                BlankHeaderRow.class
        );

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.successCount()).isZero();
        assertThat(result.getErrors().get(0).getMessage())
                .contains("@ExcelProperty header must not be blank");
    }

    @Test
    void returnsFallbackErrorMessageWhenImportExceptionMessageIsBlank() {
        ExcelImportService importService = new ExcelImportService(new ExcelProperties());

        ExcelImportResult<ImportRow> result = importService.importExcel(new NullMessageInputStream(), ImportRow.class);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.successCount()).isZero();
        assertThat(result.getErrors().get(0).getMessage()).isEqualTo("IOException");
    }

    private static ImportRow importRow(String name) {
        ImportRow row = new ImportRow();
        row.setName(name);
        return row;
    }

    public static class ImportRow {
        @ExcelProperty("Name")
        private String name;

        public ImportRow() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class WrongHeaderRow {
        @ExcelProperty("Wrong")
        private final String name;

        WrongHeaderRow(String name) {
            this.name = name;
        }
    }

    static class HeaderWithWhitespaceRow {
        @ExcelProperty(" Name ")
        private final String name;

        HeaderWithWhitespaceRow(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class NoHeaderRow {
        private String name;

        public NoHeaderRow() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class BlankHeaderRow {
        @ExcelProperty(" ")
        private String name;

        public BlankHeaderRow() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class NullMessageInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            throw new IOException();
        }
    }
}
