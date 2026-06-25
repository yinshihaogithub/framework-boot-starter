package com.framework.mq.producer;

import com.framework.mq.core.MessageWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    public static class FakeRocketMQTemplate {
        private String destination;
        private String payload;

        public Object syncSend(String destination, Object payload) {
            this.destination = destination;
            this.payload = payload.toString();
            return null;
        }
    }
}
