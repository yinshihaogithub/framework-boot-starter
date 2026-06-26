package com.framework.admin.notify;

import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.trace.TraceContext;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import com.framework.notify.service.NotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/notify")
@Tag(name = "通知中心", description = "通知模板、发送测试和发送记录")
public class NotifyAdminController {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final NotifyAdminRepository repository;
    private final ObjectProvider<NotifyService> notifyServiceProvider;
    private final AdminAuditService auditService;

    public NotifyAdminController(NotifyAdminRepository repository,
                                 ObjectProvider<NotifyService> notifyServiceProvider,
                                 AdminAuditService auditService) {
        this.repository = repository;
        this.notifyServiceProvider = notifyServiceProvider;
        this.auditService = auditService;
    }

    @Operation(summary = "通知统计")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("enabledTemplates", repository.countTemplatesByStatus("ENABLED"));
        stats.put("disabledTemplates", repository.countTemplatesByStatus("DISABLED"));
        stats.put("successRecords", repository.countRecordsBySuccess(true));
        stats.put("failedRecords", repository.countRecordsBySuccess(false));
        return Result.success(stats);
    }

    @Operation(summary = "通知模板列表")
    @GetMapping("/templates")
    public Result<PageResult<NotifyAdminModels.Template>> templates(@RequestParam(required = false) String keyword,
                                                                    @RequestParam(required = false) String channel,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(defaultValue = "1") int pageNum,
                                                                    @RequestParam(defaultValue = "20") int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<NotifyAdminModels.Template> records = repository.listTemplates(
                keyword, channel, status, safePageNum, safePageSize);
        long total = repository.countTemplates(keyword, channel, status);
        return Result.success(PageResult.of(records, total, safePageNum, safePageSize));
    }

    @Operation(summary = "新增通知模板")
    @PostMapping("/templates")
    public Result<Long> createTemplate(@RequestBody NotifyAdminModels.TemplateRequest request,
                                       HttpServletRequest servletRequest) {
        validateTemplate(request);
        Long id = repository.createTemplate(request);
        auditService.success(servletRequest, "通知中心", "新增通知模板", "CREATE",
                auditService.params("id", id, "templateCode", request.getTemplateCode(), "channel", request.getChannel()));
        return Result.success(id);
    }

    @Operation(summary = "更新通知模板")
    @PutMapping("/templates/{id}")
    public Result<String> updateTemplate(@PathVariable Long id,
                                         @RequestBody NotifyAdminModels.TemplateRequest request,
                                         HttpServletRequest servletRequest) {
        validateTemplate(request);
        if (repository.findTemplate(id).isEmpty()) {
            return Result.fail("模板不存在");
        }
        repository.updateTemplate(id, request);
        auditService.success(servletRequest, "通知中心", "更新通知模板", "UPDATE",
                auditService.params("id", id, "templateCode", request.getTemplateCode(), "channel", request.getChannel()));
        return Result.success("已更新");
    }

    @Operation(summary = "删除通知模板")
    @DeleteMapping("/templates/{id}")
    public Result<String> deleteTemplate(@PathVariable Long id, HttpServletRequest servletRequest) {
        repository.deleteTemplate(id);
        auditService.success(servletRequest, "通知中心", "删除通知模板", "DELETE",
                auditService.params("id", id));
        return Result.success("已删除");
    }

    @Operation(summary = "发送测试通知")
    @PostMapping("/templates/{id}/send-test")
    public Result<NotifyAdminModels.Record> sendTest(@PathVariable Long id,
                                                     @RequestBody(required = false) NotifyAdminModels.SendRequest request,
                                                     HttpServletRequest servletRequest) {
        NotifyAdminModels.Template template = repository.findTemplate(id).orElse(null);
        if (template == null) {
            return Result.fail("模板不存在");
        }
        String renderedContent = render(template.getContent(), request);
        NotifyResult result = send(template, request);
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
        auditService.success(servletRequest, "通知中心", "发送测试通知", "CREATE",
                auditService.params("templateId", id, "recordId", recordId, "success", record.getSuccess()));
        return Result.success(record);
    }

    @Operation(summary = "通知发送记录")
    @GetMapping("/records")
    public Result<PageResult<NotifyAdminModels.Record>> records(@RequestParam(required = false) String channel,
                                                                @RequestParam(required = false) Boolean success,
                                                                @RequestParam(defaultValue = "1") int pageNum,
                                                                @RequestParam(defaultValue = "20") int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<NotifyAdminModels.Record> records = repository.listRecords(channel, success, safePageNum, safePageSize);
        long total = repository.countRecords(channel, success);
        return Result.success(PageResult.of(records, total, safePageNum, safePageSize));
    }

    private NotifyResult send(NotifyAdminModels.Template template, NotifyAdminModels.SendRequest request) {
        NotifyService notifyService = notifyServiceProvider.getIfAvailable();
        if (notifyService == null) {
            return NotifyResult.failure(channel(template.getChannel()), "notify service is not enabled");
        }
        NotifyMessage message = new NotifyMessage()
                .setChannel(channel(template.getChannel()))
                .setTitle(template.getTitle())
                .setContent(render(template.getContent(), request))
                .setReceivers(resolveReceivers(template, request))
                .setWebhookUrl(resolveWebhookUrl(template, request));
        if (request != null && request.getTemplateParams() != null) {
            message.setTemplateParams(request.getTemplateParams());
        }
        return notifyService.send(message);
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
        return NotifyChannelType.valueOf(channel.trim());
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
}
