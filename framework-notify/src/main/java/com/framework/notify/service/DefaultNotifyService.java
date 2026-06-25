package com.framework.notify.service;

import com.framework.notify.channel.NotifyChannel;
import com.framework.notify.config.NotifyProperties;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Default notification facade implementation.
 */
@Slf4j
public class DefaultNotifyService implements NotifyService {

    private final NotifyProperties properties;
    private final Map<NotifyChannelType, NotifyChannel> channels = new EnumMap<>(NotifyChannelType.class);

    public DefaultNotifyService(NotifyProperties properties, List<NotifyChannel> channels) {
        this.properties = properties;
        channels.forEach(channel -> this.channels.put(channel.type(), channel));
    }

    @Override
    public NotifyResult send(NotifyMessage message) {
        NotifyChannelType channelType = message.getChannel() != null
                ? message.getChannel()
                : properties.getDefaultChannel();
        NotifyChannel channel = channels.get(channelType);
        if (channel == null) {
            return NotifyResult.failure(channelType, "notify channel is not registered");
        }
        try {
            return channel.send(message);
        } catch (Exception e) {
            log.warn("[通知发送失败] channel={}, title={}, error={}",
                    channelType, message.getTitle(), e.getMessage());
            return NotifyResult.failure(channelType, e.getMessage());
        }
    }
}
