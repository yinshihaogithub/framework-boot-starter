package com.framework.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Job module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.job")
public class JobProperties {

    private boolean enabled = true;
    private int poolSize = 4;
    private String threadNamePrefix = "framework-job-";
}
