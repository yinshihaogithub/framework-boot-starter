package com.framework.admin.mq;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.mq.config.MqProperties;
import com.framework.mq.deadletter.DeadLetterHandler;
import com.framework.mq.deadletter.MqAdminDTO;
import com.framework.mq.deadletter.MqFailedMessage;
import com.framework.mq.deadletter.MqFailedMessageRepository;
import com.framework.mq.deadletter.MqRetryScheduler;
import com.framework.mq.producer.MqMessageSender;
import com.framework.mq.producer.MqMessageSenderRegistry;
import com.framework.mq.core.MessageWrapper;
import com.framework.security.annotation.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.StaticApplicationContext;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MqAdminControllerTest {

    @Test
    void writeEndpointsRequireBothViewAndRetryPermissions() throws NoSuchMethodException {
        assertMqWritePermission("retryOne", Long.class, String.class, String.class, HttpServletRequest.class);
        assertMqWritePermission("batchRetry", MqAdminDTO.ManualRetryRequest.class, HttpServletRequest.class);
        assertMqWritePermission("manualSuccess", Long.class, MqAdminController.ManualCompensationRequest.class,
                HttpServletRequest.class);
        assertMqWritePermission("manualFailure", Long.class, MqAdminController.ManualCompensationRequest.class,
                HttpServletRequest.class);
        assertMqWritePermission("deleteFailedMessage", Long.class, HttpServletRequest.class);
        assertMqWritePermission("cleanProcessed", HttpServletRequest.class);
    }

    @Test
    void returnsEmptyPageWhenMqRuntimeIsNotEnabled() {
        MqAdminController controller = controller(null, null, null);

        Result<PageResult<MqAdminDTO.MqFailedMessageVO>> result = controller.listFailedMessages(
                null, null, null, null, null, -1, 0);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRecords()).isEmpty();
        assertThat(result.getData().getPageNum()).isEqualTo(1);
        assertThat(result.getData().getPageSize()).isEqualTo(20);
    }

    @Test
    void listsFailedMessagesFromFrameworkMqStore() {
        DeadLetterHandler handler = new DeadLetterHandler(
                new InMemoryMqFailedMessageRepository(List.of(
                        failedMessage(1L, "trace-a", MqFailedMessage.STATUS_PENDING),
                        failedMessage(2L, "trace-b", MqFailedMessage.STATUS_EXHAUSTED))),
                new MqProperties());
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<PageResult<MqAdminDTO.MqFailedMessageVO>> result = controller.listFailedMessages(
                null, " pending ", "trace-a", null, "ordercreated", 1, 50);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getRecords().get(0).getId()).isEqualTo(1L);
    }

    @Test
    void listFailedMessagesSupportsSuccessStatusAndIncludesErrorStack() {
        MqFailedMessage successMessage = failedMessage(10L, "trace-success", MqFailedMessage.STATUS_SUCCESS);
        successMessage.setErrorStack("stack-trace");
        DeadLetterHandler handler = new DeadLetterHandler(
                new InMemoryMqFailedMessageRepository(List.of(
                        successMessage,
                        failedMessage(11L, "trace-success", MqFailedMessage.STATUS_PENDING))),
                new MqProperties());
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<PageResult<MqAdminDTO.MqFailedMessageVO>> result = controller.listFailedMessages(
                null, " success ", "trace-success", null, null, 1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getRecords().get(0).getStatus()).isEqualTo(MqFailedMessage.STATUS_SUCCESS);
        assertThat(result.getData().getRecords().get(0).getErrorStack()).isEqualTo("stack-trace");
    }

    @Test
    void listFailedMessagesNormalizesFiltersAndKeepsNullCreateTimeLast() {
        MqFailedMessage nullTimeMessage = failedMessage(1L, "trace-a", MqFailedMessage.STATUS_PENDING);
        nullTimeMessage.setCreateTime(null);
        MqFailedMessage newestMessage = failedMessage(2L, "trace-a", MqFailedMessage.STATUS_PENDING);
        newestMessage.setCreateTime(new Date(2_000));
        MqFailedMessage otherTraceMessage = failedMessage(3L, "trace-b", MqFailedMessage.STATUS_PENDING);
        otherTraceMessage.setCreateTime(new Date(3_000));
        DeadLetterHandler handler = new DeadLetterHandler(
                new InMemoryMqFailedMessageRepository(List.of(nullTimeMessage, newestMessage, otherTraceMessage)),
                new MqProperties());
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<PageResult<MqAdminDTO.MqFailedMessageVO>> result = controller.listFailedMessages(
                " order.queue ", " pending ", " trace-a ", "order-", " ordercreated ", 1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isEqualTo(2);
        assertThat(result.getData().getRecords())
                .extracting(MqAdminDTO.MqFailedMessageVO::getId)
                .containsExactly(2L, 1L);
        assertThat(result.getData().getRecords().get(1).getCreateTime()).isNull();
    }

    @Test
    void listFailedMessagesReturnsEmptyPageForInvalidTraceIdFilter() {
        DeadLetterHandler handler = new DeadLetterHandler(
                new InMemoryMqFailedMessageRepository(List.of(
                        failedMessage(1L, "trace-a", MqFailedMessage.STATUS_PENDING))),
                new MqProperties());
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<PageResult<MqAdminDTO.MqFailedMessageVO>> result = controller.listFailedMessages(
                null, null, "bad\ntrace", null, null, 1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isZero();
        assertThat(result.getData().getRecords()).isEmpty();
    }

    @Test
    void returnsRuntimeInfoWithProviderAvailability() {
        MqProperties properties = new MqProperties();
        properties.setProvider(MqProperties.Provider.RABBIT);
        MqAdminController controller = controller(null, properties, sender(MqProperties.Provider.RABBIT));

        Result<MqAdminDTO.MqStats> result = controller.stats();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRuntime().getProvider()).isEqualTo("RABBIT");
        assertThat(result.getData().getRuntime().isDeadLetterEnabled()).isTrue();
        assertThat(result.getData().getRuntime().isRetryAvailable()).isFalse();
        assertThat(result.getData().getRuntime().getFailedMessageTableName())
                .isEqualTo("framework_mq_failed_message");
        assertThat(result.getData().getRuntime().getProviders())
                .extracting(MqAdminDTO.MqProviderStatus::getProvider,
                        MqAdminDTO.MqProviderStatus::isActive,
                        MqAdminDTO.MqProviderStatus::isAvailable)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("RABBIT", true, true),
                        org.assertj.core.groups.Tuple.tuple("KAFKA", false, false),
                        org.assertj.core.groups.Tuple.tuple("ROCKET", false, false));
    }

    @Test
    void returnsRetryAvailabilityWhenSchedulerExists() {
        MqProperties properties = new MqProperties();
        MqMessageSender sender = sender(MqProperties.Provider.RABBIT);
        DeadLetterHandler handler = new DeadLetterHandler(
                new InMemoryMqFailedMessageRepository(List.of()), properties);
        MqRetryScheduler scheduler = new MqRetryScheduler(
                handler, new MqMessageSenderRegistry(properties, List.of(sender)), properties.getMaxRetry());
        MqAdminController controller = controller(handler, properties, sender, scheduler);

        Result<MqAdminDTO.MqStats> result = controller.stats();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRuntime().isRetryAvailable()).isTrue();
    }

    @Test
    void retryFailsClearlyWhenSenderIsUnavailable() {
        MqAdminController controller = controller(null, new MqProperties(), null);

        Result<String> result = controller.retryOne(1L, "admin", null, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("未接入可用 MQ 发送器，无法重发消息");
    }

    @Test
    void retryOneSucceedsWhenAuditServiceFails() {
        MqProperties properties = new MqProperties();
        MqMessageSender sender = sender(MqProperties.Provider.RABBIT);
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                failedMessage(1L, "trace-a", MqFailedMessage.STATUS_EXHAUSTED)));
        DeadLetterHandler handler = new DeadLetterHandler(repository, properties);
        MqRetryScheduler scheduler = new MqRetryScheduler(
                handler, new MqMessageSenderRegistry(properties, List.of(sender)), properties.getMaxRetry());
        MqAdminController controller = controller(handler, properties, sender, scheduler, new ThrowingAuditService());

        Result<String> result = controller.retryOne(1L, "ops", "manual retry", null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("重发成功");
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MqFailedMessage.STATUS_MANUAL);
        assertThat(saved.getOperator()).isEqualTo("ops");
    }

    @Test
    void retryOneNormalizesRemarkBeforePersistingAndAuditing() {
        MqProperties properties = new MqProperties();
        MqMessageSender sender = sender(MqProperties.Provider.RABBIT);
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                failedMessage(1L, "trace-a", MqFailedMessage.STATUS_EXHAUSTED)));
        DeadLetterHandler handler = new DeadLetterHandler(repository, properties);
        MqRetryScheduler scheduler = new MqRetryScheduler(
                handler, new MqMessageSenderRegistry(properties, List.of(sender)), properties.getMaxRetry());
        RecordingAuditService auditService = new RecordingAuditService();
        MqAdminController controller = controller(handler, properties, sender, scheduler, auditService);

        Result<String> result = controller.retryOne(1L, " ops ", " manual retry ", null);

        assertThat(result.isSuccess()).isTrue();
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getOperator()).isEqualTo("ops");
        assertThat(saved.getCompensateRemark()).isEqualTo("manual retry");
        assertThat(auditService.action).isEqualTo("手动重发MQ消息");
        assertThat(auditService.params)
                .containsEntry("operator", "ops")
                .containsEntry("remark", "manual retry")
                .containsEntry("success", true);
    }

    @Test
    void batchRetryRequiresMessageIds() {
        MqAdminController controller = controller(null, new MqProperties(), null);

        Result<MqAdminDTO.ManualRetryResult> result = controller.batchRetry(new MqAdminDTO.ManualRetryRequest(), null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("请选择要重发的消息");
    }

    @Test
    void batchRetryNormalizesOperatorAndAuditsResult() {
        MqProperties properties = new MqProperties();
        MqMessageSender sender = sender(MqProperties.Provider.RABBIT);
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                failedMessage(1L, "trace-a", MqFailedMessage.STATUS_EXHAUSTED)));
        DeadLetterHandler handler = new DeadLetterHandler(repository, properties);
        MqRetryScheduler scheduler = new MqRetryScheduler(
                handler, new MqMessageSenderRegistry(properties, List.of(sender)), properties.getMaxRetry());
        RecordingAuditService auditService = new RecordingAuditService();
        MqAdminController controller = controller(handler, properties, sender, scheduler, auditService);
        MqAdminDTO.ManualRetryRequest request = new MqAdminDTO.ManualRetryRequest()
                .setIds(List.of(1L))
                .setOperator(" ops ")
                .setRemark(" batch retry ");

        Result<MqAdminDTO.ManualRetryResult> result = controller.batchRetry(request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getSuccess()).isEqualTo(1);
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MqFailedMessage.STATUS_MANUAL);
        assertThat(saved.getOperator()).isEqualTo("ops");
        assertThat(auditService.action).isEqualTo("批量重发MQ消息");
        assertThat(auditService.params)
                .containsEntry("ids", List.of(1L))
                .containsEntry("operator", "ops")
                .containsEntry("remark", "batch retry")
                .containsEntry("success", 1)
                .containsEntry("failure", 0);
    }

    @Test
    void singleMessageOperationsRejectInvalidIdsBeforeProviderLookup() {
        MqAdminController controller = controller(null, new MqProperties(), null);

        Result<MqAdminDTO.MqFailedMessageVO> detail = controller.getFailedMessage(0L);
        Result<String> retry = controller.retryOne(null, "admin", null, null);
        Result<String> success = controller.manualSuccess(-1L, null, null);
        Result<String> failure = controller.manualFailure(0L, null, null);
        Result<String> delete = controller.deleteFailedMessage(null, null);

        assertInvalidId(detail);
        assertInvalidId(retry);
        assertInvalidId(success);
        assertInvalidId(failure);
        assertInvalidId(delete);
    }

    @Test
    void queryEndpointsFallBackWhenMqProvidersFail() {
        MqAdminController controller = failingController();

        Result<MqAdminDTO.MqStats> stats = controller.stats();
        Result<PageResult<MqAdminDTO.MqFailedMessageVO>> page = controller.listFailedMessages(
                null, null, null, null, null, -1, 500);
        Result<MqAdminDTO.MqFailedMessageVO> detail = controller.getFailedMessage(1L);

        assertThat(stats.isSuccess()).isTrue();
        assertThat(stats.getData().getTotalCount()).isZero();
        assertThat(stats.getData().getRuntime().isEnabled()).isFalse();
        assertThat(stats.getData().getRuntime().getProvider()).isEqualTo("NONE");
        assertThat(page.isSuccess()).isTrue();
        assertThat(page.getData().getPageNum()).isEqualTo(1);
        assertThat(page.getData().getPageSize()).isEqualTo(200);
        assertThat(page.getData().getRecords()).isEmpty();
        assertThat(detail.isSuccess()).isFalse();
        assertThat(detail.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(detail.getMessage()).isEqualTo("MQ死信存储未启用");
    }

    @Test
    void manualEndpointsReportServiceErrorWhenMqProvidersFail() {
        MqAdminController controller = failingController();
        MqAdminDTO.ManualRetryRequest request = new MqAdminDTO.ManualRetryRequest();
        request.setIds(List.of(1L));

        Result<String> retry = controller.retryOne(1L, "admin", null, null);
        Result<MqAdminDTO.ManualRetryResult> batch = controller.batchRetry(request, null);
        Result<String> success = controller.manualSuccess(1L, null, null);
        Result<String> failure = controller.manualFailure(1L, null, null);
        Result<String> delete = controller.deleteFailedMessage(1L, null);

        assertThat(retry.isSuccess()).isFalse();
        assertThat(retry.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(retry.getMessage()).isEqualTo("未接入可用 MQ 发送器，无法重发消息");
        assertThat(batch.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(success.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(success.getMessage()).isEqualTo("MQ死信存储未启用");
        assertThat(failure.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(delete.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
    }

    @Test
    void detailReportsNotFoundWhenFailedMessageDoesNotExist() {
        DeadLetterHandler handler = new DeadLetterHandler(
                new InMemoryMqFailedMessageRepository(List.of()), new MqProperties());
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<MqAdminDTO.MqFailedMessageVO> result = controller.getFailedMessage(404L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
    }

    @Test
    void detailReturnsErrorStackForFailedMessage() {
        MqFailedMessage message = failedMessage(12L, "trace-z", MqFailedMessage.STATUS_EXHAUSTED);
        message.setErrorStack("java.lang.IllegalStateException: boom");
        DeadLetterHandler handler = new DeadLetterHandler(
                new InMemoryMqFailedMessageRepository(List.of(message)), new MqProperties());
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<MqAdminDTO.MqFailedMessageVO> result = controller.getFailedMessage(12L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(12L);
        assertThat(result.getData().getErrorStack()).isEqualTo("java.lang.IllegalStateException: boom");
    }

    @Test
    void manualSuccessMarksMessageAsCompensatedAndAuditsStatusTransition() {
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                withNextRetryTime(failedMessage(1L, "trace-a", MqFailedMessage.STATUS_EXHAUSTED))));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        RecordingAuditService auditService = new RecordingAuditService();
        MqAdminController.ManualCompensationRequest request = new MqAdminController.ManualCompensationRequest();
        request.setOperator(" ops ");
        request.setRemark(" order checked ");
        MqAdminController controller = controller(handler, new MqProperties(), null, null, auditService);

        Result<String> result = controller.manualSuccess(1L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已人工补偿完成");
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MqFailedMessage.STATUS_MANUAL);
        assertThat(saved.getNextRetryTime()).isNull();
        assertThat(saved.getOperator()).isEqualTo("ops");
        assertThat(saved.getCompensateRemark()).isEqualTo("order checked");
        assertThat(auditService.action).isEqualTo("人工补偿完成MQ消息");
        assertThat(auditService.params)
                .containsEntry("traceId", "trace-a")
                .containsEntry("beforeStatus", MqFailedMessage.STATUS_EXHAUSTED)
                .containsEntry("afterStatus", MqFailedMessage.STATUS_MANUAL);
    }

    @Test
    void manualSuccessSucceedsWhenAuditServiceFails() {
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                withNextRetryTime(failedMessage(1L, "trace-a", MqFailedMessage.STATUS_EXHAUSTED))));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        MqAdminController.ManualCompensationRequest request = new MqAdminController.ManualCompensationRequest();
        request.setOperator("ops");
        MqAdminController controller = controller(handler, new MqProperties(), null, null, new ThrowingAuditService());

        Result<String> result = controller.manualSuccess(1L, request, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已人工补偿完成");
        MqFailedMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MqFailedMessage.STATUS_MANUAL);
        assertThat(saved.getNextRetryTime()).isNull();
        assertThat(saved.getOperator()).isEqualTo("ops");
    }

    @Test
    void manualSuccessKeepsOriginalMessageWhenSaveFails() {
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                withNextRetryTime(failedMessage(8L, "trace-h", MqFailedMessage.STATUS_EXHAUSTED))));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        MqAdminController.ManualCompensationRequest request = new MqAdminController.ManualCompensationRequest();
        request.setOperator("ops");
        request.setRemark("done");
        repository.failOnSave = true;
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<String> result = controller.manualSuccess(8L, request, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("MQ人工补偿失败");
        MqFailedMessage stored = handler.getById(8L);
        assertThat(stored.getStatus()).isEqualTo(MqFailedMessage.STATUS_EXHAUSTED);
        assertThat(stored.getNextRetryTime()).isNotNull();
        assertThat(stored.getOperator()).isNull();
        assertThat(stored.getCompensateRemark()).isNull();
    }

    @Test
    void manualSuccessReturnsNotFoundWhenMessageDisappearsBeforeUpdate() {
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                withNextRetryTime(failedMessage(10L, "trace-j", MqFailedMessage.STATUS_EXHAUSTED))));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        repository.updateAffected = false;
        MqAdminController.ManualCompensationRequest request = new MqAdminController.ManualCompensationRequest();
        request.setOperator("ops");
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<String> result = controller.manualSuccess(10L, request, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
        assertThat(handler.getById(10L)).isNull();
        MqFailedMessage stored = repository.findById(10L).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(MqFailedMessage.STATUS_EXHAUSTED);
        assertThat(stored.getOperator()).isNull();
    }

    @Test
    void manualFailureTerminatesMessageAndAuditsStatusTransition() {
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                withNextRetryTime(failedMessage(2L, "trace-b", MqFailedMessage.STATUS_PENDING))));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        RecordingAuditService auditService = new RecordingAuditService();
        MqAdminController controller = controller(handler, new MqProperties(), null, null, auditService);

        Result<String> result = controller.manualFailure(2L, null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已人工终止");
        MqFailedMessage saved = repository.findById(2L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MqFailedMessage.STATUS_EXHAUSTED);
        assertThat(saved.getNextRetryTime()).isNull();
        assertThat(saved.getOperator()).isEqualTo("admin");
        assertThat(saved.getCompensateRemark()).isEqualTo("人工终止");
        assertThat(auditService.action).isEqualTo("人工终止MQ消息");
        assertThat(auditService.params)
                .containsEntry("traceId", "trace-b")
                .containsEntry("beforeStatus", MqFailedMessage.STATUS_PENDING)
                .containsEntry("afterStatus", MqFailedMessage.STATUS_EXHAUSTED);
    }

    @Test
    void manualFailureKeepsOriginalMessageWhenSaveFails() {
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                withNextRetryTime(failedMessage(9L, "trace-i", MqFailedMessage.STATUS_PENDING))));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        repository.failOnSave = true;
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<String> result = controller.manualFailure(9L, null, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("MQ人工终止失败");
        MqFailedMessage stored = handler.getById(9L);
        assertThat(stored.getStatus()).isEqualTo(MqFailedMessage.STATUS_PENDING);
        assertThat(stored.getNextRetryTime()).isNotNull();
        assertThat(stored.getOperator()).isNull();
        assertThat(stored.getCompensateRemark()).isNull();
    }

    @Test
    void manualFailureReturnsNotFoundWhenMessageDisappearsBeforeUpdate() {
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                withNextRetryTime(failedMessage(11L, "trace-k", MqFailedMessage.STATUS_PENDING))));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        repository.updateAffected = false;
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<String> result = controller.manualFailure(11L, null, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
        assertThat(handler.getById(11L)).isNull();
        MqFailedMessage stored = repository.findById(11L).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(MqFailedMessage.STATUS_PENDING);
        assertThat(stored.getOperator()).isNull();
    }

    @Test
    void deleteFailedMessageRemovesStoreAndRepositoryRecord() {
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                failedMessage(3L, "trace-c", MqFailedMessage.STATUS_EXHAUSTED)));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        RecordingAuditService auditService = new RecordingAuditService();
        MqAdminController controller = controller(handler, new MqProperties(), null, null, auditService);

        Result<String> result = controller.deleteFailedMessage(3L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("删除成功");
        assertThat(handler.getById(3L)).isNull();
        assertThat(repository.findById(3L)).isEmpty();
        assertThat(auditService.action).isEqualTo("删除MQ失败记录");
        assertThat(auditService.params)
                .containsEntry("id", 3L)
                .containsEntry("messageId", "msg-3")
                .containsEntry("traceId", "trace-c")
                .containsEntry("status", MqFailedMessage.STATUS_EXHAUSTED)
                .containsEntry("deleted", true);
    }

    @Test
    void deleteFailedMessageReturnsNotFoundWhenMessageDoesNotExist() {
        DeadLetterHandler handler = new DeadLetterHandler(
                new InMemoryMqFailedMessageRepository(List.of()), new MqProperties());
        RecordingAuditService auditService = new RecordingAuditService();
        MqAdminController controller = controller(handler, new MqProperties(), null, null, auditService);

        Result<String> result = controller.deleteFailedMessage(404L, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
        assertThat(auditService.action).isNull();
    }

    @Test
    void cleanProcessedKeepsPendingMessages() {
        InMemoryMqFailedMessageRepository repository = new InMemoryMqFailedMessageRepository(List.of(
                failedMessage(4L, "trace-d", MqFailedMessage.STATUS_PENDING),
                failedMessage(5L, "trace-e", MqFailedMessage.STATUS_SUCCESS),
                failedMessage(6L, "trace-f", MqFailedMessage.STATUS_MANUAL),
                failedMessage(7L, "trace-g", MqFailedMessage.STATUS_EXHAUSTED)));
        DeadLetterHandler handler = new DeadLetterHandler(repository, new MqProperties());
        MqAdminController controller = controller(handler, new MqProperties(), null);

        Result<String> result = controller.cleanProcessed(null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已清理 3 条记录");
        assertThat(handler.getFailedMessageStore().keySet()).containsExactly(4L);
        assertThat(repository.findAll()).extracting(MqFailedMessage::getId).containsExactly(4L);
    }

    private static MqAdminController controller(DeadLetterHandler handler,
                                                MqProperties properties,
                                                MqMessageSender sender) {
        return controller(handler, properties, sender, null);
    }

    private static void assertInvalidId(Result<?> result) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("消息ID必须大于0");
    }

    private static void assertMqWritePermission(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = MqAdminController.class.getDeclaredMethod(methodName, parameterTypes);
        RequirePermission permission = method.getAnnotation(RequirePermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.logicalAnd()).isTrue();
        assertThat(permission.value()).containsExactly("mq:view", "mq:retry");
    }

    private static MqAdminController controller(DeadLetterHandler handler,
                                                MqProperties properties,
                                                MqMessageSender sender,
                                                MqRetryScheduler scheduler) {
        return controller(handler, properties, sender, scheduler, auditService());
    }

    private static MqAdminController controller(DeadLetterHandler handler,
                                                MqProperties properties,
                                                MqMessageSender sender,
                                                MqRetryScheduler scheduler,
                                                AdminAuditService auditService) {
        MqAdminService service = new MqAdminService(
                provider(handler),
                provider(scheduler),
                provider(properties),
                provider(sender),
                provider(null),
                new StaticApplicationContext(),
                auditService);
        return new MqAdminController(service);
    }

    private static MqAdminController failingController() {
        MqAdminService service = new MqAdminService(
                failingProvider(),
                failingProvider(),
                failingProvider(),
                failingProvider(),
                failingProvider(),
                new StaticApplicationContext(),
                auditService());
        return new MqAdminController(service);
    }

    private static MqFailedMessage failedMessage(Long id, String traceId, String status) {
        MqFailedMessage message = new MqFailedMessage();
        message.setId(id);
        message.setMessageId("msg-" + id);
        message.setTraceId(traceId);
        message.setBusinessKey("order-" + id);
        message.setMessageType("OrderCreated");
        message.setExchange("order.exchange");
        message.setRoutingKey("order.created");
        message.setQueueName("order.queue");
        message.setPayload("{}");
        message.setErrorStack("stack-" + id);
        message.setRetryCount(0);
        message.setMaxRetry(3);
        message.setStatus(status);
        message.setCreateTime(new Date());
        message.setUpdateTime(new Date());
        return message;
    }

    private static MqFailedMessage withNextRetryTime(MqFailedMessage message) {
        message.setNextRetryTime(new Date());
        return message;
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }
        };
    }

    private static <T> ObjectProvider<T> failingProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                throw new IllegalStateException("mq provider unavailable");
            }

            @Override
            public T getIfAvailable() {
                throw new IllegalStateException("mq provider unavailable");
            }

            @Override
            public T getIfUnique() {
                throw new IllegalStateException("mq provider unavailable");
            }

            @Override
            public T getObject() {
                throw new IllegalStateException("mq provider unavailable");
            }

            @Override
            public Stream<T> stream() {
                throw new IllegalStateException("mq provider unavailable");
            }
        };
    }

    private static MqMessageSender sender(MqProperties.Provider provider) {
        return new MqMessageSender() {
            @Override
            public MqProperties.Provider provider() {
                return provider;
            }

            @Override
            public <T> void send(String destination, String routingKey, MessageWrapper<T> wrapper) {
            }
        };
    }

    private static AdminAuditService auditService() {
        return new AdminAuditService(null, null) {
            @Override
            public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            }

            @Override
            public void failure(HttpServletRequest request, String module, String action, String operationType,
                                Object params, Exception exception) {
            }
        };
    }

    private static class ThrowingAuditService extends AdminAuditService {
        private ThrowingAuditService() {
            super(null, null);
        }

        @Override
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            throw new IllegalStateException("audit unavailable");
        }
    }

    private static class RecordingAuditService extends AdminAuditService {
        private String action;
        private Map<String, Object> params;

        private RecordingAuditService() {
            super(null, null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            this.action = action;
            this.params = (Map<String, Object>) params;
        }
    }

    private static class InMemoryMqFailedMessageRepository implements MqFailedMessageRepository {
        private final Map<Long, MqFailedMessage> messages = new LinkedHashMap<>();
        private boolean failOnSave;
        private boolean updateAffected = true;

        private InMemoryMqFailedMessageRepository(List<MqFailedMessage> initialMessages) {
            initialMessages.forEach(message -> messages.put(message.getId(), message));
        }

        @Override
        public MqFailedMessage save(MqFailedMessage message) {
            if (failOnSave) {
                throw new IllegalStateException("save failed");
            }
            messages.put(message.getId(), message);
            return message;
        }

        @Override
        public boolean update(MqFailedMessage message) {
            if (failOnSave) {
                throw new IllegalStateException("save failed");
            }
            if (!updateAffected) {
                return false;
            }
            messages.put(message.getId(), message);
            return true;
        }

        @Override
        public Optional<MqFailedMessage> findById(Long id) {
            return Optional.ofNullable(messages.get(id));
        }

        @Override
        public List<MqFailedMessage> findAll() {
            return List.copyOf(messages.values());
        }

        @Override
        public boolean deleteById(Long id) {
            return messages.remove(id) != null;
        }

        @Override
        public int deleteProcessed() {
            int before = messages.size();
            messages.values().removeIf(message -> MqFailedMessage.STATUS_SUCCESS.equals(message.getStatus())
                    || MqFailedMessage.STATUS_EXHAUSTED.equals(message.getStatus())
                    || MqFailedMessage.STATUS_MANUAL.equals(message.getStatus()));
            return before - messages.size();
        }
    }
}
