package com.framework.localmessage.service;

import com.framework.localmessage.model.LocalMessage;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMessageServiceContractTest {

    @Test
    void simplePublishDelegatesToFullMessagePublish() {
        CapturingLocalMessageService service = new CapturingLocalMessageService();

        LocalMessage result = service.publish("order.created", "ORD-1", "{}");

        assertThat(result).isSameAs(service.captured);
        assertThat(service.captured.getTopic()).isEqualTo("order.created");
        assertThat(service.captured.getBusinessKey()).isEqualTo("ORD-1");
        assertThat(service.captured.getPayload()).isEqualTo("{}");
    }

    private static class CapturingLocalMessageService implements LocalMessageService {

        private LocalMessage captured;

        @Override
        public LocalMessage publish(LocalMessage message) {
            this.captured = message;
            return message;
        }

        @Override
        public int retryDueMessages() {
            throw new UnsupportedOperationException();
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

    }
}
