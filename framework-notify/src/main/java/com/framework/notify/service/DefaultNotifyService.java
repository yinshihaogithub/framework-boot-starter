package com.framework.notify.service;

import com.framework.notify.channel.NotifyChannel;
import com.framework.notify.config.NotifyProperties;
import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * Default notification facade implementation.
 */
@Slf4j
public class DefaultNotifyService implements NotifyService {

    private final NotifyProperties properties;
    private final Map<NotifyChannelType, NotifyChannel> channels = new EnumMap<>(NotifyChannelType.class);

    public DefaultNotifyService(NotifyProperties properties, List<NotifyChannel> channels) {
        this.properties = Objects.requireNonNull(properties, "notify properties must not be null");
        this.properties.validate();
        registerChannels(channels);
    }

    @Override
    public NotifyResult send(NotifyMessage message) {
        NotifyChannelType channelType = resolveChannel(message);
        NotifyResult validationResult = validate(message, channelType);
        if (validationResult != null) {
            return validationResult;
        }
        normalize(message);
        NotifyChannel channel = channels.get(channelType);
        if (channel == null) {
            return NotifyResult.failure(channelType, "notify channel is not registered");
        }
        try {
            NotifyResult result = channel.send(message);
            if (result == null) {
                log.warn("[通知发送失败] channel={}, title={}, error=channel returned null",
                        channelType, message.getTitle());
                return NotifyResult.failure(channelType, "notify channel returned null");
            }
            return result;
        } catch (Exception e) {
            String failureMessage = failureMessage(e);
            log.warn("[通知发送失败] channel={}, title={}, error={}",
                    channelType, message.getTitle(), failureMessage);
            return NotifyResult.failure(channelType, failureMessage);
        }
    }

    private NotifyChannelType resolveChannel(NotifyMessage message) {
        return message != null && message.getChannel() != null
                ? message.getChannel()
                : properties.getDefaultChannel();
    }

    private NotifyResult validate(NotifyMessage message, NotifyChannelType channelType) {
        if (message == null) {
            return NotifyResult.failure(channelType, "message must not be null");
        }
        if (!StringUtils.hasText(message.getTitle())) {
            return NotifyResult.failure(channelType, "title must not be blank");
        }
        if (!StringUtils.hasText(message.getContent())) {
            return NotifyResult.failure(channelType, "content must not be blank");
        }
        return null;
    }

    private void normalize(NotifyMessage message) {
        message.setTitle(trimToNull(message.getTitle()));
        message.setContent(trimToNull(message.getContent()));
        message.setWebhookUrl(trimToNull(message.getWebhookUrl()));
        message.setReceivers(normalizeReceivers(message.getReceivers()));
        if (message.getTemplateParams() == null) {
            message.setTemplateParams(new HashMap<>());
        }
    }

    private List<String> normalizeReceivers(List<String> receivers) {
        if (receivers == null || receivers.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalizedReceivers = new ArrayList<>();
        for (String receiver : receivers) {
            String normalizedReceiver = trimToNull(receiver);
            if (normalizedReceiver != null) {
                normalizedReceivers.add(normalizedReceiver);
            }
        }
        return normalizedReceivers;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void registerChannels(List<NotifyChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return;
        }
        for (NotifyChannel channel : channels) {
            if (channel == null) {
                throw new IllegalArgumentException("NotifyChannel must not be null");
            }
            NotifyChannelType type = channel.type();
            if (type == null) {
                throw new IllegalArgumentException("NotifyChannel type must not be null");
            }
            if (this.channels.containsKey(type)) {
                throw new IllegalArgumentException("Duplicate NotifyChannel type: " + type);
            }
            this.channels.put(type, channel);
        }
    }

    private String failureMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
