package com.framework.web.config;

import com.framework.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;

class WebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersWebInfrastructure() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(TraceIdFilter.class)
                .hasSingleBean(XssFilter.class)
                .hasSingleBean(CorsFilter.class)
                .hasSingleBean(GlobalExceptionHandler.class));
    }
}
