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
        repository.listTemplates(" alarm ", " EMAIL ", " ENABLED ", 2, 10);

        assertThat(mapper.keywordLike).isEqualTo("%alarm%");
        assertThat(mapper.channel).isEqualTo("EMAIL");
        assertThat(mapper.status).isEqualTo("ENABLED");
        assertThat(mapper.offset).isEqualTo(10);
        assertThat(mapper.pageSize).isEqualTo(10);
    }

    @Test
    void createTemplateNormalizesFieldsAndReturnsGeneratedId() {
        NotifyAdminModels.TemplateRequest request = new NotifyAdminModels.TemplateRequest();
        request.setTemplateCode(" welcome ");
        request.setTemplateName(" 欢迎 ");
        request.setChannel(" LOG ");
        request.setTitle(" hello ");
        request.setContent(" content ");
        request.setReceivers(Arrays.asList(" admin@example.com ", "", null, "ops@example.com"));

        Long id = repository.createTemplate(request);

        assertThat(id).isEqualTo(7L);
        assertThat(mapper.templateRow.getTemplateCode()).isEqualTo("welcome");
        assertThat(mapper.templateRow.getTemplateName()).isEqualTo("欢迎");
        assertThat(mapper.templateRow.getChannel()).isEqualTo("LOG");
        assertThat(mapper.templateRow.getReceivers()).isEqualTo("admin@example.com,ops@example.com");
        assertThat(mapper.templateRow.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    void findTemplateSplitsReceivers() {
        mapper.templateRow = new NotifyAdminMapper.TemplateRow()
                .setId(1L)
                .setTemplateCode("welcome")
                .setReceivers(" admin@example.com, ,ops@example.com ");

        NotifyAdminModels.Template template = repository.findTemplate(1L).orElseThrow();

        assertThat(template.getReceivers()).containsExactly("admin@example.com", "ops@example.com");
    }

    @Test
    void createRecordConvertsReceiversAndReturnsGeneratedId() {
        NotifyAdminModels.Record record = new NotifyAdminModels.Record()
                .setTemplateCode("welcome")
                .setChannel("EMAIL")
                .setReceivers(List.of("a@example.com", " b@example.com "))
                .setSuccess(true);

        Long id = repository.createRecord(record);

        assertThat(id).isEqualTo(9L);
        assertThat(mapper.recordRow.getReceivers()).isEqualTo("a@example.com,b@example.com");
        assertThat(mapper.recordRow.getSuccess()).isTrue();
    }

    private static class RecordingMapper implements NotifyAdminMapper {
        private String keywordLike;
        private String channel;
        private String status;
        private int offset;
        private int pageSize;
        private TemplateRow templateRow;
        private RecordRow recordRow;

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
            return 0;
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
            return 1;
        }

        @Override
        public int deleteTemplate(Long id) {
            return 1;
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
            this.offset = offset;
            this.pageSize = pageSize;
            return List.of();
        }

        @Override
        public long countRecords(String channel, Boolean success) {
            this.channel = channel;
            return 0;
        }

        @Override
        public long countRecordsBySuccess(boolean success) {
            return 0;
        }

        @Override
        public long countTemplatesByStatus(String status) {
            this.status = status;
            return 0;
        }
    }
}
