package com.framework.notify.channel;

import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import lombok.extern.slf4j.Slf4j;

/**
 * Default log notification channel.
 */
@Slf4j
public class LogNotifyChannel implements NotifyChannel {

    @Override
    public NotifyChannelType type() {
        return NotifyChannelType.LOG;
    }

    @Override
    public NotifyResult send(NotifyMessage message) {
        log.info("[通知] title={}, receivers={}, content={}",
                message.getTitle(), message.getReceivers(), message.getContent());
        return NotifyResult.success(type());
    }
}
