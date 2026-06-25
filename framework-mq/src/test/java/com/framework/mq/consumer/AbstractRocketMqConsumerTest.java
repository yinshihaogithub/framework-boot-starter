package com.framework.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractRocketMqConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void handlesRocketMessageBodyAndPropagatesTrace() throws Exception {
        RecordingRocketConsumer consumer = new RecordingRocketConsumer();
        MessageWrapper<String> wrapper = MessageWrapper.of("ORDER-1", "OrderCreated", "payload");
        wrapper.setTraceId(null);

        boolean consumed = consumer.handleMessage(
                objectMapper.writeValueAsString(wrapper),
                "rocket-trace",
                "ORDER-1"
        );

        assertThat(consumed).isTrue();
        assertThat(consumer.observedTrace()).hasValue("rocket-trace");
        assertThat(consumer.lastWrapper().getPayload()).isEqualTo("payload");
    }

    private static class RecordingRocketConsumer extends AbstractRocketMqConsumer<String> {

        private final AtomicReference<String> observedTrace = new AtomicReference<>();
        private MessageWrapper<String> lastWrapper;

        RecordingRocketConsumer() {
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
