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
        NotifyAdminService service = service(new InMemoryNotifyAdminMapper(), null);
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setTemplateCode("\u00A0\u3000");

        NotifyAdminService.ActionResult<Long> result = service.createTemplate(request, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.message()).isEqualTo("模板编码不能为空");
    }

    @Test
    void rejectsUnsupportedTemplateChannel() {
        NotifyAdminService service = service(new InMemoryNotifyAdminMapper(), null);
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setChannel("ding-talk");

        NotifyAdminService.ActionResult<Long> result = service.createTemplate(request, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.message()).isEqualTo("通知通道不支持");
    }

    @Test
    void rejectsUnsupportedTemplateStatus() {
        NotifyAdminService service = service(new InMemoryNotifyAdminMapper(), null);
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setStatus("ARCHIVED");

        NotifyAdminService.ActionResult<Long> result = service.createTemplate(request, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.message()).isEqualTo("状态只能是 ENABLED 或 DISABLED");
    }

    @Test
    void listsTemplatesWithSafePaging() {
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
        repository.createTemplate(templateRequest());
        NotifyAdminService service = service(repository, null);

        PageResult<NotifyAdminModels.Template> page = service.templates(null, null, null, -1, 500);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getTotal()).isEqualTo(1);
    }

    @Test
    void templatesNormalizeFiltersBeforeQueryingRepository() {
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
        repository.createRecord(record("LOG", true));
        NotifyAdminService service = service(repository, null);

        PageResult<NotifyAdminModels.Record> page = service.records("ding-talk", true, 1, 20);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void queryEndpointsFallBackWhenRepositoryFails() {
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        assertThat(repository.records.get(0).getReceivers()).isEqualTo("ops@example.com,dev@example.com");
    }

    @Test
    void sendTestFallsBackToTemplateReceiversWhenOverrideReceiversAreBlank() {
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
        NotifyAdminService service = service(repository, null, new ThrowingAuditService());

        NotifyAdminService.ActionResult<Long> result = service.createTemplate(templateRequest(), null);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(repository.templates).hasSize(1);
    }

    @Test
    void updateTemplateSucceedsWhenAuditFails() {
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
        Long templateId = repository.createTemplate(templateRequest());
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setTitle("updated");
        NotifyAdminService service = service(repository, null, new ThrowingAuditService());

        NotifyAdminService.ActionResult<String> result = service.updateTemplate(templateId, request, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo("已更新");
        assertThat(repository.findStoredTemplate(templateId).orElseThrow().getTitle()).isEqualTo("updated");
    }

    @Test
    void deleteTemplateSucceedsWhenAuditFails() {
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
        Long templateId = repository.createTemplate(templateRequest());
        NotifyAdminService service = service(repository, null, new ThrowingAuditService());

        NotifyAdminService.ActionResult<String> result = service.deleteTemplate(templateId, null);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo("已删除");
        assertThat(repository.templates).isEmpty();
    }

    @Test
    void sendTestSucceedsWhenAuditFails() {
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
        repository.queryFailure = new RuntimeException("database down");
        NotifyAdminService service = service(repository, null);

        assertInvalidTemplateId(service.updateTemplate(0L, templateRequest(), null));
        assertInvalidTemplateId(service.deleteTemplate(0L, null));
        assertInvalidTemplateId(service.sendTest(0L, null, null));
    }

    @Test
    void updateTemplateReturnsFalseWhenTemplateDoesNotExist() {
        NotifyAdminService service = service(new InMemoryNotifyAdminMapper(), null);

        NotifyAdminService.ActionResult<String> updated = service.updateTemplate(404L, templateRequest(), null);

        assertThat(updated.success()).isFalse();
        assertThat(updated.code()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(updated.message()).isEqualTo("模板不存在");
    }

    @Test
    void deleteTemplateReturnsNotFoundWhenTemplateDoesNotExist() {
        NotifyAdminService service = service(new InMemoryNotifyAdminMapper(), null);

        NotifyAdminService.ActionResult<String> deleted = service.deleteTemplate(404L, null);

        assertThat(deleted.success()).isFalse();
        assertThat(deleted.code()).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(deleted.message()).isEqualTo("模板不存在");
    }

    @Test
    void updateTemplateReturnsNotFoundWhenTemplateDisappearsBeforeUpdate() {
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
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
        InMemoryNotifyAdminMapper repository = new InMemoryNotifyAdminMapper();
        Long templateId = repository.createTemplate(templateRequest());
        repository.commandFailure = new RuntimeException("database down");
        NotifyAdminService service = service(repository, null);

        NotifyAdminService.ActionResult<NotifyAdminModels.Record> result = service.sendTest(templateId, null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(result.message()).isEqualTo("通知测试发送失败");
    }

    private static NotifyAdminService service(InMemoryNotifyAdminMapper repository, NotifyService notifyService) {
        return new NotifyAdminService(repository, provider(notifyService), auditService());
    }

    private static NotifyAdminService service(InMemoryNotifyAdminMapper repository, NotifyService notifyService,
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

    private static class InMemoryNotifyAdminMapper implements NotifyAdminMapper {
        private final List<NotifyAdminMapper.TemplateRow> templates = new ArrayList<>();
        private final List<NotifyAdminMapper.RecordRow> records = new ArrayList<>();
        private long nextTemplateId = 1;
        private long nextRecordId = 1;
        private boolean updateTemplateAffected = true;
        private boolean deleteTemplateAffected = true;
        private RuntimeException queryFailure;
        private RuntimeException commandFailure;

        Long createTemplate(NotifyAdminModels.TemplateRequest request) {
            NotifyAdminMapper.TemplateRow row = NotifyAdminMapperSupport.toTemplateRow(request);
            insertTemplate(row);
            return row.getId();
        }

        Long createRecord(NotifyAdminModels.Record record) {
            NotifyAdminMapper.RecordRow row = NotifyAdminMapperSupport.toRecordRow(record);
            insertRecord(row);
            return row.getId();
        }

        Optional<NotifyAdminModels.Template> findStoredTemplate(Long id) {
            return Optional.ofNullable(findTemplate(id)).map(NotifyAdminMapperSupport::toTemplate);
        }

        @Override
        public List<NotifyAdminMapper.TemplateRow> listTemplates(String keywordLike,
                                                                 String channel,
                                                                 String status,
                                                                 int offset,
                                                                 int pageSize) {
            failQueryIfNeeded();
            String keyword = keyword(keywordLike);
            return templates.stream()
                    .filter(template -> keyword == null || contains(template.getTemplateCode(), keyword)
                            || contains(template.getTemplateName(), keyword)
                            || contains(template.getTitle(), keyword))
                    .filter(template -> channel == null || channel.equals(template.getChannel()))
                    .filter(template -> status == null || status.equals(template.getStatus()))
                    .skip(offset)
                    .limit(pageSize)
                    .map(this::copy)
                    .toList();
        }

        @Override
        public long countTemplates(String keywordLike, String channel, String status) {
            failQueryIfNeeded();
            return listTemplates(keywordLike, channel, status, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public NotifyAdminMapper.TemplateRow findTemplate(Long id) {
            failQueryIfNeeded();
            return templates.stream()
                    .filter(template -> id.equals(template.getId()))
                    .findFirst()
                    .map(this::copy)
                    .orElse(null);
        }

        @Override
        public int insertTemplate(NotifyAdminMapper.TemplateRow row) {
            failCommandIfNeeded();
            row.setId(nextTemplateId++);
            templates.add(copy(row));
            return 1;
        }

        @Override
        public int updateTemplate(NotifyAdminMapper.TemplateRow row) {
            failCommandIfNeeded();
            if (!updateTemplateAffected) {
                return 0;
            }
            for (int i = 0; i < templates.size(); i++) {
                if (row.getId().equals(templates.get(i).getId())) {
                    templates.set(i, copy(row));
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int deleteTemplate(Long id) {
            failCommandIfNeeded();
            if (!deleteTemplateAffected) {
                return 0;
            }
            int sizeBefore = templates.size();
            templates.removeIf(template -> id.equals(template.getId()));
            return templates.size() < sizeBefore ? 1 : 0;
        }

        @Override
        public int insertRecord(NotifyAdminMapper.RecordRow row) {
            failCommandIfNeeded();
            row.setId(nextRecordId++);
            records.add(copy(row));
            return 1;
        }

        @Override
        public List<NotifyAdminMapper.RecordRow> listRecords(String channel, Boolean success, int offset, int pageSize) {
            failQueryIfNeeded();
            return records.stream()
                    .filter(record -> channel == null || channel.equals(record.getChannel()))
                    .filter(record -> success == null || success.equals(record.getSuccess()))
                    .skip(offset)
                    .limit(pageSize)
                    .map(this::copy)
                    .toList();
        }

        @Override
        public long countRecords(String channel, Boolean success) {
            failQueryIfNeeded();
            return listRecords(channel, success, 0, Integer.MAX_VALUE).size();
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

        private String keyword(String keywordLike) {
            if (keywordLike == null) {
                return null;
            }
            return keywordLike.replace("%", "");
        }

        private boolean contains(String value, String keyword) {
            return value != null && value.contains(keyword);
        }

        private NotifyAdminMapper.TemplateRow copy(NotifyAdminMapper.TemplateRow row) {
            return new NotifyAdminMapper.TemplateRow()
                    .setId(row.getId())
                    .setTemplateCode(row.getTemplateCode())
                    .setTemplateName(row.getTemplateName())
                    .setChannel(row.getChannel())
                    .setTitle(row.getTitle())
                    .setContent(row.getContent())
                    .setReceivers(row.getReceivers())
                    .setWebhookUrl(row.getWebhookUrl())
                    .setStatus(row.getStatus())
                    .setCreateTime(row.getCreateTime())
                    .setUpdateTime(row.getUpdateTime());
        }

        private NotifyAdminMapper.RecordRow copy(NotifyAdminMapper.RecordRow row) {
            return new NotifyAdminMapper.RecordRow()
                    .setId(row.getId())
                    .setTemplateCode(row.getTemplateCode())
                    .setChannel(row.getChannel())
                    .setTitle(row.getTitle())
                    .setContent(row.getContent())
                    .setReceivers(row.getReceivers())
                    .setWebhookUrl(row.getWebhookUrl())
                    .setSuccess(row.getSuccess())
                    .setResultMessage(row.getResultMessage())
                    .setTraceId(row.getTraceId())
                    .setOperatorName(row.getOperatorName())
                    .setCreateTime(row.getCreateTime());
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
