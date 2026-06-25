package com.framework.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractKafkaMqConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void handlesKafkaRecordAndPropagatesTraceHeader() throws Exception {
        RecordingKafkaConsumer consumer = new RecordingKafkaConsumer();
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-1", "OrderCreated", "payload");
        wrapper.setTraceId(null);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "order-topic",
                0,
                42,
                "ORDER-1",
                objectMapper.writeValueAsString(wrapper)
        );
        record.headers().add(FrameworkConstants.TRACE_ID_HEADER, "kafka-trace".getBytes(StandardCharsets.UTF_8));

        boolean consumed = consumer.handleRecord(record);

        assertThat(consumed).isTrue();
        assertThat(consumer.observedTrace()).hasValue("kafka-trace");
        assertThat(consumer.lastWrapper().getPayload()).isEqualTo("payload");
        assertThat(consumer.lastWrapper().getBusinessKey()).isEqualTo("ORDER-1");
    }

    private static class RecordingKafkaConsumer extends AbstractKafkaMqConsumer<String> {

        private final AtomicReference<String> observedTrace = new AtomicReference<>();
        private MessageWrapper<String> lastWrapper;

        RecordingKafkaConsumer() {
            super(null, String.class);
        }

        @Override
        protected void doConsume(MessageWrapper<String> wrapper) {
            observedTrace.set(TraceContext.getTraceId());
            lastWrapper = wrapper;
        }

        AtomicReference<String> observedTrace() {
            return observedTrace;
        }

        MessageWrapper<String> lastWrapper() {
            return lastWrapper;
        }
    }
}
