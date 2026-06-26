package com.framework.datasource.config;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

/**
 * Datasource module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.datasource")
public class DatasourceProperties implements InitializingBean {

    private static final Pattern JAVA_FIELD_NAME = Pattern.compile("[A-Za-z_$][A-Za-z\\d_$]*");

    private boolean enabled = true;
    private DbType dbType = DbType.MYSQL;
    private Long maxLimit = 1000L;
    private Audit audit = new Audit();

    @Override
    public void afterPropertiesSet() {
        if (dbType == null) {
            throw new IllegalArgumentException("framework.datasource.db-type must not be null");
        }
        if (dbType != DbType.MYSQL) {
            throw new IllegalArgumentException("framework.datasource.db-type must be MYSQL");
        }
        if (maxLimit == null || maxLimit <= 0) {
            throw new IllegalArgumentException("framework.datasource.max-limit must be greater than 0");
        }
        if (audit == null) {
            throw new IllegalArgumentException("framework.datasource.audit must not be null");
        }
        if (audit.isEnabled()) {
            audit.setCreateTimeField(normalizeAuditField("create-time-field", audit.getCreateTimeField()));
            audit.setUpdateTimeField(normalizeAuditField("update-time-field", audit.getUpdateTimeField()));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeAuditField(String propertyName, String value) {
        String property = "framework.datasource.audit." + propertyName;
        if (!hasText(value)) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        String trimmed = value.trim();
        if (!JAVA_FIELD_NAME.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(property + " must be a valid Java field name");
        }
        return trimmed;
    }

    @Data
    public static class Audit {
        private boolean enabled = true;
        private String createTimeField = "createTime";
        private String updateTimeField = "updateTime";
    }
}
