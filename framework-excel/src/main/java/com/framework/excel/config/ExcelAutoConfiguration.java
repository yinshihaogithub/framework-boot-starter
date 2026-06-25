package com.framework.excel.config;

import com.framework.excel.service.ExcelExportService;
import com.framework.excel.service.ExcelImportService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Excel module auto configuration.
 */
@Configuration
@ConditionalOnClass(name = "com.alibaba.excel.EasyExcel")
@EnableConfigurationProperties(ExcelProperties.class)
@ConditionalOnProperty(prefix = "framework.excel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExcelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExcelExportService excelExportService(ExcelProperties properties) {
        return new ExcelExportService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExcelImportService excelImportService(ExcelProperties properties) {
        return new ExcelImportService(properties);
    }
}
