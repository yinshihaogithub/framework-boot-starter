package com.framework.admin.notify;

import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
import com.framework.core.result.ResultCode;
import com.framework.core.trace.TraceContext;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import com.framework.notify.service.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class NotifyAdminService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> SUPPORTED_STATUSES = Set.of("ENABLED", "DISABLED");

    private final NotifyAdminRepository repository;
    private final ObjectProvider<NotifyService> notifyServiceProvider;
    private final AdminAuditService auditService;

    public NotifyAdminService(NotifyAdminRepository repository,
                              ObjectProvider<NotifyService> notifyServiceProvider,
                              AdminAuditService auditService) {
        this.repository = repository;
        this.notifyServiceProvider = notifyServiceProvider;
        this.auditService = auditService;
    }

    public Map<String, Long> stats() {
        try {
            Map<String, Long> stats = new LinkedHashMap<>();
            stats.put("enabledTemplates", repository.countTemplatesByStatus("ENABLED"));
            stats.put("disabledTemplates", repository.countTemplatesByStatus("DISABLED"));
            stats.put("successRecords", repository.countRecordsBySuccess(true));
            stats.put("failedRecords", repository.countRecordsBySuccess(false));
            return stats;
        } catch (RuntimeException e) {
            log.warn("[通知中心] 统计查询失败 error={}", e.getMessage());
            return emptyStats();
        }
    }

    public PageResult<NotifyAdminModels.Template> templates(String keyword, String channel, String status,
                                                           int pageNum, int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        try {
            List<NotifyAdminModels.Template> records = repository.listTemplates(
                    keyword, channel, status, safePageNum, safePageSize);
            long total = repository.countTemplates(keyword, channel, status);
            return PageResult.of(records, total, safePageNum, safePageSize);
        } catch (RuntimeException e) {
            log.warn("[通知中心] 模板列表查询失败 error={}", e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public ActionResult<Long> createTemplate(NotifyAdminModels.TemplateRequest request, HttpServletRequest servletRequest) {
        try {
            validateTemplate(request);
            Long id = repository.createTemplate(request);
            auditSuccess(servletRequest, "新增通知模板", "CREATE",
                    "id", id, "templateCode", request.getTemplateCode(), "channel", request.getChannel());
            return ActionResult.success(id);
        } catch (IllegalArgumentException e) {
            return ActionResult.fail(ResultCode.PARAM_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("[通知中心] 新增通知模板失败 error={}", e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "通知模板保存失败");
        }
    }

    public ActionResult<String> updateTemplate(Long id, NotifyAdminModels.TemplateRequest request,
                                               HttpServletRequest servletRequest) {
        ActionResult<String> invalidId = invalidTemplateId(id);
        if (invalidId != null) {
            return invalidId;
        }
        try {
            validateTemplate(request);
            if (repository.findTemplate(id).isEmpty()) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "模板不存在");
            }
            repository.updateTemplate(id, request);
            auditSuccess(servletRequest, "更新通知模板", "UPDATE",
                    "id", id, "templateCode", request.getTemplateCode(), "channel", request.getChannel());
            return ActionResult.success("已更新");
        } catch (IllegalArgumentException e) {
            return ActionResult.fail(ResultCode.PARAM_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("[通知中心] 更新通知模板失败 id={}, error={}", id, e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "通知模板更新失败");
        }
    }

    public ActionResult<String> deleteTemplate(Long id, HttpServletRequest servletRequest) {
        ActionResult<String> invalidId = invalidTemplateId(id);
        if (invalidId != null) {
            return invalidId;
        }
        try {
            if (repository.findTemplate(id).isEmpty()) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "模板不存在");
            }
            repository.deleteTemplate(id);
            auditSuccess(servletRequest, "删除通知模板", "DELETE", "id", id);
            return ActionResult.success("已删除");
        } catch (RuntimeException e) {
            log.warn("[通知中心] 删除通知模板失败 id={}, error={}", id, e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "通知模板删除失败");
        }
    }

    public ActionResult<NotifyAdminModels.Record> sendTest(Long id,
                                                           NotifyAdminModels.SendRequest request,
                                                           HttpServletRequest servletRequest) {
        ActionResult<NotifyAdminModels.Record> invalidId = invalidTemplateId(id);
        if (invalidId != null) {
            return invalidId;
        }
        NotifyAdminModels.Template template;
        try {
            template = repository.findTemplate(id).orElse(null);
        } catch (RuntimeException e) {
            log.warn("[通知中心] 测试发送查询模板失败 id={}, error={}", id, e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "通知模板查询失败");
        }
        if (template == null) {
            return ActionResult.fail(ResultCode.NOT_FOUND, "模板不存在");
        }
        try {
            String renderedContent = render(template.getContent(), request);
            NotifyResult result = "ENABLED".equalsIgnoreCase(template.getStatus())
                    ? send(template, request, renderedContent)
                    : NotifyResult.failure(channel(template.getChannel()), "模板已禁用");
            NotifyAdminModels.Record record = new NotifyAdminModels.Record()
                    .setTemplateCode(template.getTemplateCode())
                    .setChannel(template.getChannel())
                    .setTitle(template.getTitle())
                    .setContent(renderedContent)
                    .setReceivers(resolveReceivers(template, request))
                    .setWebhookUrl(resolveWebhookUrl(template, request))
                    .setSuccess(result.isSuccess())
                    .setResultMessage(result.getMessage())
                    .setTraceId(TraceContext.ensureTraceId())
                    .setOperatorName(UserContextHolder.getUsername());
            Long recordId = repository.createRecord(record);
            record.setId(recordId);
            auditSuccess(servletRequest, "发送测试通知", "CREATE",
                    "templateId", id, "recordId", recordId, "success", record.getSuccess());
            return ActionResult.success(record);
        } catch (IllegalArgumentException e) {
            return ActionResult.fail(ResultCode.PARAM_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("[通知中心] 测试发送失败 templateId={}, error={}", id, e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "通知测试发送失败");
        }
    }

    public PageResult<NotifyAdminModels.Record> records(String channel, Boolean success, int pageNum, int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        try {
            List<NotifyAdminModels.Record> records = repository.listRecords(channel, success, safePageNum, safePageSize);
            long total = repository.countRecords(channel, success);
            return PageResult.of(records, total, safePageNum, safePageSize);
        } catch (RuntimeException e) {
            log.warn("[通知中心] 发送记录查询失败 error={}", e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    private NotifyResult send(NotifyAdminModels.Template template,
                              NotifyAdminModels.SendRequest request,
                              String renderedContent) {
        NotifyChannelType channel = channel(template.getChannel());
        NotifyService notifyService = available(notifyServiceProvider);
        if (notifyService == null) {
            return NotifyResult.failure(channel, "notify service is not enabled");
        }
        NotifyMessage message = new NotifyMessage()
                .setChannel(channel)
                .setTitle(template.getTitle())
                .setContent(renderedContent)
                .setReceivers(resolveReceivers(template, request))
                .setWebhookUrl(resolveWebhookUrl(template, request));
        if (request != null && request.getTemplateParams() != null) {
            message.setTemplateParams(request.getTemplateParams());
        }
        try {
            return notifyService.send(message);
        } catch (Exception e) {
            return NotifyResult.failure(channel, "通知发送失败: " + e.getMessage());
        }
    }

    private String render(String content, NotifyAdminModels.SendRequest request) {
        if (content == null || request == null || request.getTemplateParams() == null) {
            return content;
        }
        String rendered = content;
        for (Map.Entry<String, Object> entry : request.getTemplateParams().entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return rendered;
    }

    private List<String> resolveReceivers(NotifyAdminModels.Template template, NotifyAdminModels.SendRequest request) {
        if (request != null && request.getReceivers() != null && !request.getReceivers().isEmpty()) {
            return request.getReceivers();
        }
        return template.getReceivers();
    }

    private String resolveWebhookUrl(NotifyAdminModels.Template template, NotifyAdminModels.SendRequest request) {
        if (request != null && hasText(request.getWebhookUrl())) {
            return request.getWebhookUrl().trim();
        }
        return template.getWebhookUrl();
    }

    private NotifyChannelType channel(String channel) {
        if (!hasText(channel)) {
            return NotifyChannelType.LOG;
        }
        try {
            return NotifyChannelType.valueOf(channel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("通知通道不支持");
        }
    }

    private void validateTemplate(NotifyAdminModels.TemplateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("模板不能为空");
        }
        if (!hasText(request.getTemplateCode())) {
            throw new IllegalArgumentException("模板编码不能为空");
        }
        if (!hasText(request.getTemplateName())) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        if (!hasText(request.getChannel())) {
            throw new IllegalArgumentException("通知通道不能为空");
        }
        channel(request.getChannel());
        if (!hasText(request.getTitle())) {
            throw new IllegalArgumentException("通知标题不能为空");
        }
        if (!hasText(request.getContent())) {
            throw new IllegalArgumentException("通知内容不能为空");
        }
        if (hasText(request.getStatus())
                && !SUPPORTED_STATUSES.contains(request.getStatus().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("状态只能是 ENABLED 或 DISABLED");
        }
    }

    private int safePageNum(int pageNum) {
        return pageNum > 0 ? pageNum : DEFAULT_PAGE_NUM;
    }

    private int safePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> ActionResult<T> invalidTemplateId(Long id) {
        if (id == null || id <= 0) {
            return ActionResult.fail(ResultCode.PARAM_ERROR, "模板ID必须大于0");
        }
        return null;
    }

    private void auditSuccess(HttpServletRequest request, String action, String operationType, Object... params) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.success(request, "通知中心", action, operationType, auditService.params(params));
        } catch (RuntimeException e) {
            log.warn("[通知中心] 审计日志写入失败 action={}, error={}", action, e.getMessage());
        }
    }

    private Map<String, Long> emptyStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("enabledTemplates", 0L);
        stats.put("disabledTemplates", 0L);
        stats.put("successRecords", 0L);
        stats.put("failedRecords", 0L);
        return stats;
    }

    private <T> T available(ObjectProvider<T> provider) {
        if (provider == null) {
            return null;
        }
        try {
            return provider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("[通知中心] 获取通知发送服务失败 error={}", e.getMessage());
            return null;
        }
    }

    public record ActionResult<T>(boolean success, int code, String message, T data) {

        public static <T> ActionResult<T> success(T data) {
            return new ActionResult<>(true, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
        }

        public static <T> ActionResult<T> fail(ResultCode code, String message) {
            return new ActionResult<>(false, code.getCode(), message, null);
        }
    }
}
