package com.framework.excel.service;

import com.alibaba.excel.EasyExcel;
import com.framework.excel.config.ExcelProperties;

import java.io.ByteArrayOutputStream;
import java.util.Collection;

/**
 * Excel export service.
 */
public class ExcelExportService {

    private final ExcelProperties properties;

    public ExcelExportService(ExcelProperties properties) {
        this.properties = ExcelSupport.requireExportProperties(properties);
    }

    public <T> byte[] export(Class<T> headClass, Collection<T> rows) {
        return export(properties.getDefaultSheetName(), headClass, rows);
    }

    public <T> byte[] export(String sheetName, Class<T> headClass, Collection<T> rows) {
        if (headClass == null) {
            throw new IllegalArgumentException("headClass must not be null");
        }
        if (rows == null) {
            throw new IllegalArgumentException("rows must not be null");
        }
        String safeSheetName = ExcelSupport.requireSheetName(sheetName, "sheetName");
        if (rows.size() > properties.getMaxRows()) {
            throw new IllegalArgumentException("excel rows exceed maxRows: " + properties.getMaxRows());
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, headClass)
                .sheet(safeSheetName)
                .doWrite(rows);
        return outputStream.toByteArray();
    }

    public <T> byte[] template(Class<T> headClass) {
        return export(properties.getDefaultSheetName(), headClass, java.util.List.of());
    }
}
