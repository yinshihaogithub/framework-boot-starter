package com.framework.mq.deadletter;

import com.framework.mq.mapper.MqFailedMessageMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-backed failed message repository.
 */
public class MybatisMqFailedMessageRepository implements MqFailedMessageRepository {

    private final MqFailedMessageMapper mapper;
    private final String tableName;

    public MybatisMqFailedMessageRepository(MqFailedMessageMapper mapper, String tableName) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.tableName = validateTableName(tableName);
    }

    @Override
    public MqFailedMessage save(MqFailedMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        if (message.getId() == null) {
            int inserted = mapper.insert(tableName, message);
            if (inserted <= 0 || message.getId() == null) {
                throw new IllegalStateException("mq failed message insert failed");
            }
            return message;
        }
        updateExisting(message);
        return message;
    }

    @Override
    public boolean update(MqFailedMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        return updateExisting(message) > 0;
    }

    @Override
    public Optional<MqFailedMessage> findById(Long id) {
        return Optional.ofNullable(mapper.findById(tableName, id));
    }

    @Override
    public List<MqFailedMessage> findAll() {
        return mapper.findAll(tableName);
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(tableName, id) > 0;
    }

    @Override
    public int deleteProcessed() {
        return mapper.deleteProcessed(tableName,
                MqFailedMessage.STATUS_SUCCESS,
                MqFailedMessage.STATUS_EXHAUSTED,
                MqFailedMessage.STATUS_MANUAL);
    }

    public static String validateTableName(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("mq failed message tableName must match [A-Za-z0-9_]+");
        }
        return tableName;
    }

    private int updateExisting(MqFailedMessage message) {
        return mapper.update(tableName, message);
    }
}
