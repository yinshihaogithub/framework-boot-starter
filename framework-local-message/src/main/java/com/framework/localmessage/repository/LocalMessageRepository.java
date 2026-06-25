package com.framework.localmessage.repository;

import com.framework.localmessage.model.LocalMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Local message storage abstraction.
 */
public interface LocalMessageRepository {

    LocalMessage save(LocalMessage message);

    Optional<LocalMessage> findById(Long id);

    List<LocalMessage> findDueMessages(LocalDateTime now, int limit);

    List<LocalMessage> findAll();

    void delete(Long id);
}
