package com.framework.core.trace;

import org.springframework.core.task.TaskDecorator;

/**
 * Propagates MDC trace context to Spring executor worker threads.
 */
public class TraceContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return TraceContext.wrap(runnable);
    }
}
