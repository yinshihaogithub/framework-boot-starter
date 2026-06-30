package com.framework.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.mq.config.MqProperties;
import com.framework.mq.core.MessageWrapper;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * RocketMQ sender adapter backed by RocketMQTemplate when present.
 */
@Slf4j
public class RocketMqProducer implements MqMessageSender {

    private final Object rocketMQTemplate;
    private final Method syncSend;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RocketMqProducer(Object rocketMQTemplate) {
        this.rocketMQTemplate = Objects.requireNonNull(rocketMQTemplate, "rocketMQTemplate must not be null");
        this.syncSend = resolveSyncSend(rocketMQTemplate.getClass());
    }

    @Override
    public MqProperties.Provider provider() {
        return MqProperties.Provider.ROCKET;
    }

    @Override
    public <T> void send(String topic, String tag, MessageWrapper<T> wrapper) {
        String safeTopic = MqSendSupport.requireText(topic, "topic");
        String safeTag = MqSendSupport.trimToNull(tag);
        MqSendSupport.fillTrace(wrapper);
        try {
            String destination = safeTag == null ? safeTopic : safeTopic + ":" + safeTag;
            String json = objectMapper.writeValueAsString(wrapper);
            syncSend.invoke(rocketMQTemplate, destination, json);
            log.debug("[RocketMQ发送] destination={}, messageId={}, traceId={}",
                    destination, wrapper.getMessageId(), wrapper.getTraceId());
        } catch (Exception e) {
            log.error("[RocketMQ发送失败] topic={}, tag={}", safeTopic, safeTag, e);
            throw new RuntimeException("RocketMQ消息发送失败", e);
        }
    }

    private Method resolveSyncSend(Class<?> templateClass) {
        try {
            return templateClass.getMethod("syncSend", String.class, Object.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("RocketMQTemplate.syncSend(String, Object) is required", e);
        }
    }
}
