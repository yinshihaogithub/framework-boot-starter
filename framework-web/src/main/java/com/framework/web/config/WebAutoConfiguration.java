package com.framework.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

/**
 * Web 基础 Bean 配置
 */
@Configuration
@Import({CorsConfig.class, TraceIdFilter.class, XssFilter.class, GlobalExceptionHandler.class})
public class WebAutoConfiguration implements WebMvcConfigurer {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json().build();
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new CaseInsensitiveEnumConverterFactory());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static class CaseInsensitiveEnumConverterFactory implements ConverterFactory<String, Enum> {

        @Override
        public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
            return source -> {
                if (source == null || source.isBlank()) {
                    return null;
                }
                String normalized = source.trim().toUpperCase(Locale.ROOT);
                for (T constant : targetType.getEnumConstants()) {
                    if (constant.name().equals(normalized)) {
                        return constant;
                    }
                }
                throw new IllegalArgumentException("枚举值不支持: " + source);
            };
        }
    }
}
