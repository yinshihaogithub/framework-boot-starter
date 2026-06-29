package com.framework.admin.notify;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import com.framework.notify.service.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotifyAdminServiceTest {

    @Test
    void validatesRequiredTemplateFields() {
        NotifyAdminService service = service(new InMemoryNotifyAdminRepository(), null);
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setTemplateCode(" ");

        assertThatThrownBy(() -> service.createTemplate(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模板编码不能为空");
    }

    @Test
    void rejectsUnsupportedTemplateChannel() {
        NotifyAdminService service = service(new InMemoryNotifyAdminRepository(), null);
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setChannel("ding-talk");

        assertThatThrownBy(() -> service.createTemplate(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("通知通道不支持");
    }

    @Test
    void rejectsUnsupportedTemplateStatus() {
        NotifyAdminService service = service(new InMemoryNotifyAdminRepository(), null);
        NotifyAdminModels.TemplateRequest request = templateRequest();
        request.setStatus("ARCHIVED");

        assertThatThrownBy(() -> service.createTemplate(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("状态只能是 ENABLED 或 DISABLED");
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

        Optional<NotifyAdminModels.Record> record = service.sendTest(templateId, request, null);

        assertThat(record).isPresent();
        assertThat(record.get().getId()).isNotNull();
        assertThat(record.get().getContent()).isEqualTo("hello Codex");
        assertThat(record.get().getReceivers()).containsExactly("ops@example.com");
        assertThat(record.get().getWebhookUrl()).isEqualTo("https://callback.example.com/hook");
        assertThat(record.get().getSuccess()).isTrue();
        assertThat(record.get().getResultMessage()).isEqualTo("sent");
        assertThat(notifyService.message.getContent()).isEqualTo("hello Codex");
        assertThat(notifyService.message.getReceivers()).containsExactly("ops@example.com");
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

        Optional<NotifyAdminModels.Record> record = service.sendTest(templateId, null, null);

        assertThat(record).isPresent();
        assertThat(notifyService.message.getChannel()).isEqualTo(NotifyChannelType.WEBHOOK);
    }

    @Test
    void sendTestRecordsFailureWhenNotifyServiceIsMissing() {
        InMemoryNotifyAdminRepository repository = new InMemoryNotifyAdminRepository();
        Long templateId = repository.createTemplate(templateRequest());
        NotifyAdminService service = service(repository, null);

        Optional<NotifyAdminModels.Record> record = service.sendTest(templateId, null, null);

        assertThat(record).isPresent();
        assertThat(record.get().getSuccess()).isFalse();
        assertThat(record.get().getResultMessage()).isEqualTo("notify service is not enabled");
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

        Optional<NotifyAdminModels.Record> record = service.sendTest(templateId, null, null);

        assertThat(record).isPresent();
        assertThat(record.get().getSuccess()).isFalse();
        assertThat(record.get().getResultMessage()).isEqualTo("模板已禁用");
        assertThat(notifyService.message).isNull();
        assertThat(repository.countRecordsBySuccess(false)).isEqualTo(1);
    }

    @Test
    void updateTemplateReturnsFalseWhenTemplateDoesNotExist() {
        NotifyAdminService service = service(new InMemoryNotifyAdminRepository(), null);

        boolean updated = service.updateTemplate(404L, templateRequest(), null);

        assertThat(updated).isFalse();
    }

    private static NotifyAdminService service(InMemoryNotifyAdminRepository repository, NotifyService notifyService) {
        return new NotifyAdminService(repository, provider(notifyService), auditService());
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

        private InMemoryNotifyAdminRepository() {
            super(null);
        }

        @Override
        public List<NotifyAdminModels.Template> listTemplates(String keyword, String channel, String status,
                                                              int pageNum, int pageSize) {
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
            return listTemplates(keyword, channel, status, 1, Integer.MAX_VALUE).size();
        }

        @Override
        public Optional<NotifyAdminModels.Template> findTemplate(Long id) {
            return templates.stream().filter(template -> id.equals(template.getId())).findFirst();
        }

        @Override
        public Long createTemplate(NotifyAdminModels.TemplateRequest request) {
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
        public void updateTemplate(Long id, NotifyAdminModels.TemplateRequest request) {
            findTemplate(id).ifPresent(template -> template
                    .setTemplateCode(request.getTemplateCode())
                    .setTemplateName(request.getTemplateName())
                    .setChannel(request.getChannel())
                    .setTitle(request.getTitle())
                    .setContent(request.getContent())
                    .setReceivers(request.getReceivers())
                    .setWebhookUrl(request.getWebhookUrl())
                    .setStatus(request.getStatus()));
        }

        @Override
        public void deleteTemplate(Long id) {
            templates.removeIf(template -> id.equals(template.getId()));
        }

        @Override
        public Long createRecord(NotifyAdminModels.Record record) {
            long id = nextRecordId++;
            record.setId(id);
            records.add(record);
            return id;
        }

        @Override
        public List<NotifyAdminModels.Record> listRecords(String channel, Boolean success, int pageNum, int pageSize) {
            return records.stream()
                    .filter(record -> channel == null || channel.equals(record.getChannel()))
                    .filter(record -> success == null || success.equals(record.getSuccess()))
                    .toList();
        }

        @Override
        public long countRecords(String channel, Boolean success) {
            return listRecords(channel, success, 1, Integer.MAX_VALUE).size();
        }

        @Override
        public long countRecordsBySuccess(boolean success) {
            return records.stream().filter(record -> Boolean.valueOf(success).equals(record.getSuccess())).count();
        }

        @Override
        public long countTemplatesByStatus(String status) {
            return templates.stream().filter(template -> status.equals(template.getStatus())).count();
        }
    }
}
