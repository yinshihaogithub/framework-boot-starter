package com.framework.job.service;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.IJobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.List;
import java.util.Map;

/**
 * Registers framework job handlers into XXL-JOB's handler registry.
 */
@Slf4j
public class XxlJobHandlerRegistrar implements SmartInitializingSingleton {

    private final Map<String, JobHandler> handlers;

    public XxlJobHandlerRegistrar(List<JobHandler> handlers) {
        this.handlers = JobHandlerRegistry.from(handlers);
    }

    @Override
    public void afterSingletonsInstantiated() {
        handlers.forEach(this::register);
    }

    private void register(String name, JobHandler handler) {
        if (XxlJobExecutor.loadJobHandler(name) != null) {
            log.warn("[XXL-JOB] handler already exists, skip framework JobHandler name={}", name);
            return;
        }
        XxlJobExecutor.registJobHandler(name, new IJobHandler() {
            @Override
            public void execute() throws Exception {
                handler.execute();
            }
        });
    }

}
