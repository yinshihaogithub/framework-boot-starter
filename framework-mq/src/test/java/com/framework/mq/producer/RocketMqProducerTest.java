package com.framework.mq.producer;

import com.framework.mq.core.MessageWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocketMqProducerTest {

    @Test
    void sendInvokesRocketTemplateSyncSend() {
        FakeRocketMQTemplate template = new FakeRocketMQTemplate();
        RocketMqProducer producer = new RocketMqProducer(template);

        producer.send("order-topic", "created", MessageWrapper.of("ORDER-1", "payload"));

        assertThat(template.destination).isEqualTo("order-topic:created");
        assertThat(template.payload)
                .contains("\"businessKey\":\"ORDER-1\"")
                .contains("\"payload\":\"payload\"");
    }

    @Test
    void sendWithDelayMapsMillisecondsToRocketDelayLevel() {
        DelayCapableRocketMQTemplate template = new DelayCapableRocketMQTemplate();
        RocketMqProducer producer = new RocketMqProducer(template);

        producer.sendWithDelay("order-topic", "created", MessageWrapper.of("ORDER-1", "payload"), 4_500L);

        assertThat(template.destination).isEqualTo("order-topic:created");
        assertThat(template.payload)
                .contains("\"businessKey\":\"ORDER-1\"")
                .contains("\"payload\":\"payload\"");
        assertThat(template.timeoutMs).isEqualTo(3000L);
        assertThat(template.delayLevel).isEqualTo(2);
    }

    @Test
    void sendWithDelayCapsToRocketMaxDelayLevel() {
        DelayCapableRocketMQTemplate template = new DelayCapableRocketMQTemplate();
        RocketMqProducer producer = new RocketMqProducer(template);

        producer.sendWithDelay("order-topic", null, MessageWrapper.of("payload"), 8_000_000L);

        assertThat(template.destination).isEqualTo("order-topic");
        assertThat(template.delayLevel).isEqualTo(18);
    }

    @Test
    void sendWithZeroDelayFallsBackToImmediateSyncSend() {
        FakeRocketMQTemplate template = new FakeRocketMQTemplate();
        RocketMqProducer producer = new RocketMqProducer(template);

        producer.sendWithDelay("order-topic", "created", MessageWrapper.of("payload"), 0L);

        assertThat(template.destination).isEqualTo("order-topic:created");
    }

    @Test
    void sendWithDelayReportsMissingRocketTemplateDelayOverload() {
        FakeRocketMQTemplate template = new FakeRocketMQTemplate();
        RocketMqProducer producer = new RocketMqProducer(template);

        assertThatThrownBy(() -> producer.sendWithDelay("order-topic", "created", MessageWrapper.of("payload"), 1_000L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("syncSend(String, Object, long, int)");
    }

    public static class FakeRocketMQTemplate {
        protected String destination;
        protected String payload;

        public Object syncSend(String destination, Object payload) {
            this.destination = destination;
            this.payload = payload.toString();
            return null;
        }
    }

    public static class DelayCapableRocketMQTemplate extends FakeRocketMQTemplate {
        private long timeoutMs;
        private int delayLevel;

        public Object syncSend(String destination, Object payload, long timeoutMs, int delayLevel) {
            this.destination = destination;
            this.payload = payload.toString();
            this.timeoutMs = timeoutMs;
            this.delayLevel = delayLevel;
            return null;
        }
    }
}
