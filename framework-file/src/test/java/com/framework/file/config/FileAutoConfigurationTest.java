package com.framework.file.config;

import com.framework.file.service.FileStorageService;
import com.framework.file.service.LocalFileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FileAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersLocalStorage() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(FileProperties.class)
                .hasSingleBean(FileStorageService.class)
                .hasSingleBean(LocalFileStorageService.class));
    }
}
