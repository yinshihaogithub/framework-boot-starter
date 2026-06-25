package com.framework.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * File module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.file")
public class FileProperties {

    private boolean enabled = true;
    private String basePath = System.getProperty("java.io.tmpdir") + "/framework-files";
    private String publicUrlPrefix = "/files";
    private long maxSize = 100 * 1024 * 1024L;
    private List<String> allowedExtensions = new ArrayList<>();
}
