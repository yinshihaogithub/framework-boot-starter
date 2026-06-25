package com.framework.excel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Excel module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.excel")
public class ExcelProperties {

    private boolean enabled = true;
    private String defaultSheetName = "Sheet1";
    private int maxRows = 100000;
}
