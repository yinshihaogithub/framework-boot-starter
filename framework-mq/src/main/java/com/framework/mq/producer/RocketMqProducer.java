package com.framework.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.trace.TraceContext;
import com.framework.mq.config.MqProperties;
import com.framework.mq.core.MessageWrapper;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * RocketMQ sender adapter backed by RocketMQTemplate when present.
 */
@Slf4j
public class RocketMqProducer implements MqMessageSender {

    private final Object rocketMQTemplate;
    private final Method syncSend;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RocketMqProducer(Object rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.syncSend = resolveSyncSend(rocketMQTemplate.getClass());
    }

    @Override
    public MqProperties.Provider provider() {
        return MqProperties.Provider.ROCKET;
    }

    @Override
    public <T> void send(String topic, String tag, MessageWrapper<T> wrapper) {
        try {
            fillTrace(wrapper);
            String destination = (tag == null || tag.isBlank()) ? topic : topic + ":" + tag;
            String json = objectMapper.writeValueAsString(wrapper);
            syncSend.invoke(rocketMQTemplate, destination, json);
            log.debug("[RocketMQ发送] destination={}, messageId={}, traceId={}",
                    destination, wrapper.getMessageId(), wrapper.getTraceId());
        } catch (Exception e) {
            log.error("[RocketMQ发送失败] topic={}, tag={}", topic, tag, e);
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

    private <T> void fillTrace(MessageWrapper<T> wrapper) {
        if (wrapper.getTraceId() == null || wrapper.getTraceId().isBlank()) {
            wrapper.setTraceId(TraceContext.ensureTraceId());
        }
    }
}
