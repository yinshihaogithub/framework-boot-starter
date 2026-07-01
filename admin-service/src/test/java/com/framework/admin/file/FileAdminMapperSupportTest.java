package com.framework.admin.file;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileAdminMapperSupportTest {

    private final RecordingMapper mapper = new RecordingMapper();

    @Test
    void listTrimsFiltersAndCalculatesOffset() {
        FileAdminMapperSupport.list(mapper, "\u00A0report\u3000", "\u3000system\u00A0",
                "\u00A0user-1\u3000", "\u3000text\u00A0", 3, 20);

        assertThat(mapper.keyword).isEqualTo("%report%");
        assertThat(mapper.businessType).isEqualTo("system");
        assertThat(mapper.businessKey).isEqualTo("%user-1%");
        assertThat(mapper.contentType).isEqualTo("%text%");
        assertThat(mapper.offset).isEqualTo(40);
        assertThat(mapper.pageSize).isEqualTo(20);
    }

    @Test
    void blankFiltersBecomeNull() {
        FileAdminMapperSupport.count(mapper, "\u00A0\u3000", "", "\t", "\n");

        assertThat(mapper.keyword).isNull();
        assertThat(mapper.businessType).isNull();
        assertThat(mapper.businessKey).isNull();
        assertThat(mapper.contentType).isNull();
    }

    @Test
    void statsAndCommandsDelegateToMapper() {
        FileAdminModels.FileRecord record = new FileAdminModels.FileRecord().setOriginalFilename("a.txt");

        assertThat(FileAdminMapperSupport.stats(mapper))
                .containsEntry("active", 4L)
                .containsEntry("deleted", 1L)
                .containsEntry("totalSize", 1024L);
        assertThat(FileAdminMapperSupport.create(mapper, record).getId()).isEqualTo(9L);
        assertThat(FileAdminMapperSupport.findById(mapper, 9L)).contains(record);
        assertThat(FileAdminMapperSupport.markDeleted(mapper, 9L)).isTrue();

        assertThat(mapper.deletedId).isEqualTo(9L);
    }

    private static class RecordingMapper implements FileAdminMapper {
        private String keyword;
        private String businessType;
        private String businessKey;
        private String contentType;
        private int offset;
        private int pageSize;
        private Long deletedId;
        private FileAdminModels.FileRecord record;

        @Override
        public List<FileAdminModels.FileRecord> list(String keyword, String businessType, String businessKey,
                                                     String contentType, int offset, int pageSize) {
            this.keyword = keyword;
            this.businessType = businessType;
            this.businessKey = businessKey;
            this.contentType = contentType;
            this.offset = offset;
            this.pageSize = pageSize;
            return List.of();
        }

        @Override
        public long count(String keyword, String businessType, String businessKey, String contentType) {
            this.keyword = keyword;
            this.businessType = businessType;
            this.businessKey = businessKey;
            this.contentType = contentType;
            return 0;
        }

        @Override
        public long countActive() {
            return 4L;
        }

        @Override
        public long countDeleted() {
            return 1L;
        }

        @Override
        public long sumActiveSize() {
            return 1024L;
        }

        @Override
        public FileAdminModels.FileRecord findById(Long id) {
            return record;
        }

        @Override
        public int insert(FileAdminModels.FileRecord record) {
            this.record = record.setId(9L);
            return 1;
        }

        @Override
        public int markDeleted(Long id) {
            this.deletedId = id;
            return 1;
        }
    }
}
