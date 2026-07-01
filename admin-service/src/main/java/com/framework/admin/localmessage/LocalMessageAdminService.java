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
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
}
