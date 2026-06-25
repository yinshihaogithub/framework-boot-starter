package com.framework.notify.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Notification sending result.
 */
@Data
@AllArgsConstructor
public class NotifyResult {

    private boolean success;
    private NotifyChannelType channel;
    private String message;

    public static NotifyResult success(NotifyChannelType channel) {
        return new NotifyResult(true, channel, "success");
    }

    public static NotifyResult failure(NotifyChannelType channel, String message) {
        return new NotifyResult(false, channel, message);
    }
}
