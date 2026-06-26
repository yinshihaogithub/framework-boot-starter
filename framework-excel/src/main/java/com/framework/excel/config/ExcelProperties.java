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

    private boolean enabled = true;
    private String defaultSheetName = "Sheet1";
    private int maxRows = 100000;

    @Override
    public void afterPropertiesSet() {
        if (defaultSheetName == null || defaultSheetName.isBlank()) {
            throw new IllegalArgumentException("framework.excel.default-sheet-name must not be blank");
        }
        if (maxRows <= 0) {
            throw new IllegalArgumentException("framework.excel.max-rows must be greater than 0");
        }
    }
}
