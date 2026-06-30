package com.framework.admin.excel;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.core.result.ResultCode;
import com.framework.excel.config.ExcelProperties;
import com.framework.excel.service.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelAdminServiceTest {

    @Test
    void exportTaskReturnsEmptyWhenExportServiceIsMissing() {
        ExcelAdminService service = service(new InMemoryExcelAdminRepository(), null);

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.message()).isEqualTo("Excel导出服务未启用");
    }

    @Test
    void exportTaskReturnsEmptyWhenExportProviderFails() {
        ExcelAdminService service = new ExcelAdminService(
                new InMemoryExcelAdminRepository(), failingProvider(), auditService());

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.message()).isEqualTo("Excel导出服务未启用");
    }

    @Test
    void exportTaskCreatesSuccessTask() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        CapturingExcelExportService exportService = new CapturingExcelExportService();
        ExcelAdminModels.ExportRequest request = new ExcelAdminModels.ExportRequest();
        request.setTaskName(" 用户导出 ");
        request.setBizType(" user ");
        ExcelAdminService service = service(repository, exportService);

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(request, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getStatus()).isEqualTo("SUCCESS");
        assertThat(result.data().getTotalRows()).isEqualTo(2);
        assertThat(result.data().getSuccessRows()).isEqualTo(2);
        assertThat(result.data().getFileSize()).isEqualTo(5L);
        assertThat(exportService.sheetName).isEqualTo("用户清单");
        assertThat(exportService.rowCount).isEqualTo(2);
        assertThat(repository.tasks)
                .extracting(ExcelAdminModels.Task::getTaskName, ExcelAdminModels.Task::getBizType,
                        ExcelAdminModels.Task::getTaskType, ExcelAdminModels.Task::getStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("用户导出", "user", "EXPORT", "SUCCESS"));
    }

    @Test
    void exportTaskSucceedsWhenSuccessAuditFails() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        ExcelAdminService service = service(repository, new CapturingExcelExportService(), new ThrowingAuditService());

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getStatus()).isEqualTo("SUCCESS");
        assertThat(repository.tasks).hasSize(1);
    }

    @Test
    void exportTaskPersistsFailedTaskWhenExportThrows() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        CapturingExcelExportService exportService = new CapturingExcelExportService();
        exportService.failure = new IllegalStateException("template broken");
        RecordingAuditService auditService = new RecordingAuditService();
        ExcelAdminService service = service(repository, exportService, auditService);

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getStatus()).isEqualTo("FAILED");
        assertThat(result.data().getTotalRows()).isEqualTo(2);
        assertThat(result.data().getSuccessRows()).isZero();
        assertThat(result.data().getFailureRows()).isEqualTo(2);
        assertThat(result.data().getFileSize()).isZero();
        assertThat(repository.tasks)
                .extracting(ExcelAdminModels.Task::getTaskType, ExcelAdminModels.Task::getStatus,
                        ExcelAdminModels.Task::getErrorMessage)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("EXPORT", "FAILED", "template broken"));
        assertThat(auditService.failureAction).isEqualTo("创建导出任务");
        assertThat(auditService.failureMessage).isEqualTo("template broken");
    }

    @Test
    void exportTaskPersistsFailedTaskWhenFailureAuditFails() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        CapturingExcelExportService exportService = new CapturingExcelExportService();
        exportService.failure = new IllegalStateException("template broken");
        ExcelAdminService service = service(repository, exportService, new ThrowingAuditService());

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getStatus()).isEqualTo("FAILED");
        assertThat(result.data().getFailureRows()).isEqualTo(2);
        assertThat(repository.tasks)
                .extracting(ExcelAdminModels.Task::getTaskType, ExcelAdminModels.Task::getStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("EXPORT", "FAILED"));
    }

    @Test
    void importFailureTaskCreatesFailedTaskAndErrors() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        ExcelAdminModels.FailureRequest request = new ExcelAdminModels.FailureRequest();
        request.setErrorMessage("手机号格式错误");
        ExcelAdminService service = service(repository, null);

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createImportFailureTask(request, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getStatus()).isEqualTo("FAILED");
        assertThat(result.data().getTotalRows()).isEqualTo(3);
        assertThat(result.data().getFailureRows()).isEqualTo(2);
        assertThat(repository.tasks).hasSize(1);
        assertThat(repository.errors(result.data().getTaskId()))
                .extracting(ExcelAdminModels.ErrorRecord::getRowIndex, ExcelAdminModels.ErrorRecord::getErrorMessage)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2, "手机号格式错误"),
                        org.assertj.core.groups.Tuple.tuple(3, "手机号格式错误"));
    }

    @Test
    void importFailureTaskNormalizesRequestAndUsesSameErrorMessageForAllErrorRows() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        ExcelAdminModels.FailureRequest request = new ExcelAdminModels.FailureRequest();
        request.setTaskName(" 用户导入失败 ");
        request.setBizType(" system-user ");
        request.setErrorMessage(" 模板字段缺失 ");
        ExcelAdminService service = service(repository, null);

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result =
                service.createImportFailureTask(request, null);

        assertThat(result.success()).isTrue();
        assertThat(repository.tasks)
                .extracting(ExcelAdminModels.Task::getTaskName, ExcelAdminModels.Task::getBizType,
                        ExcelAdminModels.Task::getErrorMessage)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("用户导入失败", "system-user", "模板字段缺失"));
        assertThat(repository.errors(result.data().getTaskId()))
                .extracting(ExcelAdminModels.ErrorRecord::getErrorMessage)
                .containsExactly("模板字段缺失", "模板字段缺失");
    }

    @Test
    void importFailureTaskSucceedsWhenAuditFails() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        ExcelAdminService service = service(repository, null, new ThrowingAuditService());

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createImportFailureTask(null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getStatus()).isEqualTo("FAILED");
        assertThat(repository.tasks).hasSize(1);
        assertThat(repository.errorRecords).hasSize(2);
    }

    @Test
    void writeTasksReturnServiceErrorWhenRepositoryFails() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        repository.commandFailure = new RuntimeException("database down");
        ExcelAdminService exportService = service(repository, new CapturingExcelExportService());
        ExcelAdminService importService = service(repository, null);

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> exportResult =
                exportService.createExportTask(null, null);
        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> importResult =
                importService.createImportFailureTask(null, null);

        assertThat(exportResult.success()).isFalse();
        assertThat(exportResult.code()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(exportResult.message()).isEqualTo("Excel导出任务创建失败");
        assertThat(importResult.success()).isFalse();
        assertThat(importResult.code()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(importResult.message()).isEqualTo("Excel导入失败任务登记失败");
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

    @Test
    void tasksKeepProcessingStatusAsValidFilter() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        repository.createTask(new ExcelAdminModels.Task()
                .setTaskName("导入中")
                .setTaskType("IMPORT")
                .setStatus("PROCESSING"));
        repository.createTask(new ExcelAdminModels.Task()
                .setTaskName("导出完成")
                .setTaskType("EXPORT")
                .setStatus("SUCCESS"));
        ExcelAdminService service = service(repository, null);

        PageResult<ExcelAdminModels.Task> page = service.tasks(" import ", " processing ", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords())
                .extracting(ExcelAdminModels.Task::getTaskType, ExcelAdminModels.Task::getStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("IMPORT", "PROCESSING"));
    }

    @Test
    void tasksReturnEmptyPageForInvalidTypeOrStatusFilter() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        repository.createTask(new ExcelAdminModels.Task().setTaskName("导出").setTaskType("EXPORT").setStatus("SUCCESS"));
        ExcelAdminService service = service(repository, null);

        PageResult<ExcelAdminModels.Task> invalidType = service.tasks("CSV", null, 1, 20);
        PageResult<ExcelAdminModels.Task> invalidStatus = service.tasks(null, "ARCHIVED", 1, 20);

        assertThat(invalidType.getTotal()).isZero();
        assertThat(invalidType.getRecords()).isEmpty();
        assertThat(invalidStatus.getTotal()).isZero();
        assertThat(invalidStatus.getRecords()).isEmpty();
    }

    @Test
    void queryEndpointsFallBackWhenRepositoryFails() {
        ExcelAdminService service = service(new FailingExcelAdminRepository(), null);

        Map<String, Long> stats = service.stats();
        PageResult<ExcelAdminModels.Task> page = service.tasks(null, null, -1, 500);
        List<ExcelAdminModels.ErrorRecord> errors = service.errors(1L);

        assertThat(stats)
                .containsEntry("total", 0L)
                .containsEntry("success", 0L)
                .containsEntry("failed", 0L)
                .containsEntry("import", 0L)
                .containsEntry("export", 0L);
        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getRecords()).isEmpty();
        assertThat(errors).isEmpty();
    }

    @Test
    void errorsRejectInvalidTaskIdBeforeRepositoryLookup() {
        InMemoryExcelAdminRepository repository = new InMemoryExcelAdminRepository();
        ExcelAdminService service = service(repository, null);

        assertThat(service.errors(0L)).isEmpty();
        assertThat(service.errors(null)).isEmpty();

        assertThat(repository.listErrorsTaskId).isNull();
    }

    private static ExcelAdminService service(ExcelAdminRepository repository, ExcelExportService exportService) {
        return service(repository, exportService, auditService());
    }

    private static ExcelAdminService service(ExcelAdminRepository repository, ExcelExportService exportService,
                                             AdminAuditService auditService) {
        return new ExcelAdminService(repository, provider(exportService), auditService);
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
                throw new IllegalStateException("excel provider unavailable");
            }

            @Override
            public T getIfAvailable() {
                throw new IllegalStateException("excel provider unavailable");
            }

            @Override
            public T getIfUnique() {
                throw new IllegalStateException("excel provider unavailable");
            }

            @Override
            public T getObject() {
                throw new IllegalStateException("excel provider unavailable");
            }

            @Override
            public Stream<T> stream() {
                return Stream.empty();
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
        private RuntimeException failure;

        private CapturingExcelExportService() {
            super(new ExcelProperties());
        }

        @Override
        public <T> byte[] export(String sheetName, Class<T> headClass, java.util.Collection<T> rows) {
            if (failure != null) {
                throw failure;
            }
            this.sheetName = sheetName;
            this.rowCount = rows.size();
            return new byte[]{1, 2, 3, 4, 5};
        }
    }

    private static class RecordingAuditService extends AdminAuditService {
        private String failureAction;
        private String failureMessage;

        private RecordingAuditService() {
            super(null, null);
        }

        @Override
        public void failure(HttpServletRequest request, String module, String action, String operationType,
                            Object params, Exception exception) {
            this.failureAction = action;
            this.failureMessage = exception == null ? null : exception.getMessage();
        }
    }

    private static class ThrowingAuditService extends AdminAuditService {
        private ThrowingAuditService() {
            super(null, null);
        }

        @Override
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            throw new IllegalStateException("audit unavailable");
        }

        @Override
        public void failure(HttpServletRequest request, String module, String action, String operationType,
                            Object params, Exception exception) {
            throw new IllegalStateException("audit unavailable");
        }
    }

    private static class InMemoryExcelAdminRepository extends ExcelAdminRepository {
        private final List<ExcelAdminModels.Task> tasks = new ArrayList<>();
        private final List<ExcelAdminModels.ErrorRecord> errorRecords = new ArrayList<>();
        private long nextTaskId = 1;
        private long nextErrorId = 1;
        private Long listErrorsTaskId;
        private RuntimeException commandFailure;

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
            failCommandIfNeeded();
            long id = nextTaskId++;
            task.setId(id);
            tasks.add(task);
            return id;
        }

        @Override
        public void createError(Long taskId, int rowIndex, String errorMessage, String rawData) {
            failCommandIfNeeded();
            errorRecords.add(new ExcelAdminModels.ErrorRecord()
                    .setId(nextErrorId++)
                    .setTaskId(taskId)
                    .setRowIndex(rowIndex)
                    .setErrorMessage(errorMessage)
                    .setRawData(rawData));
        }

        @Override
        public List<ExcelAdminModels.ErrorRecord> listErrors(Long taskId) {
            this.listErrorsTaskId = taskId;
            return errors(taskId);
        }

        private List<ExcelAdminModels.ErrorRecord> errors(Long taskId) {
            return errorRecords.stream()
                    .filter(error -> taskId.equals(error.getTaskId()))
                    .toList();
        }

        private void failCommandIfNeeded() {
            if (commandFailure != null) {
                throw commandFailure;
            }
        }
    }

    private static class FailingExcelAdminRepository extends ExcelAdminRepository {
        private FailingExcelAdminRepository() {
            super(null);
        }

        @Override
        public List<ExcelAdminModels.Task> listTasks(String taskType, String status, int pageNum, int pageSize) {
            throw new IllegalStateException("excel repository unavailable");
        }

        @Override
        public long countTasks(String taskType, String status) {
            throw new IllegalStateException("excel repository unavailable");
        }

        @Override
        public Map<String, Long> stats() {
            throw new IllegalStateException("excel repository unavailable");
        }

        @Override
        public List<ExcelAdminModels.ErrorRecord> listErrors(Long taskId) {
            throw new IllegalStateException("excel repository unavailable");
        }
    }
}
