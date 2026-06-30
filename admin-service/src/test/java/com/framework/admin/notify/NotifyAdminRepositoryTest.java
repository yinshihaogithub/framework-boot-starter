package com.framework.admin.notify;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyAdminRepositoryTest {

    private final RecordingMapper mapper = new RecordingMapper();
    private final NotifyAdminRepository repository = new NotifyAdminRepository(mapper);

    @Test
    void listTemplatesTrimsFiltersAndCalculatesOffset() {
        repository.listTemplates("\u00A0alarm\u3000", "\u3000email\u00A0", "\u00A0enabled\u3000", 2, 10);

        assertThat(mapper.keywordLike).isEqualTo("%alarm%");
        assertThat(mapper.channel).isEqualTo("EMAIL");
        assertThat(mapper.status).isEqualTo("ENABLED");
        assertThat(mapper.offset).isEqualTo(10);
        assertThat(mapper.pageSize).isEqualTo(10);
    }

    @Test
    void createTemplateNormalizesFieldsAndReturnsGeneratedId() {
        NotifyAdminModels.TemplateRequest request = new NotifyAdminModels.TemplateRequest();
        request.setTemplateCode("\u00A0welcome\u3000");
        request.setTemplateName("\u3000欢迎\u00A0");
        request.setChannel("\u00A0log\u3000");
        request.setTitle("\u3000hello\u00A0");
        request.setContent("\u00A0content\u3000");
        request.setReceivers(Arrays.asList("\u00A0admin@example.com\u3000", "\u3000", null, "ops@example.com"));
        request.setStatus("\u3000disabled\u00A0");

        Long id = repository.createTemplate(request);

        assertThat(id).isEqualTo(7L);
        assertThat(mapper.templateRow.getTemplateCode()).isEqualTo("welcome");
        assertThat(mapper.templateRow.getTemplateName()).isEqualTo("欢迎");
        assertThat(mapper.templateRow.getChannel()).isEqualTo("LOG");
        assertThat(mapper.templateRow.getReceivers()).isEqualTo("admin@example.com,ops@example.com");
        assertThat(mapper.templateRow.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void createTemplateDefaultsStatusAndDropsBlankReceivers() {
        NotifyAdminModels.TemplateRequest request = new NotifyAdminModels.TemplateRequest();
        request.setTemplateCode("welcome");
        request.setTemplateName("欢迎");
        request.setChannel("log");
        request.setTitle("hello");
        request.setContent("content");
        request.setReceivers(Arrays.asList("\u00A0\u3000", null));
        request.setStatus("\u00A0\u3000");

        Long id = repository.createTemplate(request);

        assertThat(id).isEqualTo(7L);
        assertThat(mapper.templateRow.getReceivers()).isNull();
        assertThat(mapper.templateRow.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    void findTemplateSplitsReceivers() {
        mapper.templateRow = new NotifyAdminMapper.TemplateRow()
                .setId(1L)
                .setTemplateCode("welcome")
                .setReceivers("\u00A0admin@example.com\u3000,\u00A0\u3000,ops@example.com\u00A0");

        NotifyAdminModels.Template template = repository.findTemplate(1L).orElseThrow();

        assertThat(template.getReceivers()).containsExactly("admin@example.com", "ops@example.com");
    }

    @Test
    void createRecordConvertsReceiversAndReturnsGeneratedId() {
        NotifyAdminModels.Record record = new NotifyAdminModels.Record()
                .setTemplateCode("welcome")
                .setChannel("EMAIL")
                .setReceivers(List.of("a@example.com", "\u3000b@example.com\u00A0"))
                .setSuccess(true);

        Long id = repository.createRecord(record);

        assertThat(id).isEqualTo(9L);
        assertThat(mapper.recordRow.getReceivers()).isEqualTo("a@example.com,b@example.com");
        assertThat(mapper.recordRow.getSuccess()).isTrue();
    }

    @Test
    void listRecordsNormalizesFilterAndMapsRows() {
        mapper.recordRows = List.of(new NotifyAdminMapper.RecordRow()
                .setId(3L)
                .setTemplateCode("welcome")
                .setChannel("EMAIL")
                .setTitle("hello")
                .setContent("content")
                .setReceivers("\u00A0admin@example.com\u3000,\u00A0\u3000,ops@example.com\u00A0")
                .setWebhookUrl("https://example.com/hook")
                .setSuccess(false)
                .setResultMessage("failed")
                .setTraceId("trace-1")
                .setOperatorName("admin")
                .setCreateTime("2026-01-01 10:00:00"));

        List<NotifyAdminModels.Record> records = repository.listRecords("\u3000email\u00A0", false, 3, 15);

        assertThat(mapper.channel).isEqualTo("EMAIL");
        assertThat(mapper.success).isFalse();
        assertThat(mapper.offset).isEqualTo(30);
        assertThat(mapper.pageSize).isEqualTo(15);
        assertThat(records).hasSize(1);
        NotifyAdminModels.Record record = records.get(0);
        assertThat(record.getId()).isEqualTo(3L);
        assertThat(record.getReceivers()).containsExactly("admin@example.com", "ops@example.com");
        assertThat(record.getWebhookUrl()).isEqualTo("https://example.com/hook");
        assertThat(record.getSuccess()).isFalse();
        assertThat(record.getResultMessage()).isEqualTo("failed");
        assertThat(record.getTraceId()).isEqualTo("trace-1");
        assertThat(record.getOperatorName()).isEqualTo("admin");
        assertThat(record.getCreateTime()).isEqualTo("2026-01-01 10:00:00");
    }

    @Test
    void countQueriesNormalizeArguments() {
        assertThat(repository.countTemplates("\u00A0welcome\u3000", "\u3000log\u00A0", "\u00A0enabled\u3000")).isEqualTo(11L);
        assertThat(mapper.keywordLike).isEqualTo("%welcome%");
        assertThat(mapper.channel).isEqualTo("LOG");
        assertThat(mapper.status).isEqualTo("ENABLED");

        assertThat(repository.countRecords("\u3000email\u00A0", false)).isEqualTo(12L);
        assertThat(mapper.channel).isEqualTo("EMAIL");
        assertThat(mapper.success).isFalse();

        assertThat(repository.countTemplatesByStatus("\u00A0disabled\u3000")).isEqualTo(13L);
        assertThat(mapper.status).isEqualTo("DISABLED");
        assertThat(repository.countRecordsBySuccess(true)).isEqualTo(14L);
        assertThat(mapper.success).isTrue();
    }

    @Test
    void updateAndDeleteTemplateReportAffectedRows() {
        NotifyAdminModels.TemplateRequest request = new NotifyAdminModels.TemplateRequest();
        request.setTemplateCode("welcome");
        request.setTemplateName("欢迎");
        request.setChannel("LOG");
        request.setTitle("hello");
        request.setContent("content");

        assertThat(repository.updateTemplate(9L, request)).isTrue();
        assertThat(repository.deleteTemplate(9L)).isTrue();

        mapper.updateTemplateResult = 0;
        mapper.deleteTemplateResult = 0;

        assertThat(repository.updateTemplate(9L, request)).isFalse();
        assertThat(repository.deleteTemplate(9L)).isFalse();
    }

    private static class RecordingMapper implements NotifyAdminMapper {
        private String keywordLike;
        private String channel;
        private String status;
        private int offset;
        private int pageSize;
        private Boolean success;
        private TemplateRow templateRow;
        private RecordRow recordRow;
        private List<RecordRow> recordRows = List.of();
        private int updateTemplateResult = 1;
        private int deleteTemplateResult = 1;

        @Override
        public List<TemplateRow> listTemplates(String keywordLike, String channel, String status, int offset, int pageSize) {
            this.keywordLike = keywordLike;
            this.channel = channel;
            this.status = status;
            this.offset = offset;
            this.pageSize = pageSize;
            return List.of();
        }

        @Override
        public long countTemplates(String keywordLike, String channel, String status) {
            this.keywordLike = keywordLike;
            this.channel = channel;
            this.status = status;
            return 11L;
        }

        @Override
        public TemplateRow findTemplate(Long id) {
            return templateRow;
        }

        @Override
        public int insertTemplate(TemplateRow row) {
            this.templateRow = row;
            row.setId(7L);
            return 1;
        }

        @Override
        public int updateTemplate(TemplateRow row) {
            this.templateRow = row;
            return updateTemplateResult;
        }

        @Override
        public int deleteTemplate(Long id) {
            return deleteTemplateResult;
        }

        @Override
        public int insertRecord(RecordRow row) {
            this.recordRow = row;
            row.setId(9L);
            return 1;
        }

        @Override
        public List<RecordRow> listRecords(String channel, Boolean success, int offset, int pageSize) {
            this.channel = channel;
            this.success = success;
            this.offset = offset;
            this.pageSize = pageSize;
            return recordRows;
        }

        @Override
        public long countRecords(String channel, Boolean success) {
            this.channel = channel;
            this.success = success;
            return 12L;
        }

        @Override
        public long countRecordsBySuccess(boolean success) {
            this.success = success;
            return 14L;
        }

        @Override
        public long countTemplatesByStatus(String status) {
            this.status = status;
            return 13L;
        }
    }
}
