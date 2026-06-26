package com.framework.job.service;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.IJobHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XxlJobHandlerRegistrarTest {

    @Test
    void registersFrameworkJobHandlersIntoXxlJobRegistry() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        JobHandler handler = handler("framework-test-" + UUID.randomUUID(), runs::incrementAndGet);

        new XxlJobHandlerRegistrar(List.of(handler)).afterSingletonsInstantiated();

        IJobHandler xxlJobHandler = XxlJobExecutor.loadJobHandler(handler.name());
        assertThat(xxlJobHandler).isNotNull();

        xxlJobHandler.execute();

        assertThat(runs).hasValue(1);
    }

    @Test
    void rejectsBlankOrDuplicateHandlerNames() {
        assertThatThrownBy(() -> new XxlJobHandlerRegistrar(List.of(handler("", () -> {
        }))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JobHandler name must not be blank");

        assertThatThrownBy(() -> new XxlJobHandlerRegistrar(List.of(
                handler("duplicate", () -> {
                }),
                handler("duplicate", () -> {
                }))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate JobHandler name");
    }

    private static JobHandler handler(String name, ThrowingRunnable runnable) {
        return new JobHandler() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void execute() throws Exception {
                runnable.run();
            }
        };
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
