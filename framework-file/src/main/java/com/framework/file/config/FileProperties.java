package com.framework.file.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * File module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.file")
public class FileProperties implements InitializingBean {

    private boolean enabled = true;
    private String basePath = System.getProperty("java.io.tmpdir") + "/framework-files";
    private String publicUrlPrefix = "/files";
    private long maxSize = 100 * 1024 * 1024L;
    private List<String> allowedExtensions = new ArrayList<>();

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    public void validate() {
        if (basePath == null || basePath.isBlank()) {
            throw new IllegalArgumentException("framework.file.base-path must not be blank");
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("framework.file.max-size must be greater than 0");
        }
        if (allowedExtensions == null) {
            throw new IllegalArgumentException("framework.file.allowed-extensions must not be null");
        }
        for (String extension : allowedExtensions) {
            if (extension == null || extension.isBlank()) {
                throw new IllegalArgumentException("framework.file.allowed-extensions must not contain blank values");
            }
        }
    }
}
