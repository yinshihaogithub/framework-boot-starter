package com.framework.localmessage.scheduler;

import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.service.LocalMessageService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMessageRetrySchedulerTest {

    @Test
    void constructorRejectsNullService() {
        assertThatThrownBy(() -> new LocalMessageRetryScheduler(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("localMessageService");
    }

    @Test
    void retryDueMessagesDoesNotPropagateServiceFailures() {
        LocalMessageRetryScheduler scheduler = new LocalMessageRetryScheduler(new StubLocalMessageService() {
            @Override
            public int retryDueMessages() {
                throw new IllegalStateException("database unavailable");
            }
        });

        assertThatCode(scheduler::retryDueMessages).doesNotThrowAnyException();
    }

    private static class StubLocalMessageService implements LocalMessageService {

        @Override
        public LocalMessage publish(String topic, String businessKey, String payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalMessage publish(LocalMessage message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int retryDueMessages() {
            return 0;
        }

        @Override
        public boolean retryNow(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markSuccess(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markFailure(Long id, Exception exception) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<LocalMessage> findById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LocalMessage> findAll() {
            throw new UnsupportedOperationException();
        }
    }
}
