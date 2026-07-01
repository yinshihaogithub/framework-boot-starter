package com.framework.admin.excel;

import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
import com.framework.core.result.ResultCode;
import com.framework.excel.config.ExcelProperties;
import com.framework.excel.service.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelAdminServiceTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void exportTaskReturnsEmptyWhenExportServiceIsMissing() {
        ExcelAdminService service = service(new InMemoryExcelAdminMapper(), null);

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.message()).isEqualTo("Excel导出服务未启用");
    }

    @Test
    void exportTaskReturnsEmptyWhenExportProviderFails() {
        ExcelAdminService service = new ExcelAdminService(
                new InMemoryExcelAdminMapper(), failingProvider(), auditService());

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.message()).isEqualTo("Excel导出服务未启用");
    }

    @Test
    void exportTaskCreatesSuccessTask() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        CapturingExcelExportService exportService = new CapturingExcelExportService();
        ExcelAdminModels.ExportRequest request = new ExcelAdminModels.ExportRequest();
        request.setTaskName("\u00A0用户导出\u3000");
        request.setBizType("\u3000user\u00A0");
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
    void exportTaskRecordsCurrentUserAsOperatorAndAuditsIt() {
        UserContextHolder.set(new LoginUser().setUserId(7L).setUsername("alice"));
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        RecordingAuditService auditService = new RecordingAuditService();
        ExcelAdminService service = service(repository, new CapturingExcelExportService(), auditService);

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result.success()).isTrue();
        assertThat(repository.tasks.get(0).getOperatorName()).isEqualTo("alice");
        assertThat(auditService.successAction).isEqualTo("创建导出任务");
        assertThat(auditService.successParams).containsEntry("operator", "alice");
    }

    @Test
    void exportTaskDefaultsOperatorWhenUserContextIsMissing() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        ExcelAdminService service = service(repository, new CapturingExcelExportService());

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result.success()).isTrue();
        assertThat(repository.tasks.get(0).getOperatorName()).isEqualTo("admin");
    }

    @Test
    void exportTaskSucceedsWhenSuccessAuditFails() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        ExcelAdminService service = service(repository, new CapturingExcelExportService(), new ThrowingAuditService());

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createExportTask(null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getStatus()).isEqualTo("SUCCESS");
        assertThat(repository.tasks).hasSize(1);
    }

    @Test
    void exportTaskPersistsFailedTaskWhenExportThrows() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
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
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
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
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
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
    void importFailureTaskRecordsCurrentUserAsOperatorAndAuditsIt() {
        UserContextHolder.set(new LoginUser().setUserId(7L).setUsername("alice"));
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        RecordingAuditService auditService = new RecordingAuditService();
        ExcelAdminService service = service(repository, null, auditService);

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createImportFailureTask(null, null);

        assertThat(result.success()).isTrue();
        assertThat(repository.tasks.get(0).getOperatorName()).isEqualTo("alice");
        assertThat(auditService.successAction).isEqualTo("登记导入失败任务");
        assertThat(auditService.successParams).containsEntry("operator", "alice");
    }

    @Test
    void importFailureTaskNormalizesRequestAndUsesSameErrorMessageForAllErrorRows() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        ExcelAdminModels.FailureRequest request = new ExcelAdminModels.FailureRequest();
        request.setTaskName("\u00A0用户导入失败\u3000");
        request.setBizType("\u3000system-user\u00A0");
        request.setErrorMessage("\u00A0模板字段缺失\u3000");
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
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        ExcelAdminService service = service(repository, null, new ThrowingAuditService());

        ExcelAdminService.ActionResult<ExcelAdminModels.TaskResult> result = service.createImportFailureTask(null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getStatus()).isEqualTo("FAILED");
        assertThat(repository.tasks).hasSize(1);
        assertThat(repository.errorRecords).hasSize(2);
    }

    @Test
    void writeTasksReturnServiceErrorWhenRepositoryFails() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
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
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        repository.createTask(new ExcelAdminModels.Task().setTaskName("导出").setTaskType("EXPORT").setStatus("SUCCESS"));
        ExcelAdminService service = service(repository, null);

        PageResult<ExcelAdminModels.Task> page = service.tasks(null, null, -1, 500);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getTotal()).isEqualTo(1);
    }

    @Test
    void tasksNormalizeTypeAndStatusFilters() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        repository.createTask(new ExcelAdminModels.Task().setTaskName("导出").setTaskType("EXPORT").setStatus("SUCCESS"));
        ExcelAdminService service = service(repository, null);

        PageResult<ExcelAdminModels.Task> page = service.tasks("\u00A0export\u3000", "\u3000success\u00A0", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords())
                .extracting(ExcelAdminModels.Task::getTaskType, ExcelAdminModels.Task::getStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("EXPORT", "SUCCESS"));
    }

    @Test
    void tasksKeepProcessingStatusAsValidFilter() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        repository.createTask(new ExcelAdminModels.Task()
                .setTaskName("导入中")
                .setTaskType("IMPORT")
                .setStatus("PROCESSING"));
        repository.createTask(new ExcelAdminModels.Task()
                .setTaskName("导出完成")
                .setTaskType("EXPORT")
                .setStatus("SUCCESS"));
        ExcelAdminService service = service(repository, null);

        PageResult<ExcelAdminModels.Task> page = service.tasks("\u3000import\u00A0", "\u00A0processing\u3000", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords())
                .extracting(ExcelAdminModels.Task::getTaskType, ExcelAdminModels.Task::getStatus)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("IMPORT", "PROCESSING"));
    }

    @Test
    void tasksReturnEmptyPageForInvalidTypeOrStatusFilter() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
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
        ExcelAdminService service = service(new FailingExcelAdminMapper(), null);

        Map<String, Long> stats = service.stats();
        PageResult<ExcelAdminModels.Task> page = service.tasks(null, null, -1, 500);
        PageResult<ExcelAdminModels.ErrorRecord> errors = service.errors(1L, -1, 500);

        assertThat(stats)
                .containsEntry("total", 0L)
                .containsEntry("success", 0L)
                .containsEntry("failed", 0L)
                .containsEntry("import", 0L)
                .containsEntry("export", 0L);
        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getRecords()).isEmpty();
        assertThat(errors.getPageNum()).isEqualTo(1);
        assertThat(errors.getPageSize()).isEqualTo(200);
        assertThat(errors.getRecords()).isEmpty();
    }

    @Test
    void errorsRejectInvalidTaskIdBeforeRepositoryLookupAndKeepSafePaging() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        ExcelAdminService service = service(repository, null);

        PageResult<ExcelAdminModels.ErrorRecord> zeroId = service.errors(0L, -1, 500);
        PageResult<ExcelAdminModels.ErrorRecord> nullId = service.errors(null, 2, 10);

        assertThat(zeroId.getPageNum()).isEqualTo(1);
        assertThat(zeroId.getPageSize()).isEqualTo(200);
        assertThat(zeroId.getRecords()).isEmpty();
        assertThat(nullId.getPageNum()).isEqualTo(2);
        assertThat(nullId.getPageSize()).isEqualTo(10);
        assertThat(nullId.getRecords()).isEmpty();

        assertThat(repository.listErrorsTaskId).isNull();
    }

    @Test
    void errorsArePagedAndCounted() {
        InMemoryExcelAdminMapper repository = new InMemoryExcelAdminMapper();
        Long taskId = repository.createTask(new ExcelAdminModels.Task().setTaskName("导入失败"));
        for (int i = 1; i <= 5; i++) {
            repository.insertError(taskId, i, "错误" + i, "{}");
        }
        ExcelAdminService service = service(repository, null);

        PageResult<ExcelAdminModels.ErrorRecord> page = service.errors(taskId, 2, 2);

        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.getPageNum()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getRecords())
                .extracting(ExcelAdminModels.ErrorRecord::getRowIndex)
                .containsExactly(3, 4);
        assertThat(repository.listErrorsOffset).isEqualTo(2);
        assertThat(repository.listErrorsPageSize).isEqualTo(2);
        assertThat(repository.countErrorsTaskId).isEqualTo(taskId);
    }

    private static ExcelAdminService service(ExcelAdminMapper mapper, ExcelExportService exportService) {
        return service(mapper, exportService, auditService());
    }

    private static ExcelAdminService service(ExcelAdminMapper mapper, ExcelExportService exportService,
                                             AdminAuditService auditService) {
        return new ExcelAdminService(mapper, provider(exportService), auditService);
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
        private String successAction;
        private Map<String, Object> successParams;
        private String failureAction;
        private String failureMessage;

        private RecordingAuditService() {
            super(null, null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            this.successAction = action;
            this.successParams = (Map<String, Object>) params;
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

    private static class InMemoryExcelAdminMapper implements ExcelAdminMapper {
        private final List<ExcelAdminModels.Task> tasks = new ArrayList<>();
        private final List<ExcelAdminModels.ErrorRecord> errorRecords = new ArrayList<>();
        private long nextTaskId = 1;
        private long nextErrorId = 1;
        private Long listErrorsTaskId;
        private Long countErrorsTaskId;
        private int listErrorsOffset;
        private int listErrorsPageSize;
        private RuntimeException commandFailure;

        Long createTask(ExcelAdminModels.Task task) {
            insertTask(task);
            return task.getId();
        }

        List<ExcelAdminModels.ErrorRecord> errors(Long taskId) {
            return errorRecords.stream()
                    .filter(error -> taskId.equals(error.getTaskId()))
                    .toList();
        }

        @Override
        public List<ExcelAdminModels.Task> listTasks(String taskType, String status, int offset, int pageSize) {
            return tasks.stream()
                    .filter(task -> taskType == null || taskType.equals(task.getTaskType()))
                    .filter(task -> status == null || status.equals(task.getStatus()))
                    .skip(offset)
                    .limit(pageSize)
                    .toList();
        }

        @Override
        public long countTasks(String taskType, String status) {
            return listTasks(taskType, status, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public long countAllTasks() {
            return tasks.size();
        }

        @Override
        public long countTasksByStatus(String status) {
            return tasks.stream().filter(task -> status.equals(task.getStatus())).count();
        }

        @Override
        public long countTasksByType(String taskType) {
            return tasks.stream().filter(task -> taskType.equals(task.getTaskType())).count();
        }

        @Override
        public int insertTask(ExcelAdminModels.Task task) {
            failCommandIfNeeded();
            long id = nextTaskId++;
            task.setId(id);
            tasks.add(task);
            return 1;
        }

        @Override
        public int insertError(Long taskId, int rowIndex, String errorMessage, String rawData) {
            failCommandIfNeeded();
            errorRecords.add(new ExcelAdminModels.ErrorRecord()
                    .setId(nextErrorId++)
                    .setTaskId(taskId)
                    .setRowIndex(rowIndex)
                    .setErrorMessage(errorMessage)
                    .setRawData(rawData));
            return 1;
        }

        @Override
        public List<ExcelAdminModels.ErrorRecord> listErrors(Long taskId, int offset, int pageSize) {
            this.listErrorsTaskId = taskId;
            this.listErrorsOffset = offset;
            this.listErrorsPageSize = pageSize;
            return errors(taskId).stream()
                    .skip(offset)
                    .limit(pageSize)
                    .toList();
        }

        @Override
        public long countErrors(Long taskId) {
            this.countErrorsTaskId = taskId;
            return errors(taskId).size();
        }

        private void failCommandIfNeeded() {
            if (commandFailure != null) {
                throw commandFailure;
            }
        }
    }

    private static class FailingExcelAdminMapper implements ExcelAdminMapper {
        @Override
        public List<ExcelAdminModels.Task> listTasks(String taskType, String status, int offset, int pageSize) {
            throw unavailable();
        }

        @Override
        public long countTasks(String taskType, String status) {
            throw unavailable();
        }

        @Override
        public long countAllTasks() {
            throw unavailable();
        }

        @Override
        public long countTasksByStatus(String status) {
            throw unavailable();
        }

        @Override
        public long countTasksByType(String taskType) {
            throw unavailable();
        }

        @Override
        public int insertTask(ExcelAdminModels.Task task) {
            throw unavailable();
        }

        @Override
        public int insertError(Long taskId, int rowIndex, String errorMessage, String rawData) {
            throw unavailable();
        }

        @Override
        public List<ExcelAdminModels.ErrorRecord> listErrors(Long taskId, int offset, int pageSize) {
            throw unavailable();
        }

        @Override
        public long countErrors(Long taskId) {
            throw unavailable();
        }

        private IllegalStateException unavailable() {
            return new IllegalStateException("excel mapper unavailable");
        }
    }
}
