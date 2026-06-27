package com.framework.admin.mq;

import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
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
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MqAdminService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

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
        DeadLetterHandler handler = deadLetterHandlerProvider.getIfAvailable();
        if (handler != null) {
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
        }
        stats.setQueues(listQueues());
        stats.setRuntime(getRuntimeInfo());
        return stats;
    }

    public PageResult<MqAdminDTO.MqFailedMessageVO> listFailedMessages(String queueName, String status,
                                                                       String traceId, String businessKey,
                                                                       String messageType, int pageNum,
                                                                       int pageSize) {
        DeadLetterHandler handler = deadLetterHandlerProvider.getIfAvailable();
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        if (handler == null) {
            return PageResult.empty(safePageNum, safePageSize);
        }

        List<MqFailedMessage> filtered = handler.getFailedMessageStore().values().stream()
                .filter(m -> isBlank(queueName) || queueName.equals(m.getQueueName()))
                .filter(m -> isBlank(status) || status.equals(m.getStatus()))
                .filter(m -> isBlank(traceId) || contains(m.getTraceId(), traceId))
                .filter(m -> isBlank(businessKey) || contains(m.getBusinessKey(), businessKey))
                .filter(m -> isBlank(messageType) || messageType.equals(m.getMessageType()))
                .sorted(Comparator.comparing(MqFailedMessage::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());

        int total = filtered.size();
        long offset = (long) (safePageNum - 1) * safePageSize;
        int start = offset < total ? (int) offset : total;
        int end = Math.min(start + safePageSize, total);
        List<MqAdminDTO.MqFailedMessageVO> page = start < total
                ? filtered.subList(start, end).stream().map(this::toVO).collect(Collectors.toList())
                : Collections.emptyList();
        return PageResult.of(page, total, safePageNum, safePageSize);
    }

    public ActionResult<MqAdminDTO.MqFailedMessageVO> getFailedMessage(Long id) {
        DeadLetterHandler handler = deadLetterHandlerProvider.getIfAvailable();
        MqFailedMessage message = handler == null ? null : handler.getById(id);
        return message == null ? ActionResult.fail("消息不存在") : ActionResult.success(toVO(message));
    }

    public ActionResult<String> retryOne(Long id, String operator, String remark, HttpServletRequest servletRequest) {
        MqRetryScheduler scheduler = retrySchedulerProvider.getIfAvailable();
        if (scheduler == null) {
            return ActionResult.fail("MQ重试调度未启用");
        }
        boolean ok = scheduler.manualRetry(id, operator, remark);
        auditService.success(servletRequest, "MQ管理", "手动重发MQ消息", "UPDATE",
                auditService.params("id", id, "operator", operator, "remark", remark, "success", ok));
        return ok ? ActionResult.success("重发成功") : ActionResult.fail("重发失败");
    }

    public ActionResult<MqAdminDTO.ManualRetryResult> batchRetry(MqAdminDTO.ManualRetryRequest request,
                                                                 HttpServletRequest servletRequest) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return ActionResult.fail("请选择要重发的消息");
        }
        MqRetryScheduler scheduler = retrySchedulerProvider.getIfAvailable();
        if (scheduler == null) {
            return ActionResult.fail("MQ重试调度未启用");
        }
        MqAdminDTO.ManualRetryResult result = scheduler.batchManualRetry(
                request.getIds(),
                isBlank(request.getOperator()) ? "admin" : request.getOperator(),
                request.getRemark());
        auditService.success(servletRequest, "MQ管理", "批量重发MQ消息", "UPDATE",
                auditService.params("ids", request.getIds(), "operator", request.getOperator(),
                        "remark", request.getRemark(), "success", result.getSuccess(),
                        "failure", result.getFailed()));
        return ActionResult.success(result);
    }

    public ActionResult<String> manualSuccess(Long id, String operator, String remark, HttpServletRequest servletRequest) {
        DeadLetterHandler handler = deadLetterHandlerProvider.getIfAvailable();
        if (handler == null) {
            return ActionResult.fail("MQ死信存储未启用");
        }
        MqFailedMessage message = handler.getById(id);
        if (message == null) {
            return ActionResult.fail("消息不存在");
        }
        message.setStatus(MqFailedMessage.STATUS_MANUAL);
        message.setNextRetryTime(null);
        message.setOperator(operator(operator));
        message.setCompensateRemark(remark(remark, "人工补偿完成"));
        handler.updateRecord(message);
        auditService.success(servletRequest, "MQ管理", "人工补偿完成MQ消息", "UPDATE",
                auditService.params("id", id, "messageId", message.getMessageId(), "traceId", message.getTraceId(),
                        "operator", message.getOperator(), "remark", message.getCompensateRemark()));
        return ActionResult.success("已人工补偿完成");
    }

    public ActionResult<String> manualFailure(Long id, String operator, String remark, HttpServletRequest servletRequest) {
        DeadLetterHandler handler = deadLetterHandlerProvider.getIfAvailable();
        if (handler == null) {
            return ActionResult.fail("MQ死信存储未启用");
        }
        MqFailedMessage message = handler.getById(id);
        if (message == null) {
            return ActionResult.fail("消息不存在");
        }
        message.setStatus(MqFailedMessage.STATUS_EXHAUSTED);
        message.setNextRetryTime(null);
        message.setOperator(operator(operator));
        message.setCompensateRemark(remark(remark, "人工终止"));
        handler.updateRecord(message);
        auditService.success(servletRequest, "MQ管理", "人工终止MQ消息", "UPDATE",
                auditService.params("id", id, "messageId", message.getMessageId(), "traceId", message.getTraceId(),
                        "operator", message.getOperator(), "remark", message.getCompensateRemark()));
        return ActionResult.success("已人工终止");
    }

    public ActionResult<String> deleteFailedMessage(Long id, HttpServletRequest servletRequest) {
        DeadLetterHandler handler = deadLetterHandlerProvider.getIfAvailable();
        if (handler == null) {
            return ActionResult.fail("MQ死信存储未启用");
        }
        boolean deleted = handler.removeRecord(id);
        auditService.success(servletRequest, "MQ管理", "删除MQ失败记录", "DELETE",
                auditService.params("id", id, "deleted", deleted));
        return deleted ? ActionResult.success("删除成功") : ActionResult.fail("记录不存在");
    }

    public ActionResult<String> cleanProcessed(HttpServletRequest servletRequest) {
        DeadLetterHandler handler = deadLetterHandlerProvider.getIfAvailable();
        if (handler == null) {
            return ActionResult.success("已清理 0 条记录");
        }
        int cleaned = handler.cleanProcessedRecords();
        auditService.success(servletRequest, "MQ管理", "清空MQ已处理记录", "DELETE",
                auditService.params("cleaned", cleaned));
        return ActionResult.success("已清理 " + cleaned + " 条记录");
    }

    public List<MqAdminDTO.MqQueueInfo> listQueues() {
        List<MqAdminDTO.MqQueueInfo> queues = new ArrayList<>();
        RabbitAdmin rabbitAdmin = rabbitAdminProvider.getIfAvailable();
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
        MqProperties properties = mqPropertiesProvider.getIfAvailable();
        MqAdminDTO.MqRuntimeInfo info = new MqAdminDTO.MqRuntimeInfo();
        if (properties == null) {
            return info.setEnabled(false)
                    .setProvider("NONE")
                    .setDeadLetterEnabled(false)
                    .setMaxRetry(0)
                    .setRetryFixedDelay(0)
                    .setProviders(providerStatuses(null));
        }
        return info.setEnabled(properties.isEnabled())
                .setProvider(properties.getProvider().name())
                .setDeadLetterEnabled(properties.getDeadLetter().isEnabled())
                .setDeadLetterQueue(properties.getDeadLetter().getQueue())
                .setMaxRetry(properties.getMaxRetry())
                .setRetryFixedDelay(properties.getRetry().getFixedDelay())
                .setFailedMessageTableName(properties.getFailedMessageTableName())
                .setProviders(providerStatuses(properties.getProvider()));
    }

    private List<MqAdminDTO.MqProviderStatus> providerStatuses(MqProperties.Provider activeProvider) {
        Set<MqProperties.Provider> availableProviders = messageSenderProvider.stream()
                .map(MqMessageSender::provider)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MqProperties.Provider.class)));
        return List.of(MqProperties.Provider.RABBIT, MqProperties.Provider.KAFKA, MqProperties.Provider.ROCKET)
                .stream()
                .map(provider -> new MqAdminDTO.MqProviderStatus()
                        .setProvider(provider.name())
                        .setActive(provider == activeProvider)
                        .setAvailable(availableProviders.contains(provider)))
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

    public record ActionResult<T>(boolean success, String message, T data) {
        public static <T> ActionResult<T> success(T data) {
            return new ActionResult<>(true, null, data);
        }

        public static <T> ActionResult<T> fail(String message) {
            return new ActionResult<>(false, message, null);
        }
    }
}
