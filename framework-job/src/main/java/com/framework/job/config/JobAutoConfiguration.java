package com.framework.job.config;

import com.framework.job.service.XxlJobHandlerRegistrar;
import com.framework.job.service.DefaultJobService;
import com.framework.job.service.JobHandler;
import com.framework.job.service.JobService;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * XXL-JOB module auto configuration.
 */
@Configuration
@ConditionalOnClass(XxlJobSpringExecutor.class)
@EnableConfigurationProperties(JobProperties.class)
@ConditionalOnProperty(prefix = "framework.job", name = "enabled", havingValue = "true")
public class JobAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public XxlJobSpringExecutor xxlJobExecutor(JobProperties properties) {
        properties.validateExecutor();
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(trimToNull(properties.getAdminAddresses()));
        executor.setAppname(trimToNull(properties.getAppName()));
        executor.setAddress(trimToNull(properties.getAddress()));
        executor.setIp(trimToNull(properties.getIp()));
        executor.setPort(properties.getPort());
        executor.setAccessToken(trimToNull(properties.getAccessToken()));
        executor.setLogPath(trimToNull(properties.getLogPath()));
        executor.setLogRetentionDays(properties.getLogRetentionDays());
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public JobService jobService(List<JobHandler> handlers) {
        return new DefaultJobService(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public XxlJobHandlerRegistrar xxlJobHandlerRegistrar(List<JobHandler> handlers) {
        return new XxlJobHandlerRegistrar(handlers);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
