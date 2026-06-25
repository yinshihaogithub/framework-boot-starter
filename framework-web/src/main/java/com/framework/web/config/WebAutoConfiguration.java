package com.framework.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Web 基础 Bean 配置
 */
@Configuration
@Import({CorsConfig.class, TraceIdFilter.class, XssFilter.class, GlobalExceptionHandler.class})
public class WebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
