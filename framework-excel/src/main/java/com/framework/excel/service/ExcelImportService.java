package com.framework.excel.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisStopException;
import com.framework.excel.config.ExcelProperties;
import com.framework.excel.model.ExcelImportResult;
import com.framework.excel.model.ExcelRowError;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Excel import service.
 */
public class ExcelImportService {

    private final ExcelProperties properties;

    public ExcelImportService(ExcelProperties properties) {
        this.properties = ExcelSupport.requireProperties(properties);
    }

    public <T> ExcelImportResult<T> importExcel(InputStream inputStream, Class<T> rowClass) {
        ExcelImportResult<T> result = new ExcelImportResult<>();
        if (inputStream == null) {
            result.getErrors().add(new ExcelRowError(0, "inputStream must not be null"));
            return result;
        }
        if (rowClass == null) {
            result.getErrors().add(new ExcelRowError(0, "rowClass must not be null"));
            return result;
        }
        try {
            BufferedInputStream bufferedInputStream = toBufferedInputStream(inputStream);
            if (!isExcelStream(bufferedInputStream)) {
                result.getErrors().add(new ExcelRowError(0, "inputStream must be an Excel file"));
                return result;
            }
            List<String> expectedHeaders = expectedHeaders(rowClass);
            EasyExcel.read(bufferedInputStream, rowClass, new AnalysisEventListener<T>() {
                        @Override
                        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                            if (!headersMatch(expectedHeaders, headMap)) {
                                result.getErrors().add(new ExcelRowError(0,
                                        "header mismatch: expected " + expectedHeaders + ", actual " + headMap.values()));
                                throw new ExcelAnalysisStopException("header mismatch");
                            }
                        }

                        @Override
                        public void invoke(T data, AnalysisContext context) {
                            if (result.getRows().size() >= properties.getMaxRows()) {
                                throw new IllegalArgumentException("excel rows exceed maxRows: " + properties.getMaxRows());
                            }
                            result.getRows().add(data);
                        }

                        @Override
                        public void onException(Exception exception, AnalysisContext context) {
                            int rowIndex = context != null && context.readRowHolder() != null
                                    ? context.readRowHolder().getRowIndex() + 1
                                    : 0;
                            result.getErrors().add(new ExcelRowError(rowIndex, exception.getMessage()));
                        }

                        @Override
                        public void doAfterAllAnalysed(AnalysisContext context) {
                        }
                    })
                    .sheet()
                    .doRead();
        } catch (Exception e) {
            if (e instanceof ExcelAnalysisStopException && result.hasErrors()) {
                return result;
            }
            result.getErrors().add(new ExcelRowError(0, e.getMessage()));
        }
        return result;
    }

    private BufferedInputStream toBufferedInputStream(InputStream inputStream) {
        return inputStream instanceof BufferedInputStream bufferedInputStream
                ? bufferedInputStream
                : new BufferedInputStream(inputStream);
    }

    private boolean isExcelStream(BufferedInputStream inputStream) throws IOException {
        inputStream.mark(8);
        byte[] header = inputStream.readNBytes(8);
        inputStream.reset();
        if (header.length < 4) {
            return false;
        }
        boolean xlsx = header[0] == 'P' && header[1] == 'K';
        boolean xls = (header[0] & 0xFF) == 0xD0
                && (header[1] & 0xFF) == 0xCF
                && (header[2] & 0xFF) == 0x11
                && (header[3] & 0xFF) == 0xE0;
        return xlsx || xls;
    }

    private List<String> expectedHeaders(Class<?> rowClass) {
        List<String> headers = new ArrayList<>();
        for (Field field : rowClass.getDeclaredFields()) {
            ExcelProperty property = field.getAnnotation(ExcelProperty.class);
            if (property == null || property.value().length == 0) {
                continue;
            }
            headers.add(normalizeHeader(property.value()[property.value().length - 1]));
        }
        return headers;
    }

    private boolean headersMatch(List<String> expectedHeaders, Map<Integer, String> actualHeaders) {
        if (expectedHeaders.isEmpty()) {
            return true;
        }
        if (actualHeaders.size() < expectedHeaders.size()) {
            return false;
        }
        for (int i = 0; i < expectedHeaders.size(); i++) {
            String actual = actualHeaders.get(i);
            if (!expectedHeaders.get(i).equals(normalizeHeader(actual))) {
                return false;
            }
        }
        return true;
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.trim();
    }
}
