package com.framework.mq.deadletter;

import com.framework.mq.config.MqProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

/**
 * Creates MQ management tables for MySQL when enabled.
 */
public class MqTableInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final MqProperties properties;

    public MqTableInitializer(JdbcTemplate jdbcTemplate, MqProperties properties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @PostConstruct
    public void init() {
        if (!properties.isAutoCreateTable()) {
            return;
        }
        String tableName = JdbcMqFailedMessageRepository.validateTableName(properties.getFailedMessageTableName());
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    message_id VARCHAR(64),
                    trace_id VARCHAR(64),
                    parent_message_id VARCHAR(64),
                    business_key VARCHAR(256),
                    message_type VARCHAR(128),
                    exchange_name VARCHAR(128),
                    routing_key VARCHAR(128),
                    queue_name VARCHAR(128),
                    payload LONGTEXT,
                    error_message TEXT,
                    error_stack LONGTEXT,
                    retry_count INT NOT NULL DEFAULT 0,
                    max_retry INT NOT NULL DEFAULT 3,
                    status VARCHAR(32) NOT NULL,
                    next_retry_time DATETIME NULL,
                    source VARCHAR(32),
                    tenant_id VARCHAR(64),
                    operator VARCHAR(64),
                    compensate_remark VARCHAR(512),
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_status_next_retry (status, next_retry_time),
                    INDEX idx_message_id (message_id),
                    INDEX idx_trace_id (trace_id),
                    INDEX idx_business_key (business_key),
                    INDEX idx_queue_status (queue_name, status),
                    INDEX idx_create_time (create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ失败消息与人工补偿表'
                """.formatted(tableName));
    }
}
