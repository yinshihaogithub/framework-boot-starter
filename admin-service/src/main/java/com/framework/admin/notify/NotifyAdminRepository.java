package com.framework.admin.notify;

import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class NotifyAdminRepository {

    private final NotifyAdminMapper mapper;

    public NotifyAdminRepository(NotifyAdminMapper mapper) {
        this.mapper = mapper;
    }

    public List<NotifyAdminModels.Template> listTemplates(String keyword, String channel, String status,
                                                          int pageNum, int pageSize) {
        return mapper.listTemplates(like(keyword), normalize(channel), normalize(status),
                        offset(pageNum, pageSize), pageSize)
                .stream()
                .map(this::toTemplate)
                .toList();
    }

    public long countTemplates(String keyword, String channel, String status) {
        return mapper.countTemplates(like(keyword), normalize(channel), normalize(status));
    }

    public Optional<NotifyAdminModels.Template> findTemplate(Long id) {
        return Optional.ofNullable(mapper.findTemplate(id)).map(this::toTemplate);
    }

    public Long createTemplate(NotifyAdminModels.TemplateRequest request) {
        NotifyAdminMapper.TemplateRow row = toTemplateRow(request);
        mapper.insertTemplate(row);
        return row.getId();
    }

    public boolean updateTemplate(Long id, NotifyAdminModels.TemplateRequest request) {
        return mapper.updateTemplate(toTemplateRow(request).setId(id)) > 0;
    }

    public boolean deleteTemplate(Long id) {
        return mapper.deleteTemplate(id) > 0;
    }

    public Long createRecord(NotifyAdminModels.Record record) {
        NotifyAdminMapper.RecordRow row = toRecordRow(record);
        mapper.insertRecord(row);
        return row.getId();
    }

    public List<NotifyAdminModels.Record> listRecords(String channel, Boolean success, int pageNum, int pageSize) {
        return mapper.listRecords(normalize(channel), success, offset(pageNum, pageSize), pageSize)
                .stream()
                .map(this::toRecord)
                .toList();
    }

    public long countRecords(String channel, Boolean success) {
        return mapper.countRecords(normalize(channel), success);
    }

    public long countRecordsBySuccess(boolean success) {
        return mapper.countRecordsBySuccess(success);
    }

    public long countTemplatesByStatus(String status) {
        return mapper.countTemplatesByStatus(status);
    }

    private NotifyAdminMapper.TemplateRow toTemplateRow(NotifyAdminModels.TemplateRequest request) {
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

    private NotifyAdminMapper.RecordRow toRecordRow(NotifyAdminModels.Record record) {
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

    private NotifyAdminModels.Template toTemplate(NotifyAdminMapper.TemplateRow row) {
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

    private NotifyAdminModels.Record toRecord(NotifyAdminMapper.RecordRow row) {
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
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
        return String.join(",", values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList());
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

}
