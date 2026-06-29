package com.framework.localmessage.scheduler;

import com.framework.localmessage.service.LocalMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * Scheduled local message retry worker.
 */
@Slf4j
public class LocalMessageRetryScheduler {

    private final LocalMessageService localMessageService;

    public LocalMessageRetryScheduler(LocalMessageService localMessageService) {
        this.localMessageService = Objects.requireNonNull(localMessageService, "localMessageService must not be null");
    }

    @Scheduled(fixedDelayString = "${framework.local-message.scheduler.fixed-delay:30000}")
    public void retryDueMessages() {
        try {
            int handled = localMessageService.retryDueMessages();
            if (handled > 0) {
                log.info("[本地消息] 已处理待重试消息 {} 条", handled);
            }
        } catch (Exception e) {
            log.warn("[本地消息] 待重试消息扫描失败 error={}", failureMessage(e));
        }
    }

    private String failureMessage(Exception exception) {
        if (exception == null) {
            return "unknown error";
        }
        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        String simpleName = exception.getClass().getSimpleName();
        return simpleName.isBlank() ? exception.getClass().getName() : simpleName;
    }
}
