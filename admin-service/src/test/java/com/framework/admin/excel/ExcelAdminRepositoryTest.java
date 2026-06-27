package com.framework.admin.excel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelAdminRepositoryTest {

    private final RecordingMapper mapper = new RecordingMapper();
    private final ExcelAdminRepository repository = new ExcelAdminRepository(mapper);

    @Test
    void listTasksTrimsFiltersAndCalculatesOffset() {
        repository.listTasks(" EXPORT ", " SUCCESS ", 3, 20);

        assertThat(mapper.taskType).isEqualTo("EXPORT");
        assertThat(mapper.status).isEqualTo("SUCCESS");
        assertThat(mapper.offset).isEqualTo(40);
        assertThat(mapper.pageSize).isEqualTo(20);
    }

    @Test
    void blankFiltersBecomeNull() {
        repository.countTasks(" ", "");

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

    private static class RecordingMapper implements ExcelAdminMapper {
        private String taskType;
        private String status;
        private int offset;
        private int pageSize;

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
            task.setId(99L);
            return 1;
        }

        @Override
        public int insertError(Long taskId, int rowIndex, String errorMessage, String rawData) {
            return 1;
        }

        @Override
        public List<ExcelAdminModels.ErrorRecord> listErrors(Long taskId) {
            return List.of();
        }
    }
}
