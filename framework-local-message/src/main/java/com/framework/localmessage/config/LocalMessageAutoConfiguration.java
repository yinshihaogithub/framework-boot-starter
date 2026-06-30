package com.framework.localmessage.config;

import com.framework.localmessage.mapper.LocalMessageMapper;
import com.framework.localmessage.repository.LocalMessageRepository;
import com.framework.localmessage.repository.LocalMessageTableInitializer;
import com.framework.localmessage.repository.MybatisLocalMessageRepository;
import com.framework.localmessage.scheduler.LocalMessageRetryScheduler;
import com.framework.localmessage.service.DefaultLocalMessageService;
import com.framework.localmessage.service.LocalMessageHandler;
import com.framework.localmessage.service.LocalMessageService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * Local message module auto configuration.
 */
@Configuration
@EnableScheduling
@AutoConfigureAfter(name = {
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
})
@EnableConfigurationProperties(LocalMessageProperties.class)
@ConditionalOnProperty(prefix = "framework.local-message", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LocalMessageAutoConfiguration {

    @Bean
    @ConditionalOnBean(LocalMessageMapper.class)
    @ConditionalOnMissingBean
    public LocalMessageTableInitializer localMessageTableInitializer(LocalMessageMapper mapper,
                                                                     LocalMessageProperties properties) {
        return new LocalMessageTableInitializer(mapper, properties);
    }

    @Bean
    @ConditionalOnBean(LocalMessageMapper.class)
    @ConditionalOnMissingBean
    public LocalMessageRepository localMessageRepository(LocalMessageMapper mapper,
                                                         LocalMessageProperties properties) {
        return new MybatisLocalMessageRepository(mapper, properties.getTableName());
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

    @Configuration
    @ConditionalOnBean(SqlSessionFactory.class)
    @MapperScan("com.framework.localmessage.mapper")
    static class LocalMessageMapperScanConfiguration {
    }
}
