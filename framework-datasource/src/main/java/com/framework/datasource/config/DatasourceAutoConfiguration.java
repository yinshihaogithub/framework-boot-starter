package com.framework.datasource.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Datasource module auto configuration.
 */
@Configuration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(DatasourceProperties.class)
@ConditionalOnProperty(prefix = "framework.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatasourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DatasourceProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(properties.getDbType());
        pagination.setMaxLimit(properties.getMaxLimit());
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public FrameworkMetaObjectHandler frameworkMetaObjectHandler(DatasourceProperties properties) {
        return new FrameworkMetaObjectHandler(properties);
    }
}
