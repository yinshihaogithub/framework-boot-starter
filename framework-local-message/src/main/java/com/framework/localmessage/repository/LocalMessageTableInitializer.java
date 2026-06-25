package com.framework.localmessage.repository;

import com.framework.localmessage.config.LocalMessageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates the local message table when enabled.
 */
public class LocalMessageTableInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final LocalMessageProperties properties;

    public LocalMessageTableInitializer(JdbcTemplate jdbcTemplate, LocalMessageProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
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
                    topic VARCHAR(128) NOT NULL,
                    business_key VARCHAR(256),
                    payload TEXT,
                    status VARCHAR(32) NOT NULL,
                    retry_count INT NOT NULL DEFAULT 0,
                    max_retry INT NOT NULL DEFAULT 3,
                    next_retry_time DATETIME NULL,
                    error_message TEXT,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_status_next_retry (status, next_retry_time),
                    INDEX idx_topic_business_key (topic, business_key),
                    INDEX idx_create_time (create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表'
                """.formatted(tableName));
    }
}
