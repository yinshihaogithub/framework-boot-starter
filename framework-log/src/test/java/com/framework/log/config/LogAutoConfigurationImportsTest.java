package com.framework.log.config;

import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import com.framework.log.aspect.OperationLogAspect;
import com.framework.log.service.OperationLogStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LogAutoConfigurationImportsTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogAutoConfiguration.class));

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void autoConfigurationRegistersOperationLogInfrastructure() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(LogProperties.class)
                .hasSingleBean(OperationLogAspect.class)
                .hasSingleBean(ApiLogFilter.class)
                .hasSingleBean(OperationLogStorageService.class));
    }

    @Test
    void logAsyncExecutorPropagatesTraceIdToWorkerThread() {
        contextRunner.run(context -> {
            Executor executor = context.getBean("logAsyncExecutor", Executor.class);
            CompletableFuture<String> observed = new CompletableFuture<>();
            TraceContext.putTraceId("async-trace");

            executor.execute(() -> observed.complete(MDC.get(FrameworkConstants.TRACE_ID_MDC_KEY)));

            assertThat(observed.get(3, TimeUnit.SECONDS)).isEqualTo("async-trace");
            if (executor instanceof ThreadPoolTaskExecutor taskExecutor) {
                taskExecutor.shutdown();
            }
        });
    }

    @Test
    void autoConfigurationRejectsInvalidLogPropertiesAtStartup() {
        contextRunner
                .withPropertyValues("framework.log.api-sample-rate=-1")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.log.api-sample-rate"));

        contextRunner
                .withPropertyValues("framework.log.api-sample-rate=101")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.log.api-sample-rate"));

        contextRunner
                .withPropertyValues("framework.log.retention-days=0")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.log.retention-days"));
    }
}
