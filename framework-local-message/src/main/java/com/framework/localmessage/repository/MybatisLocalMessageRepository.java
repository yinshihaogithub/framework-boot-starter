package com.framework.localmessage.repository;

import com.framework.localmessage.mapper.LocalMessageMapper;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-backed local message table repository.
 */
public class MybatisLocalMessageRepository implements LocalMessageRepository {

    private final LocalMessageMapper mapper;
    private final String tableName;

    public MybatisLocalMessageRepository(LocalMessageMapper mapper, String tableName) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.tableName = validateTableName(tableName);
    }

    @Override
    public LocalMessage save(LocalMessage message) {
        touch(message);
        if (message.getId() == null) {
            int inserted = mapper.insert(tableName, message);
            if (inserted <= 0 || message.getId() == null) {
                throw new IllegalStateException("local message insert failed");
            }
            return message;
        }
        updateExisting(message);
        return message;
    }

    @Override
    public boolean update(LocalMessage message) {
        touch(message);
        return updateExisting(message) > 0;
    }

    @Override
    public Optional<LocalMessage> findById(Long id) {
        return Optional.ofNullable(mapper.findById(tableName, id));
    }

    @Override
    public List<LocalMessage> findDueMessages(LocalDateTime now, int limit) {
        return mapper.findDueMessages(tableName, LocalMessageStatus.PENDING, now, limit);
    }

    @Override
    public boolean delete(Long id) {
        return mapper.delete(tableName, id) > 0;
    }

    public static String validateTableName(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("local message tableName must match [A-Za-z0-9_]+");
        }
        return tableName;
    }

    private int updateExisting(LocalMessage message) {
        return mapper.update(tableName, message);
    }

    private static void touch(LocalMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        LocalDateTime now = LocalDateTime.now();
        if (message.getCreateTime() == null) {
            message.setCreateTime(now);
        }
        message.setUpdateTime(now);
    }
}
