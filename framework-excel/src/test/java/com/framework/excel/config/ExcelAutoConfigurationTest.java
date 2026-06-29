package com.framework.excel.config;

import com.framework.excel.service.ExcelExportService;
import com.framework.excel.service.ExcelImportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void propertiesRejectInvalidDefaultsAtStartup() {
        ExcelProperties properties = new ExcelProperties();
        properties.setDefaultSheetName(" ");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.excel.default-sheet-name");

        properties.setDefaultSheetName("bad/name");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.excel.default-sheet-name")
                .hasMessageContaining("invalid character");

        properties.setDefaultSheetName("'Sheet1'");

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apostrophe");

        properties.setDefaultSheetName("Sheet1");
        properties.setMaxRows(0);

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.excel.max-rows");
    }

    @Test
    void autoConfigurationRejectsInvalidDefaultSheetNameAtStartup() {
        contextRunner
                .withPropertyValues("framework.excel.default-sheet-name=bad/name")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.excel.default-sheet-name"));
    }
}
