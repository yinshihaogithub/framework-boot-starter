package com.framework.cache.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cache module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.cache")
public class CacheProperties implements InitializingBean {

    private boolean enabled = true;
    private boolean multiLevel = true;
    private Local local = new Local();
    private Remote remote = new Remote();

    @Override
    public void afterPropertiesSet() {
        if (local == null) {
            throw new IllegalArgumentException("framework.cache.local must not be null");
        }
        if (local.getMaxSize() <= 0) {
            throw new IllegalArgumentException("framework.cache.local.max-size must be greater than 0");
        }
        if (local.getExpireAfterWrite() <= 0) {
            throw new IllegalArgumentException("framework.cache.local.expire-after-write must be greater than 0");
        }
        if (remote == null) {
            throw new IllegalArgumentException("framework.cache.remote must not be null");
        }
        if (remote.getDefaultTtl() <= 0) {
            throw new IllegalArgumentException("framework.cache.remote.default-ttl must be greater than 0");
        }
    }

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
