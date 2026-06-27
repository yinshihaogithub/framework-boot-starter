package com.framework.admin.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthEndpoint;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorAdminServiceTest {

    @Test
    void healthReturnsUnknownWhenEndpointMissing() {
        MonitorAdminService service = new MonitorAdminService(provider(null));

        Object health = service.health();

        assertThat(health).isEqualTo(Map.of("status", "UNKNOWN"));
    }

    @Test
    void jvmReturnsRuntimeOverview() {
        MonitorAdminService service = new MonitorAdminService(provider(null));

        Map<String, Object> jvm = service.jvm();

        assertThat(jvm)
                .containsKeys("availableProcessors", "maxMemory", "totalMemory", "freeMemory", "uptime", "javaVersion");
        assertThat((Integer) jvm.get("availableProcessors")).isPositive();
        assertThat((Long) jvm.get("maxMemory")).isPositive();
    }

    private static ObjectProvider<HealthEndpoint> provider(HealthEndpoint value) {
        return new ObjectProvider<>() {
            @Override
            public HealthEndpoint getObject(Object... args) {
                return value;
            }

            @Override
            public HealthEndpoint getIfAvailable() {
                return value;
            }

            @Override
            public HealthEndpoint getIfUnique() {
                return value;
            }

            @Override
            public HealthEndpoint getObject() {
                return value;
            }

            @Override
            public Stream<HealthEndpoint> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }
        };
    }
}
