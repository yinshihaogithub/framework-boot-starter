package com.framework.admin.localmessage;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import com.framework.localmessage.repository.LocalMessageRepository;
import com.framework.localmessage.service.LocalMessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMessageAdminControllerTest {

    @Test
    void manualFailureMarksMessageAsTerminalFailed() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        LocalMessage message = localMessage(1L)
                .setStatus(LocalMessageStatus.PENDING)
                .setRetryCount(0)
                .setMaxRetry(3)
                .setNextRetryTime(LocalDateTime.now().plusMinutes(5));
        repository.save(message);
        LocalMessageAdminController.FailureRequest request = new LocalMessageAdminController.FailureRequest();
        request.setReason("manual stop");
        LocalMessageAdminController controller = controller(repository);

        Result<String> result = controller.markFailure(1L, request, null);

        assertThat(result.isSuccess()).isTrue();
        LocalMessage saved = repository.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.FAILED);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getNextRetryTime()).isNull();
        assertThat(saved.getErrorMessage()).isEqualTo("manual stop");
    }

    @Test
    void manualSuccessClearsRetryState() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        repository.save(localMessage(2L)
                .setStatus(LocalMessageStatus.PENDING)
                .setErrorMessage("old error")
                .setNextRetryTime(LocalDateTime.now().plusMinutes(5)));
        LocalMessageAdminController controller = controller(repository);

        Result<String> result = controller.markSuccess(2L, null);

        assertThat(result.isSuccess()).isTrue();
        LocalMessage saved = repository.findById(2L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.SUCCESS);
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getNextRetryTime()).isNull();
    }

    @Test
    void manualRetryResetsMessageForImmediateRetry() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        repository.save(localMessage(3L)
                .setStatus(LocalMessageStatus.FAILED)
                .setRetryCount(3)
                .setMaxRetry(3)
                .setErrorMessage("handler missing")
                .setNextRetryTime(null));
        LocalMessageAdminController controller = controller(repository);

        Result<String> result = controller.retryNow(3L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已加入重试队列");
        LocalMessage saved = repository.findById(3L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getNextRetryTime()).isNotNull();
    }

    @Test
    void manualRetrySucceedsWhenAuditServiceFails() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        repository.save(localMessage(3L)
                .setStatus(LocalMessageStatus.FAILED)
                .setRetryCount(3)
                .setErrorMessage("handler missing")
                .setNextRetryTime(null));
        LocalMessageAdminController controller = controller(repository, new ThrowingAuditService());

        Result<String> result = controller.retryNow(3L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已加入重试队列");
        LocalMessage saved = repository.findById(3L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void manualSuccessSucceedsWhenAuditServiceFails() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        repository.save(localMessage(4L)
                .setStatus(LocalMessageStatus.PENDING)
                .setErrorMessage("old error")
                .setNextRetryTime(LocalDateTime.now().plusMinutes(5)));
        LocalMessageAdminController controller = controller(repository, new ThrowingAuditService());

        Result<String> result = controller.markSuccess(4L, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("已标记成功");
        LocalMessage saved = repository.findById(4L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LocalMessageStatus.SUCCESS);
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getNextRetryTime()).isNull();
    }

    @Test
    void manualUpdateFailsWhenMessageDoesNotExist() {
        LocalMessageAdminController controller = controller(new InMemoryLocalMessageRepository());

        Result<String> result = controller.markSuccess(404L, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("消息不存在");
    }

    @Test
    void detailReportsServiceUnavailableWhenLocalMessageServiceIsMissing() {
        LocalMessageAdminController controller = disabledController();

        Result<LocalMessageVO> result = controller.detail(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("本地消息服务未启用");
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
        assertThat(detail.getMessage()).isEqualTo("本地消息服务未启用");
    }

    @Test
    void manualEndpointsReportServiceErrorWhenRepositoryProviderFails() {
        LocalMessageAdminController controller = failingController();

        Result<String> retry = controller.retryNow(1L, null);
        Result<String> success = controller.markSuccess(1L, null);
        LocalMessageAdminController.FailureRequest failureRequest = new LocalMessageAdminController.FailureRequest();
        Result<String> failure = controller.markFailure(1L, failureRequest, null);
        Result<String> delete = controller.delete(1L, null);

        assertThat(retry.isSuccess()).isFalse();
        assertThat(retry.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(retry.getMessage()).isEqualTo("本地消息仓储未启用");
        assertThat(success.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(failure.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(delete.getCode()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
    }

    @Test
    void listFiltersAndUsesSafePaging() {
        InMemoryLocalMessageRepository repository = new InMemoryLocalMessageRepository();
        repository.save(localMessage(1L)
                .setTopic("order.created")
                .setTraceId("trace-a")
                .setBusinessKey("biz-a")
                .setCreateTime(LocalDateTime.now().minusMinutes(1)));
        repository.save(localMessage(2L)
                .setTopic("inventory.changed")
                .setTraceId("trace-b")
                .setBusinessKey("biz-b")
                .setCreateTime(LocalDateTime.now()));
        LocalMessageAdminController controller = controller(repository);

        Result<PageResult<LocalMessageVO>> result = controller.list(
                "order.created", null, "trace", "biz-a", -1, 500);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getPageNum()).isEqualTo(1);
        assertThat(result.getData().getPageSize()).isEqualTo(200);
        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getRecords()).extracting(LocalMessageVO::getId).containsExactly(1L);
    }

    private static LocalMessageAdminController controller(LocalMessageRepository repository) {
        return controller(repository, auditService());
    }

    private static LocalMessageAdminController controller(LocalMessageRepository repository,
                                                         AdminAuditService auditService) {
        LocalMessageAdminService service = new LocalMessageAdminService(
                provider(localMessageService(repository)),
                provider(repository),
                auditService);
        return new LocalMessageAdminController(service);
    }

    private static LocalMessageAdminController disabledController() {
        LocalMessageAdminService service = new LocalMessageAdminService(
                provider(null),
                provider(null),
                auditService());
        return new LocalMessageAdminController(service);
    }

    private static LocalMessageAdminController failingController() {
        LocalMessageAdminService service = new LocalMessageAdminService(
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

    private static LocalMessageService localMessageService(LocalMessageRepository repository) {
        return new LocalMessageService() {
            @Override
            public LocalMessage publish(String topic, String businessKey, String payload) {
                return null;
            }

            @Override
            public int retryDueMessages() {
                return repository.findDueMessages(LocalDateTime.now(), Integer.MAX_VALUE).size();
            }

            @Override
            public boolean retryNow(Long id) {
                Optional<LocalMessage> optionalMessage = repository.findById(id);
                optionalMessage.ifPresent(message -> {
                    message.setStatus(LocalMessageStatus.SUCCESS);
                    message.setErrorMessage(null);
                    message.setNextRetryTime(null);
                    repository.save(message);
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
                return repository.findById(id);
            }

            @Override
            public List<LocalMessage> findAll() {
                return repository.findAll();
            }
        };
    }

    private static class InMemoryLocalMessageRepository implements LocalMessageRepository {
        private final Map<Long, LocalMessage> messages = new LinkedHashMap<>();

        @Override
        public LocalMessage save(LocalMessage message) {
            messages.put(message.getId(), message);
            return message;
        }

        @Override
        public Optional<LocalMessage> findById(Long id) {
            return Optional.ofNullable(messages.get(id));
        }

        @Override
        public List<LocalMessage> findDueMessages(LocalDateTime now, int limit) {
            return messages.values().stream()
                    .filter(message -> LocalMessageStatus.PENDING == message.getStatus())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<LocalMessage> findAll() {
            return new ArrayList<>(messages.values());
        }

        @Override
        public void delete(Long id) {
            messages.remove(id);
        }
    }
}
