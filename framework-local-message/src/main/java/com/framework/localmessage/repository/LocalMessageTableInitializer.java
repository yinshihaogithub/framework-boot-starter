package com.framework.localmessage.repository;

import com.framework.localmessage.config.LocalMessageProperties;
import com.framework.localmessage.mapper.LocalMessageMapper;
import jakarta.annotation.PostConstruct;

import java.util.Objects;

/**
 * Creates the local message table when enabled.
 */
public class LocalMessageTableInitializer {

    private final LocalMessageMapper mapper;
    private final LocalMessageProperties properties;

    public LocalMessageTableInitializer(LocalMessageMapper mapper, LocalMessageProperties properties) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @PostConstruct
    public void init() {
        if (!properties.isAutoCreateTable()) {
            return;
        }
        String tableName = MybatisLocalMessageRepository.validateTableName(properties.getTableName());
        mapper.createTableIfNotExists(tableName);
    }
}
