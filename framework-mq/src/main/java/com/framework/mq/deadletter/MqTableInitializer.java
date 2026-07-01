package com.framework.mq.deadletter;

import com.framework.mq.config.MqProperties;
import com.framework.mq.mapper.MqFailedMessageMapper;
import jakarta.annotation.PostConstruct;

import java.util.Objects;

/**
 * Creates MQ management tables for MySQL when enabled.
 */
public class MqTableInitializer {

    private final MqFailedMessageMapper mapper;
    private final MqProperties properties;

    public MqTableInitializer(MqFailedMessageMapper mapper, MqProperties properties) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @PostConstruct
    public void init() {
        if (!properties.isAutoCreateTable()) {
            return;
        }
        String tableName = MybatisMqFailedMessageRepository.validateTableName(properties.getFailedMessageTableName());
        mapper.createTableIfNotExists(tableName);
    }
}
