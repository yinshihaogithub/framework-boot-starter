package com.framework.notify.channel;

import com.framework.notify.model.NotifyChannelType;
import com.framework.notify.model.NotifyMessage;
import com.framework.notify.model.NotifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

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
        NotifyResult validationResult = validateMessage(message);
        if (validationResult != null) {
            return validationResult;
        }
        log.info("[通知] title={}, receivers={}, content={}",
                message.getTitle(), message.getReceivers(), message.getContent());
        return NotifyResult.success(type());
    }

    private NotifyResult validateMessage(NotifyMessage message) {
        if (message == null) {
            return NotifyResult.failure(type(), "message must not be null");
        }
        if (!StringUtils.hasText(message.getTitle())) {
            return NotifyResult.failure(type(), "title must not be blank");
        }
        if (!StringUtils.hasText(message.getContent())) {
            return NotifyResult.failure(type(), "content must not be blank");
        }
        return null;
    }
}
