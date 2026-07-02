package com.framework.admin.localmessage;

import com.framework.localmessage.config.LocalMessageProperties;
import com.framework.localmessage.mapper.LocalMessageMapper;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMessageAdminMapperSupportTest {

    @Test
    void tableNameUsesValidatedLocalMessageProperties() {
        LocalMessageProperties properties = new LocalMessageProperties();
        properties.setTableName("tenant_local_message");

        assertThat(LocalMessageAdminMapperSupport.tableName(properties)).isEqualTo("tenant_local_message");

        properties.setTableName("tenant-local-message");
        assertThatThrownBy(() -> LocalMessageAdminMapperSupport.tableName(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("framework.local-message.table-name");
    }

    @Test
    void listAndCountNormalizeFiltersAndPaging() {
        CapturingMapper mapper = new CapturingMapper();

        LocalMessageAdminMapperSupport.list(mapper, "framework_local_message",
                "\u00A0order.created\u3000", LocalMessageStatus.PENDING,
                "trace-a", "\u00A0ORD-\u3000", 3, 20);
        LocalMessageAdminMapperSupport.count(mapper, "framework_local_message",
                "\u00A0order.created\u3000", LocalMessageStatus.PENDING,
                "trace-a", "\u00A0ORD-\u3000");

        assertThat(mapper.listTableName).isEqualTo("framework_local_message");
        assertThat(mapper.listTopic).isEqualTo("order.created");
        assertThat(mapper.listStatus).isEqualTo(LocalMessageStatus.PENDING);
        assertThat(mapper.listTraceIdLike).isEqualTo("%trace-a%");
        assertThat(mapper.listBusinessKeyLike).isEqualTo("%ORD-%");
        assertThat(mapper.listOffset).isEqualTo(40);
        assertThat(mapper.listPageSize).isEqualTo(20);
        assertThat(mapper.countTopic).isEqualTo("order.created");

        LocalMessageAdminMapperSupport.listByTraceId(mapper, "framework_local_message", "trace-a", 2, 50);
        LocalMessageAdminMapperSupport.countByTraceId(mapper, "framework_local_message", "trace-a",
                LocalMessageStatus.FAILED);

        assertThat(mapper.listTopic).isNull();
        assertThat(mapper.listStatus).isNull();
        assertThat(mapper.listTraceIdLike).isEqualTo("trace-a");
        assertThat(mapper.listOffset).isEqualTo(50);
        assertThat(mapper.listPageSize).isEqualTo(50);
        assertThat(mapper.countStatus).isEqualTo(LocalMessageStatus.FAILED);
        assertThat(mapper.countTraceIdLike).isEqualTo("trace-a");
    }

    @Test
    void statsDelegatesStatusAndTotalCounts() {
        CapturingMapper mapper = new CapturingMapper();

        assertThat(LocalMessageAdminMapperSupport.stats(mapper, "framework_local_message"))
                .containsEntry("PENDING", 1L)
                .containsEntry("PROCESSING", 2L)
                .containsEntry("SUCCESS", 3L)
                .containsEntry("FAILED", 4L)
                .containsEntry("TOTAL", 10L);
    }

    @Test
    void updateTouchesTimestampsBeforeDelegating() {
        CapturingMapper mapper = new CapturingMapper();
        LocalMessage message = new LocalMessage().setId(9L).setStatus(LocalMessageStatus.PENDING);

        assertThat(LocalMessageAdminMapperSupport.update(mapper, "framework_local_message", message)).isTrue();

        assertThat(mapper.updatedMessage).isSameAs(message);
        assertThat(message.getCreateTime()).isNotNull();
        assertThat(message.getUpdateTime()).isNotNull();
    }

    private static class CapturingMapper implements LocalMessageMapper {
        private String listTableName;
        private String listTopic;
        private LocalMessageStatus listStatus;
        private String listTraceIdLike;
        private String listBusinessKeyLike;
        private int listOffset;
        private int listPageSize;
        private String countTopic;
        private LocalMessageStatus countStatus;
        private String countTraceIdLike;
        private LocalMessage updatedMessage;

        @Override
        public void createTableIfNotExists(String tableName) {
        }

        @Override
        public int insert(String tableName, LocalMessage message) {
            return 0;
        }

        @Override
        public int update(String tableName, LocalMessage message) {
            this.updatedMessage = message;
            return 1;
        }

        @Override
        public LocalMessage findById(String tableName, Long id) {
            return null;
        }

        @Override
        public List<LocalMessage> findDueMessages(String tableName, LocalMessageStatus status,
                                                  LocalDateTime now, int limit) {
            return List.of();
        }

        @Override
        public List<LocalMessage> list(String tableName, String topic, LocalMessageStatus status,
                                       String traceIdLike, String businessKeyLike, int offset, int pageSize) {
            this.listTableName = tableName;
            this.listTopic = topic;
            this.listStatus = status;
            this.listTraceIdLike = traceIdLike;
            this.listBusinessKeyLike = businessKeyLike;
            this.listOffset = offset;
            this.listPageSize = pageSize;
            return List.of();
        }

        @Override
        public long count(String tableName, String topic, LocalMessageStatus status,
                          String traceIdLike, String businessKeyLike) {
            this.countTopic = topic;
            this.countStatus = status;
            this.countTraceIdLike = traceIdLike;
            return 0;
        }

        @Override
        public long countAll(String tableName) {
            return 10;
        }

        @Override
        public long countByStatus(String tableName, LocalMessageStatus status) {
            return status.ordinal() + 1L;
        }

        @Override
        public int delete(String tableName, Long id) {
            return 0;
        }

        @Override
        public int deleteByStatus(String tableName, LocalMessageStatus status) {
            return 0;
        }
    }
}
