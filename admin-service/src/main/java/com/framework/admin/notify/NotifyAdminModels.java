package com.framework.admin.notify;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

public class NotifyAdminModels {

    @Data
    @Accessors(chain = true)
    public static class Template {
        private Long id;
        private String templateCode;
        private String templateName;
        private String channel;
        private String title;
        private String content;
        private List<String> receivers;
        private String webhookUrl;
        private String status;
        private String createTime;
        private String updateTime;
    }

    @Data
    @Accessors(chain = true)
    public static class Record {
        private Long id;
        private String templateCode;
        private String channel;
        private String title;
        private String content;
        private List<String> receivers;
        private String webhookUrl;
        private Boolean success;
        private String resultMessage;
        private String traceId;
        private String operatorName;
        private String createTime;
    }

    @Data
    public static class TemplateRequest {
        private String templateCode;
        private String templateName;
        private String channel;
        private String title;
        private String content;
        private List<String> receivers;
        private String webhookUrl;
        private String status;
    }

    @Data
    public static class SendRequest {
        private List<String> receivers;
        private String webhookUrl;
        private Map<String, Object> templateParams;
    }
}
