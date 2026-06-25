package com.framework.mq.producer;

import com.framework.mq.config.MqProperties;
import com.framework.mq.core.MessageWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqMessageSenderRegistryTest {

    @Test
    void selectsActiveSenderByConfiguredProvider() {
        StubSender rabbit = new StubSender(MqProperties.Provider.RABBIT);
        StubSender kafka = new StubSender(MqProperties.Provider.KAFKA);
        MqProperties properties = new MqProperties();
        properties.setProvider(MqProperties.Provider.KAFKA);

        MqMessageSenderRegistry registry = new MqMessageSenderRegistry(properties, List.of(rabbit, kafka));

        assertThat(registry.activeSender()).isSameAs(kafka);
    }

    @Test
    void throwsClearExceptionWhenProviderSenderIsMissing() {
        MqProperties properties = new MqProperties();
        properties.setProvider(MqProperties.Provider.ROCKET);
        MqMessageSenderRegistry registry = new MqMessageSenderRegistry(
                properties,
                List.of(new StubSender(MqProperties.Provider.RABBIT))
        );

        assertThatThrownBy(registry::activeSender)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROCKET");
    }

    private static class StubSender implements MqMessageSender {

        private final MqProperties.Provider provider;

        private StubSender(MqProperties.Provider provider) {
            this.provider = provider;
        }

        @Override
        public MqProperties.Provider provider() {
            return provider;
        }

        @Override
        public <T> void send(String destination, String routingKey, MessageWrapper<T> wrapper) {
        }
    }
}
