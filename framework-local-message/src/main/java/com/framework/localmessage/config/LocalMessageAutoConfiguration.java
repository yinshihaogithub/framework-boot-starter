package com.framework.localmessage.config;

import com.framework.localmessage.repository.JdbcLocalMessageRepository;
import com.framework.localmessage.repository.LocalMessageRepository;
import com.framework.localmessage.repository.LocalMessageTableInitializer;
import com.framework.localmessage.scheduler.LocalMessageRetryScheduler;
import com.framework.localmessage.service.DefaultLocalMessageService;
import com.framework.localmessage.service.LocalMessageHandler;
import com.framework.localmessage.service.LocalMessageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.util.List;

/**
 * Local message module auto configuration.
 */
@Configuration
@EnableScheduling
@ConditionalOnClass(JdbcTemplate.class)
@EnableConfigurationProperties(LocalMessageProperties.class)
@ConditionalOnProperty(prefix = "framework.local-message", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LocalMessageAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public JdbcTemplate localMessageJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public LocalMessageTableInitializer localMessageTableInitializer(JdbcTemplate jdbcTemplate,
                                                                     LocalMessageProperties properties) {
        return new LocalMessageTableInitializer(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public LocalMessageRepository localMessageRepository(JdbcTemplate jdbcTemplate,
                                                         LocalMessageProperties properties) {
        return new JdbcLocalMessageRepository(jdbcTemplate, properties.getTableName());
    }

    @Bean
    @ConditionalOnBean(LocalMessageRepository.class)
    @ConditionalOnMissingBean
    public LocalMessageService localMessageService(LocalMessageRepository repository,
                                                   LocalMessageProperties properties,
                                                   List<LocalMessageHandler> handlers) {
        return new DefaultLocalMessageService(repository, properties, handlers);
    }

    @Bean
    @ConditionalOnBean(LocalMessageService.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "framework.local-message.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LocalMessageRetryScheduler localMessageRetryScheduler(LocalMessageService localMessageService) {
        return new LocalMessageRetryScheduler(localMessageService);
    }
}
