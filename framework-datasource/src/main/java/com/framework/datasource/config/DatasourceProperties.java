package com.framework.datasource.config;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datasource module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.datasource")
public class DatasourceProperties {

    private boolean enabled = true;
    private DbType dbType = DbType.MYSQL;
    private Long maxLimit = 1000L;
    private Audit audit = new Audit();

    @Data
    public static class Audit {
        private boolean enabled = true;
        private String createTimeField = "createTime";
        private String updateTimeField = "updateTime";
    }
}
