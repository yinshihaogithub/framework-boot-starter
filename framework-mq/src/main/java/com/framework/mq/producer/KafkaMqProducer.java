package com.framework.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.constant.FrameworkConstants;
import com.framework.mq.config.MqProperties;
import com.framework.mq.core.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaOperations;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Kafka sender adapter.
 */
@Slf4j
public class KafkaMqProducer implements MqMessageSender {

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaMqProducer(KafkaOperations<String, String> kafkaOperations) {
        this.kafkaOperations = Objects.requireNonNull(kafkaOperations, "kafkaOperations must not be null");
    }

    @Override
    public MqProperties.Provider provider() {
        return MqProperties.Provider.KAFKA;
    }

    @Override
    public <T> void send(String topic, String key, MessageWrapper<T> wrapper) {
        String safeTopic = MqSendSupport.requireText(topic, "topic");
        String safeKey = MqSendSupport.trimToNull(key);
        MqSendSupport.fillTrace(wrapper);
        try {
            String json = objectMapper.writeValueAsString(wrapper);
            ProducerRecord<String, String> record = new ProducerRecord<>(safeTopic, safeKey, json);
            record.headers().add(FrameworkConstants.TRACE_ID_HEADER,
                    wrapper.getTraceId().getBytes(StandardCharsets.UTF_8));
            if (wrapper.getParentMessageId() != null) {
                record.headers().add("X-Parent-Message-Id",
                        wrapper.getParentMessageId().getBytes(StandardCharsets.UTF_8));
            }
            kafkaOperations.send(record);
            log.debug("[Kafka发送] topic={}, key={}, messageId={}, traceId={}",
                    safeTopic, safeKey, wrapper.getMessageId(), wrapper.getTraceId());
        } catch (Exception e) {
            log.error("[Kafka发送失败] topic={}, key={}", safeTopic, safeKey, e);
            throw new RuntimeException("Kafka消息发送失败", e);
        }
    }
}
