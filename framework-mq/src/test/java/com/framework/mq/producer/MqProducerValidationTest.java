package com.framework.mq.producer;

import com.framework.core.trace.TraceContext;
import com.framework.mq.core.MessageWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqProducerValidationTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void rabbitProducerRejectsInvalidDestinationBeforeSending() {
        MqProducer producer = new MqProducer(new RabbitTemplate());

        assertThatThrownBy(() -> producer.send("", " ", MessageWrapper.of("payload")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("routingKey");
    }

    @Test
    void rabbitProducerRejectsNegativeDelayAndTtl() {
        MqProducer producer = new MqProducer(new RabbitTemplate());

        assertThatThrownBy(() -> producer.sendWithDelay("", "order.created", MessageWrapper.of("payload"), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delayMs");
        assertThatThrownBy(() -> producer.sendWithTtl("", "order.created", "payload", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttlMs");
    }

    @Test
    void senderSupportRejectsInvalidWrapper() {
        MessageWrapper<String> wrapper = MessageWrapper.of("payload");
        wrapper.setMessageId(" ");

        assertThatThrownBy(() -> new MqProducer(new RabbitTemplate()).send("", "order.created", wrapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageId");
    }

    @Test
    void senderSupportNormalizesValidTraceIdBeforeSending() {
        MessageWrapper<String> wrapper = MessageWrapper.of("payload");
        wrapper.setTraceId(" trace-from-upstream ");

        MqSendSupport.fillTrace(wrapper);

        assertThat(wrapper.getTraceId()).isEqualTo("trace-from-upstream");
    }

    @Test
    void senderSupportReplacesUnsafeTraceIdFromCurrentContextBeforeSending() {
        TraceContext.putTraceId("caller-trace");
        MessageWrapper<String> wrapper = MessageWrapper.of("payload");
        wrapper.setTraceId("bad\r\nX-Evil: 1");

        MqSendSupport.fillTrace(wrapper);

        assertThat(wrapper.getTraceId()).isEqualTo("caller-trace");
    }

    @Test
    void senderSupportGeneratesTraceIdWhenWrapperTraceIdIsUnsafeAndContextIsEmpty() {
        MessageWrapper<String> wrapper = MessageWrapper.of("payload");
        wrapper.setTraceId("bad\r\nX-Evil: 1");

        MqSendSupport.fillTrace(wrapper);

        assertThat(wrapper.getTraceId()).matches("[0-9a-f]{32}");
    }

    @Test
    void kafkaProducerRejectsNullTemplate() {
        assertThatThrownBy(() -> new KafkaMqProducer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("kafkaOperations");
    }

    @Test
    void kafkaProducerRejectsBlankTopicBeforeSending() {
        KafkaMqProducer producer = new KafkaMqProducer(kafkaOperations());

        assertThatThrownBy(() -> producer.send(" ", "ORDER-1", MessageWrapper.of("payload")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void rocketProducerRejectsNullTemplate() {
        assertThatThrownBy(() -> new RocketMqProducer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rocketMQTemplate");
    }

    @Test
    void rocketProducerRejectsBlankTopicBeforeSending() {
        RocketMqProducer producer = new RocketMqProducer(new FakeRocketMQTemplate());

        assertThatThrownBy(() -> producer.send(" ", "created", MessageWrapper.of("payload")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @SuppressWarnings("unchecked")
    private static KafkaOperations<String, String> kafkaOperations() {
        return (KafkaOperations<String, String>) Proxy.newProxyInstance(
                KafkaOperations.class.getClassLoader(),
                new Class<?>[]{KafkaOperations.class},
                (proxy, method, args) -> null);
    }

    public static class FakeRocketMQTemplate {
        public Object syncSend(String destination, Object payload) {
            return null;
        }
    }
}
