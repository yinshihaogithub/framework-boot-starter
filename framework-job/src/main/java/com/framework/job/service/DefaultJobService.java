package com.framework.job.service;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default job facade implementation.
 */
@Slf4j
public class DefaultJobService implements JobService {

    private final Map<String, JobHandler> handlers;

    public DefaultJobService(List<JobHandler> handlers) {
        this.handlers = JobHandlerRegistry.from(handlers);
    }

    @Override
    public Set<String> names() {
        return handlers.keySet();
    }

    @Override
    public boolean run(String name) {
        String normalizedName = JobHandlerRegistry.normalize(name);
        JobHandler handler = handlers.get(normalizedName);
        if (handler == null) {
            return false;
        }
        try {
            handler.execute();
            return true;
        } catch (Exception e) {
            log.error("[任务执行失败] name={}", normalizedName, e);
            return false;
        }
    }
}
