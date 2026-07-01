package com.framework.admin.localmessage;

import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.localmessage.config.LocalMessageProperties;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.mapper.LocalMessageMapper;
import com.framework.localmessage.service.LocalMessageService;
import com.framework.security.annotation.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMessageAdminControllerTest {

    private static final String TABLE_NAME = "framework_local_message";

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void writeEndpointsRequireBothViewAndRetryPermissions() throws NoSuchMethodException {
        assertLocalMessageWritePermission("retryDueMessages", HttpServletRequest.class);
        assertLocalMessageWritePermission("retryNow", Long.class, HttpServletRequest.class);
        assertLocalMessageWritePermission("markSuccess", Long.class, HttpServletRequest.class);
        assertLocalMessageWritePermission("markFailure", Long.class, LocalMessageAdminController.FailureRequest.class,
                HttpServletRequest.class);
        assertLocalMessageWritePermission("delete", Long.class, HttpServletRequest.class);
    }

    @Test
    void manualFailureMarksMessageAsTerminalFailed() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        LocalMessage message = localMessage(1L)
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(2)
                .setMaxRetry(3)
                .setNextRetryTime(LocalDateTime.now().plusMinutes(5));
        mapper.save(message);
        LocalMessageAdminController.FailureRequest request = new LocalMessageAdminController.FailureRequest();
        request.setReason("\u00A0manual stop\u3000");
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.markFailure(1L, request, null);

        assertThat(result.isSuccess()).isTrue();
        LocalMessage saved = mapper.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.FAILED);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getNextRetryTime()).isNull();
        assertThat(saved.getErrorMessage()).isEqualTo("manual stop");
    }

    @Test
    void manualFailureUsesDefaultReasonWhenReasonIsBlank() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(5L)
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(1)
                .setNextRetryTime(LocalDateTime.now().plusMinutes(5)));
        LocalMessageAdminController.FailureRequest request = new LocalMessageAdminController.FailureRequest();
        request.setReason("\u00A0\u3000");
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.markFailure(5L, request, null);

        assertThat(result.isSuccess()).isTrue();
        LocalMessage saved = mapper.findById(5L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.FAILED);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getNextRetryTime()).isNull();
        assertThat(saved.getErrorMessage()).isEqualTo("manual terminate");
    }

    @Test
    void manualSuccessClearsRetryState() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(2L)
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(2)
                .setErrorMessage("old error")
                .setNextRetryTime(LocalDateTime.now().plusMinutes(5)));
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.markSuccess(2L, null);

        assertThat(result.isSuccess()).isTrue();
        LocalMessage saved = mapper.findById(2L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.SUCCESS);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getNextRetryTime()).isNull();
    }

    @Test
    void manualRetryResetsMessageForImmediateRetry() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(3L)
                .setStatus(LocalMessageStatus.FAILED)
                .setRetryCount(3)
                .setMaxRetry(3)
                .setErrorMessage("handler missing")
                .setNextRetryTime(null));
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.retryNow(3L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已加入重试队列");
        LocalMessage saved = mapper.findById(3L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getNextRetryTime()).isNotNull();
    }

    @Test
    void manualActionsUseCurrentUserAsOperator() {
        UserContextHolder.set(new LoginUser().setUserId(7L).setUsername("alice"));
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(21L)
                .setStatus(LocalMessageStatus.FAILED)
                .setRetryCount(3)
                .setErrorMessage("handler missing")
                .setNextRetryTime(null));
        mapper.save(localMessage(22L)
                .setStatus(LocalMessageStatus.PENDING)
                .setErrorMessage("old error")
                .setNextRetryTime(LocalDateTime.now().plusMinutes(5)));
        mapper.save(localMessage(23L)
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(1)
                .setNextRetryTime(LocalDateTime.now().plusMinutes(5)));
        mapper.save(localMessage(24L).setStatus(LocalMessageStatus.SUCCESS));
        RecordingAuditService auditService = new RecordingAuditService();
        LocalMessageAdminController controller = controller(mapper, auditService);

        Result<String> retryResult = controller.retryNow(21L, null);
        assertThat(retryResult.isSuccess()).isTrue();
        assertThat(mapper.findById(21L).orElseThrow().getOperator()).isEqualTo("alice");
        assertThat(auditService.action).isEqualTo("人工立即重试本地消息");
        assertThat(auditService.params).containsEntry("operator", "alice");

        Result<String> successResult = controller.markSuccess(22L, null);
        assertThat(successResult.isSuccess()).isTrue();
        assertThat(mapper.findById(22L).orElseThrow().getOperator()).isEqualTo("alice");
        assertThat(auditService.action).isEqualTo("人工标记本地消息成功");
        assertThat(auditService.params).containsEntry("operator", "alice");

        LocalMessageAdminController.FailureRequest request = new LocalMessageAdminController.FailureRequest();
        request.setReason("manual stop");
        Result<String> failureResult = controller.markFailure(23L, request, null);
        assertThat(failureResult.isSuccess()).isTrue();
        assertThat(mapper.findById(23L).orElseThrow().getOperator()).isEqualTo("alice");
        assertThat(auditService.action).isEqualTo("人工标记本地消息失败");
        assertThat(auditService.params).containsEntry("operator", "alice");

        Result<String> deleteResult = controller.delete(24L, null);
        assertThat(deleteResult.isSuccess()).isTrue();
        assertThat(mapper.findById(24L)).isEmpty();
        assertThat(auditService.action).isEqualTo("删除本地消息");
        assertThat(auditService.params).containsEntry("operator", "alice");
    }

    @Test
    void manualRetrySucceedsWhenAuditServiceFails() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(3L)
                .setStatus(LocalMessageStatus.FAILED)
                .setRetryCount(3)
                .setErrorMessage("handler missing")
                .setNextRetryTime(null));
        LocalMessageAdminController controller = controller(mapper, new ThrowingAuditService());

        Result<String> result = controller.retryNow(3L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已加入重试队列");
        LocalMessage saved = mapper.findById(3L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void manualRetryKeepsOriginalMessageWhenSaveFails() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(7L)
                .setStatus(LocalMessageStatus.FAILED)
                .setRetryCount(3)
                .setErrorMessage("handler missing")
                .setNextRetryTime(null));
        mapper.failOnSave = true;
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.retryNow(7L, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("本地消息操作失败");
        LocalMessage saved = mapper.findById(7L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.FAILED);
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getErrorMessage()).isEqualTo("handler missing");
        assertThat(saved.getNextRetryTime()).isNull();
    }

    @Test
    void manualSuccessSucceedsWhenAuditServiceFails() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(4L)
                .setStatus(LocalMessageStatus.PENDING)
                .setErrorMessage("old error")
                .setNextRetryTime(LocalDateTime.now().plusMinutes(5)));
        LocalMessageAdminController controller = controller(mapper, new ThrowingAuditService());

        Result<String> result = controller.markSuccess(4L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已标记成功");
        LocalMessage saved = mapper.findById(4L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.SUCCESS);
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getNextRetryTime()).isNull();
    }

    @Test
    void manualSuccessKeepsOriginalMessageWhenSaveFails() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(5);
        mapper.save(localMessage(8L)
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(2)
                .setErrorMessage("old error")
                .setNextRetryTime(nextRetryTime));
        mapper.failOnSave = true;
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.markSuccess(8L, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("本地消息操作失败");
        LocalMessage saved = mapper.findById(8L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(2);
        assertThat(saved.getErrorMessage()).isEqualTo("old error");
        assertThat(saved.getNextRetryTime()).isEqualTo(nextRetryTime);
    }

    @Test
    void manualUpdateFailsWhenMessageDoesNotExist() {
        LocalMessageAdminController controller = controller(new FakeLocalMessageMapper());

        Result<String> result = controller.markSuccess(404L, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
    }

    @Test
    void manualRetryReturnsNotFoundWhenMessageDisappearsBeforeUpdate() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(11L)
                .setStatus(LocalMessageStatus.FAILED)
                .setRetryCount(3)
                .setErrorMessage("handler missing"));
        mapper.updateAffected = false;
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.retryNow(11L, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
        LocalMessage saved = mapper.findById(11L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.FAILED);
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getErrorMessage()).isEqualTo("handler missing");
    }

    @Test
    void manualSuccessReturnsNotFoundWhenMessageDisappearsBeforeUpdate() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(12L)
                .setStatus(LocalMessageStatus.PENDING)
                .setErrorMessage("old error"));
        mapper.updateAffected = false;
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.markSuccess(12L, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
        LocalMessage saved = mapper.findById(12L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getErrorMessage()).isEqualTo("old error");
    }

    @Test
    void manualFailureReturnsNotFoundWhenMessageDisappearsBeforeUpdate() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(13L)
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(1));
        mapper.updateAffected = false;
        LocalMessageAdminController.FailureRequest request = new LocalMessageAdminController.FailureRequest();
        request.setReason("manual stop");
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.markFailure(13L, request, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
        LocalMessage saved = mapper.findById(13L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
    }

    @Test
    void manualFailureKeepsOriginalMessageWhenSaveFails() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        LocalDateTime nextRetryTime = LocalDateTime.now().plusMinutes(5);
        mapper.save(localMessage(9L)
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(1)
                .setErrorMessage("old error")
                .setNextRetryTime(nextRetryTime));
        mapper.failOnSave = true;
        LocalMessageAdminController.FailureRequest request = new LocalMessageAdminController.FailureRequest();
        request.setReason("manual stop");
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.markFailure(9L, request, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("本地消息操作失败");
        LocalMessage saved = mapper.findById(9L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getErrorMessage()).isEqualTo("old error");
        assertThat(saved.getNextRetryTime()).isEqualTo(nextRetryTime);
    }

    @Test
    void deleteRemovesExistingMessage() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(6L));
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.delete(6L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已删除");
        assertThat(mapper.findById(6L)).isEmpty();
    }

    @Test
    void deleteFailsWhenMessageDoesNotExist() {
        LocalMessageAdminController controller = controller(new FakeLocalMessageMapper());

        Result<String> result = controller.delete(404L, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
    }

    @Test
    void deleteReturnsNotFoundWhenMessageDisappearsBeforeDelete() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(14L));
        mapper.deleteAffected = false;
        LocalMessageAdminController controller = controller(mapper);

        Result<String> result = controller.delete(14L, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
        assertThat(mapper.findById(14L)).isPresent();
    }

    @Test
    void idEndpointsRejectInvalidIdBeforeProviderLookup() {
        LocalMessageAdminController controller = failingController();
        LocalMessageAdminController.FailureRequest failureRequest = new LocalMessageAdminController.FailureRequest();

        assertInvalidId(controller.detail(0L));
        assertInvalidId(controller.retryNow(0L, null));
        assertInvalidId(controller.markSuccess(0L, null));
        assertInvalidId(controller.markFailure(0L, failureRequest, null));
        assertInvalidId(controller.delete(0L, null));
    }

    @Test
    void detailReportsServiceUnavailableWhenLocalMessageTableIsMissing() {
        LocalMessageAdminController controller = disabledController();

        Result<LocalMessageVO> result = controller.detail(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("本地消息表未启用");
    }

    @Test
    void queryEndpointsFallBackWhenLocalMessageProviderFails() {
        LocalMessageAdminController controller = failingController();

        Result<Map<String, Long>> stats = controller.stats();
        Result<PageResult<LocalMessageVO>> page = controller.list(null, null, null, null, -1, 500);
        Result<LocalMessageVO> detail = controller.detail(1L);

        assertThat(stats.isSuccess()).isTrue();
        assertThat(stats.getData()).containsEntry("TOTAL", 0L);
        assertThat(page.isSuccess()).isTrue();
        assertThat(page.getData().getPageNum()).isEqualTo(1);
        assertThat(page.getData().getPageSize()).isEqualTo(200);
        assertThat(page.getData().getRecords()).isEmpty();
        assertThat(detail.isSuccess()).isFalse();
        assertThat(detail.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(detail.getMessage()).isEqualTo("本地消息表未启用");
    }

    @Test
    void manualEndpointsReportServiceErrorWhenMapperProviderFails() {
        LocalMessageAdminController controller = failingController();

        Result<String> retry = controller.retryNow(1L, null);
        Result<String> success = controller.markSuccess(1L, null);
        LocalMessageAdminController.FailureRequest failureRequest = new LocalMessageAdminController.FailureRequest();
        Result<String> failure = controller.markFailure(1L, failureRequest, null);
        Result<String> delete = controller.delete(1L, null);

        assertThat(retry.isSuccess()).isFalse();
        assertThat(retry.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(retry.getMessage()).isEqualTo("本地消息表未启用");
        assertThat(success.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(failure.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(delete.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
    }

    @Test
    void listFiltersAndUsesSafePaging() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(1L)
                .setTopic("order.created")
                .setTraceId("trace-a")
                .setBusinessKey("biz-a")
                .setCreateTime(LocalDateTime.now().minusMinutes(1)));
        mapper.save(localMessage(2L)
                .setTopic("inventory.changed")
                .setTraceId("trace-b")
                .setBusinessKey("biz-b")
                .setCreateTime(LocalDateTime.now()));
        LocalMessageAdminController controller = controller(mapper);

        Result<PageResult<LocalMessageVO>> result = controller.list(
                "order.created", null, "trace", "biz-a", -1, 500);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getPageNum()).isEqualTo(1);
        assertThat(result.getData().getPageSize()).isEqualTo(200);
        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getRecords()).extracting(LocalMessageVO::getId).containsExactly(1L);
    }

    @Test
    void listTrimsFiltersAndKeepsNullCreateTimeLast() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(1L)
                .setTopic("order.created")
                .setTraceId("trace-a")
                .setBusinessKey("ORD-1")
                .setCreateTime(null));
        mapper.save(localMessage(2L)
                .setTopic("order.created")
                .setTraceId("trace-a")
                .setBusinessKey("ORD-2")
                .setCreateTime(LocalDateTime.now()));
        LocalMessageAdminController controller = controller(mapper);

        Result<PageResult<LocalMessageVO>> result = controller.list(
                "\u00A0order.created\u3000", null, "\u3000trace-a\u00A0", "\u00A0ORD-\u3000", 1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isEqualTo(2);
        assertThat(result.getData().getRecords()).extracting(LocalMessageVO::getId).containsExactly(2L, 1L);
        assertThat(result.getData().getRecords().get(1).getCreateTime()).isNull();
    }

    @Test
    void listNormalizesStatusFilter() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(1L)
                .setStatus(LocalMessageStatus.PENDING)
                .setCreateTime(LocalDateTime.now().minusMinutes(1)));
        mapper.save(localMessage(2L)
                .setStatus(LocalMessageStatus.SUCCESS)
                .setCreateTime(LocalDateTime.now()));
        LocalMessageAdminController controller = controller(mapper);

        Result<PageResult<LocalMessageVO>> result = controller.list(
                null, "\u00A0pending\u3000", null, null, 1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getRecords()).extracting(LocalMessageVO::getId).containsExactly(1L);
        assertThat(result.getData().getRecords().get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void listReturnsEmptyPageForInvalidStatusFilter() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(1L)
                .setStatus(LocalMessageStatus.PENDING)
                .setCreateTime(LocalDateTime.now()));
        LocalMessageAdminController controller = controller(mapper);

        Result<PageResult<LocalMessageVO>> result = controller.list(
                null, "ARCHIVED", null, null, 1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isZero();
        assertThat(result.getData().getRecords()).isEmpty();
    }

    @Test
    void listReturnsEmptyPageForInvalidTraceIdFilter() {
        FakeLocalMessageMapper mapper = new FakeLocalMessageMapper();
        mapper.save(localMessage(1L)
                .setTopic("order.created")
                .setTraceId("trace-a")
                .setCreateTime(LocalDateTime.now()));
        LocalMessageAdminController controller = controller(mapper);

        Result<PageResult<LocalMessageVO>> result = controller.list(
                null, null, "bad\ntrace", null, 1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isZero();
        assertThat(result.getData().getRecords()).isEmpty();
    }

    private static LocalMessageAdminController controller(FakeLocalMessageMapper mapper) {
        return controller(mapper, auditService());
    }

    private static LocalMessageAdminController controller(FakeLocalMessageMapper mapper,
                                                         AdminAuditService auditService) {
        LocalMessageAdminService service = new LocalMessageAdminService(
                provider(localMessageService(mapper)),
                provider(mapper),
                provider(properties()),
                auditService);
        return new LocalMessageAdminController(service);
    }

    private static LocalMessageAdminController disabledController() {
        LocalMessageAdminService service = new LocalMessageAdminService(
                provider(null),
                provider(null),
                provider(null),
                auditService());
        return new LocalMessageAdminController(service);
    }

    private static LocalMessageAdminController failingController() {
        LocalMessageAdminService service = new LocalMessageAdminService(
                failingProvider(),
                failingProvider(),
                failingProvider(),
                auditService());
        return new LocalMessageAdminController(service);
    }

    private static LocalMessage localMessage(Long id) {
        return new LocalMessage()
                .setId(id)
                .setMessageId("local-" + id)
                .setTraceId("trace-" + id)
                .setTopic("topic")
                .setBusinessKey("biz-" + id)
                .setPayload("{}")
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(0)
                .setMaxRetry(3);
    }

    private static LocalMessageProperties properties() {
        LocalMessageProperties properties = new LocalMessageProperties();
        properties.setTableName(TABLE_NAME);
        return properties;
    }

    private static void assertInvalidId(Result<?> result) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("本地消息ID必须大于0");
    }

    private static void assertLocalMessageWritePermission(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = LocalMessageAdminController.class.getDeclaredMethod(methodName, parameterTypes);
        RequirePermission permission = method.getAnnotation(RequirePermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.logicalAnd()).isTrue();
        assertThat(permission.value()).containsExactly("local-message:view", "local-message:retry");
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
                throw new IllegalStateException("local message provider unavailable");
            }

            @Override
            public T getIfAvailable() {
                throw new IllegalStateException("local message provider unavailable");
            }

            @Override
            public T getIfUnique() {
                throw new IllegalStateException("local message provider unavailable");
            }

            @Override
            public T getObject() {
                throw new IllegalStateException("local message provider unavailable");
            }

            @Override
            public Stream<T> stream() {
                return Stream.empty();
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

    private static LocalMessageService localMessageService(FakeLocalMessageMapper mapper) {
        return new LocalMessageService() {
            @Override
            public LocalMessage publish(LocalMessage message) {
                return null;
            }

            @Override
            public int retryDueMessages() {
                return mapper.findDueMessages(LocalDateTime.now(), Integer.MAX_VALUE).size();
            }

            @Override
            public boolean retryNow(Long id) {
                Optional<LocalMessage> optionalMessage = mapper.findById(id);
                optionalMessage.ifPresent(message -> {
                    message.setStatus(LocalMessageStatus.SUCCESS);
                    message.setErrorMessage(null);
                    message.setNextRetryTime(null);
                    mapper.save(message);
                });
                return optionalMessage.isPresent();
            }

            @Override
            public void markSuccess(Long id) {
            }

            @Override
            public void markFailure(Long id, Exception exception) {
            }

            @Override
            public Optional<LocalMessage> findById(Long id) {
                return mapper.findById(id);
            }

        };
    }

    private static class FakeLocalMessageMapper implements LocalMessageMapper {
        private final Map<Long, LocalMessage> messages = new LinkedHashMap<>();
        private boolean failOnSave;
        private boolean updateAffected = true;
        private boolean deleteAffected = true;

        public LocalMessage save(LocalMessage message) {
            if (failOnSave) {
                throw new IllegalStateException("save failed");
            }
            messages.put(message.getId(), message);
            return message;
        }

        public boolean update(LocalMessage message) {
            if (failOnSave) {
                throw new IllegalStateException("save failed");
            }
            if (!updateAffected) {
                return false;
            }
            messages.put(message.getId(), message);
            return true;
        }

        public Optional<LocalMessage> findById(Long id) {
            return Optional.ofNullable(messages.get(id));
        }

        public List<LocalMessage> findDueMessages(LocalDateTime now, int limit) {
            return findDueMessages(TABLE_NAME, LocalMessageStatus.PENDING, now, limit);
        }

        public boolean delete(Long id) {
            if (!deleteAffected) {
                return false;
            }
            return messages.remove(id) != null;
        }

        @Override
        public void createTableIfNotExists(String tableName) {
        }

        @Override
        public int insert(String tableName, LocalMessage message) {
            save(message);
            return 1;
        }

        @Override
        public int update(String tableName, LocalMessage message) {
            return update(message) ? 1 : 0;
        }

        @Override
        public LocalMessage findById(String tableName, Long id) {
            return messages.get(id);
        }

        @Override
        public List<LocalMessage> findDueMessages(String tableName,
                                                  LocalMessageStatus status,
                                                  LocalDateTime now,
                                                  int limit) {
            return messages.values().stream()
                    .filter(message -> status == message.getStatus())
                    .filter(message -> message.getNextRetryTime() == null
                            || !message.getNextRetryTime().isAfter(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<LocalMessage> list(String tableName,
                                       String topic,
                                       LocalMessageStatus status,
                                       String traceIdLike,
                                       String businessKeyLike,
                                       int offset,
                                       int pageSize) {
            return messages.values().stream()
                    .filter(message -> matches(message, topic, status, traceIdLike, businessKeyLike))
                    .sorted(Comparator
                            .comparing(LocalMessage::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(LocalMessage::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                    .skip(offset)
                    .limit(pageSize)
                    .toList();
        }

        @Override
        public long count(String tableName,
                          String topic,
                          LocalMessageStatus status,
                          String traceIdLike,
                          String businessKeyLike) {
            return messages.values().stream()
                    .filter(message -> matches(message, topic, status, traceIdLike, businessKeyLike))
                    .count();
        }

        @Override
        public long countAll(String tableName) {
            return messages.size();
        }

        @Override
        public long countByStatus(String tableName, LocalMessageStatus status) {
            return messages.values().stream()
                    .filter(message -> status == message.getStatus())
                    .count();
        }

        @Override
        public int delete(String tableName, Long id) {
            return delete(id) ? 1 : 0;
        }

        private boolean matches(LocalMessage message,
                                String topic,
                                LocalMessageStatus status,
                                String traceIdLike,
                                String businessKeyLike) {
            return (topic == null || topic.equals(message.getTopic()))
                    && (status == null || status == message.getStatus())
                    && matchesLike(message.getTraceId(), traceIdLike)
                    && matchesLike(message.getBusinessKey(), businessKeyLike);
        }

        private boolean matchesLike(String value, String like) {
            if (like == null) {
                return true;
            }
            String keyword = like.replace("%", "");
            return value != null && value.contains(keyword);
        }
    }
}
