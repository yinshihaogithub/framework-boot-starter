package com.framework.admin.excel;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.excel.config.ExcelProperties;
import com.framework.excel.service.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelAdminServiceTest {

    @Test
    void exportTaskReturnsEmptyWhenExportServiceIsMissing() {
        ExcelAdminService service = service(new InMemoryExcelAdminRepository(), null);

        Optional<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void exportTaskCreatesSuccessTask() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        CapturingExcelExportService exportService = new CapturingExcelExportService();
        ExcelAdminModels.ExportRequest request = new ExcelAdminModels.ExportRequest();
        request.setTaskName(" 用户导出 ");
        request.setBizType(" user ");
        ExcelAdminService service = service(repository, exportService);

        Optional<ExcelAdminModels.TaskResult> result = service.createExportTask(request, null);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("SUCCESS");
        assertThat(result.get().getTotalRows()).isEqualTo(2);
        assertThat(result.get().getSuccessRows()).isEqualTo(2);
        assertThat(result.get().getFileSize()).isEqualTo(5L);
        assertThat(exportService.sheetName).isEqualTo("用户清单");
        assertThat(exportService.rowCount).isEqualTo(2);
        assertThat(repository.tasks)
                .extracting(ExcelAdminModels.Task::getTaskName, ExcelAdminModels.Task::getBizType,
                        ExcelAdminModels.Task::getTaskType, ExcelAdminModels.Task::getStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("用户导出", "user", "EXPORT", "SUCCESS"));
    }

    @Test
    void importFailureTaskCreatesFailedTaskAndErrors() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        ExcelAdminModels.FailureRequest request = new ExcelAdminModels.FailureRequest();
        request.setErrorMessage("手机号格式错误");
        ExcelAdminService service = service(repository, null);

        ExcelAdminModels.TaskResult result = service.createImportFailureTask(request, null);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getFailureRows()).isEqualTo(2);
        assertThat(repository.tasks).hasSize(1);
        assertThat(repository.errors(result.getTaskId()))
                .extracting(ExcelAdminModels.ErrorRecord::getRowIndex, ExcelAdminModels.ErrorRecord::getErrorMessage)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2, "手机号格式错误"),
                        org.assertj.core.groups.Tuple.tuple(3, "手机号格式错误"));
    }

    @Test
    void tasksUseSafePaging() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        repository.createTask(new ExcelAdminModels.Task().setTaskName("导出").setTaskType("EXPORT").setStatus("SUCCESS"));
        ExcelAdminService service = service(repository, null);

        PageResult<ExcelAdminModels.Task> page = service.tasks(null, null, -1, 500);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getTotal()).isEqualTo(1);
    }

    @Test
    void tasksNormalizeTypeAndStatusFilters() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        repository.createTask(new ExcelAdminModels.Task().setTaskName("导出").setTaskType("EXPORT").setStatus("SUCCESS"));
        ExcelAdminService service = service(repository, null);

        PageResult<ExcelAdminModels.Task> page = service.tasks(" export ", " success ", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords())
                .extracting(ExcelAdminModels.Task::getTaskType, ExcelAdminModels.Task::getStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("EXPORT", "SUCCESS"));
    }

    private static ExcelAdminService service(InMemoryExcelAdminRepository repository, ExcelExportService exportService) {
        return new ExcelAdminService(repository, provider(exportService), auditService());
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

    private static AdminAuditService auditService() {
        return new AdminAuditService(null, null) {
            @Override
            public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            }

            @Override
            public void failure(HttpServletRequest request, String module, String action, String operationType,
                                Object params, Exception exception) {
            }
        };
    }

    private static class CapturingExcelExportService extends ExcelExportService {
        private String sheetName;
        private int rowCount;

        private CapturingExcelExportService() {
            super(new ExcelProperties());
        }

        @Override
        public <T> byte[] export(String sheetName, Class<T> headClass, java.util.Collection<T> rows) {
            this.sheetName = sheetName;
            this.rowCount = rows.size();
            return new byte[]{1, 2, 3, 4, 5};
        }
    }

    private static class InMemoryExcelAdminRepository extends ExcelAdminRepository {
        private final List<ExcelAdminModels.Task> tasks = new ArrayList<>();
        private final List<ExcelAdminModels.ErrorRecord> errorRecords = new ArrayList<>();
        private long nextTaskId = 1;
        private long nextErrorId = 1;

        private InMemoryExcelAdminRepository() {
            super(null);
        }

        @Override
        public List<ExcelAdminModels.Task> listTasks(String taskType, String status, int pageNum, int pageSize) {
            return tasks.stream()
                    .filter(task -> taskType == null || taskType.equals(task.getTaskType()))
                    .filter(task -> status == null || status.equals(task.getStatus()))
                    .toList();
        }

        @Override
        public long countTasks(String taskType, String status) {
            return listTasks(taskType, status, 1, Integer.MAX_VALUE).size();
        }

        @Override
        public Map<String, Long> stats() {
            Map<String, Long> stats = new LinkedHashMap<>();
            stats.put("total", (long) tasks.size());
            stats.put("success", tasks.stream().filter(task -> "SUCCESS".equals(task.getStatus())).count());
            stats.put("failed", tasks.stream().filter(task -> "FAILED".equals(task.getStatus())).count());
            stats.put("import", tasks.stream().filter(task -> "IMPORT".equals(task.getTaskType())).count());
            stats.put("export", tasks.stream().filter(task -> "EXPORT".equals(task.getTaskType())).count());
            return stats;
        }

        @Override
        public Long createTask(ExcelAdminModels.Task task) {
            long id = nextTaskId++;
            task.setId(id);
            tasks.add(task);
            return id;
        }

        @Override
        public void createError(Long taskId, int rowIndex, String errorMessage, String rawData) {
            errorRecords.add(new ExcelAdminModels.ErrorRecord()
                    .setId(nextErrorId++)
                    .setTaskId(taskId)
                    .setRowIndex(rowIndex)
                    .setErrorMessage(errorMessage)
                    .setRawData(rawData));
        }

        @Override
        public List<ExcelAdminModels.ErrorRecord> listErrors(Long taskId) {
            return errors(taskId);
        }

        private List<ExcelAdminModels.ErrorRecord> errors(Long taskId) {
            return errorRecords.stream()
                    .filter(error -> taskId.equals(error.getTaskId()))
                    .toList();
        }
    }
}
