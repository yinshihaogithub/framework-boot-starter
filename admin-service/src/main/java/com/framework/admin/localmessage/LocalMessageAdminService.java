package com.framework.admin.localmessage;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.repository.LocalMessageRepository;
import com.framework.localmessage.service.LocalMessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LocalMessageAdminService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

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
        LocalMessageService service = localMessageServiceProvider.getIfAvailable();
        if (service == null) {
            return stats;
        }
        List<LocalMessage> messages = service.findAll();
        for (LocalMessageStatus status : LocalMessageStatus.values()) {
            stats.put(status.name(), messages.stream().filter(message -> status == message.getStatus()).count());
        }
        stats.put("TOTAL", (long) messages.size());
        return stats;
    }

    public PageResult<LocalMessageVO> list(String topic, LocalMessageStatus status, String traceId,
                                           String businessKey, int pageNum, int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        LocalMessageService service = localMessageServiceProvider.getIfAvailable();
        if (service == null) {
            return PageResult.empty(safePageNum, safePageSize);
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
        return PageResult.of(records, total, safePageNum, safePageSize);
    }

    public ActionResult<LocalMessageVO> detail(Long id) {
        LocalMessageService service = localMessageServiceProvider.getIfAvailable();
        if (service == null) {
            return ActionResult.fail("本地消息服务未启用");
        }
        return service.findById(id)
                .map(LocalMessageVO::from)
                .map(ActionResult::success)
                .orElseGet(() -> ActionResult.fail("消息不存在"));
    }

    public ActionResult<Integer> retryDueMessages(HttpServletRequest servletRequest) {
        LocalMessageService service = localMessageServiceProvider.getIfAvailable();
        if (service == null) {
            return ActionResult.fail("本地消息服务未启用");
        }
        int count = service.retryDueMessages();
        auditService.success(servletRequest, "本地消息", "扫描并重试到期本地消息", "UPDATE",
                auditService.params("count", count));
        return ActionResult.success(count);
    }

    public ActionResult<String> markSuccess(Long id, HttpServletRequest servletRequest) {
        LocalMessageRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return ActionResult.fail("本地消息仓储未启用");
        }
        LocalMessage message = repository.findById(id).orElse(null);
        if (message == null) {
            return ActionResult.fail("消息不存在");
        }
        message.setStatus(LocalMessageStatus.SUCCESS);
        message.setErrorMessage(null);
        message.setNextRetryTime(null);
        repository.save(message);
        auditService.success(servletRequest, "本地消息", "人工标记本地消息成功", "UPDATE",
                auditService.params("id", id, "messageId", message.getMessageId(), "traceId", message.getTraceId()));
        return ActionResult.success("已标记成功");
    }

    public ActionResult<String> markFailure(Long id, String reason, HttpServletRequest servletRequest) {
        LocalMessageRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return ActionResult.fail("本地消息仓储未启用");
        }
        LocalMessage message = repository.findById(id).orElse(null);
        if (message == null) {
            return ActionResult.fail("消息不存在");
        }
        String safeReason = isBlank(reason) ? "manual terminate" : reason;
        message.setStatus(LocalMessageStatus.FAILED);
        message.setErrorMessage(safeReason);
        message.setNextRetryTime(null);
        repository.save(message);
        auditService.success(servletRequest, "本地消息", "人工标记本地消息失败", "UPDATE",
                auditService.params("id", id, "messageId", message.getMessageId(), "traceId", message.getTraceId(),
                        "reason", safeReason));
        return ActionResult.success("已标记失败");
    }

    public ActionResult<String> delete(Long id, HttpServletRequest servletRequest) {
        LocalMessageRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return ActionResult.fail("本地消息仓储未启用");
        }
        repository.delete(id);
        auditService.success(servletRequest, "本地消息", "删除本地消息", "DELETE",
                auditService.params("id", id));
        return ActionResult.success("已删除");
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

    public record ActionResult<T>(boolean success, String message, T data) {
        public static <T> ActionResult<T> success(T data) {
            return new ActionResult<>(true, null, data);
        }

        public static <T> ActionResult<T> fail(String message) {
            return new ActionResult<>(false, message, null);
        }
    }
}
