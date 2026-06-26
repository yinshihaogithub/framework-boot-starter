package com.framework.admin.localmessage;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.repository.LocalMessageRepository;
import com.framework.localmessage.service.LocalMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地消息表后台管理接口。
 */
@RestController
@RequestMapping("/admin/local-messages")
@Tag(name = "本地消息管理", description = "本地消息表与人工补偿")
public class LocalMessageAdminController {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ObjectProvider<LocalMessageService> localMessageServiceProvider;
    private final ObjectProvider<LocalMessageRepository> repositoryProvider;
    private final AdminAuditService auditService;

    public LocalMessageAdminController(ObjectProvider<LocalMessageService> localMessageServiceProvider,
                                       ObjectProvider<LocalMessageRepository> repositoryProvider,
                                       AdminAuditService auditService) {
        this.localMessageServiceProvider = localMessageServiceProvider;
        this.repositoryProvider = repositoryProvider;
        this.auditService = auditService;
    }

    @Operation(summary = "本地消息统计")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (LocalMessageStatus status : LocalMessageStatus.values()) {
            stats.put(status.name(), 0L);
        }
        stats.put("TOTAL", 0L);
        LocalMessageService service = localMessageServiceProvider.getIfAvailable();
        if (service == null) {
            return Result.success(stats);
        }
        List<LocalMessage> messages = service.findAll();
        for (LocalMessageStatus status : LocalMessageStatus.values()) {
            stats.put(status.name(), messages.stream().filter(message -> status == message.getStatus()).count());
        }
        stats.put("TOTAL", (long) messages.size());
        return Result.success(stats);
    }

    @Operation(summary = "本地消息列表")
    @GetMapping
    public Result<PageResult<LocalMessageVO>> list(@RequestParam(required = false) String topic,
                                                   @RequestParam(required = false) LocalMessageStatus status,
                                                   @RequestParam(required = false) String traceId,
                                                   @RequestParam(required = false) String businessKey,
                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        LocalMessageService service = localMessageServiceProvider.getIfAvailable();
        if (service == null) {
            return Result.success(PageResult.empty(safePageNum, safePageSize));
        }
        List<LocalMessage> filtered = service.findAll().stream()
                .filter(message -> isBlank(topic) || topic.equals(message.getTopic()))
                .filter(message -> status == null || status == message.getStatus())
                .filter(message -> isBlank(traceId) || contains(message.getTraceId(), traceId))
                .filter(message -> isBlank(businessKey) || contains(message.getBusinessKey(), businessKey))
                .sorted(Comparator.comparing(LocalMessage::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        int total = filtered.size();
        long offset = (long) (safePageNum - 1) * safePageSize;
        int start = offset < total ? (int) offset : total;
        int end = Math.min(start + safePageSize, total);
        List<LocalMessageVO> records = start < total
                ? filtered.subList(start, end).stream().map(LocalMessageVO::from).toList()
                : List.of();
        return Result.success(PageResult.of(records, total, safePageNum, safePageSize));
    }

    @Operation(summary = "本地消息详情")
    @GetMapping("/{id}")
    public Result<LocalMessageVO> detail(@PathVariable Long id) {
        LocalMessageService service = localMessageServiceProvider.getIfAvailable();
        if (service == null) {
            return Result.fail("本地消息服务未启用");
        }
        return service.findById(id).map(LocalMessageVO::from).map(Result::success).orElseGet(() -> Result.fail("消息不存在"));
    }

    @Operation(summary = "扫描并重试到期消息")
    @PostMapping("/retry-due")
    public Result<Integer> retryDueMessages(HttpServletRequest servletRequest) {
        LocalMessageService service = localMessageServiceProvider.getIfAvailable();
        if (service == null) {
            return Result.fail("本地消息服务未启用");
        }
        int count = service.retryDueMessages();
        auditService.success(servletRequest, "本地消息", "扫描并重试到期本地消息", "UPDATE",
                auditService.params("count", count));
        return Result.success(count);
    }

    @Operation(summary = "标记成功")
    @PostMapping("/{id}/success")
    public Result<String> markSuccess(@PathVariable Long id, HttpServletRequest servletRequest) {
        LocalMessageRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return Result.fail("本地消息仓储未启用");
        }
        LocalMessage message = repository.findById(id).orElse(null);
        if (message == null) {
            return Result.fail("消息不存在");
        }
        message.setStatus(LocalMessageStatus.SUCCESS);
        message.setErrorMessage(null);
        message.setNextRetryTime(null);
        repository.save(message);
        auditService.success(servletRequest, "本地消息", "人工标记本地消息成功", "UPDATE",
                auditService.params("id", id, "messageId", message.getMessageId(), "traceId", message.getTraceId()));
        return Result.success("已标记成功");
    }

    @Operation(summary = "标记失败")
    @PostMapping("/{id}/failure")
    public Result<String> markFailure(@PathVariable Long id,
                                      @RequestBody(required = false) FailureRequest request,
                                      HttpServletRequest servletRequest) {
        LocalMessageRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return Result.fail("本地消息仓储未启用");
        }
        LocalMessage message = repository.findById(id).orElse(null);
        if (message == null) {
            return Result.fail("消息不存在");
        }
        String reason = request == null || isBlank(request.getReason()) ? "manual terminate" : request.getReason();
        message.setStatus(LocalMessageStatus.FAILED);
        message.setErrorMessage(reason);
        message.setNextRetryTime(null);
        repository.save(message);
        auditService.success(servletRequest, "本地消息", "人工标记本地消息失败", "UPDATE",
                auditService.params("id", id, "messageId", message.getMessageId(), "traceId", message.getTraceId(),
                        "reason", reason));
        return Result.success("已标记失败");
    }

    @Operation(summary = "删除本地消息")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest servletRequest) {
        LocalMessageRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return Result.fail("本地消息仓储未启用");
        }
        repository.delete(id);
        auditService.success(servletRequest, "本地消息", "删除本地消息", "DELETE",
                auditService.params("id", id));
        return Result.success("已删除");
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

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Data
    public static class FailureRequest {
        private String reason;
    }
}
