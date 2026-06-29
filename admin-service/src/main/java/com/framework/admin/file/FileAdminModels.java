package com.framework.admin.file;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * DTOs used by file admin APIs.
 */
public class FileAdminModels {

    @Data
    @Accessors(chain = true)
    public static class FileRecord {
        private Long id;
        private String fileKey;
        private String originalFilename;
        private String contentType;
        private Long fileSize;
        private String url;
        private String storageType;
        private String businessType;
        private String businessKey;
        private Long operatorId;
        private String operatorName;
        private Boolean deleted;
        private String createTime;
        private String updateTime;
    }
}
