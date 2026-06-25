package com.framework.log.config;

import com.framework.log.aspect.OperationLogAspect;
import com.framework.log.service.OperationLogStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Log module auto configuration.
 */
@Configuration
@EnableConfigurationProperties(LogProperties.class)
@ConditionalOnProperty(prefix = "framework.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({LogAsyncConfig.class, OperationLogAspect.class, OperationLogStorageService.class, ApiLogFilter.class})
public class LogAutoConfiguration {
}
