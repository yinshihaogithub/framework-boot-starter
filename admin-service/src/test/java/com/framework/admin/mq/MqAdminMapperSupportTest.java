package com.framework.admin.mq;

import com.framework.mq.config.MqProperties;
import com.framework.mq.deadletter.MqFailedMessage;
import com.framework.mq.mapper.MqFailedMessageMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqAdminMapperSupportTest {

    @Test
    void tableNameUsesValidatedMqProperties() {
        MqProperties properties = new MqProperties();
        properties.setFailedMessageTableName("tenant_mq_failed_message");

        assertThat(MqAdminMapperSupport.tableName(properties)).isEqualTo("tenant_mq_failed_message");

        properties.setFailedMessageTableName("tenant-mq-failed-message");
        assertThatThrownBy(() -> MqAdminMapperSupport.tableName(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");
    }

    @Test
    void listAndTraceQueriesNormalizeFiltersAndPaging() {
        CapturingMapper mapper = new CapturingMapper();

        MqAdminMapperSupport.list(mapper, "framework_mq_failed_message",
                "\u00A0order.queue\u3000", "PENDING", "trace-a", "\u00A0ORD-\u3000",
                "\u3000OrderCreated\u00A0", 3, 20);
        MqAdminMapperSupport.count(mapper, "framework_mq_failed_message",
                "\u00A0order.queue\u3000", "PENDING", "trace-a", "\u00A0ORD-\u3000",
                "\u3000OrderCreated\u00A0");

        assertThat(mapper.listTableName).isEqualTo("framework_mq_failed_message");
        assertThat(mapper.listQueueName).isEqualTo("order.queue");
        assertThat(mapper.listStatus).isEqualTo("PENDING");
        assertThat(mapper.listTraceIdLike).isEqualTo("%trace-a%");
        assertThat(mapper.listBusinessKeyLike).isEqualTo("%ORD-%");
        assertThat(mapper.listMessageType).isEqualTo("OrderCreated");
        assertThat(mapper.listOffset).isEqualTo(40);
        assertThat(mapper.listPageSize).isEqualTo(20);
        assertThat(mapper.countQueueName).isEqualTo("order.queue");

        MqAdminMapperSupport.listByTraceId(mapper, "framework_mq_failed_message", "trace-a", 2, 50);
        MqAdminMapperSupport.countByTraceId(mapper, "framework_mq_failed_message", "trace-a",
                MqFailedMessage.STATUS_EXHAUSTED);

        assertThat(mapper.listQueueName).isNull();
        assertThat(mapper.listStatus).isNull();
        assertThat(mapper.listTraceIdLike).isEqualTo("trace-a");
        assertThat(mapper.listOffset).isEqualTo(50);
        assertThat(mapper.listPageSize).isEqualTo(50);
        assertThat(mapper.countStatus).isEqualTo(MqFailedMessage.STATUS_EXHAUSTED);
        assertThat(mapper.countTraceIdLike).isEqualTo("trace-a");
    }

    @Test
    void statsDelegatesStatusAndTotalCounts() {
        CapturingMapper mapper = new CapturingMapper();

        assertThat(MqAdminMapperSupport.stats(mapper, "framework_mq_failed_message"))
                .satisfies(stats -> {
                    assertThat(stats.getPendingCount()).isEqualTo(1L);
                    assertThat(stats.getRetryingCount()).isEqualTo(2L);
                    assertThat(stats.getSuccessCount()).isEqualTo(7L);
                    assertThat(stats.getExhaustedCount()).isEqualTo(4L);
                    assertThat(stats.getTotalCount()).isEqualTo(10L);
                });
    }

    private static class CapturingMapper implements MqFailedMessageMapper {
        private String listTableName;
        private String listQueueName;
        private String listStatus;
        private String listTraceIdLike;
        private String listBusinessKeyLike;
        private String listMessageType;
        private int listOffset;
        private int listPageSize;
        private String countQueueName;
        private String countStatus;
        private String countTraceIdLike;

        @Override
        public void createTableIfNotExists(String tableName) {
        }

        @Override
        public int insert(String tableName, MqFailedMessage message) {
            return 0;
        }

        @Override
        public int update(String tableName, MqFailedMessage message) {
            return 0;
        }

        @Override
        public MqFailedMessage findById(String tableName, Long id) {
            return null;
        }

        @Override
        public List<MqFailedMessage> findAll(String tableName) {
            return List.of();
        }

        @Override
        public List<MqFailedMessage> list(String tableName, String queueName, String status,
                                          String traceIdLike, String businessKeyLike, String messageType,
                                          int offset, int pageSize) {
            this.listTableName = tableName;
            this.listQueueName = queueName;
            this.listStatus = status;
            this.listTraceIdLike = traceIdLike;
            this.listBusinessKeyLike = businessKeyLike;
            this.listMessageType = messageType;
            this.listOffset = offset;
            this.listPageSize = pageSize;
            return List.of();
        }

        @Override
        public long count(String tableName, String queueName, String status,
                          String traceIdLike, String businessKeyLike, String messageType) {
            this.countQueueName = queueName;
            this.countStatus = status;
            this.countTraceIdLike = traceIdLike;
            return 0;
        }

        @Override
        public long countAll(String tableName) {
            return 10L;
        }

        @Override
        public long countByStatus(String tableName, String status) {
            return switch (status) {
                case MqFailedMessage.STATUS_PENDING -> 1L;
                case MqFailedMessage.STATUS_RETRYING -> 2L;
                case MqFailedMessage.STATUS_SUCCESS -> 3L;
                case MqFailedMessage.STATUS_MANUAL -> 4L;
                case MqFailedMessage.STATUS_EXHAUSTED -> 4L;
                default -> 0L;
            };
        }

        @Override
        public int deleteById(String tableName, Long id) {
            return 0;
        }

        @Override
        public int deleteProcessed(String tableName, String successStatus,
                                   String exhaustedStatus, String manualStatus) {
            return 0;
        }
    }
}
