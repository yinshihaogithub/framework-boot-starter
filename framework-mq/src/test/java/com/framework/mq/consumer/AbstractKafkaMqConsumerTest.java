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
        record.headers().add(FrameworkConstants.TRACE_ID_HEADER, "\u00A0kafka-trace\u3000".getBytes(StandardCharsets.UTF_8));

        boolean consumed = consumer.handleRecord(record);

        assertThat(consumed).isTrue();
        assertThat(consumer.observedTrace()).hasValue("kafka-trace");
        assertThat(consumer.lastWrapper().getPayload()).isEqualTo("payload");
        assertThat(consumer.lastWrapper().getBusinessKey()).isEqualTo("ORDER-1");
    }

    @Test
    void ignoresNullKafkaTraceHeaderValueAndRestoresPreviousContext() throws Exception {
        RecordingKafkaConsumer consumer = new RecordingKafkaConsumer();
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-2", "OrderCreated", "payload");
        wrapper.setTraceId("wrapper-trace");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "order-topic",
                0,
                43,
                "ORDER-2",
                objectMapper.writeValueAsString(wrapper)
        );
        record.headers().add(FrameworkConstants.TRACE_ID_HEADER, null);
        TraceContext.putTraceId("caller-trace");

        boolean consumed = consumer.handleRecord(record);

        assertThat(consumed).isTrue();
        assertThat(consumer.observedTrace()).hasValue("wrapper-trace");
        assertThat(TraceContext.getTraceId()).isEqualTo("caller-trace");
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
