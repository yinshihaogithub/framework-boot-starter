package com.framework.datasource.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DatasourceAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersDatasourceInfrastructure() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(DatasourceProperties.class)
                .hasSingleBean(MybatisPlusInterceptor.class)
                .hasSingleBean(FrameworkMetaObjectHandler.class));
    }
}
