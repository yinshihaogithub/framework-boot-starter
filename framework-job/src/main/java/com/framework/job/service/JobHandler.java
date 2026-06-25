package com.framework.job.service;

/**
 * Named job extension point.
 */
public interface JobHandler {

    String name();

    void execute() throws Exception;
}
