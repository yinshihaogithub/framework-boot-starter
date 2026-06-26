package com.framework.admin.codegen;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

public class CodegenModels {

    @Data
    @Accessors(chain = true)
    public static class TableInfo {
        private String tableName;
        private String tableComment;
        private Long tableRows;
        private String engine;
        private String createTime;
        private String updateTime;
    }

    @Data
    @Accessors(chain = true)
    public static class ColumnInfo {
        private String columnName;
        private String columnType;
        private String dataType;
        private String columnComment;
        private Boolean nullable;
        private Boolean primaryKey;
        private Boolean autoIncrement;
        private String columnDefault;
        private Integer ordinalPosition;
        private String javaType;
        private String javaField;
        private String tsType;
    }

    @Data
    public static class PreviewRequest {
        private String tableName;
        private String packageName;
        private String moduleName;
        private String entityName;
        private String author;
    }

    @Data
    @Accessors(chain = true)
    public static class PreviewResponse {
        private TableInfo table;
        private List<ColumnInfo> columns;
        private List<GeneratedFile> files;
    }

    @Data
    @Accessors(chain = true)
    public static class GeneratedFile {
        private String fileName;
        private String filePath;
        private String language;
        private String content;
    }
}
