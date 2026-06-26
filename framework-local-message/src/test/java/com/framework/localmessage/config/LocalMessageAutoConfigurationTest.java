package com.framework.localmessage.config;

import com.framework.localmessage.repository.LocalMessageRepository;
import com.framework.localmessage.scheduler.LocalMessageRetryScheduler;
import com.framework.localmessage.service.LocalMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMessageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LocalMessageAutoConfiguration.class));

    @Test
    void autoConfigurationSkipsTableInfrastructureWithoutDataSource() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(LocalMessageProperties.class)
                .doesNotHaveBean(LocalMessageRepository.class)
                .doesNotHaveBean(LocalMessageService.class)
                .doesNotHaveBean(LocalMessageRetryScheduler.class));
    }

    @Test
    void autoConfigurationRegistersTableInfrastructureWithDataSource() {
        contextRunner
                .withPropertyValues(
                        "framework.local-message.auto-create-table=false",
                        "framework.local-message.scheduler.enabled=false")
                .withBean(DataSource.class, LocalMessageAutoConfigurationTest::dataSource)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(LocalMessageProperties.class)
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

    private static DataSource dataSource() {
        return new ThrowingDataSource();
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
}
