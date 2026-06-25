package com.framework.excel.config;

import com.framework.excel.service.ExcelExportService;
import com.framework.excel.service.ExcelImportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExcelAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersExcelInfrastructure() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(ExcelProperties.class)
                .hasSingleBean(ExcelExportService.class)
                .hasSingleBean(ExcelImportService.class));
    }
}
