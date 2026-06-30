package com.framework.admin.excel;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelAdminRepositoryTest {

    private final RecordingMapper mapper = new RecordingMapper();
    private final ExcelAdminRepository repository = new ExcelAdminRepository(mapper);

    @Test
    void listTasksTrimsFiltersAndCalculatesOffset() {
        repository.listTasks("\u00A0export\u3000", "\u3000success\u00A0", 3, 20);

        assertThat(mapper.taskType).isEqualTo("EXPORT");
        assertThat(mapper.status).isEqualTo("SUCCESS");
        assertThat(mapper.offset).isEqualTo(40);
        assertThat(mapper.pageSize).isEqualTo(20);
    }

    @Test
    void blankFiltersBecomeNull() {
        repository.countTasks("\u00A0\u3000", "");

        assertThat(mapper.taskType).isNull();
        assertThat(mapper.status).isNull();
    }

    @Test
    void createTaskReturnsGeneratedIdFromMapper() {
        ExcelAdminModels.Task task = new ExcelAdminModels.Task().setTaskName("导出");

        Long id = repository.createTask(task);

        assertThat(id).isEqualTo(99L);
        assertThat(task.getId()).isEqualTo(99L);
    }

    @Test
    void createTaskFailsWhenMapperDoesNotInsertRow() {
        mapper.insertTaskResult = 0;
        ExcelAdminModels.Task task = new ExcelAdminModels.Task().setTaskName("导出");

        assertThatThrownBy(() -> repository.createTask(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("excel task insert failed");
    }

    @Test
    void createTaskFailsWhenGeneratedIdIsMissing() {
        mapper.assignGeneratedId = false;
        ExcelAdminModels.Task task = new ExcelAdminModels.Task().setTaskName("导出");

        assertThatThrownBy(() -> repository.createTask(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("excel task insert failed");
    }

    @Test
    void createErrorFailsWhenMapperDoesNotInsertRow() {
        mapper.insertErrorResult = 0;

        assertThatThrownBy(() -> repository.createError(1L, 2, "错误", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("excel error insert failed");
    }

    @Test
    void createTaskWithErrorsUsesManualLocalTransaction() {
        RecordingTransactionTemplate transactionTemplate = new RecordingTransactionTemplate();
        ExcelAdminRepository transactionalRepository = new ExcelAdminRepository(mapper, transactionTemplate);
        ExcelAdminModels.Task task = new ExcelAdminModels.Task().setTaskName("导入失败");

        Long taskId = transactionalRepository.createTaskWithErrors(task, List.of(
                new ExcelAdminModels.ErrorRecord().setRowIndex(2).setErrorMessage("空用户名").setRawData("{}"),
                new ExcelAdminModels.ErrorRecord().setRowIndex(3).setErrorMessage("手机号错误").setRawData("{}")
        ));

        assertThat(taskId).isEqualTo(99L);
        assertThat(transactionTemplate.executions).isEqualTo(1);
        assertThat(mapper.errorTaskIds).containsExactly(99L, 99L);
        assertThat(mapper.errorRowIndexes).containsExactly(2, 3);
    }

    private static class RecordingMapper implements ExcelAdminMapper {
        private String taskType;
        private String status;
        private int offset;
        private int pageSize;
        private int insertTaskResult = 1;
        private int insertErrorResult = 1;
        private boolean assignGeneratedId = true;
        private final java.util.ArrayList<Long> errorTaskIds = new java.util.ArrayList<>();
        private final java.util.ArrayList<Integer> errorRowIndexes = new java.util.ArrayList<>();

        @Override
        public List<ExcelAdminModels.Task> listTasks(String taskType, String status, int offset, int pageSize) {
            this.taskType = taskType;
            this.status = status;
            this.offset = offset;
            this.pageSize = pageSize;
            return List.of();
        }

        @Override
        public long countTasks(String taskType, String status) {
            this.taskType = taskType;
            this.status = status;
            return 0;
        }

        @Override
        public long countAllTasks() {
            return 0;
        }

        @Override
        public long countTasksByStatus(String status) {
            return 0;
        }

        @Override
        public long countTasksByType(String taskType) {
            return 0;
        }

        @Override
        public int insertTask(ExcelAdminModels.Task task) {
            if (assignGeneratedId) {
                task.setId(99L);
            }
            return insertTaskResult;
        }

        @Override
        public int insertError(Long taskId, int rowIndex, String errorMessage, String rawData) {
            errorTaskIds.add(taskId);
            errorRowIndexes.add(rowIndex);
            return insertErrorResult;
        }

        @Override
        public List<ExcelAdminModels.ErrorRecord> listErrors(Long taskId) {
            return List.of();
        }
    }

    private static class RecordingTransactionTemplate extends TransactionTemplate {
        private int executions;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            executions++;
            return action.doInTransaction((TransactionStatus) null);
        }
    }
}
