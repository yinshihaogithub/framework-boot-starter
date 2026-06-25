package com.framework.notify.channel;

import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;

/**
 * Notification channel extension point.
 */
public interface NotifyChannel {

    NotifyChannelType type();

    NotifyResult send(NotifyMessage message);
}
