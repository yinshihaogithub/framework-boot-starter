package com.framework.admin.notify;

import com.framework.admin.audit.AdminAuditService;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.result.ResultCode;
import com.framework.core.result.PageResult;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import com.framework.notify.service.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyAdminServiceTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void validatesRequiredTemplateFields() {
        NotifyAdminService service = service(new InMemoryNotifyAdminRepository(), null);
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setTemplateCode("\u00A0\u3000");

        NotifyAdminService.ActionResult<Long> result = service.createTemplate(request, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.message()).isEqualTo("模板编码不能为空");
    }

    @Test
    void rejectsUnsupportedTemplateChannel() {
        NotifyAdminService service = service(new InMemoryNotifyAdminRepository(), null);
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setChannel("ding-talk");

        NotifyAdminService.ActionResult<Long> result = service.createTemplate(request, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.message()).isEqualTo("通知通道不支持");
    }

    @Test
    void rejectsUnsupportedTemplateStatus() {
        NotifyAdminService service = service(new InMemoryNotifyAdminRepository(), null);
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setStatus("ARCHIVED");

        NotifyAdminService.ActionResult<Long> result = service.createTemplate(request, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.message()).isEqualTo("状态只能是 ENABLED 或 DISABLED");
    }

    @Test
    void listsTemplatesWithSafePaging() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        repository.createTemplate(templateRequest());
        NotifyAdminService service = service(repository, null);

        PageResult<NotifyAdminModels.Template> page = service.templates(null, null, null, -1, 500);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getTotal()).isEqualTo(1);
    }

    @Test
    void templatesNormalizeFiltersBeforeQueryingRepository() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        repository.createTemplate(templateRequest());
        NotifyAdminModels.TemplateRequest otherTemplate = templateRequest();
        otherTemplate.setTemplateCode("alert");
        otherTemplate.setTemplateName("告警通知");
        otherTemplate.setTitle("alert");
        otherTemplate.setChannel("EMAIL");
        otherTemplate.setStatus("DISABLED");
        repository.createTemplate(otherTemplate);
        NotifyAdminService service = service(repository, null);

        PageResult<NotifyAdminModels.Template> page =
                service.templates("\u00A0welcome\u3000", "\u3000log\u00A0", "\u00A0enabled\u3000", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(NotifyAdminModels.Template::getTemplateCode)
                .containsExactly("welcome");
    }

    @Test
    void templatesReturnEmptyPageForInvalidChannelOrStatusFilter() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        repository.createTemplate(templateRequest());
        NotifyAdminService service = service(repository, null);

        PageResult<NotifyAdminModels.Template> invalidChannel = service.templates(null, "ding-talk", null, 1, 20);
        PageResult<NotifyAdminModels.Template> invalidStatus = service.templates(null, null, "ARCHIVED", 1, 20);

        assertThat(invalidChannel.getTotal()).isZero();
        assertThat(invalidChannel.getRecords()).isEmpty();
        assertThat(invalidStatus.getTotal()).isZero();
        assertThat(invalidStatus.getRecords()).isEmpty();
    }

    @Test
    void recordsNormalizeChannelFilterBeforeQueryingRepository() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        repository.createRecord(record("LOG", true));
        repository.createRecord(record("EMAIL", true));
        NotifyAdminService service = service(repository, null);

        PageResult<NotifyAdminModels.Record> page = service.records("\u3000log\u00A0", true, 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(NotifyAdminModels.Record::getChannel)
                .containsExactly("LOG");
    }

    @Test
    void recordsReturnEmptyPageForInvalidChannelFilter() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        repository.createRecord(record("LOG", true));
        NotifyAdminService service = service(repository, null);

        PageResult<NotifyAdminModels.Record> page = service.records("ding-talk", true, 1, 20);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void queryEndpointsFallBackWhenRepositoryFails() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        repository.queryFailure = new RuntimeException("database down");
        NotifyAdminService service = service(repository, null);

        PageResult<NotifyAdminModels.Template> templates = service.templates(null, null, null, 0, 0);
        PageResult<NotifyAdminModels.Record> records = service.records(null, null, 0, 0);

        assertThat(service.stats())
                .containsEntry("enabledTemplates", 0L)
                .containsEntry("disabledTemplates", 0L)
                .containsEntry("successRecords", 0L)
                .containsEntry("failedRecords", 0L);
        assertThat(templates.getPageNum()).isEqualTo(1);
        assertThat(templates.getPageSize()).isEqualTo(20);
        assertThat(templates.getRecords()).isEmpty();
        assertThat(records.getPageNum()).isEqualTo(1);
        assertThat(records.getPageSize()).isEqualTo(20);
        assertThat(records.getRecords()).isEmpty();
    }

    @Test
    void sendTestRendersTemplateAndPersistsRecord() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        CapturingNotifyService notifyService = new CapturingNotifyService(true, "sent");
        NotifyAdminService service = service(repository, notifyService);
        NotifyAdminModels.SendRequest request = new NotifyAdminModels.SendRequest();
        request.setReceivers(List.of("ops@example.com"));
        request.setWebhookUrl("https://callback.example.com/hook");
        request.setTemplateParams(new LinkedHashMap<>());
        request.getTemplateParams().put("name", "Codex");

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> record = service.sendTest(templateId, request, null);

        assertThat(record.success()).isTrue();
        assertThat(record.data().getId()).isNotNull();
        assertThat(record.data().getContent()).isEqualTo("hello Codex");
        assertThat(record.data().getReceivers()).containsExactly("ops@example.com");
        assertThat(record.data().getWebhookUrl()).isEqualTo("https://callback.example.com/hook");
        assertThat(record.data().getSuccess()).isTrue();
        assertThat(record.data().getResultMessage()).isEqualTo("sent");
        assertThat(notifyService.message.getContent()).isEqualTo("hello Codex");
        assertThat(notifyService.message.getReceivers()).containsExactly("ops@example.com");
        assertThat(repository.records).hasSize(1);
    }

    @Test
    void sendTestRecordsCurrentUserAsOperatorAndAuditsIt() {
        UserContextHolder.set(new LoginUser().setUserId(7L).setUsername("alice"));
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        CapturingNotifyService notifyService = new CapturingNotifyService(true, "sent");
        RecordingAuditService auditService = new RecordingAuditService();
        NotifyAdminService service = service(repository, notifyService, auditService);

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> result = service.sendTest(templateId, null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getOperatorName()).isEqualTo("alice");
        assertThat(repository.records.get(0).getOperatorName()).isEqualTo("alice");
        assertThat(auditService.action).isEqualTo("发送测试通知");
        assertThat(auditService.params)
                .containsEntry("operator", "alice")
                .containsEntry("success", true);
    }

    @Test
    void sendTestDefaultsOperatorWhenUserContextIsMissing() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        NotifyAdminService service = service(repository, new CapturingNotifyService(true, "sent"));

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> result = service.sendTest(templateId, null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getOperatorName()).isEqualTo("admin");
        assertThat(repository.records.get(0).getOperatorName()).isEqualTo("admin");
    }

    @Test
    void createTemplateAuditsCurrentUserAsOperator() {
        UserContextHolder.set(new LoginUser().setUserId(7L).setUsername("alice"));
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        RecordingAuditService auditService = new RecordingAuditService();
        NotifyAdminService service = service(repository, null, auditService);

        NotifyAdminService.ActionResult<Long> result = service.createTemplate(templateRequest(), null);

        assertThat(result.success()).isTrue();
        assertThat(auditService.action).isEqualTo("新增通知模板");
        assertThat(auditService.params)
                .containsEntry("id", result.data())
                .containsEntry("operator", "alice");
    }

    @Test
    void updateTemplateAuditsCurrentUserAsOperator() {
        UserContextHolder.set(new LoginUser().setUserId(8L).setUsername("bob"));
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        RecordingAuditService auditService = new RecordingAuditService();
        NotifyAdminService service = service(repository, null, auditService);

        NotifyAdminService.ActionResult<String> result = service.updateTemplate(templateId, templateRequest(), null);

        assertThat(result.success()).isTrue();
        assertThat(auditService.action).isEqualTo("更新通知模板");
        assertThat(auditService.params)
                .containsEntry("id", templateId)
                .containsEntry("operator", "bob");
    }

    @Test
    void deleteTemplateDefaultsAuditOperatorWhenUserContextIsMissing() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        RecordingAuditService auditService = new RecordingAuditService();
        NotifyAdminService service = service(repository, null, auditService);

        NotifyAdminService.ActionResult<String> result = service.deleteTemplate(templateId, null);

        assertThat(result.success()).isTrue();
        assertThat(auditService.action).isEqualTo("删除通知模板");
        assertThat(auditService.params)
                .containsEntry("id", templateId)
                .containsEntry("operator", "admin");
    }

    @Test
    void sendTestNormalizesReceiverAndWebhookOverridesBeforeSendingAndRecording() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        CapturingNotifyService notifyService = new CapturingNotifyService(true, "sent");
        NotifyAdminService service = service(repository, notifyService);
        NotifyAdminModels.SendRequest request = new NotifyAdminModels.SendRequest();
        request.setReceivers(java.util.Arrays.asList("\u00A0ops@example.com\u3000", "\u3000", null, "dev@example.com"));
        request.setWebhookUrl("\u3000https://callback.example.com/hook\u00A0");

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> record = service.sendTest(templateId, request, null);

        assertThat(record.success()).isTrue();
        assertThat(record.data().getReceivers()).containsExactly("ops@example.com", "dev@example.com");
        assertThat(record.data().getWebhookUrl()).isEqualTo("https://callback.example.com/hook");
        assertThat(notifyService.message.getReceivers()).containsExactly("ops@example.com", "dev@example.com");
        assertThat(notifyService.message.getWebhookUrl()).isEqualTo("https://callback.example.com/hook");
        assertThat(repository.records.get(0).getReceivers()).containsExactly("ops@example.com", "dev@example.com");
    }

    @Test
    void sendTestFallsBackToTemplateReceiversWhenOverrideReceiversAreBlank() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        CapturingNotifyService notifyService = new CapturingNotifyService(true, "sent");
        NotifyAdminService service = service(repository, notifyService);
        NotifyAdminModels.SendRequest request = new NotifyAdminModels.SendRequest();
        request.setReceivers(List.of("\u00A0\u3000"));

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> record = service.sendTest(templateId, request, null);

        assertThat(record.success()).isTrue();
        assertThat(record.data().getReceivers()).containsExactly("admin@example.com");
        assertThat(notifyService.message.getReceivers()).containsExactly("admin@example.com");
    }

    @Test
    void createTemplateSucceedsWhenAuditFails() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        NotifyAdminService service = service(repository, null, new ThrowingAuditService());

        NotifyAdminService.ActionResult<Long> result = service.createTemplate(templateRequest(), null);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(repository.templates).hasSize(1);
    }

    @Test
    void updateTemplateSucceedsWhenAuditFails() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setTitle("updated");
        NotifyAdminService service = service(repository, null, new ThrowingAuditService());

        NotifyAdminService.ActionResult<String> result = service.updateTemplate(templateId, request, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo("已更新");
        assertThat(repository.findTemplate(templateId).orElseThrow().getTitle()).isEqualTo("updated");
    }

    @Test
    void deleteTemplateSucceedsWhenAuditFails() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        NotifyAdminService service = service(repository, null, new ThrowingAuditService());

        NotifyAdminService.ActionResult<String> result = service.deleteTemplate(templateId, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo("已删除");
        assertThat(repository.templates).isEmpty();
    }

    @Test
    void sendTestSucceedsWhenAuditFails() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        NotifyAdminService service = service(repository, new CapturingNotifyService(true, "sent"),
                new ThrowingAuditService());

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> result = service.sendTest(templateId, null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data().getSuccess()).isTrue();
        assertThat(repository.records).hasSize(1);
    }

    @Test
    void sendTestAcceptsLowercaseTemplateChannel() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        NotifyAdminModels.TemplateRequest templateRequest = templateRequest();
        templateRequest.setChannel("webhook");
        Long templateId = repository.createTemplate(templateRequest);
        CapturingNotifyService notifyService = new CapturingNotifyService(true, "sent");
        NotifyAdminService service = service(repository, notifyService);

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> record = service.sendTest(templateId, null, null);

        assertThat(record.success()).isTrue();
        assertThat(notifyService.message.getChannel()).isEqualTo(NotifyChannelType.WEBHOOK);
    }

    @Test
    void sendTestRecordsFailureWhenNotifyServiceIsMissing() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        NotifyAdminService service = service(repository, null);

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> record = service.sendTest(templateId, null, null);

        assertThat(record.success()).isTrue();
        assertThat(record.data().getSuccess()).isFalse();
        assertThat(record.data().getResultMessage()).isEqualTo("notify service is not enabled");
        assertThat(repository.countRecordsBySuccess(false)).isEqualTo(1);
    }

    @Test
    void sendTestRecordsFailureWhenNotifyProviderFails() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        NotifyAdminService service = new NotifyAdminService(repository, failingProvider(), auditService());

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> record = service.sendTest(templateId, null, null);

        assertThat(record.success()).isTrue();
        assertThat(record.data().getSuccess()).isFalse();
        assertThat(record.data().getResultMessage()).isEqualTo("notify service is not enabled");
        assertThat(repository.countRecordsBySuccess(false)).isEqualTo(1);
    }

    @Test
    void sendTestRecordsFailureWhenNotifySendThrows() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        NotifyAdminService service = service(repository, message -> {
            throw new IllegalStateException("webhook unavailable");
        });

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> record = service.sendTest(templateId, null, null);

        assertThat(record.success()).isTrue();
        assertThat(record.data().getSuccess()).isFalse();
        assertThat(record.data().getResultMessage()).isEqualTo("通知发送失败: webhook unavailable");
        assertThat(repository.countRecordsBySuccess(false)).isEqualTo(1);
    }

    @Test
    void sendTestDoesNotDispatchDisabledTemplateAndPersistsFailureRecord() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        NotifyAdminModels.TemplateRequest templateRequest = templateRequest();
        templateRequest.setStatus("DISABLED");
        Long templateId = repository.createTemplate(templateRequest);
        CapturingNotifyService notifyService = new CapturingNotifyService(true, "sent");
        NotifyAdminService service = service(repository, notifyService);

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> record = service.sendTest(templateId, null, null);

        assertThat(record.success()).isTrue();
        assertThat(record.data().getSuccess()).isFalse();
        assertThat(record.data().getResultMessage()).isEqualTo("模板已禁用");
        assertThat(notifyService.message).isNull();
        assertThat(repository.countRecordsBySuccess(false)).isEqualTo(1);
    }

    @Test
    void templateIdOperationsRejectInvalidIdBeforeRepositoryLookup() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        repository.queryFailure = new RuntimeException("database down");
        NotifyAdminService service = service(repository, null);

        assertInvalidTemplateId(service.updateTemplate(0L, templateRequest(), null));
        assertInvalidTemplateId(service.deleteTemplate(0L, null));
        assertInvalidTemplateId(service.sendTest(0L, null, null));
    }

    @Test
    void updateTemplateReturnsFalseWhenTemplateDoesNotExist() {
        NotifyAdminService service = service(new InMemoryNotifyAdminRepository(), null);

        NotifyAdminService.ActionResult<String> updated = service.updateTemplate(404L, templateRequest(), null);

        assertThat(updated.success()).isFalse();
        assertThat(updated.code()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(updated.message()).isEqualTo("模板不存在");
    }

    @Test
    void deleteTemplateReturnsNotFoundWhenTemplateDoesNotExist() {
        NotifyAdminService service = service(new InMemoryNotifyAdminRepository(), null);

        NotifyAdminService.ActionResult<String> deleted = service.deleteTemplate(404L, null);

        assertThat(deleted.success()).isFalse();
        assertThat(deleted.code()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(deleted.message()).isEqualTo("模板不存在");
    }

    @Test
    void updateTemplateReturnsNotFoundWhenTemplateDisappearsBeforeUpdate() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        repository.updateTemplateAffected = false;
        NotifyAdminService service = service(repository, null);

        NotifyAdminService.ActionResult<String> result = service.updateTemplate(templateId, templateRequest(), null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.message()).isEqualTo("模板不存在");
    }

    @Test
    void deleteTemplateReturnsNotFoundWhenTemplateDisappearsBeforeDelete() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        repository.deleteTemplateAffected = false;
        NotifyAdminService service = service(repository, null);

        NotifyAdminService.ActionResult<String> result = service.deleteTemplate(templateId, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(result.message()).isEqualTo("模板不存在");
        assertThat(repository.templates).hasSize(1);
    }

    @Test
    void writeOperationsReturnServiceErrorWhenRepositoryFails() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        repository.commandFailure = new RuntimeException("database down");
        NotifyAdminService service = service(repository, null);

        NotifyAdminService.ActionResult<Long> created = service.createTemplate(templateRequest(), null);
        NotifyAdminService.ActionResult<String> deleted = service.deleteTemplate(templateId, null);

        assertThat(created.success()).isFalse();
        assertThat(created.code()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(created.message()).isEqualTo("通知模板保存失败");
        assertThat(deleted.success()).isFalse();
        assertThat(deleted.code()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(deleted.message()).isEqualTo("通知模板删除失败");
    }

    @Test
    void sendTestReturnsServiceErrorWhenRecordSaveFails() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        repository.commandFailure = new RuntimeException("database down");
        NotifyAdminService service = service(repository, null);

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> result = service.sendTest(templateId, null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.message()).isEqualTo("通知测试发送失败");
    }

    private static NotifyAdminService service(InMemoryNotifyAdminRepository repository, NotifyService notifyService) {
        return new NotifyAdminService(repository, provider(notifyService), auditService());
    }

    private static NotifyAdminService service(InMemoryNotifyAdminRepository repository, NotifyService notifyService,
                                             AdminAuditService auditService) {
        return new NotifyAdminService(repository, provider(notifyService), auditService);
    }

    private static NotifyAdminModels.TemplateRequest templateRequest() {
        NotifyAdminModels.TemplateRequest request = new NotifyAdminModels.TemplateRequest();
        request.setTemplateCode("welcome");
        request.setTemplateName("欢迎通知");
        request.setChannel("LOG");
        request.setTitle("hello");
        request.setContent("hello ${name}");
        request.setReceivers(List.of("admin@example.com"));
        request.setWebhookUrl("https://example.com/webhook");
        request.setStatus("ENABLED");
        return request;
    }

    private static NotifyAdminModels.Record record(String channel, boolean success) {
        return new NotifyAdminModels.Record()
                .setTemplateCode("welcome")
                .setChannel(channel)
                .setTitle("hello")
                .setContent("hello Codex")
                .setSuccess(success);
    }

    private static void assertInvalidTemplateId(NotifyAdminService.ActionResult<?> result) {
        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.message()).isEqualTo("模板ID必须大于0");
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
                throw new IllegalStateException("notify provider unavailable");
            }

            @Override
            public T getIfAvailable() {
                throw new IllegalStateException("notify provider unavailable");
            }

            @Override
            public T getIfUnique() {
                throw new IllegalStateException("notify provider unavailable");
            }

            @Override
            public T getObject() {
                throw new IllegalStateException("notify provider unavailable");
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

    private static class CapturingNotifyService implements NotifyService {
        private final boolean success;
        private final String messageText;
        private NotifyMessage message;

        private CapturingNotifyService(boolean success, String messageText) {
            this.success = success;
            this.messageText = messageText;
        }

        @Override
        public NotifyResult send(NotifyMessage message) {
            this.message = message;
            return success
                    ? new NotifyResult(true, message.getChannel(), messageText)
                    : NotifyResult.failure(message.getChannel(), messageText);
        }
    }

    private static class InMemoryNotifyAdminRepository extends NotifyAdminRepository {
        private final List<NotifyAdminModels.Template> templates = new ArrayList<>();
        private final List<NotifyAdminModels.Record> records = new ArrayList<>();
        private long nextTemplateId = 1;
        private long nextRecordId = 1;
        private boolean updateTemplateAffected = true;
        private boolean deleteTemplateAffected = true;
        private RuntimeException queryFailure;
        private RuntimeException commandFailure;

        private InMemoryNotifyAdminRepository() {
            super(null);
        }

        @Override
        public List<NotifyAdminModels.Template> listTemplates(String keyword, String channel, String status,
                                                              int pageNum, int pageSize) {
            failQueryIfNeeded();
            return templates.stream()
                    .filter(template -> keyword == null || template.getTemplateCode().contains(keyword)
                            || template.getTemplateName().contains(keyword)
                            || template.getTitle().contains(keyword))
                    .filter(template -> channel == null || channel.equals(template.getChannel()))
                    .filter(template -> status == null || status.equals(template.getStatus()))
                    .toList();
        }

        @Override
        public long countTemplates(String keyword, String channel, String status) {
            failQueryIfNeeded();
            return listTemplates(keyword, channel, status, 1, Integer.MAX_VALUE).size();
        }

        @Override
        public Optional<NotifyAdminModels.Template> findTemplate(Long id) {
            failQueryIfNeeded();
            return templates.stream().filter(template -> id.equals(template.getId())).findFirst();
        }

        @Override
        public Long createTemplate(NotifyAdminModels.TemplateRequest request) {
            failCommandIfNeeded();
            long id = nextTemplateId++;
            templates.add(new NotifyAdminModels.Template()
                    .setId(id)
                    .setTemplateCode(request.getTemplateCode())
                    .setTemplateName(request.getTemplateName())
                    .setChannel(request.getChannel())
                    .setTitle(request.getTitle())
                    .setContent(request.getContent())
                    .setReceivers(request.getReceivers())
                    .setWebhookUrl(request.getWebhookUrl())
                    .setStatus(request.getStatus() == null ? "ENABLED" : request.getStatus()));
            return id;
        }

        @Override
        public boolean updateTemplate(Long id, NotifyAdminModels.TemplateRequest request) {
            failCommandIfNeeded();
            if (!updateTemplateAffected) {
                return false;
            }
            findTemplate(id).ifPresent(template -> template
                    .setTemplateCode(request.getTemplateCode())
                    .setTemplateName(request.getTemplateName())
                    .setChannel(request.getChannel())
                    .setTitle(request.getTitle())
                    .setContent(request.getContent())
                    .setReceivers(request.getReceivers())
                    .setWebhookUrl(request.getWebhookUrl())
                    .setStatus(request.getStatus()));
            return true;
        }

        @Override
        public boolean deleteTemplate(Long id) {
            failCommandIfNeeded();
            if (!deleteTemplateAffected) {
                return false;
            }
            templates.removeIf(template -> id.equals(template.getId()));
            return true;
        }

        @Override
        public Long createRecord(NotifyAdminModels.Record record) {
            failCommandIfNeeded();
            long id = nextRecordId++;
            record.setId(id);
            records.add(record);
            return id;
        }

        @Override
        public List<NotifyAdminModels.Record> listRecords(String channel, Boolean success, int pageNum, int pageSize) {
            failQueryIfNeeded();
            return records.stream()
                    .filter(record -> channel == null || channel.equals(record.getChannel()))
                    .filter(record -> success == null || success.equals(record.getSuccess()))
                    .toList();
        }

        @Override
        public long countRecords(String channel, Boolean success) {
            failQueryIfNeeded();
            return listRecords(channel, success, 1, Integer.MAX_VALUE).size();
        }

        @Override
        public long countRecordsBySuccess(boolean success) {
            failQueryIfNeeded();
            return records.stream().filter(record -> Boolean.valueOf(success).equals(record.getSuccess())).count();
        }

        @Override
        public long countTemplatesByStatus(String status) {
            failQueryIfNeeded();
            return templates.stream().filter(template -> status.equals(template.getStatus())).count();
        }

        private void failQueryIfNeeded() {
            if (queryFailure != null) {
                throw queryFailure;
            }
        }

        private void failCommandIfNeeded() {
            if (commandFailure != null) {
                throw commandFailure;
            }
        }
    }
}
