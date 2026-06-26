package com.framework.admin.dashboard;

import com.framework.core.result.Result;
import com.framework.localmessage.service.LocalMessageService;
import com.framework.log.mapper.OperationLogMapper;
import com.framework.mq.deadletter.DeadLetterHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerTest {

    @Test
    void moduleStatusesMatchAdminRuntimeModules() {
        DashboardController controller = new DashboardController(
                provider(null),
                provider(null),
                provider(null));

        Result<DashboardController.DashboardSummary> result = controller.summary();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().modules())
                .hasSize(22)
                .extracting(DashboardController.ModuleStatus::name)
                .contains(
                        "framework-core",
                        "framework-cache",
                        "framework-notify",
                        "framework-excel",
                        "framework-datasource",
                        "framework-redis",
                        "framework-feign",
                        "framework-file",
                        "framework-job");
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }
        };
    }
}
