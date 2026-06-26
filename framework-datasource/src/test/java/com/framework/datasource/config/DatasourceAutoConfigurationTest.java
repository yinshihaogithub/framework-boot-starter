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

    @Test
    void autoConfigurationRejectsInvalidDatasourcePropertiesAtStartup() {
        contextRunner
                .withPropertyValues("framework.datasource.max-limit=0")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.datasource.max-limit"));

        contextRunner
                .withPropertyValues("framework.datasource.audit.create-time-field= ")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.datasource.audit.create-time-field"));

        contextRunner
                .withPropertyValues("framework.datasource.db-type=POSTGRE_SQL")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.datasource.db-type must be MYSQL"));

        contextRunner
                .withPropertyValues("framework.datasource.audit.update-time-field=updated-at")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.datasource.audit.update-time-field must be a valid Java field name"));
    }
}
