package com.framework.admin.localmessage;

import com.framework.admin.audit.AdminAuditService;
import com.framework.admin.support.AdminPageSupport;
import com.framework.core.result.PageResult;
import com.framework.core.result.ResultCode;
import com.framework.core.trace.TraceContext;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.repository.LocalMessageRepository;
import com.framework.localmessage.service.LocalMessageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class LocalMessageAdminService {

    private final ObjectProvider<LocalMessageService> localMessageServiceProvider;
    private final ObjectProvider<LocalMessageRepository> repositoryProvider;
    private final AdminAuditService auditService;

    public LocalMessageAdminService(ObjectProvider<LocalMessageService> localMessageServiceProvider,
                                    ObjectProvider<LocalMessageRepository> repositoryProvider,
                                    AdminAuditService auditService) {
        this.localMessageServiceProvider = localMessageServiceProvider;
        this.repositoryProvider = repositoryProvider;
        this.auditService = auditService;
    }

    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (LocalMessageStatus status : LocalMessageStatus.values()) {
            stats.put(status.name(), 0L);
        }
        stats.put("TOTAL", 0L);
        LocalMessageService service = available(localMessageServiceProvider);
        if (service == null) {
            return stats;
        }
        try {
            List<LocalMessage> messages = service.findAll();
            for (LocalMessageStatus status : LocalMessageStatus.values()) {
                stats.put(status.name(), messages.stream().filter(message -> status == message.getStatus()).count());
            }
            stats.put("TOTAL", (long) messages.size());
        } catch (Exception ignored) {
            return zero(stats);
        }
        return stats;
    }

    public PageResult<LocalMessageVO> list(String topic, String status, String traceId,
                                           String businessKey, int pageNum, int pageSize) {
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        String safeTopic = trimToNull(topic);
        LocalMessageStatus safeStatus = normalizeStatusFilter(status);
        String safeTraceId = normalizeTraceIdFilter(traceId);
        String safeBusinessKey = trimToNull(businessKey);
        if (isInvalidStatusFilter(status, safeStatus)) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        if (isInvalidTraceIdFilter(traceId, safeTraceId)) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        LocalMessageService service = available(localMessageServiceProvider);
        if (service == null) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        try {
            List<LocalMessage> filtered = service.findAll().stream()
                    .filter(message -> safeTopic == null || safeTopic.equals(message.getTopic()))
                    .filter(message -> safeStatus == null || safeStatus == message.getStatus())
                    .filter(message -> safeTraceId == null || contains(message.getTraceId(), safeTraceId))
                    .filter(message -> safeBusinessKey == null || contains(message.getBusinessKey(), safeBusinessKey))
                    .sorted(Comparator.comparing(LocalMessage::getCreateTime, newestFirst()))
                    .toList();
            int total = filtered.size();
            long offset = (long) (safePageNum - 1) * safePageSize;
            int start = offset < total ? (int) offset : total;
            int end = Math.min(start + safePageSize, total);
            List<LocalMessageVO> records = start < total
                    ? filtered.subList(start, end).stream().map(LocalMessageVO::from).toList()
                    : List.of();
            return PageResult.of(records, total, safePageNum, safePageSize);
        } catch (Exception ignored) {
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public ActionResult<LocalMessageVO> detail(Long id) {
        ActionResult<LocalMessageVO> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        LocalMessageService service = available(localMessageServiceProvider);
        if (service == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息服务未启用");
        }
        try {
            return service.findById(id)
                    .map(LocalMessageVO::from)
                    .map(ActionResult::success)
                    .orElseGet(() -> ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在"));
        } catch (Exception ignored) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息查询失败");
        }
    }

    public ActionResult<Integer> retryDueMessages(HttpServletRequest servletRequest) {
        LocalMessageService service = available(localMessageServiceProvider);
        if (service == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息服务未启用");
        }
        try {
            int count = service.retryDueMessages();
            auditSuccess(servletRequest, "扫描并重试到期本地消息", "UPDATE", "count", count);
            return ActionResult.success(count);
        } catch (Exception ignored) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息重试失败");
        }
    }

    public ActionResult<String> retryNow(Long id, HttpServletRequest servletRequest) {
        ActionResult<String> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        LocalMessageRepository repository = available(repositoryProvider);
        if (repository == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息仓储未启用");
        }
        try {
            LocalMessage message = repository.findById(id).orElse(null);
            if (message == null) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            message.setStatus(LocalMessageStatus.PENDING);
            message.setRetryCount(0);
            message.setErrorMessage(null);
            message.setNextRetryTime(LocalDateTime.now());
            repository.save(message);
            auditSuccess(servletRequest, "人工立即重试本地消息", "UPDATE",
                    "id", id, "messageId", message.getMessageId(),
                    "traceId", message.getTraceId(), "status", message.getStatus());
            return ActionResult.success("已加入重试队列");
        } catch (Exception ignored) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息操作失败");
        }
    }

    public ActionResult<String> markSuccess(Long id, HttpServletRequest servletRequest) {
        ActionResult<String> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        LocalMessageRepository repository = available(repositoryProvider);
        if (repository == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息仓储未启用");
        }
        try {
            LocalMessage message = repository.findById(id).orElse(null);
            if (message == null) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            message.setStatus(LocalMessageStatus.SUCCESS);
            message.setErrorMessage(null);
            message.setNextRetryTime(null);
            repository.save(message);
            auditSuccess(servletRequest, "人工标记本地消息成功", "UPDATE",
                    "id", id, "messageId", message.getMessageId(), "traceId", message.getTraceId());
            return ActionResult.success("已标记成功");
        } catch (Exception ignored) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息操作失败");
        }
    }

    public ActionResult<String> markFailure(Long id, String reason, HttpServletRequest servletRequest) {
        ActionResult<String> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        LocalMessageRepository repository = available(repositoryProvider);
        if (repository == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息仓储未启用");
        }
        try {
            LocalMessage message = repository.findById(id).orElse(null);
            if (message == null) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            String safeReason = trimToNull(reason);
            if (safeReason == null) {
                safeReason = "manual terminate";
            }
            message.setStatus(LocalMessageStatus.FAILED);
            message.setRetryCount(0);
            message.setErrorMessage(safeReason);
            message.setNextRetryTime(null);
            repository.save(message);
            auditSuccess(servletRequest, "人工标记本地消息失败", "UPDATE",
                    "id", id, "messageId", message.getMessageId(), "traceId", message.getTraceId(),
                    "reason", safeReason);
            return ActionResult.success("已标记失败");
        } catch (Exception ignored) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息操作失败");
        }
    }

    public ActionResult<String> delete(Long id, HttpServletRequest servletRequest) {
        ActionResult<String> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        LocalMessageRepository repository = available(repositoryProvider);
        if (repository == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息仓储未启用");
        }
        try {
            repository.delete(id);
            auditSuccess(servletRequest, "删除本地消息", "DELETE", "id", id);
            return ActionResult.success("已删除");
        } catch (Exception ignored) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息操作失败");
        }
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeTraceIdFilter(String traceId) {
        String trimmedTraceId = trimToNull(traceId);
        return trimmedTraceId == null ? null : TraceContext.normalizeTraceId(trimmedTraceId);
    }

    private LocalMessageStatus normalizeStatusFilter(String status) {
        String trimmedStatus = trimToNull(status);
        if (trimmedStatus == null) {
            return null;
        }
        try {
            return LocalMessageStatus.valueOf(trimmedStatus.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isInvalidStatusFilter(String originalStatus, LocalMessageStatus normalizedStatus) {
        return !isBlank(originalStatus) && normalizedStatus == null;
    }

    private boolean isInvalidTraceIdFilter(String originalTraceId, String normalizedTraceId) {
        return !isBlank(originalTraceId) && normalizedTraceId == null;
    }

    private <T extends Comparable<? super T>> Comparator<T> newestFirst() {
        return Comparator.nullsLast(Comparator.reverseOrder());
    }

    private Map<String, Long> zero(Map<String, Long> stats) {
        stats.replaceAll((key, value) -> 0L);
        return stats;
    }

    private <T> ActionResult<T> invalidIdResult(Long id) {
        if (id == null || id <= 0) {
            return ActionResult.fail(ResultCode.PARAM_ERROR, "本地消息ID必须大于0");
        }
        return null;
    }

    private void auditSuccess(HttpServletRequest servletRequest, String action, String operationType, Object... params) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.success(servletRequest, "本地消息", action, operationType, auditService.params(params));
        } catch (RuntimeException e) {
            log.warn("[本地消息] 审计日志写入失败 action={}, error={}", action, e.getMessage());
        }
    }

    private <T> T available(ObjectProvider<T> provider) {
        if (provider == null) {
            return null;
        }
        try {
            return provider.getIfAvailable();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record ActionResult<T>(boolean success, int code, String message, T data) {
        public static <T> ActionResult<T> success(T data) {
            return new ActionResult<>(true, ResultCode.SUCCESS.getCode(), null, data);
        }

        public static <T> ActionResult<T> fail(ResultCode code, String message) {
            return new ActionResult<>(false, code.getCode(), message, null);
        }
    }
}
