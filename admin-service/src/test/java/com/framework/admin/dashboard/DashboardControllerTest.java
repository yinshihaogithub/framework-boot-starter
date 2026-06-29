package com.framework.admin.dashboard;

import com.framework.admin.excel.ExcelAdminRepository;
import com.framework.admin.file.FileAdminRepository;
import com.framework.admin.notify.NotifyAdminRepository;
import com.framework.admin.system.AdminSystemModels.ConfigItem;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.core.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerTest {

    @Test
    void moduleStatusesMatchAdminRuntimeModules() {
        DashboardService service = new DashboardService(
                provider(null),
                provider(null),
                provider(null),
                provider(null),
                provider(null),
                provider(null),
                provider(null));
        DashboardController controller = new DashboardController(service);

        Result<DashboardController.DashboardSummary> result = controller.summary();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().modules())
                .hasSize(21)
                .extracting(DashboardController.ModuleStatus::name)
                .contains(
                        "framework-core",
                        "framework-cache",
                        "framework-notify",
                        "framework-excel",
                        "framework-datasource",
                        "framework-redis",
                        "framework-feign",
                        "framework-file")
                .doesNotContain("framework-job");
    }

    @Test
    void summaryIncludesOperationalCenterMetrics() {
        DashboardService service = new DashboardService(
                provider(null),
                provider(null),
                provider(null),
                provider(new FakeNotifyRepository()),
                provider(new FakeExcelRepository()),
                provider(new FakeFileRepository()),
                provider(new FakeSystemRepository(false)));
        DashboardController controller = new DashboardController(service);

        Result<DashboardController.DashboardSummary> result = controller.summary();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().notifications())
                .containsEntry("templates", 3L)
                .containsEntry("successRecords", 7L)
                .containsEntry("failedRecords", 2L);
        assertThat(result.getData().excel())
                .containsEntry("total", 5L)
                .containsEntry("failed", 1L);
        assertThat(result.getData().files())
                .containsEntry("active", 4L)
                .containsEntry("totalSize", 1024L);
        assertThat(result.getData().security().defaultPasswordChanged()).isFalse();
    }

    @Test
    void summaryIncludesDefaultPasswordChangedSecurityStatus() {
        DashboardService service = new DashboardService(
                provider(null),
                provider(null),
                provider(null),
                provider(null),
                provider(null),
                provider(null),
                provider(new FakeSystemRepository(true)));
        DashboardController controller = new DashboardController(service);

        Result<DashboardController.DashboardSummary> result = controller.summary();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().security().defaultPasswordChanged()).isTrue();
    }

    @Test
    void summaryFallsBackWhenOptionalProvidersFail() {
        DashboardService service = new DashboardService(
                failingProvider(),
                failingProvider(),
                failingProvider(),
                failingProvider(),
                failingProvider(),
                failingProvider(),
                failingProvider());
        DashboardController controller = new DashboardController(service);

        Result<DashboardController.DashboardSummary> result = controller.summary();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().mq()).containsEntry("total", 0L);
        assertThat(result.getData().localMessage()).containsEntry("total", 0L);
        assertThat(result.getData().logs()).containsEntry("total", 0L);
        assertThat(result.getData().notifications()).containsEntry("records", 0L);
        assertThat(result.getData().excel()).containsEntry("total", 0L);
        assertThat(result.getData().files()).containsEntry("active", 0L);
        assertThat(result.getData().security().defaultPasswordChanged()).isTrue();
    }

    private static class FakeNotifyRepository extends NotifyAdminRepository {
        private FakeNotifyRepository() {
            super(null);
        }

        @Override
        public long countTemplates(String keyword, String channel, String status) {
            return 3L;
        }

        @Override
        public long countTemplatesByStatus(String status) {
            return 2L;
        }

        @Override
        public long countRecords(String channel, Boolean success) {
            return 9L;
        }

        @Override
        public long countRecordsBySuccess(boolean success) {
            return success ? 7L : 2L;
        }
    }

    private static class FakeExcelRepository extends ExcelAdminRepository {
        private FakeExcelRepository() {
            super(null);
        }

        @Override
        public Map<String, Long> stats() {
            return Map.of("total", 5L, "success", 4L, "failed", 1L, "import", 2L, "export", 3L);
        }
    }

    private static class FakeFileRepository extends FileAdminRepository {
        private FakeFileRepository() {
            super(null);
        }

        @Override
        public Map<String, Long> stats() {
            return Map.of("active", 4L, "deleted", 1L, "totalSize", 1024L);
        }
    }

    private static class FakeSystemRepository extends AdminSystemRepository {
        private final boolean defaultPasswordChanged;

        private FakeSystemRepository(boolean defaultPasswordChanged) {
            super(null);
            this.defaultPasswordChanged = defaultPasswordChanged;
        }

        @Override
        public List<ConfigItem> listConfigs() {
            return List.of(new ConfigItem()
                    .setConfigKey("admin.default.password.changed")
                    .setConfigValue(String.valueOf(defaultPasswordChanged)));
        }
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

    private static <T> ObjectProvider<T> failingProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                throw new IllegalStateException("optional provider unavailable");
            }

            @Override
            public T getIfAvailable() {
                throw new IllegalStateException("optional provider unavailable");
            }

            @Override
            public T getIfUnique() {
                throw new IllegalStateException("optional provider unavailable");
            }

            @Override
            public T getObject() {
                throw new IllegalStateException("optional provider unavailable");
            }

            @Override
            public Stream<T> stream() {
                return Stream.empty();
            }
        };
    }
}
