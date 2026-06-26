package com.framework.job.config;

import com.framework.job.service.JobService;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JobAutoConfiguration.class));

    @Test
    void autoConfigurationDoesNotRegisterSpringSchedulerInfrastructureByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(JobService.class)
                .doesNotHaveBean(ThreadPoolTaskScheduler.class)
                .doesNotHaveBean(XxlJobSpringExecutor.class));
    }

    @Test
    void xxlJobExecutorMapsValidatedProperties() {
        JobProperties properties = new JobProperties();
        properties.setEnabled(true);
        properties.setAdminAddresses("http://127.0.0.1:8080/xxl-job-admin");
        properties.setAppName("framework-demo-job");
        properties.setAddress("http://127.0.0.1:9999");
        properties.setIp("127.0.0.1");
        properties.setPort(9999);
        properties.setAccessToken("token");
        properties.setLogPath("/tmp/xxl-job");
        properties.setLogRetentionDays(7);

        XxlJobSpringExecutor executor = new JobAutoConfiguration().xxlJobExecutor(properties);

        assertThat(ReflectionTestUtils.getField(executor, "adminAddresses"))
                .isEqualTo("http://127.0.0.1:8080/xxl-job-admin");
        assertThat(ReflectionTestUtils.getField(executor, "appname")).isEqualTo("framework-demo-job");
        assertThat(ReflectionTestUtils.getField(executor, "address")).isEqualTo("http://127.0.0.1:9999");
        assertThat(ReflectionTestUtils.getField(executor, "ip")).isEqualTo("127.0.0.1");
        assertThat(ReflectionTestUtils.getField(executor, "port")).isEqualTo(9999);
        assertThat(ReflectionTestUtils.getField(executor, "accessToken")).isEqualTo("token");
        assertThat(ReflectionTestUtils.getField(executor, "logPath")).isEqualTo("/tmp/xxl-job");
        assertThat(ReflectionTestUtils.getField(executor, "logRetentionDays")).isEqualTo(7);
    }

    @Test
    void xxlJobExecutorTrimsTextPropertiesBeforeMapping() {
        JobProperties properties = new JobProperties();
        properties.setEnabled(true);
        properties.setAdminAddresses(" http://127.0.0.1:8080/xxl-job-admin ");
        properties.setAppName(" framework-demo-job ");
        properties.setAddress(" http://127.0.0.1:9999 ");
        properties.setIp(" 127.0.0.1 ");
        properties.setAccessToken(" token ");
        properties.setLogPath(" /tmp/xxl-job ");

        XxlJobSpringExecutor executor = new JobAutoConfiguration().xxlJobExecutor(properties);

        assertThat(ReflectionTestUtils.getField(executor, "adminAddresses"))
                .isEqualTo("http://127.0.0.1:8080/xxl-job-admin");
        assertThat(ReflectionTestUtils.getField(executor, "appname")).isEqualTo("framework-demo-job");
        assertThat(ReflectionTestUtils.getField(executor, "address")).isEqualTo("http://127.0.0.1:9999");
        assertThat(ReflectionTestUtils.getField(executor, "ip")).isEqualTo("127.0.0.1");
        assertThat(ReflectionTestUtils.getField(executor, "accessToken")).isEqualTo("token");
        assertThat(ReflectionTestUtils.getField(executor, "logPath")).isEqualTo("/tmp/xxl-job");
    }

    @Test
    void xxlJobExecutorFailsFastForMissingAdminAddresses() {
        JobProperties properties = new JobProperties();
        properties.setEnabled(true);
        properties.setAppName("framework-demo-job");

        assertThatThrownBy(() -> new JobAutoConfiguration().xxlJobExecutor(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.job.admin-addresses");
    }

    @Test
    void propertiesValidateOnlyWhenXxlJobIsEnabled() {
        JobProperties disabledProperties = new JobProperties();

        assertThatCode(disabledProperties::afterPropertiesSet).doesNotThrowAnyException();

        JobProperties enabledProperties = new JobProperties();
        enabledProperties.setEnabled(true);

        assertThatThrownBy(enabledProperties::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.job.admin-addresses");
    }

    @Test
    void xxlJobExecutorFailsFastForBlankLogPath() {
        JobProperties properties = new JobProperties();
        properties.setEnabled(true);
        properties.setAdminAddresses("http://127.0.0.1:8080/xxl-job-admin");
        properties.setAppName("framework-demo-job");
        properties.setLogPath(" ");

        assertThatThrownBy(() -> new JobAutoConfiguration().xxlJobExecutor(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.job.log-path");
    }
}
