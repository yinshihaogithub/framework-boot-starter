package com.framework.localmessage.scheduler;

import com.framework.localmessage.service.LocalMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Scheduled local message retry worker.
 */
@Slf4j
public class LocalMessageRetryScheduler {

    private final LocalMessageService localMessageService;

    public LocalMessageRetryScheduler(LocalMessageService localMessageService) {
        this.localMessageService = localMessageService;
    }

    @Scheduled(fixedDelayString = "${framework.local-message.scheduler.fixed-delay:30000}")
    public void retryDueMessages() {
        int handled = localMessageService.retryDueMessages();
        if (handled > 0) {
            log.info("[本地消息] 已处理待重试消息 {} 条", handled);
        }
    }
}
