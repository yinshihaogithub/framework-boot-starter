package com.framework.localmessage.repository;

import com.framework.localmessage.config.LocalMessageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

/**
 * Creates the local message table when enabled.
 */
public class LocalMessageTableInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final LocalMessageProperties properties;

    public LocalMessageTableInitializer(JdbcTemplate jdbcTemplate, LocalMessageProperties properties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @PostConstruct
    public void init() {
        if (!properties.isAutoCreateTable()) {
            return;
        }
        String tableName = JdbcLocalMessageRepository.validateTableName(properties.getTableName());
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    message_id VARCHAR(64),
                    trace_id VARCHAR(64),
                    parent_message_id VARCHAR(64),
                    topic VARCHAR(128) NOT NULL,
                    business_key VARCHAR(256),
                    tenant_id VARCHAR(64),
                    operator VARCHAR(64),
                    source VARCHAR(64),
                    payload LONGTEXT,
                    status VARCHAR(32) NOT NULL,
                    retry_count INT NOT NULL DEFAULT 0,
                    max_retry INT NOT NULL DEFAULT 3,
                    next_retry_time DATETIME NULL,
                    error_message TEXT,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_status_next_retry (status, next_retry_time),
                    INDEX idx_message_id (message_id),
                    INDEX idx_trace_id (trace_id),
                    INDEX idx_topic_business_key (topic, business_key),
                    INDEX idx_tenant_status (tenant_id, status),
                    INDEX idx_create_time (create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表'
                """.formatted(tableName));
    }
}
