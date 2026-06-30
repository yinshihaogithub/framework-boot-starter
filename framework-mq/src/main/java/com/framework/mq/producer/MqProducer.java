package com.framework.mq.producer;

import com.framework.core.constant.FrameworkConstants;
import com.framework.mq.config.MqProperties;
import com.framework.mq.core.MessageWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Objects;

/**
 * 消息生产者
 * 支持普通消息、延迟消息、事务消息
 */
@Slf4j
public class MqProducer implements MqMessageSender {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public MqProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public MqProperties.Provider provider() {
        return MqProperties.Provider.RABBIT;
    }

    /**
     * 发送普通消息
     */
    public <T> void send(String exchange, String routingKey, T payload) {
        send(exchange, routingKey, MessageWrapper.of(payload));
    }

    /**
     * 发送带业务Key的消息（用于消费端幂等）
     */
    public <T> void send(String exchange, String routingKey, String businessKey, T payload) {
        send(exchange, routingKey, MessageWrapper.of(businessKey, payload));
    }

    /**
     * 发送包装消息
     */
    @Override
    public <T> void send(String exchange, String routingKey, MessageWrapper<T> wrapper) {
        MqSendSupport.requireNotNull(exchange, "exchange");
        String safeRoutingKey = MqSendSupport.requireText(routingKey, "routingKey");
        MqSendSupport.fillTrace(wrapper);
        try {
            String json = objectMapper.writeValueAsString(wrapper);
            rabbitTemplate.convertAndSend(exchange, safeRoutingKey, json, message -> {
                prepareMessage(message.getMessageProperties(), wrapper);
                return message;
            });
            log.debug("[MQ发送] exchange={}, routingKey={}, messageId={}, traceId={}, businessKey={}",
                    exchange, safeRoutingKey, wrapper.getMessageId(), wrapper.getTraceId(), wrapper.getBusinessKey());
        } catch (Exception e) {
            log.error("[MQ发送失败] exchange={}, routingKey={}", exchange, safeRoutingKey, e);
            throw new RuntimeException("消息发送失败", e);
        }
    }

    /**
     * 发送延迟消息（基于延迟插件 rabbitmq_delayed_message_exchange）
     *
     * @param delayMs 延迟毫秒数
     */
    public <T> void sendWithDelay(String exchange, String routingKey, T payload, long delayMs) {
        sendWithDelay(exchange, routingKey, MessageWrapper.of(payload), delayMs);
    }

    /**
     * 发送延迟消息
     */
    @Override
    public <T> void sendWithDelay(String exchange, String routingKey, MessageWrapper<T> wrapper, long delayMs) {
        MqSendSupport.requireNotNull(exchange, "exchange");
        String safeRoutingKey = MqSendSupport.requireText(routingKey, "routingKey");
        MqSendSupport.requireNonNegative(delayMs, "delayMs");
        MqSendSupport.fillTrace(wrapper);
        try {
            String json = objectMapper.writeValueAsString(wrapper);
            rabbitTemplate.convertAndSend(exchange, safeRoutingKey, json, message -> {
                prepareMessage(message.getMessageProperties(), wrapper);
                message.getMessageProperties().setHeader("x-delay", delayMs);
                return message;
            });
            log.debug("[MQ延迟发送] exchange={}, routingKey={}, delayMs={}, messageId={}, traceId={}",
                    exchange, safeRoutingKey, delayMs, wrapper.getMessageId(), wrapper.getTraceId());
        } catch (Exception e) {
            log.error("[MQ延迟发送失败] exchange={}, routingKey={}", exchange, safeRoutingKey, e);
            throw new RuntimeException("延迟消息发送失败", e);
        }
    }

    /**
     * 发送带 TTL 的消息（进入死信队列超时后转发）
     *
     * @param ttlMs 消息存活时间（毫秒），超时后转发到死信队列
     */
    public <T> void sendWithTtl(String exchange, String routingKey, T payload, long ttlMs) {
        MqSendSupport.requireNotNull(exchange, "exchange");
        String safeRoutingKey = MqSendSupport.requireText(routingKey, "routingKey");
        MqSendSupport.requireNonNegative(ttlMs, "ttlMs");
        MessageWrapper<T> wrapper = MessageWrapper.of(payload);
        MqSendSupport.fillTrace(wrapper);
        try {
            String json = objectMapper.writeValueAsString(wrapper);
            MessagePostProcessor processor = message -> {
                prepareMessage(message.getMessageProperties(), wrapper);
                message.getMessageProperties().setExpiration(String.valueOf(ttlMs));
                return message;
            };
            rabbitTemplate.convertAndSend(exchange, safeRoutingKey, json, processor);
            log.debug("[MQ TTL发送] exchange={}, routingKey={}, ttlMs={}, messageId={}, traceId={}",
                    exchange, safeRoutingKey, ttlMs, wrapper.getMessageId(), wrapper.getTraceId());
        } catch (Exception e) {
            log.error("[MQ TTL发送失败] exchange={}", exchange, e);
            throw new RuntimeException("TTL消息发送失败", e);
        }
    }

    private <T> void prepareMessage(MessageProperties properties, MessageWrapper<T> wrapper) {
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(wrapper.getMessageId());
        properties.setTimestamp(new java.util.Date(wrapper.getTimestamp()));
        properties.setHeader(FrameworkConstants.TRACE_ID_HEADER, wrapper.getTraceId());
        properties.setHeader(FrameworkConstants.TRACE_ID_MDC_KEY, wrapper.getTraceId());
        if (wrapper.getParentMessageId() != null) {
            properties.setHeader("X-Parent-Message-Id", wrapper.getParentMessageId());
        }
    }
}
