package com.framework.mq.admin;

import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.mq.deadletter.DeadLetterHandler;
import com.framework.mq.deadletter.MqAdminDTO;
import com.framework.mq.deadletter.MqFailedMessage;
import com.framework.mq.deadletter.MqRetryScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MQ 管理控制台接口
 * 提供失败消息查询、重发、统计、队列状态等管理能力
 */
@Slf4j
@RestController
@RequestMapping("/admin/mq")
@Tag(name = "MQ管理", description = "消息队列管理控制台")
public class MqAdminController {

    private final DeadLetterHandler deadLetterHandler;
    private final MqRetryScheduler retryScheduler;
    private final ObjectProvider<RabbitAdmin> rabbitAdminProvider;

    public MqAdminController(DeadLetterHandler deadLetterHandler,
                             MqRetryScheduler retryScheduler,
                             ObjectProvider<RabbitAdmin> rabbitAdminProvider) {
        this.deadLetterHandler = deadLetterHandler;
        this.retryScheduler = retryScheduler;
        this.rabbitAdminProvider = rabbitAdminProvider;
    }

    /**
     * 统计概览
     */
    @Operation(summary = "MQ统计概览")
    @GetMapping("/stats")
    public Result<MqAdminDTO.MqStats> stats() {
        var store = deadLetterHandler.getFailedMessageStore();
        var stats = new MqAdminDTO.MqStats();

        long pending = store.values().stream().filter(m -> MqFailedMessage.STATUS_PENDING.equals(m.getStatus())).count();
        long retrying = store.values().stream().filter(m -> MqFailedMessage.STATUS_RETRYING.equals(m.getStatus())).count();
        long success = store.values().stream().filter(m -> MqFailedMessage.STATUS_SUCCESS.equals(m.getStatus()) || MqFailedMessage.STATUS_MANUAL.equals(m.getStatus())).count();
        long exhausted = store.values().stream().filter(m -> MqFailedMessage.STATUS_EXHAUSTED.equals(m.getStatus())).count();

        stats.setPendingCount(pending);
        stats.setRetryingCount(retrying);
        stats.setSuccessCount(success);
        stats.setExhaustedCount(exhausted);
        stats.setTotalCount(store.size());

        // 队列信息
        List<MqAdminDTO.MqQueueInfo> queues = getQueueInfos();
        stats.setQueues(queues);

        return Result.success(stats);
    }

    /**
     * 失败消息列表（分页+筛选）
     */
    @Operation(summary = "失败消息列表")
    @GetMapping("/failed-messages")
    public Result<PageResult<MqAdminDTO.MqFailedMessageVO>> listFailedMessages(
            @RequestParam(required = false) String queueName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) String messageType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {

        var store = deadLetterHandler.getFailedMessageStore();
        List<MqFailedMessage> filtered = store.values().stream()
                .filter(m -> queueName == null || queueName.isEmpty() || queueName.equals(m.getQueueName()))
                .filter(m -> status == null || status.isEmpty() || status.equals(m.getStatus()))
                .filter(m -> traceId == null || traceId.isEmpty() || (m.getTraceId() != null && m.getTraceId().contains(traceId)))
                .filter(m -> businessKey == null || businessKey.isEmpty() || (m.getBusinessKey() != null && m.getBusinessKey().contains(businessKey)))
                .filter(m -> messageType == null || messageType.isEmpty() || messageType.equals(m.getMessageType()))
                .sorted(Comparator.comparing(MqFailedMessage::getCreateTime).reversed())
                .collect(Collectors.toList());

        int total = filtered.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        List<MqAdminDTO.MqFailedMessageVO> page = start < total
                ? filtered.subList(start, end).stream().map(this::toVO).collect(Collectors.toList())
                : Collections.emptyList();

        return Result.success(PageResult.of(page, total, pageNum, pageSize));
    }

    /**
     * 失败消息详情
     */
    @Operation(summary = "失败消息详情")
    @GetMapping("/failed-messages/{id}")
    public Result<MqAdminDTO.MqFailedMessageVO> getFailedMessage(@PathVariable Long id) {
        MqFailedMessage msg = deadLetterHandler.getById(id);
        if (msg == null) {
            return Result.fail("消息不存在");
        }
        return Result.success(toVO(msg));
    }

    /**
     * 手动重发单条
     */
    @Operation(summary = "手动重发单条消息")
    @PostMapping("/failed-messages/{id}/retry")
    public Result<String> retryOne(@PathVariable Long id,
                                    @RequestParam(defaultValue = "admin") String operator,
                                    @RequestParam(required = false) String remark) {
        boolean ok = retryScheduler.manualRetry(id, operator, remark);
        return ok ? Result.success("重发成功") : Result.fail("重发失败");
    }

