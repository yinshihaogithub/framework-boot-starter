package com.framework.admin.excel;

import lombok.Data;
import lombok.experimental.Accessors;

public class ExcelAdminModels {

    @Data
    @Accessors(chain = true)
    public static class Task {
        private Long id;
        private String taskName;
        private String taskType;
        private String bizType;
        private String status;
        private String filename;
        private Integer totalRows;
        private Integer successRows;
        private Integer failureRows;
        private String operatorName;
        private String errorMessage;
        private String createTime;
        private String updateTime;
    }

    @Data
    @Accessors(chain = true)
    public static class ErrorRecord {
        private Long id;
        private Long taskId;
        private Integer rowIndex;
        private String errorMessage;
        private String rawData;
        private String createTime;
    }

    @Data
    @Accessors(chain = true)
    public static class TaskResult {
        private Long taskId;
        private String filename;
        private String status;
        private Integer totalRows;
        private Integer successRows;
        private Integer failureRows;
        private Long fileSize;
    }

    @Data
    public static class ExportRequest {
        private String taskName;
        private String bizType;
    }

    @Data
    public static class FailureRequest {
        private String taskName;
        private String bizType;
        private String errorMessage;
    }
}
