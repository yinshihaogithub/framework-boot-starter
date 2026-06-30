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

        MessageWrapper<String> unicodeBlankWrapper = MessageWrapper.of("payload");
        unicodeBlankWrapper.setMessageId("\u00A0\u3000");

        assertThatThrownBy(() -> new MqProducer(new RabbitTemplate()).send("", "order.created", unicodeBlankWrapper))
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
    void senderSupportNormalizesMessageMetadataBeforeSending() {
        MessageWrapper<String> wrapper = MessageWrapper.of(" ORD-1 ", " OrderCreated ", "payload");
        wrapper.setMessageId(" msg-1 ");
        wrapper.setParentMessageId(" parent-1 ");
        wrapper.setSource(" order-service ");

        MqSendSupport.fillTrace(wrapper);

        assertThat(wrapper.getMessageId()).isEqualTo("msg-1");
        assertThat(wrapper.getType()).isEqualTo("OrderCreated");
        assertThat(wrapper.getBusinessKey()).isEqualTo("ORD-1");
        assertThat(wrapper.getParentMessageId()).isEqualTo("parent-1");
        assertThat(wrapper.getSource()).isEqualTo("order-service");
    }

    @Test
    void senderSupportNormalizesUnicodeBoundarySpacesBeforeSending() {
        MessageWrapper<String> wrapper = MessageWrapper.of(
                "\u00A0ORD-1\u3000",
                "\u00A0OrderCreated\u3000",
                "payload");
        wrapper.setMessageId("\u00A0msg-1\u3000");
        wrapper.setParentMessageId("\u00A0parent-1\u3000");
        wrapper.setSource("\u00A0order-service\u3000");

        MqSendSupport.fillTrace(wrapper);

        assertThat(wrapper.getMessageId()).isEqualTo("msg-1");
        assertThat(wrapper.getType()).isEqualTo("OrderCreated");
        assertThat(wrapper.getBusinessKey()).isEqualTo("ORD-1");
        assertThat(wrapper.getParentMessageId()).isEqualTo("parent-1");
        assertThat(wrapper.getSource()).isEqualTo("order-service");
    }

    @Test
    void senderSupportClearsBlankOptionalMetadataBeforeSending() {
        MessageWrapper<String> wrapper = MessageWrapper.of("payload");
        wrapper.setBusinessKey(" ");
        wrapper.setParentMessageId(" ");
        wrapper.setSource(" ");

        MqSendSupport.fillTrace(wrapper);

        assertThat(wrapper.getBusinessKey()).isNull();
        assertThat(wrapper.getParentMessageId()).isNull();
        assertThat(wrapper.getSource()).isNull();
    }

    @Test
    void senderSupportClearsUnicodeBlankOptionalMetadataBeforeSending() {
        MessageWrapper<String> wrapper = MessageWrapper.of("payload");
        wrapper.setBusinessKey("\u00A0\u3000");
        wrapper.setParentMessageId("\u00A0\u3000");
        wrapper.setSource("\u00A0\u3000");

        MqSendSupport.fillTrace(wrapper);

        assertThat(wrapper.getBusinessKey()).isNull();
        assertThat(wrapper.getParentMessageId()).isNull();
        assertThat(wrapper.getSource()).isNull();
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
