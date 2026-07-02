package com.framework.admin.monitor;

import com.framework.core.result.Result;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorAdminControllerTest {

    @Test
    void healthWrapsServiceResult() {
        Object health = Map.of(
                "status", "UP",
                "components", Map.of("db", Map.of("status", "UP")));
        RecordingMonitorAdminService service = new RecordingMonitorAdminService(health, Map.of());
        MonitorAdminController controller = new MonitorAdminController(service);

        Result<Object> result = controller.health();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(health);
        assertThat(service.healthCalled).isTrue();
    }

    @Test
    void jvmWrapsServiceResult() {
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("availableProcessors", 8);
        jvm.put("maxMemory", 1024L);
        jvm.put("javaVersion", "21");
        RecordingMonitorAdminService service = new RecordingMonitorAdminService(Map.of(), jvm);
        MonitorAdminController controller = new MonitorAdminController(service);

        Result<Map<String, Object>> result = controller.jvm();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(jvm);
        assertThat(service.jvmCalled).isTrue();
    }

    private static final class RecordingMonitorAdminService extends MonitorAdminService {
        private final Object healthResult;
        private final Map<String, Object> jvmResult;
        private boolean healthCalled;
        private boolean jvmCalled;

        private RecordingMonitorAdminService(Object healthResult, Map<String, Object> jvmResult) {
            super(null);
            this.healthResult = healthResult;
            this.jvmResult = jvmResult;
        }

        @Override
        public Object health() {
            healthCalled = true;
            return healthResult;
        }

        @Override
        public Map<String, Object> jvm() {
            jvmCalled = true;
            return jvmResult;
        }
    }
}