    /**
     * 批量重发
     */
    @Operation(summary = "批量重发消息")
    @PostMapping("/failed-messages/batch-retry")
    public Result<MqAdminDTO.ManualRetryResult> batchRetry(
            @RequestBody MqAdminDTO.ManualRetryRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            return Result.fail("请选择要重发的消息");
        }
        MqAdminDTO.ManualRetryResult result = retryScheduler.batchManualRetry(
                request.getIds(),
                request.getOperator() != null ? request.getOperator() : "admin",
                request.getRemark());
        return Result.success(result);
    }

    /**
     * 删除失败记录
     */
    @Operation(summary = "删除失败记录")
    @DeleteMapping("/failed-messages/{id}")
    public Result<String> deleteFailedMessage(@PathVariable Long id) {
        boolean deleted = deadLetterHandler.removeRecord(id);
        return deleted ? Result.success("删除成功") : Result.fail("记录不存在");
    }

    /**
     * 清空已处理记录（SUCCESS/EXHAUSTED）
     */
    @Operation(summary = "清空已处理记录")
    @DeleteMapping("/failed-messages/clean")
    public Result<String> cleanProcessed() {
        int cleaned = deadLetterHandler.cleanProcessedRecords();
        return Result.success("已清理 " + cleaned + " 条记录");
    }

    /**
     * 队列列表
     */
    @Operation(summary = "队列列表")
    @GetMapping("/queues")
    public Result<List<MqAdminDTO.MqQueueInfo>> listQueues() {
        return Result.success(getQueueInfos());
    }

    // ===== 私有方法 =====

    private List<MqAdminDTO.MqQueueInfo> getQueueInfos() {
        List<MqAdminDTO.MqQueueInfo> queues = new ArrayList<>();
        RabbitAdmin rabbitAdmin = rabbitAdminProvider.getIfAvailable();
        if (rabbitAdmin == null) {
            return queues;
        }
        try {
            // 从 Spring 上下文获取所有已声明的 Queue Bean
            org.springframework.context.ApplicationContext ctx =
                    org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
            if (ctx == null) {
                return queues;
            }
            java.util.Map<String, org.springframework.amqp.core.Queue> queueBeans =
                    ctx.getBeansOfType(org.springframework.amqp.core.Queue.class);
            for (var entry : queueBeans.entrySet()) {
                org.springframework.amqp.core.Queue q = entry.getValue();
                MqAdminDTO.MqQueueInfo info = new MqAdminDTO.MqQueueInfo();
                info.setQueueName(q.getName());
                try {
                    Properties props = rabbitAdmin.getQueueProperties(q.getName());
                    if (props != null) {
                        info.setMessageCount(props.get(org.springframework.amqp.rabbit.core.RabbitAdmin.QUEUE_MESSAGE_COUNT) != null
                                ? ((Number) props.get(org.springframework.amqp.rabbit.core.RabbitAdmin.QUEUE_MESSAGE_COUNT)).longValue()
                                : 0);
                        info.setConsumerCount(props.get(org.springframework.amqp.rabbit.core.RabbitAdmin.QUEUE_CONSUMER_COUNT) != null
                                ? ((Number) props.get(org.springframework.amqp.rabbit.core.RabbitAdmin.QUEUE_CONSUMER_COUNT)).longValue()
                                : 0);
                    }
                } catch (Exception e) {
                    // 队列可能尚未声明
                    info.setMessageCount(0);
                    info.setConsumerCount(0);
                }
                info.setState("RUNNING");
                queues.add(info);
            }
        } catch (Exception e) {
            log.debug("获取队列信息失败: {}", e.getMessage());
        }
        return queues;
    }

    private MqAdminDTO.MqFailedMessageVO toVO(MqFailedMessage msg) {
        MqAdminDTO.MqFailedMessageVO vo = new MqAdminDTO.MqFailedMessageVO();
        vo.setId(msg.getId());
        vo.setMessageId(msg.getMessageId());
        vo.setTraceId(msg.getTraceId());
        vo.setParentMessageId(msg.getParentMessageId());
        vo.setBusinessKey(msg.getBusinessKey());
        vo.setMessageType(msg.getMessageType());
        vo.setExchange(msg.getExchange());
        vo.setRoutingKey(msg.getRoutingKey());
        vo.setQueueName(msg.getQueueName());
        vo.setPayload(msg.getPayload());
        vo.setErrorMessage(msg.getErrorMessage());
        vo.setRetryCount(msg.getRetryCount());
        vo.setMaxRetry(msg.getMaxRetry());
        vo.setStatus(msg.getStatus());
        vo.setSource(msg.getSource());
        vo.setTenantId(msg.getTenantId());
        vo.setNextRetryTime(msg.getNextRetryTime());
        vo.setCreateTime(msg.getCreateTime());
        vo.setUpdateTime(msg.getUpdateTime());
        vo.setOperator(msg.getOperator());
        vo.setCompensateRemark(msg.getCompensateRemark());
        return vo;
    }
}
