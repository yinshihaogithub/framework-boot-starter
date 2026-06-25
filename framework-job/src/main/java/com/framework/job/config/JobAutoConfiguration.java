package com.framework.job.config;

import com.framework.job.service.DefaultJobService;
import com.framework.job.service.JobHandler;
import com.framework.job.service.JobService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;

/**
 * Job module auto configuration.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(JobProperties.class)
@ConditionalOnProperty(prefix = "framework.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolTaskScheduler frameworkTaskScheduler(JobProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getPoolSize());
        scheduler.setThreadNamePrefix(properties.getThreadNamePrefix());
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean
    public JobService jobService(List<JobHandler> handlers) {
        return new DefaultJobService(handlers);
    }
}
