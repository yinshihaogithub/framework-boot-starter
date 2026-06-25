package com.framework.file.config;

import com.framework.file.service.FileStorageService;
import com.framework.file.service.LocalFileStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * File module auto configuration.
 */
@Configuration
@EnableConfigurationProperties(FileProperties.class)
@ConditionalOnProperty(prefix = "framework.file", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LocalFileStorageService localFileStorageService(FileProperties properties) {
        return new LocalFileStorageService(properties);
    }

    @Bean
    @ConditionalOnMissingBean(FileStorageService.class)
    public FileStorageService fileStorageService(LocalFileStorageService localFileStorageService) {
        return localFileStorageService;
    }
}
