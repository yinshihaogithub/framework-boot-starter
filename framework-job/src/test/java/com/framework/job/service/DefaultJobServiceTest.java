package com.framework.job.service;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultJobServiceTest {

    @Test
    void runsRegisteredHandlerByName() {
        AtomicInteger runs = new AtomicInteger();
        DefaultJobService jobService = new DefaultJobService(List.of(handler(" clean ", runs::incrementAndGet)));

        boolean result = jobService.run(" clean ");

        assertThat(result).isTrue();
        assertThat(runs).hasValue(1);
        assertThat(jobService.names()).containsExactly("clean");
    }

    @Test
    void returnsFalseForMissingOrFailingHandlers() {
        DefaultJobService jobService = new DefaultJobService(List.of(handler("fail", () -> {
            throw new IllegalStateException("boom");
        })));

        assertThat(jobService.run("missing")).isFalse();
        assertThat(runWithJobLoggingDisabled(() -> jobService.run("fail"))).isFalse();
    }

    @Test
    void allowsNullHandlerListAsEmptyRegistry() {
        DefaultJobService jobService = new DefaultJobService(null);

        assertThat(jobService.names()).isEmpty();
        assertThat(jobService.run("clean")).isFalse();
    }

    @Test
    void rejectsBlankOrDuplicateHandlerNames() {
        assertThatThrownBy(() -> new DefaultJobService(List.of(handler(" ", () -> {
        }))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JobHandler name must not be blank");

        assertThatThrownBy(() -> new DefaultJobService(List.of(
                handler("clean", () -> {
                }),
                handler("clean", () -> {
                }))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate JobHandler name");

        assertThatThrownBy(() -> new DefaultJobService(List.of(handler("clean job", () -> {
        }))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JobHandler name must match");
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

    private static boolean runWithJobLoggingDisabled(BooleanSupplier supplier) {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DefaultJobService.class);
        ch.qos.logback.classic.Level previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.OFF);
        try {
            return supplier.getAsBoolean();
        } finally {
            logger.setLevel(previousLevel);
        }
    }

    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
