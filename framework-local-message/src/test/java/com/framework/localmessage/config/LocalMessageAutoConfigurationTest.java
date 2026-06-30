package com.framework.localmessage.config;

import com.framework.localmessage.mapper.LocalMessageMapper;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.repository.LocalMessageRepository;
import com.framework.localmessage.scheduler.LocalMessageRetryScheduler;
import com.framework.localmessage.service.LocalMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMessageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LocalMessageAutoConfiguration.class));

    @Test
    void autoConfigurationSkipsTableInfrastructureWithoutMapper() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(LocalMessageProperties.class)
                .doesNotHaveBean(LocalMessageRepository.class)
                .doesNotHaveBean(LocalMessageService.class)
                .doesNotHaveBean(LocalMessageRetryScheduler.class));
    }

    @Test
    void autoConfigurationRegistersTableInfrastructureWithMapper() {
        contextRunner
                .withPropertyValues(
                        "framework.local-message.auto-create-table=false",
                        "framework.local-message.scheduler.enabled=false")
                .withBean(LocalMessageMapper.class, CapturingLocalMessageMapper::new)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(LocalMessageProperties.class)
                            .hasSingleBean(LocalMessageMapper.class)
                            .hasSingleBean(LocalMessageRepository.class)
                            .hasSingleBean(LocalMessageService.class)
                            .doesNotHaveBean(LocalMessageRetryScheduler.class);
                });
    }

    @Test
    void autoConfigurationRejectsInvalidLocalMessagePropertiesAtStartup() {
        assertInvalidProperty("framework.local-message.table-name=framework-local-message",
                "framework.local-message.table-name");
        assertInvalidProperty("framework.local-message.max-retry=0",
                "framework.local-message.max-retry");
        assertInvalidProperty("framework.local-message.batch-size=0",
                "framework.local-message.batch-size");
        assertInvalidProperty("framework.local-message.retry-interval=0s",
                "framework.local-message.retry-interval");
        assertInvalidProperty("framework.local-message.scheduler.fixed-delay=0",
                "framework.local-message.scheduler.fixed-delay");
    }

    private void assertInvalidProperty(String property, String message) {
        contextRunner
                .withPropertyValues(property)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining(message));
    }

    private static class CapturingLocalMessageMapper implements LocalMessageMapper {

        @Override
        public void createTableIfNotExists(String tableName) {
        }

        @Override
        public int insert(String tableName, LocalMessage message) {
            return 0;
        }

        @Override
        public int update(String tableName, LocalMessage message) {
            return 0;
        }

        @Override
        public LocalMessage findById(String tableName, Long id) {
            return null;
        }

        @Override
        public List<LocalMessage> findDueMessages(String tableName, LocalMessageStatus status,
                                                  LocalDateTime now, int limit) {
            return List.of();
        }

        @Override
        public List<LocalMessage> findAll(String tableName) {
            return List.of();
        }

        @Override
        public int delete(String tableName, Long id) {
            return 0;
        }
    }
}
