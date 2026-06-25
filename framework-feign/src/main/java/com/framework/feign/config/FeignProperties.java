package com.framework.feign.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Feign module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.feign")
public class FeignProperties {

    private boolean enabled = true;
    private List<String> relayHeaders = new ArrayList<>(List.of(
            "Authorization",
            "X-Trace-Id",
            "X-Tenant-Id"
    ));
}
