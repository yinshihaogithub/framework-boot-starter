package com.framework.notify.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified notification message.
 */
@Data
@Accessors(chain = true)
public class NotifyMessage {

    private NotifyChannelType channel;
    private String title;
    private String content;
    private List<String> receivers = new ArrayList<>();
    private Map<String, Object> templateParams = new HashMap<>();
    private String webhookUrl;

    public static NotifyMessage of(NotifyChannelType channel, String title, String content) {
        return new NotifyMessage()
                .setChannel(channel)
                .setTitle(title)
                .setContent(content);
    }
}
