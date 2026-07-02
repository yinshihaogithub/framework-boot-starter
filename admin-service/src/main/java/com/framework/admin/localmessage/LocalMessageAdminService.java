package com.framework.admin.localmessage;

import com.framework.admin.audit.AdminAuditService;
import com.framework.admin.support.AdminPageSupport;
import com.framework.admin.support.AdminTextSupport;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
import com.framework.core.result.ResultCode;
import com.framework.core.trace.TraceContext;
import com.framework.localmessage.config.LocalMessageProperties;
import com.framework.localmessage.mapper.LocalMessageMapper;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.service.LocalMessageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class LocalMessageAdminService {

    private final ObjectProvider<LocalMessageService> localMessageServiceProvider;
    private final ObjectProvider<LocalMessageMapper> mapperProvider;
    private final ObjectProvider<LocalMessageProperties> propertiesProvider;
    private final AdminAuditService auditService;

    public LocalMessageAdminService(ObjectProvider<LocalMessageService> localMessageServiceProvider,
                                    ObjectProvider<LocalMessageMapper> mapperProvider,
                                    ObjectProvider<LocalMessageProperties> propertiesProvider,
                                    AdminAuditService auditService) {
        this.localMessageServiceProvider = localMessageServiceProvider;
        this.mapperProvider = mapperProvider;
        this.propertiesProvider = propertiesProvider;
        this.auditService = auditService;
    }

    public Map<String, Long> stats() {
        Map<String, Long> stats = LocalMessageAdminMapperSupport.zeroStats();
        LocalMessageMapper mapper = available(mapperProvider);
        String tableName = tableName();
        if (mapper == null || tableName == null) {
            return stats;
        }
        try {
            return LocalMessageAdminMapperSupport.stats(mapper, tableName);
        } catch (Exception ignored) {
            return zero(stats);
        }
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
        LocalMessageMapper mapper = available(mapperProvider);
        String tableName = tableName();
        if (mapper == null || tableName == null) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        try {
            List<LocalMessageVO> records = LocalMessageAdminMapperSupport.list(
                            mapper, tableName, safeTopic, safeStatus, safeTraceId, safeBusinessKey,
                            safePageNum, safePageSize)
                    .stream()
                    .map(LocalMessageVO::from)
                    .toList();
            long total = LocalMessageAdminMapperSupport.count(
                    mapper, tableName, safeTopic, safeStatus, safeTraceId, safeBusinessKey);
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
        LocalMessageMapper mapper = available(mapperProvider);
        String tableName = tableName();
        if (mapper == null || tableName == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息表未启用");
        }
        try {
            return LocalMessageAdminMapperSupport.findById(mapper, tableName, id)
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
            String operator = operator();
            int count = service.retryDueMessages();
            auditSuccess(servletRequest, "扫描并重试到期本地消息", "UPDATE",
                    "operator", operator, "count", count);
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
        LocalMessageMapper mapper = available(mapperProvider);
        String tableName = tableName();
        if (mapper == null || tableName == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息表未启用");
        }
        try {
            LocalMessage message = LocalMessageAdminMapperSupport.findById(mapper, tableName, id).orElse(null);
            if (message == null) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            String operator = operator();
            LocalMessage updated = copyForUpdate(message);
            updated.setStatus(LocalMessageStatus.PENDING);
            updated.setRetryCount(0);
            updated.setErrorMessage(null);
            updated.setNextRetryTime(LocalDateTime.now());
            updated.setOperator(operator);
            if (!LocalMessageAdminMapperSupport.update(mapper, tableName, updated)) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            auditSuccess(servletRequest, "人工立即重试本地消息", "UPDATE",
                    "id", id, "messageId", updated.getMessageId(),
                    "traceId", updated.getTraceId(), "status", updated.getStatus(), "operator", operator);
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
        LocalMessageMapper mapper = available(mapperProvider);
        String tableName = tableName();
        if (mapper == null || tableName == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息表未启用");
        }
        try {
            LocalMessage message = LocalMessageAdminMapperSupport.findById(mapper, tableName, id).orElse(null);
            if (message == null) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            String operator = operator();
            LocalMessage updated = copyForUpdate(message);
            updated.setStatus(LocalMessageStatus.SUCCESS);
            updated.setRetryCount(0);
            updated.setErrorMessage(null);
            updated.setNextRetryTime(null);
            updated.setOperator(operator);
            if (!LocalMessageAdminMapperSupport.update(mapper, tableName, updated)) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            auditSuccess(servletRequest, "人工标记本地消息成功", "UPDATE",
                    "id", id, "messageId", updated.getMessageId(),
                    "traceId", updated.getTraceId(), "operator", operator);
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
        LocalMessageMapper mapper = available(mapperProvider);
        String tableName = tableName();
        if (mapper == null || tableName == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息表未启用");
        }
        try {
            LocalMessage message = LocalMessageAdminMapperSupport.findById(mapper, tableName, id).orElse(null);
            if (message == null) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            String safeReason = trimToNull(reason);
            if (safeReason == null) {
                safeReason = "manual terminate";
            }
            String operator = operator();
            LocalMessage updated = copyForUpdate(message);
            updated.setStatus(LocalMessageStatus.FAILED);
            updated.setRetryCount(0);
            updated.setErrorMessage(safeReason);
            updated.setNextRetryTime(null);
            updated.setOperator(operator);
            if (!LocalMessageAdminMapperSupport.update(mapper, tableName, updated)) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            auditSuccess(servletRequest, "人工标记本地消息失败", "UPDATE",
                    "id", id, "messageId", updated.getMessageId(), "traceId", updated.getTraceId(),
                    "operator", operator, "reason", safeReason);
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
        LocalMessageMapper mapper = available(mapperProvider);
        String tableName = tableName();
        if (mapper == null || tableName == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息表未启用");
        }
        try {
            LocalMessage message = LocalMessageAdminMapperSupport.findById(mapper, tableName, id).orElse(null);
            if (message == null) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            String operator = operator();
            if (!LocalMessageAdminMapperSupport.delete(mapper, tableName, id)) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            auditSuccess(servletRequest, "删除本地消息", "DELETE",
                    "id", id, "messageId", message.getMessageId(),
                    "traceId", message.getTraceId(), "status", message.getStatus(), "operator", operator);
            return ActionResult.success("已删除");
        } catch (Exception ignored) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息操作失败");
        }
    }

    public ActionResult<LocalMessageAdminDTO.BatchActionResult> batchRetry(LocalMessageAdminDTO.BatchActionRequest request,
                                                                           HttpServletRequest servletRequest) {
        List<Long> ids = request == null ? null : request.getIds();
        return batchUpdate(ids, servletRequest, "批量立即重试本地消息",
                (message, operator) -> retryMessage(message, operator));
    }

    public ActionResult<LocalMessageAdminDTO.BatchActionResult> batchMarkSuccess(
            LocalMessageAdminDTO.BatchActionRequest request,
            HttpServletRequest servletRequest) {
        List<Long> ids = request == null ? null : request.getIds();
        return batchUpdate(ids, servletRequest, "批量标记本地消息成功",
                (message, operator) -> successMessage(message, operator));
    }

    public ActionResult<LocalMessageAdminDTO.BatchActionResult> batchMarkFailure(
            LocalMessageAdminDTO.BatchFailureRequest request,
            HttpServletRequest servletRequest) {
        List<Long> ids = request == null ? null : request.getIds();
        String reason = defaultFailureReason(request == null ? null : request.getReason());
        return batchUpdate(ids, servletRequest, "批量标记本地消息失败",
                (message, operator) -> failedMessage(message, operator, reason),
                "reason", reason);
    }

    public ActionResult<String> cleanProcessed(HttpServletRequest servletRequest) {
        LocalMessageMapper mapper = available(mapperProvider);
        String tableName = tableName();
        if (mapper == null || tableName == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息表未启用");
        }
        try {
            String operator = operator();
            int cleaned = LocalMessageAdminMapperSupport.deleteByStatus(mapper, tableName, LocalMessageStatus.SUCCESS);
            auditSuccess(servletRequest, "清理本地消息成功记录", "DELETE",
                    "operator", operator, "status", LocalMessageStatus.SUCCESS.name(), "cleaned", cleaned);
            return ActionResult.success("已清理 " + cleaned + " 条记录");
        } catch (Exception ignored) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息清理失败");
        }
    }

    private boolean isBlank(String value) {
        return !AdminTextSupport.hasText(value);
    }

    private String trimToNull(String value) {
        return AdminTextSupport.trimToNull(value);
    }

    private String operator() {
        String username = trimToNull(UserContextHolder.getUsername());
        return username == null ? "admin" : username;
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

    private Map<String, Long> zero(Map<String, Long> stats) {
        stats.replaceAll((key, value) -> 0L);
        return stats;
    }

    private ActionResult<LocalMessageAdminDTO.BatchActionResult> batchUpdate(List<Long> ids,
                                                                             HttpServletRequest servletRequest,
                                                                             String action,
                                                                             BatchUpdater updater,
                                                                             Object... extraAuditParams) {
        if (ids == null || ids.isEmpty()) {
            return ActionResult.fail(ResultCode.PARAM_ERROR, "请选择要处理的消息");
        }
        if (hasInvalidId(ids)) {
            return ActionResult.fail(ResultCode.PARAM_ERROR, "本地消息ID必须大于0");
        }
        LocalMessageMapper mapper = available(mapperProvider);
        String tableName = tableName();
        if (mapper == null || tableName == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "本地消息表未启用");
        }
        List<Long> distinctIds = distinctIds(ids);
        String operator = operator();
        List<String> failedMessages = new ArrayList<>();
        int success = 0;
        for (Long id : distinctIds) {
            try {
                LocalMessage message = LocalMessageAdminMapperSupport.findById(mapper, tableName, id).orElse(null);
                if (message == null) {
                    failedMessages.add(id + ": 消息不存在");
                    continue;
                }
                LocalMessage updated = updater.apply(message, operator);
                if (!LocalMessageAdminMapperSupport.update(mapper, tableName, updated)) {
                    failedMessages.add(id + ": 消息不存在");
                    continue;
                }
                success++;
            } catch (RuntimeException e) {
                failedMessages.add(id + ": " + defaultFailureDetail(e));
            }
        }
        LocalMessageAdminDTO.BatchActionResult result = new LocalMessageAdminDTO.BatchActionResult()
                .setTotal(distinctIds.size())
                .setSuccess(success)
                .setFailed(distinctIds.size() - success)
                .setFailedMessages(failedMessages);
        Object[] auditParams = new Object[] {
                "ids", distinctIds,
                "operator", operator,
                "success", result.getSuccess(),
                "failed", result.getFailed(),
                "failedMessages", failedMessages
        };
        auditSuccess(servletRequest, action, "UPDATE", mergeAuditParams(auditParams, extraAuditParams));
        return ActionResult.success(result);
    }

    private Object[] mergeAuditParams(Object[] base, Object... extra) {
        if (extra == null || extra.length == 0) {
            return base;
        }
        Object[] merged = new Object[base.length + extra.length];
        System.arraycopy(base, 0, merged, 0, base.length);
        System.arraycopy(extra, 0, merged, base.length, extra.length);
        return merged;
    }

    private LocalMessage retryMessage(LocalMessage message, String operator) {
        LocalMessage updated = copyForUpdate(message);
        updated.setStatus(LocalMessageStatus.PENDING);
        updated.setRetryCount(0);
        updated.setErrorMessage(null);
        updated.setNextRetryTime(LocalDateTime.now());
        updated.setOperator(operator);
        return updated;
    }

    private LocalMessage successMessage(LocalMessage message, String operator) {
        LocalMessage updated = copyForUpdate(message);
        updated.setStatus(LocalMessageStatus.SUCCESS);
        updated.setRetryCount(0);
        updated.setErrorMessage(null);
        updated.setNextRetryTime(null);
        updated.setOperator(operator);
        return updated;
    }

    private LocalMessage failedMessage(LocalMessage message, String operator, String reason) {
        LocalMessage updated = copyForUpdate(message);
        updated.setStatus(LocalMessageStatus.FAILED);
        updated.setRetryCount(0);
        updated.setErrorMessage(reason);
        updated.setNextRetryTime(null);
        updated.setOperator(operator);
        return updated;
    }

    private String defaultFailureReason(String reason) {
        String safeReason = trimToNull(reason);
        return safeReason == null ? "manual terminate" : safeReason;
    }

    private String defaultFailureDetail(Exception exception) {
        String message = trimToNull(exception == null ? null : exception.getMessage());
        return message == null ? "操作失败" : message;
    }

    private boolean hasInvalidId(List<Long> ids) {
        return ids.stream().anyMatch(id -> id == null || id <= 0);
    }

    private List<Long> distinctIds(List<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>(ids);
        return List.copyOf(distinct);
    }

    private LocalMessage copyForUpdate(LocalMessage message) {
        return new LocalMessage()
                .setId(message.getId())
                .setMessageId(message.getMessageId())
                .setTraceId(message.getTraceId())
                .setParentMessageId(message.getParentMessageId())
                .setTopic(message.getTopic())
                .setBusinessKey(message.getBusinessKey())
                .setTenantId(message.getTenantId())
                .setOperator(message.getOperator())
                .setSource(message.getSource())
                .setPayload(message.getPayload())
                .setStatus(message.getStatus())
                .setRetryCount(message.getRetryCount())
                .setMaxRetry(message.getMaxRetry())
                .setNextRetryTime(message.getNextRetryTime())
                .setErrorMessage(message.getErrorMessage())
                .setCreateTime(message.getCreateTime())
                .setUpdateTime(message.getUpdateTime());
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

    private String tableName() {
        LocalMessageProperties properties = available(propertiesProvider);
        if (properties == null) {
            return null;
        }
        try {
            return LocalMessageAdminMapperSupport.tableName(properties);
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

    @FunctionalInterface
    private interface BatchUpdater {
        LocalMessage apply(LocalMessage message, String operator);
    }
}
