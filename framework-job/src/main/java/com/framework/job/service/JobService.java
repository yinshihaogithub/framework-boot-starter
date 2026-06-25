package com.framework.job.service;

import java.util.Set;

/**
 * Job facade.
 */
public interface JobService {

    Set<String> names();

    boolean run(String name);
}
