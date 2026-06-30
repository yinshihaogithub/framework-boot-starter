package com.framework.admin.mq;

import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.mq.deadletter.MqAdminDTO;
import com.framework.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MQ 管理后台接口。
 */
@RestController
@RequestMapping("/admin/mq")
@Tag(name = "MQ管理", description = "消息队列管理控制台")
@RequirePermission("mq:view")
public class MqAdminController {

    private final MqAdminService mqAdminService;

    public MqAdminController(MqAdminService mqAdminService) {
        this.mqAdminService = mqAdminService;
    }

    @Operation(summary = "MQ统计概览")
    @GetMapping("/stats")
    public Result<MqAdminDTO.MqStats> stats() {
        return Result.success(mqAdminService.stats());
    }

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
        return Result.success(mqAdminService.listFailedMessages(
                queueName, status, traceId, businessKey, messageType, pageNum, pageSize));
    }

    @Operation(summary = "失败消息详情")
    @GetMapping("/failed-messages/{id}")
    public Result<MqAdminDTO.MqFailedMessageVO> getFailedMessage(@PathVariable Long id) {
        return toResult(mqAdminService.getFailedMessage(id));
    }

    @Operation(summary = "手动重发单条消息")
    @PostMapping("/failed-messages/{id}/retry")
    @RequirePermission(value = {"mq:view", "mq:retry"}, logicalAnd = true)
    public Result<String> retryOne(@PathVariable Long id,
                                   @RequestParam(defaultValue = "admin") String operator,
                                   @RequestParam(required = false) String remark,
                                   HttpServletRequest servletRequest) {
        return toResult(mqAdminService.retryOne(id, operator, remark, servletRequest));
    }

    @Operation(summary = "批量重发消息")
    @PostMapping("/failed-messages/batch-retry")
    @RequirePermission(value = {"mq:view", "mq:retry"}, logicalAnd = true)
    public Result<MqAdminDTO.ManualRetryResult> batchRetry(@RequestBody MqAdminDTO.ManualRetryRequest request,
                                                           HttpServletRequest servletRequest) {
        return toResult(mqAdminService.batchRetry(request, servletRequest));
    }

    @Operation(summary = "人工补偿完成")
    @PostMapping("/failed-messages/{id}/manual-success")
    @RequirePermission(value = {"mq:view", "mq:retry"}, logicalAnd = true)
    public Result<String> manualSuccess(@PathVariable Long id,
                                        @RequestBody(required = false) ManualCompensationRequest request,
                                        HttpServletRequest servletRequest) {
        return toResult(mqAdminService.manualSuccess(id, request == null ? null : request.getOperator(),
                request == null ? null : request.getRemark(), servletRequest));
    }

    @Operation(summary = "人工终止消息")
    @PostMapping("/failed-messages/{id}/manual-failure")
    @RequirePermission(value = {"mq:view", "mq:retry"}, logicalAnd = true)
    public Result<String> manualFailure(@PathVariable Long id,
                                        @RequestBody(required = false) ManualCompensationRequest request,
                                        HttpServletRequest servletRequest) {
        return toResult(mqAdminService.manualFailure(id, request == null ? null : request.getOperator(),
                request == null ? null : request.getRemark(), servletRequest));
    }

    @Operation(summary = "删除失败记录")
    @DeleteMapping("/failed-messages/{id}")
    @RequirePermission(value = {"mq:view", "mq:retry"}, logicalAnd = true)
    public Result<String> deleteFailedMessage(@PathVariable Long id, HttpServletRequest servletRequest) {
        return toResult(mqAdminService.deleteFailedMessage(id, servletRequest));
    }

    @Operation(summary = "清空已处理记录")
    @DeleteMapping("/failed-messages/clean")
    @RequirePermission(value = {"mq:view", "mq:retry"}, logicalAnd = true)
    public Result<String> cleanProcessed(HttpServletRequest servletRequest) {
        return toResult(mqAdminService.cleanProcessed(servletRequest));
    }

    @Operation(summary = "队列列表")
    @GetMapping("/queues")
    public Result<List<MqAdminDTO.MqQueueInfo>> listQueues() {
        return Result.success(mqAdminService.listQueues());
    }

    private <T> Result<T> toResult(MqAdminService.ActionResult<T> result) {
        return result.success() ? Result.success(result.data()) : Result.fail(result.code(), result.message());
    }

    @Data
    public static class ManualCompensationRequest {
        private String operator;
        private String remark;
    }
}
