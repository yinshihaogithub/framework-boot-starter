package com.framework.job.service;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default job facade implementation.
 */
@Slf4j
public class DefaultJobService implements JobService {

    private final Map<String, JobHandler> handlers;

    public DefaultJobService(List<JobHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(JobHandler::name, Function.identity(), (left, right) -> left));
    }

    @Override
    public Set<String> names() {
        return handlers.keySet();
    }

    @Override
    public boolean run(String name) {
        JobHandler handler = handlers.get(name);
        if (handler == null) {
            return false;
        }
        try {
            handler.execute();
            return true;
        } catch (Exception e) {
            log.error("[任务执行失败] name={}", name, e);
            return false;
        }
    }
}
