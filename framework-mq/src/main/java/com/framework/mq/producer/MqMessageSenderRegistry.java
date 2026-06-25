package com.framework.mq.producer;

import com.framework.mq.config.MqProperties;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Selects the active MQ sender by framework.mq.provider.
 */
public class MqMessageSenderRegistry {

    private final MqProperties properties;
    private final Map<MqProperties.Provider, MqMessageSender> senders;

    public MqMessageSenderRegistry(MqProperties properties, List<MqMessageSender> senders) {
        this.properties = properties;
        this.senders = new EnumMap<>(MqProperties.Provider.class);
        for (MqMessageSender sender : senders) {
            this.senders.put(sender.provider(), sender);
        }
    }

    public MqMessageSender activeSender() {
        MqMessageSender sender = senders.get(properties.getProvider());
        if (sender == null) {
            throw new IllegalStateException("No MQ sender available for provider " + properties.getProvider());
        }
        return sender;
    }
}
