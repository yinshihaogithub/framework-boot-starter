package com.framework.admin.dashboard;

import com.framework.admin.excel.ExcelAdminMapper;
import com.framework.admin.file.FileAdminMapper;
import com.framework.admin.notify.NotifyAdminMapper;
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
                provider(new FakeNotifyMapper()),
                provider(new FakeExcelMapper()),
                provider(new FakeFileMapper()),
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

    private static class FakeNotifyMapper implements NotifyAdminMapper {
        @Override
        public java.util.List<TemplateRow> listTemplates(String keywordLike,
                                                         String channel,
                                                         String status,
                                                         int offset,
                                                         int pageSize) {
            return java.util.List.of();
        }

        @Override
        public long countTemplates(String keywordLike, String channel, String status) {
            return 3L;
        }

        @Override
        public TemplateRow findTemplate(Long id) {
            return null;
        }

        @Override
        public int insertTemplate(TemplateRow row) {
            return 1;
        }

        @Override
        public int updateTemplate(TemplateRow row) {
            return 1;
        }

        @Override
        public int deleteTemplate(Long id) {
            return 1;
        }

        @Override
        public int insertRecord(RecordRow row) {
            return 1;
        }

        @Override
        public java.util.List<RecordRow> listRecords(String channel, Boolean success, int offset, int pageSize) {
            return java.util.List.of();
        }

        @Override
        public long countRecords(String channel, Boolean success) {
            return 9L;
        }

        @Override
        public long countTemplatesByStatus(String status) {
            return 2L;
        }

        @Override
        public long countRecordsBySuccess(boolean success) {
            return success ? 7L : 2L;
        }
    }

    private static class FakeExcelMapper implements ExcelAdminMapper {
        @Override
        public List<com.framework.admin.excel.ExcelAdminModels.Task> listTasks(String taskType,
                                                                               String status,
                                                                               int offset,
                                                                               int pageSize) {
            return List.of();
        }

        @Override
        public long countTasks(String taskType, String status) {
            return 5L;
        }

        @Override
        public long countAllTasks() {
            return 5L;
        }

        @Override
        public long countTasksByStatus(String status) {
            return "FAILED".equals(status) ? 1L : 4L;
        }

        @Override
        public long countTasksByType(String taskType) {
            return "IMPORT".equals(taskType) ? 2L : 3L;
        }

        @Override
        public int insertTask(com.framework.admin.excel.ExcelAdminModels.Task task) {
            return 1;
        }

        @Override
        public int insertError(Long taskId, int rowIndex, String errorMessage, String rawData) {
            return 1;
        }

        @Override
        public List<com.framework.admin.excel.ExcelAdminModels.ErrorRecord> listErrors(Long taskId) {
            return List.of();
        }
    }

    private static class FakeFileMapper implements FileAdminMapper {
        @Override
        public List<com.framework.admin.file.FileAdminModels.FileRecord> list(String keyword,
                                                                              String businessType,
                                                                              String businessKey,
                                                                              String contentType,
                                                                              int offset,
                                                                              int pageSize) {
            return List.of();
        }

        @Override
        public long count(String keyword, String businessType, String businessKey, String contentType) {
            return 4L;
        }

        @Override
        public long countActive() {
            return 4L;
        }

        @Override
        public long countDeleted() {
            return 1L;
        }

        @Override
        public long sumActiveSize() {
            return 1024L;
        }

        @Override
        public com.framework.admin.file.FileAdminModels.FileRecord findById(Long id) {
            return null;
        }

        @Override
        public int insert(com.framework.admin.file.FileAdminModels.FileRecord record) {
            return 1;
        }

        @Override
        public int markDeleted(Long id) {
            return 1;
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
