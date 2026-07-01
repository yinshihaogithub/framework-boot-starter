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

    private static final long DEFAULT_SEND_TIMEOUT_MS = 3000L;
    private static final long[] DELAY_LEVEL_MILLIS = {
            1_000L,
            5_000L,
            10_000L,
            30_000L,
            60_000L,
            120_000L,
            180_000L,
            240_000L,
            300_000L,
            360_000L,
            420_000L,
            480_000L,
            540_000L,
            600_000L,
            1_200_000L,
            1_800_000L,
            3_600_000L,
            7_200_000L
    };

    private final Object rocketMQTemplate;
    private final Method syncSend;
    private final Method syncSendDelay;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RocketMqProducer(Object rocketMQTemplate) {
        this.rocketMQTemplate = Objects.requireNonNull(rocketMQTemplate, "rocketMQTemplate must not be null");
        this.syncSend = resolveSyncSend(rocketMQTemplate.getClass());
        this.syncSendDelay = resolveSyncSendDelay(rocketMQTemplate.getClass());
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
            String destination = destination(safeTopic, safeTag);
            String json = objectMapper.writeValueAsString(wrapper);
            syncSend.invoke(rocketMQTemplate, destination, json);
            log.debug("[RocketMQ发送] destination={}, messageId={}, traceId={}",
                    destination, wrapper.getMessageId(), wrapper.getTraceId());
        } catch (Exception e) {
            log.error("[RocketMQ发送失败] topic={}, tag={}", safeTopic, safeTag, e);
            throw new RuntimeException("RocketMQ消息发送失败", e);
        }
    }

    @Override
    public <T> void sendWithDelay(String topic, String tag, MessageWrapper<T> wrapper, long delayMs) {
        String safeTopic = MqSendSupport.requireText(topic, "topic");
        String safeTag = MqSendSupport.trimToNull(tag);
        MqSendSupport.requireNonNegative(delayMs, "delayMs");
        if (delayMs == 0) {
            send(safeTopic, safeTag, wrapper);
            return;
        }
        if (syncSendDelay == null) {
            throw new UnsupportedOperationException(
                    "RocketMQTemplate.syncSend(String, Object, long, int) is required for delay send");
        }
        MqSendSupport.fillTrace(wrapper);
        try {
            String destination = destination(safeTopic, safeTag);
            String json = objectMapper.writeValueAsString(wrapper);
            int delayLevel = delayLevel(delayMs);
            syncSendDelay.invoke(rocketMQTemplate, destination, json, DEFAULT_SEND_TIMEOUT_MS, delayLevel);
            log.debug("[RocketMQ延迟发送] destination={}, delayMs={}, delayLevel={}, messageId={}, traceId={}",
                    destination, delayMs, delayLevel, wrapper.getMessageId(), wrapper.getTraceId());
        } catch (Exception e) {
            log.error("[RocketMQ延迟发送失败] topic={}, tag={}, delayMs={}", safeTopic, safeTag, delayMs, e);
            throw new RuntimeException("RocketMQ延迟消息发送失败", e);
        }
    }

    private Method resolveSyncSend(Class<?> templateClass) {
        try {
            return templateClass.getMethod("syncSend", String.class, Object.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("RocketMQTemplate.syncSend(String, Object) is required", e);
        }
    }

    private Method resolveSyncSendDelay(Class<?> templateClass) {
        try {
            return templateClass.getMethod("syncSend", String.class, Object.class, long.class, int.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private String destination(String topic, String tag) {
        return tag == null ? topic : topic + ":" + tag;
    }

    private int delayLevel(long delayMs) {
        for (int index = 0; index < DELAY_LEVEL_MILLIS.length; index++) {
            if (delayMs <= DELAY_LEVEL_MILLIS[index]) {
                return index + 1;
            }
        }
        return DELAY_LEVEL_MILLIS.length;
    }
}
