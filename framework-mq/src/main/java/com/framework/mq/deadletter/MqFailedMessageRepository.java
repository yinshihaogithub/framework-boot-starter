package com.framework.mq.deadletter;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MQ failed message records.
 */
public interface MqFailedMessageRepository {

    MqFailedMessage save(MqFailedMessage message);

    boolean update(MqFailedMessage message);

    Optional<MqFailedMessage> findById(Long id);

    List<MqFailedMessage> findAll();

    boolean deleteById(Long id);

    int deleteProcessed();
}
