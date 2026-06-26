package com.framework.mq.config;

import com.framework.mq.deadletter.DeadLetterHandler;
import com.framework.mq.deadletter.JdbcMqFailedMessageRepository;
import com.framework.mq.deadletter.MqDeadLetterListener;
import com.framework.mq.deadletter.MqFailedMessageRepository;
import com.framework.mq.deadletter.MqRetryScheduler;
import com.framework.mq.deadletter.MqTableInitializer;
import com.framework.mq.producer.KafkaMqProducer;
import com.framework.mq.producer.MqMessageSender;
import com.framework.mq.producer.MqMessageSenderRegistry;
import com.framework.mq.producer.MqProducer;
import com.framework.mq.producer.RocketMqProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.util.List;

/**
 * MQ 自动配置
 * - 消息转换器（JSON）
 * - 生产者
 * - 消费者容器工厂（手动ACK + 并发控制）
 * - 死信处理器
 * - 重试调度器
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MqProperties.class)
@ConditionalOnProperty(prefix = "framework.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RabbitTemplate.class)
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnMissingBean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("[MQ发送确认失败] correlationData={}, cause={}", correlationData, cause);
            }
        });
        template.setReturnsCallback(returned -> log.warn(
                "[MQ消息退回] exchange={}, routingKey={}, replyCode={}, replyText={}, messageId={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText(),
                returned.getMessage().getMessageProperties().getMessageId()));
        return template;
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnMissingBean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    @ConditionalOnBean(RabbitTemplate.class)
    @ConditionalOnMissingBean
    public MqProducer mqProducer(RabbitTemplate rabbitTemplate) {
        return new MqProducer(rabbitTemplate);
    }

    @Bean
    @ConditionalOnClass(KafkaOperations.class)
    @ConditionalOnBean(KafkaOperations.class)
    @ConditionalOnMissingBean
    public KafkaMqProducer kafkaMqProducer(KafkaOperations<String, String> kafkaOperations) {
        return new KafkaMqProducer(kafkaOperations);
    }

    @Bean
    @ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
    @ConditionalOnBean(name = "rocketMQTemplate")
    @ConditionalOnMissingBean
    public RocketMqProducer rocketMqProducer(ApplicationContext applicationContext) {
        return new RocketMqProducer(applicationContext.getBean("rocketMQTemplate"));
    }

    @Bean
    @ConditionalOnBean(MqMessageSender.class)
    @ConditionalOnMissingBean
    public MqMessageSenderRegistry mqMessageSenderRegistry(MqProperties properties,
                                                           List<MqMessageSender> senders) {
        return new MqMessageSenderRegistry(properties, senders);
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnMissingBean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);
        factory.setFailedDeclarationRetryInterval(5000L);
        factory.setMissingQueuesFatal(false);
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public JdbcTemplate mqJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public MqTableInitializer mqTableInitializer(JdbcTemplate jdbcTemplate, MqProperties properties) {
        return new MqTableInitializer(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public MqFailedMessageRepository mqFailedMessageRepository(JdbcTemplate jdbcTemplate, MqProperties properties) {
        return new JdbcMqFailedMessageRepository(jdbcTemplate, properties.getFailedMessageTableName());
    }

    @Bean
    @ConditionalOnBean(MqFailedMessageRepository.class)
    @ConditionalOnMissingBean
    public DeadLetterHandler deadLetterHandler(MqFailedMessageRepository repository, MqProperties properties) {
        return new DeadLetterHandler(repository, properties);
    }

    @Bean
    @ConditionalOnBean({DeadLetterHandler.class, MqMessageSenderRegistry.class})
    @ConditionalOnMissingBean
    public MqRetryScheduler mqRetryScheduler(DeadLetterHandler deadLetterHandler,
                                             MqMessageSenderRegistry senderRegistry,
                                             MqProperties properties) {
        return new MqRetryScheduler(deadLetterHandler, senderRegistry, properties.getMaxRetry());
    }

    @Bean
    @ConditionalOnBean({DeadLetterHandler.class, RabbitTemplate.class})
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "framework.mq.dead-letter", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MqDeadLetterListener mqDeadLetterListener(DeadLetterHandler deadLetterHandler) {
        return new MqDeadLetterListener(deadLetterHandler);
    }
}
