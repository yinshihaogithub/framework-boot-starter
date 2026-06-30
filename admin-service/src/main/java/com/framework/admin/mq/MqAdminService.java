package com.framework.admin.mq;

import com.framework.admin.audit.AdminAuditService;
import com.framework.admin.support.AdminPageSupport;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
import com.framework.core.result.ResultCode;
import com.framework.core.trace.TraceContext;
import com.framework.mq.config.MqProperties;
import com.framework.mq.deadletter.DeadLetterHandler;
import com.framework.mq.deadletter.MqAdminDTO;
import com.framework.mq.deadletter.MqFailedMessage;
import com.framework.mq.deadletter.MqRetryScheduler;
import com.framework.mq.producer.MqMessageSender;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MqAdminService {

    private final ObjectProvider<DeadLetterHandler> deadLetterHandlerProvider;
    private final ObjectProvider<MqRetryScheduler> retrySchedulerProvider;
    private final ObjectProvider<MqProperties> mqPropertiesProvider;
    private final ObjectProvider<MqMessageSender> messageSenderProvider;
    private final ObjectProvider<RabbitAdmin> rabbitAdminProvider;
    private final ApplicationContext applicationContext;
    private final AdminAuditService auditService;

    public MqAdminService(ObjectProvider<DeadLetterHandler> deadLetterHandlerProvider,
                          ObjectProvider<MqRetryScheduler> retrySchedulerProvider,
                          ObjectProvider<MqProperties> mqPropertiesProvider,
                          ObjectProvider<MqMessageSender> messageSenderProvider,
                          ObjectProvider<RabbitAdmin> rabbitAdminProvider,
                          ApplicationContext applicationContext,
                          AdminAuditService auditService) {
        this.deadLetterHandlerProvider = deadLetterHandlerProvider;
        this.retrySchedulerProvider = retrySchedulerProvider;
        this.mqPropertiesProvider = mqPropertiesProvider;
        this.messageSenderProvider = messageSenderProvider;
        this.rabbitAdminProvider = rabbitAdminProvider;
        this.applicationContext = applicationContext;
        this.auditService = auditService;
    }

    public MqAdminDTO.MqStats stats() {
        MqAdminDTO.MqStats stats = new MqAdminDTO.MqStats();
        DeadLetterHandler handler = available(deadLetterHandlerProvider);
        if (handler != null) {
            try {
                var store = handler.getFailedMessageStore();
                stats.setPendingCount(store.values().stream()
                        .filter(m -> MqFailedMessage.STATUS_PENDING.equals(m.getStatus())).count());
                stats.setRetryingCount(store.values().stream()
                        .filter(m -> MqFailedMessage.STATUS_RETRYING.equals(m.getStatus())).count());
                stats.setSuccessCount(store.values().stream()
                        .filter(m -> MqFailedMessage.STATUS_SUCCESS.equals(m.getStatus())
                                || MqFailedMessage.STATUS_MANUAL.equals(m.getStatus())).count());
                stats.setExhaustedCount(store.values().stream()
                        .filter(m -> MqFailedMessage.STATUS_EXHAUSTED.equals(m.getStatus())).count());
                stats.setTotalCount(store.size());
            } catch (Exception e) {
                log.debug("获取MQ失败消息统计失败: {}", e.getMessage());
            }
        }
        stats.setQueues(listQueues());
        stats.setRuntime(getRuntimeInfo());
        return stats;
    }

    public PageResult<MqAdminDTO.MqFailedMessageVO> listFailedMessages(String queueName, String status,
                                                                       String traceId, String businessKey,
                                                                       String messageType, int pageNum,
                                                                       int pageSize) {
        DeadLetterHandler handler = available(deadLetterHandlerProvider);
        int safePageNum = AdminPageSupport.safePageNum(pageNum);
        int safePageSize = AdminPageSupport.safePageSize(pageSize);
        if (handler == null) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        String normalizedQueueName = text(queueName);
        String normalizedStatus = upper(status);
        String normalizedTraceId = normalizeTraceIdFilter(traceId);
        String normalizedBusinessKey = text(businessKey);
        String normalizedMessageType = text(messageType);
        if (isInvalidTraceIdFilter(traceId, normalizedTraceId)) {
            return PageResult.empty(safePageNum, safePageSize);
        }

        try {
            List<MqFailedMessage> filtered = handler.getFailedMessageStore().values().stream()
                    .filter(m -> isBlank(normalizedQueueName) || normalizedQueueName.equals(m.getQueueName()))
                    .filter(m -> isBlank(normalizedStatus) || normalizedStatus.equals(m.getStatus()))
                    .filter(m -> isBlank(normalizedTraceId) || contains(m.getTraceId(), normalizedTraceId))
                    .filter(m -> isBlank(normalizedBusinessKey) || contains(m.getBusinessKey(), normalizedBusinessKey))
                    .filter(m -> isBlank(normalizedMessageType)
                            || normalizedMessageType.equalsIgnoreCase(m.getMessageType()))
                    .sorted(Comparator.comparing(MqFailedMessage::getCreateTime, newestFirst()))
                    .collect(Collectors.toList());

            int total = filtered.size();
            long offset = (long) (safePageNum - 1) * safePageSize;
            int start = offset < total ? (int) offset : total;
            int end = Math.min(start + safePageSize, total);
            List<MqAdminDTO.MqFailedMessageVO> page = start < total
                    ? filtered.subList(start, end).stream().map(this::toVO).collect(Collectors.toList())
                    : Collections.emptyList();
            return PageResult.of(page, total, safePageNum, safePageSize);
        } catch (Exception e) {
            log.debug("查询MQ失败消息失败: {}", e.getMessage());
            return PageResult.empty(safePageNum, safePageSize);
        }
    }

    public ActionResult<MqAdminDTO.MqFailedMessageVO> getFailedMessage(Long id) {
        ActionResult<MqAdminDTO.MqFailedMessageVO> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        DeadLetterHandler handler = available(deadLetterHandlerProvider);
        if (handler == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ死信存储未启用");
        }
        try {
            MqFailedMessage message = handler.getById(id);
            return message == null ? ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在") : ActionResult.success(toVO(message));
        } catch (Exception e) {
            log.debug("查询MQ失败消息详情失败: {}", e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ失败消息查询失败");
        }
    }

    public ActionResult<String> retryOne(Long id, String operator, String remark, HttpServletRequest servletRequest) {
        ActionResult<String> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        MqRetryScheduler scheduler = available(retrySchedulerProvider);
        if (scheduler == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, retryUnavailableMessage());
        }
        try {
            String normalizedOperator = operator(operator);
            String normalizedRemark = text(remark);
            boolean ok = scheduler.manualRetry(id, normalizedOperator, normalizedRemark);
            auditSuccess(servletRequest, "手动重发MQ消息", "UPDATE",
                    auditService.params("id", id, "operator", normalizedOperator, "remark", normalizedRemark,
                            "success", ok));
            return ok ? ActionResult.success("重发成功") : ActionResult.fail(ResultCode.BUSINESS_ERROR, "重发失败");
        } catch (Exception e) {
            log.debug("手动重发MQ消息失败: {}", e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ消息重发失败");
        }
    }

    public ActionResult<MqAdminDTO.ManualRetryResult> batchRetry(MqAdminDTO.ManualRetryRequest request,
                                                                 HttpServletRequest servletRequest) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return ActionResult.fail(ResultCode.PARAM_ERROR, "请选择要重发的消息");
        }
        MqRetryScheduler scheduler = available(retrySchedulerProvider);
        if (scheduler == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, retryUnavailableMessage());
        }
        try {
            String normalizedOperator = operator(request.getOperator());
            String normalizedRemark = text(request.getRemark());
            MqAdminDTO.ManualRetryResult result = scheduler.batchManualRetry(
                    request.getIds(),
                    normalizedOperator,
                    normalizedRemark);
            auditSuccess(servletRequest, "批量重发MQ消息", "UPDATE",
                    auditService.params("ids", request.getIds(), "operator", normalizedOperator,
                            "remark", normalizedRemark, "success", result.getSuccess(),
                            "failure", result.getFailed()));
            return ActionResult.success(result);
        } catch (Exception e) {
            log.debug("批量重发MQ消息失败: {}", e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ消息批量重发失败");
        }
    }

    public ActionResult<String> manualSuccess(Long id, String operator, String remark, HttpServletRequest servletRequest) {
        ActionResult<String> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        DeadLetterHandler handler = available(deadLetterHandlerProvider);
        if (handler == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ死信存储未启用");
        }
        try {
            MqFailedMessage message = handler.getById(id);
            if (message == null) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            String beforeStatus = message.getStatus();
            MqFailedMessage updated = message.copy();
            updated.setStatus(MqFailedMessage.STATUS_MANUAL);
            updated.setNextRetryTime(null);
            updated.setOperator(operator(operator));
            updated.setCompensateRemark(remark(remark, "人工补偿完成"));
            if (!handler.updateRecord(updated)) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            auditSuccess(servletRequest, "人工补偿完成MQ消息", "UPDATE",
                    auditService.params("id", id, "messageId", updated.getMessageId(), "traceId", updated.getTraceId(),
                            "operator", updated.getOperator(), "remark", updated.getCompensateRemark(),
                            "beforeStatus", beforeStatus, "afterStatus", updated.getStatus()));
            return ActionResult.success("已人工补偿完成");
        } catch (Exception e) {
            log.debug("人工补偿MQ消息失败: {}", e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ人工补偿失败");
        }
    }

    public ActionResult<String> manualFailure(Long id, String operator, String remark, HttpServletRequest servletRequest) {
        ActionResult<String> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        DeadLetterHandler handler = available(deadLetterHandlerProvider);
        if (handler == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ死信存储未启用");
        }
        try {
            MqFailedMessage message = handler.getById(id);
            if (message == null) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            String beforeStatus = message.getStatus();
            MqFailedMessage updated = message.copy();
            updated.setStatus(MqFailedMessage.STATUS_EXHAUSTED);
            updated.setNextRetryTime(null);
            updated.setOperator(operator(operator));
            updated.setCompensateRemark(remark(remark, "人工终止"));
            if (!handler.updateRecord(updated)) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            auditSuccess(servletRequest, "人工终止MQ消息", "UPDATE",
                    auditService.params("id", id, "messageId", updated.getMessageId(), "traceId", updated.getTraceId(),
                            "operator", updated.getOperator(), "remark", updated.getCompensateRemark(),
                            "beforeStatus", beforeStatus, "afterStatus", updated.getStatus()));
            return ActionResult.success("已人工终止");
        } catch (Exception e) {
            log.debug("人工终止MQ消息失败: {}", e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ人工终止失败");
        }
    }

    public ActionResult<String> deleteFailedMessage(Long id, HttpServletRequest servletRequest) {
        ActionResult<String> invalidId = invalidIdResult(id);
        if (invalidId != null) {
            return invalidId;
        }
        DeadLetterHandler handler = available(deadLetterHandlerProvider);
        if (handler == null) {
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ死信存储未启用");
        }
        try {
            MqFailedMessage message = handler.getById(id);
            boolean deleted = handler.removeRecord(id);
            if (!deleted) {
                return ActionResult.fail(ResultCode.NOT_FOUND, "消息不存在");
            }
            auditSuccess(servletRequest, "删除MQ失败记录", "DELETE",
                    auditService.params("id", id, "messageId", message == null ? null : message.getMessageId(),
                            "traceId", message == null ? null : message.getTraceId(),
                            "status", message == null ? null : message.getStatus(),
                            "deleted", true));
            return ActionResult.success("删除成功");
        } catch (Exception e) {
            log.debug("删除MQ失败记录失败: {}", e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ失败记录删除失败");
        }
    }

    public ActionResult<String> cleanProcessed(HttpServletRequest servletRequest) {
        DeadLetterHandler handler = available(deadLetterHandlerProvider);
        if (handler == null) {
            return ActionResult.success("已清理 0 条记录");
        }
        try {
            int cleaned = handler.cleanProcessedRecords();
            auditSuccess(servletRequest, "清空MQ已处理记录", "DELETE",
                    auditService.params("cleaned", cleaned));
            return ActionResult.success("已清理 " + cleaned + " 条记录");
        } catch (Exception e) {
            log.debug("清空MQ已处理记录失败: {}", e.getMessage());
            return ActionResult.fail(ResultCode.SERVICE_ERROR, "MQ已处理记录清理失败");
        }
    }

    public List<MqAdminDTO.MqQueueInfo> listQueues() {
        List<MqAdminDTO.MqQueueInfo> queues = new ArrayList<>();
        RabbitAdmin rabbitAdmin = available(rabbitAdminProvider);
        try {
            applicationContext.getBeansOfType(Queue.class).values().forEach(queue -> {
                MqAdminDTO.MqQueueInfo info = new MqAdminDTO.MqQueueInfo();
                info.setQueueName(queue.getName());
                info.setState(rabbitAdmin == null ? "UNKNOWN" : "RUNNING");
                if (rabbitAdmin != null) {
                    fillRabbitQueueMetrics(rabbitAdmin, queue, info);
                }
                queues.add(info);
            });
        } catch (Exception e) {
            log.debug("获取队列信息失败: {}", e.getMessage());
        }
        return queues;
    }

    private MqAdminDTO.MqRuntimeInfo getRuntimeInfo() {
        MqProperties properties = available(mqPropertiesProvider);
        MqAdminDTO.MqRuntimeInfo info = new MqAdminDTO.MqRuntimeInfo();
        if (properties == null) {
            return info.setEnabled(false)
                    .setProvider("NONE")
                    .setDeadLetterEnabled(false)
                    .setRetryAvailable(false)
                    .setMaxRetry(0)
                    .setRetryFixedDelay(0)
                    .setProviders(providerStatuses(null));
        }
        return info.setEnabled(properties.isEnabled())
                .setProvider(properties.getProvider().name())
                .setDeadLetterEnabled(properties.getDeadLetter().isEnabled())
                .setRetryAvailable(available(retrySchedulerProvider) != null)
                .setDeadLetterQueue(properties.getDeadLetter().getQueue())
                .setMaxRetry(properties.getMaxRetry())
                .setRetryFixedDelay(properties.getRetry().getFixedDelay())
                .setFailedMessageTableName(properties.getFailedMessageTableName())
                .setProviders(providerStatuses(properties.getProvider()));
    }

    private List<MqAdminDTO.MqProviderStatus> providerStatuses(MqProperties.Provider activeProvider) {
        Set<MqProperties.Provider> availableProviders;
        try {
            availableProviders = messageSenderProvider == null ? EnumSet.noneOf(MqProperties.Provider.class)
                    : messageSenderProvider.stream()
                    .map(MqMessageSender::provider)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(MqProperties.Provider.class)));
        } catch (RuntimeException e) {
            log.debug("获取MQ发送器状态失败: {}", e.getMessage());
            availableProviders = EnumSet.noneOf(MqProperties.Provider.class);
        }
        Set<MqProperties.Provider> providers = availableProviders;
        return List.of(MqProperties.Provider.RABBIT, MqProperties.Provider.KAFKA, MqProperties.Provider.ROCKET)
                .stream()
                .map(provider -> new MqAdminDTO.MqProviderStatus()
                        .setProvider(provider.name())
                        .setActive(provider == activeProvider)
                        .setAvailable(providers.contains(provider)))
                .toList();
    }

    private void fillRabbitQueueMetrics(RabbitAdmin rabbitAdmin, Queue queue, MqAdminDTO.MqQueueInfo info) {
        try {
            Properties props = rabbitAdmin.getQueueProperties(queue.getName());
            if (props == null) {
                return;
            }
            Object messageCount = props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
            Object consumerCount = props.get(RabbitAdmin.QUEUE_CONSUMER_COUNT);
            info.setMessageCount(messageCount instanceof Number number ? number.longValue() : 0);
            info.setConsumerCount(consumerCount instanceof Number number ? number.longValue() : 0);
        } catch (Exception e) {
            info.setState("UNKNOWN");
        }
    }

    private MqAdminDTO.MqFailedMessageVO toVO(MqFailedMessage msg) {
        return new MqAdminDTO.MqFailedMessageVO()
                .setId(msg.getId())
                .setMessageId(msg.getMessageId())
                .setTraceId(msg.getTraceId())
                .setParentMessageId(msg.getParentMessageId())
                .setBusinessKey(msg.getBusinessKey())
                .setMessageType(msg.getMessageType())
                .setExchange(msg.getExchange())
                .setRoutingKey(msg.getRoutingKey())
                .setQueueName(msg.getQueueName())
                .setPayload(msg.getPayload())
                .setErrorMessage(msg.getErrorMessage())
                .setErrorStack(msg.getErrorStack())
                .setRetryCount(msg.getRetryCount())
                .setMaxRetry(msg.getMaxRetry())
                .setStatus(msg.getStatus())
                .setSource(msg.getSource())
                .setTenantId(msg.getTenantId())
                .setNextRetryTime(msg.getNextRetryTime())
                .setCreateTime(msg.getCreateTime())
                .setUpdateTime(msg.getUpdateTime())
                .setOperator(msg.getOperator())
                .setCompensateRemark(msg.getCompensateRemark());
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String text(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String normalizeTraceIdFilter(String traceId) {
        String text = text(traceId);
        return text == null ? null : TraceContext.normalizeTraceId(text);
    }

    private boolean isInvalidTraceIdFilter(String originalTraceId, String normalizedTraceId) {
        return !isBlank(originalTraceId) && normalizedTraceId == null;
    }

    private <T extends Comparable<? super T>> Comparator<T> newestFirst() {
        return Comparator.nullsLast(Comparator.reverseOrder());
    }

    private String upper(String value) {
        String text = text(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private String operator(String operator) {
        if (!isBlank(operator)) {
            return operator.trim();
        }
        String username = UserContextHolder.getUsername();
        return isBlank(username) ? "admin" : username;
    }

    private String remark(String remark, String defaultRemark) {
        if (!isBlank(remark)) {
            return remark.trim();
        }
        return defaultRemark;
    }

    private String retryUnavailableMessage() {
        return "未接入可用 MQ 发送器，无法重发消息";
    }

    private <T> ActionResult<T> invalidIdResult(Long id) {
        if (id == null || id <= 0) {
            return ActionResult.fail(ResultCode.PARAM_ERROR, "消息ID必须大于0");
        }
        return null;
    }

    private void auditSuccess(HttpServletRequest servletRequest, String action, String operationType, Object params) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.success(servletRequest, "MQ管理", action, operationType, params);
        } catch (RuntimeException e) {
            log.warn("[MQ管理] 审计日志写入失败 action={}, error={}", action, e.getMessage());
        }
    }

    private <T> T available(ObjectProvider<T> provider) {
        if (provider == null) {
            return null;
        }
        try {
            return provider.getIfAvailable();
        } catch (RuntimeException e) {
            log.debug("获取可选MQ组件失败: {}", e.getMessage());
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
