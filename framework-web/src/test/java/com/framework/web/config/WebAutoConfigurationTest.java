package com.framework.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.filter.CorsFilter;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    @Test
    void objectMapperSupportsJavaTimeTypes() {
        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            assertThatCode(() -> objectMapper.writeValueAsString(LocalDateTime.of(2026, 6, 25, 15, 0)))
                    .doesNotThrowAnyException();
        });
    }
}
