package com.framework.mq.config;

import com.framework.mq.deadletter.DeadLetterHandler;
import com.framework.mq.deadletter.MqFailedMessageRepository;
import com.framework.mq.deadletter.MqRetryScheduler;
import com.framework.mq.deadletter.MqTableInitializer;
import com.framework.mq.producer.KafkaMqProducer;
import com.framework.mq.producer.MqMessageSenderRegistry;
import com.framework.mq.producer.MqProducer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaOperations;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLFeatureNotSupportedException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class MqAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MqAutoConfiguration.class));

    @Test
    void autoConfigurationStartsWithoutRabbitAndRedis() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(MessageConverter.class)
                .doesNotHaveBean(RabbitTemplate.class)
                .doesNotHaveBean(KafkaMqProducer.class)
                .doesNotHaveBean(MqProducer.class)
                .doesNotHaveBean(MqFailedMessageRepository.class)
                .doesNotHaveBean(DeadLetterHandler.class));
    }

    @Test
    void autoConfigurationRegistersRabbitInfrastructureWithoutMysqlManagementWhenOnlyConnectionFactoryExists() {
        contextRunner
                .withBean(ConnectionFactory.class, MqAutoConfigurationTest::connectionFactory)
                .run(context -> assertThat(context)
                        .hasSingleBean(RabbitTemplate.class)
                        .hasSingleBean(RabbitAdmin.class)
                        .hasSingleBean(MqProducer.class)
                        .hasSingleBean(MqMessageSenderRegistry.class)
                        .doesNotHaveBean(DeadLetterHandler.class)
                        .doesNotHaveBean(MqRetryScheduler.class));
    }

    @Test
    void autoConfigurationRegistersKafkaSenderWhenKafkaOperationsExists() {
        contextRunner
                .withBean(KafkaOperations.class, MqAutoConfigurationTest::kafkaOperations)
                .run(context -> assertThat(context)
                        .hasSingleBean(KafkaMqProducer.class)
                        .hasSingleBean(MqMessageSenderRegistry.class)
                        .doesNotHaveBean(MqProducer.class));
    }

    @Test
    void autoConfigurationRegistersMysqlRepositoryWhenDataSourceExists() {
        contextRunner
                .withPropertyValues("framework.mq.auto-create-table=false")
                .withBean(DataSource.class, ThrowingDataSource::new)
                .run(context -> assertThat(context)
                        .hasSingleBean(MqProperties.class)
                        .hasSingleBean(MqTableInitializer.class)
                        .hasSingleBean(MqFailedMessageRepository.class)
                        .hasSingleBean(DeadLetterHandler.class)
                        .doesNotHaveBean(MqRetryScheduler.class));
    }

    @Test
    void autoConfigurationRegistersMqRuntimeWhenRabbitAndRepositoryExist() {
        contextRunner
                .withBean(ConnectionFactory.class, MqAutoConfigurationTest::connectionFactory)
                .withBean(MqFailedMessageRepository.class, InMemoryMqFailedMessageRepository::new)
                .run(context -> assertThat(context)
                        .hasSingleBean(MqProperties.class)
                        .hasSingleBean(MqMessageSenderRegistry.class)
                        .hasSingleBean(DeadLetterHandler.class)
                        .hasSingleBean(MqRetryScheduler.class));
    }

    @Test
    void autoConfigurationRejectsInvalidMqPropertiesAtStartup() {
        contextRunner
                .withPropertyValues("framework.mq.max-retry=0")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.mq.max-retry"));

        contextRunner
                .withPropertyValues("framework.mq.retry.fixed-delay=0")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.mq.retry.fixed-delay"));

        contextRunner
                .withPropertyValues("framework.mq.dead-letter.queue= ")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.mq.dead-letter.queue"));

        contextRunner
                .withPropertyValues("framework.mq.failed-message-table-name=framework-mq-failed-message")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.mq.failed-message-table-name"));
    }

    private static KafkaOperations<String, String> kafkaOperations() {
        return (KafkaOperations<String, String>) Proxy.newProxyInstance(
                KafkaOperations.class.getClassLoader(),
                new Class<?>[]{KafkaOperations.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static ConnectionFactory connectionFactory() {
        return (ConnectionFactory) Proxy.newProxyInstance(
                ConnectionFactory.class.getClassLoader(),
                new Class<?>[]{ConnectionFactory.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }

    private static class ThrowingDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("test data source should not be used");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("test data source should not be used");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private static class InMemoryMqFailedMessageRepository implements MqFailedMessageRepository {
        @Override
        public com.framework.mq.deadletter.MqFailedMessage save(com.framework.mq.deadletter.MqFailedMessage message) {
            if (message.getId() == null) {
                message.setId(1L);
            }
            return message;
        }

        @Override
        public boolean update(com.framework.mq.deadletter.MqFailedMessage message) {
            return true;
        }

        @Override
        public Optional<com.framework.mq.deadletter.MqFailedMessage> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public List<com.framework.mq.deadletter.MqFailedMessage> findAll() {
            return Collections.emptyList();
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public int deleteProcessed() {
            return 0;
        }
    }
}
