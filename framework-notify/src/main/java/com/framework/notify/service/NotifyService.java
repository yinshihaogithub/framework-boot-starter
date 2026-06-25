package com.framework.notify.service;

import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;

/**
 * Notification facade.
 */
public interface NotifyService {

    NotifyResult send(NotifyMessage message);

    default NotifyResult send(NotifyChannelType channel, String title, String content) {
        return send(NotifyMessage.of(channel, title, content));
    }
}
