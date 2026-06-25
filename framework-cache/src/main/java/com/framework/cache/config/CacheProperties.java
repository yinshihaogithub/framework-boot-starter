package com.framework.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cache module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.cache")
public class CacheProperties {

    private boolean enabled = true;
    private boolean multiLevel = true;
    private Local local = new Local();
    private Remote remote = new Remote();

    @Data
    public static class Local {
        private long maxSize = 10000;
        private long expireAfterWrite = 300;
    }

    @Data
    public static class Remote {
        private long defaultTtl = 3600;
    }
}
