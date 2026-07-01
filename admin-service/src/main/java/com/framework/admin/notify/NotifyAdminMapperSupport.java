package com.framework.admin.notify;

import com.framework.admin.support.AdminTextSupport;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class NotifyAdminMapperSupport {

    private NotifyAdminMapperSupport() {
    }

    static List<NotifyAdminModels.Template> listTemplates(NotifyAdminMapper mapper,
                                                          String keyword,
                                                          String channel,
                                                          String status,
                                                          int pageNum,
                                                          int pageSize) {
        return mapper.listTemplates(like(keyword), normalize(channel), normalize(status),
                        offset(pageNum, pageSize), pageSize)
                .stream()
                .map(NotifyAdminMapperSupport::toTemplate)
                .toList();
    }

    static long countTemplates(NotifyAdminMapper mapper, String keyword, String channel, String status) {
        return mapper.countTemplates(like(keyword), normalize(channel), normalize(status));
    }

    static Optional<NotifyAdminModels.Template> findTemplate(NotifyAdminMapper mapper, Long id) {
        return Optional.ofNullable(mapper.findTemplate(id)).map(NotifyAdminMapperSupport::toTemplate);
    }

    static Long createTemplate(NotifyAdminMapper mapper, NotifyAdminModels.TemplateRequest request) {
        NotifyAdminMapper.TemplateRow row = toTemplateRow(request);
        mapper.insertTemplate(row);
        return row.getId();
    }

    static boolean updateTemplate(NotifyAdminMapper mapper, Long id, NotifyAdminModels.TemplateRequest request) {
        return mapper.updateTemplate(toTemplateRow(request).setId(id)) > 0;
    }

    static boolean deleteTemplate(NotifyAdminMapper mapper, Long id) {
        return mapper.deleteTemplate(id) > 0;
    }

    static Long createRecord(NotifyAdminMapper mapper, NotifyAdminModels.Record record) {
        NotifyAdminMapper.RecordRow row = toRecordRow(record);
        mapper.insertRecord(row);
        return row.getId();
    }

    static List<NotifyAdminModels.Record> listRecords(NotifyAdminMapper mapper,
                                                      String channel,
                                                      Boolean success,
                                                      int pageNum,
                                                      int pageSize) {
        return mapper.listRecords(normalize(channel), success, offset(pageNum, pageSize), pageSize)
                .stream()
                .map(NotifyAdminMapperSupport::toRecord)
                .toList();
    }

    static long countRecords(NotifyAdminMapper mapper, String channel, Boolean success) {
        return mapper.countRecords(normalize(channel), success);
    }

    static long countRecordsBySuccess(NotifyAdminMapper mapper, boolean success) {
        return mapper.countRecordsBySuccess(success);
    }

    static long countTemplatesByStatus(NotifyAdminMapper mapper, String status) {
        return mapper.countTemplatesByStatus(normalize(status));
    }

    static NotifyAdminMapper.TemplateRow toTemplateRow(NotifyAdminModels.TemplateRequest request) {
        return new NotifyAdminMapper.TemplateRow()
                .setTemplateCode(text(request.getTemplateCode()))
                .setTemplateName(text(request.getTemplateName()))
                .setChannel(normalize(request.getChannel()))
                .setTitle(text(request.getTitle()))
                .setContent(text(request.getContent()))
                .setReceivers(join(request.getReceivers()))
                .setWebhookUrl(text(request.getWebhookUrl()))
                .setStatus(defaultStatus(request.getStatus()));
    }

    static NotifyAdminMapper.RecordRow toRecordRow(NotifyAdminModels.Record record) {
        return new NotifyAdminMapper.RecordRow()
                .setTemplateCode(record.getTemplateCode())
                .setChannel(record.getChannel())
                .setTitle(record.getTitle())
                .setContent(record.getContent())
                .setReceivers(join(record.getReceivers()))
                .setWebhookUrl(record.getWebhookUrl())
                .setSuccess(Boolean.TRUE.equals(record.getSuccess()))
                .setResultMessage(record.getResultMessage())
                .setTraceId(record.getTraceId())
                .setOperatorName(record.getOperatorName());
    }

    static NotifyAdminModels.Template toTemplate(NotifyAdminMapper.TemplateRow row) {
        return new NotifyAdminModels.Template()
                .setId(row.getId())
                .setTemplateCode(row.getTemplateCode())
                .setTemplateName(row.getTemplateName())
                .setChannel(row.getChannel())
                .setTitle(row.getTitle())
                .setContent(row.getContent())
                .setReceivers(split(row.getReceivers()))
                .setWebhookUrl(row.getWebhookUrl())
                .setStatus(row.getStatus())
                .setCreateTime(row.getCreateTime())
                .setUpdateTime(row.getUpdateTime());
    }

    static NotifyAdminModels.Record toRecord(NotifyAdminMapper.RecordRow row) {
        return new NotifyAdminModels.Record()
                .setId(row.getId())
                .setTemplateCode(row.getTemplateCode())
                .setChannel(row.getChannel())
                .setTitle(row.getTitle())
                .setContent(row.getContent())
                .setReceivers(split(row.getReceivers()))
                .setWebhookUrl(row.getWebhookUrl())
                .setSuccess(row.getSuccess())
                .setResultMessage(row.getResultMessage())
                .setTraceId(row.getTraceId())
                .setOperatorName(row.getOperatorName())
                .setCreateTime(row.getCreateTime());
    }

    private static int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * pageSize;
    }

    private static String like(String value) {
        String text = text(value);
        return text == null ? null : "%" + text + "%";
    }

    private static String text(String value) {
        return AdminTextSupport.trimToNull(value);
    }

    private static String normalize(String value) {
        String text = text(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private static String defaultStatus(String status) {
        String text = normalize(status);
        return text == null ? "ENABLED" : text;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalized = values.stream()
                .filter(AdminTextSupport::hasText)
                .map(AdminTextSupport::trimBoundarySpace)
                .toList();
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private static List<String> split(String value) {
        if (!AdminTextSupport.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(AdminTextSupport::trimBoundarySpace)
                .filter(AdminTextSupport::hasText)
                .toList();
    }
}
