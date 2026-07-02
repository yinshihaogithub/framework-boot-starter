package com.framework.admin.localmessage;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 本地消息后台管理请求与结果模型。
 */
public class LocalMessageAdminDTO {

    @Data
    public static class BatchActionRequest {
        private List<Long> ids;
    }

    @Data
    public static class BatchFailureRequest {
        private List<Long> ids;
        private String reason;
    }

    @Data
    @Accessors(chain = true)
    public static class BatchActionResult {
        private int total;
        private int success;
        private int failed;
        private List<String> failedMessages;
    }
}
