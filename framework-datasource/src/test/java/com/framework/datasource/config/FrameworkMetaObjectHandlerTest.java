package com.framework.datasource.config;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkMetaObjectHandlerTest {

    @Test
    void insertFillSetsCreateAndUpdateTimeWhenAuditIsEnabled() {
        AuditEntity entity = new AuditEntity();
        FrameworkMetaObjectHandler handler = new FrameworkMetaObjectHandler(new DatasourceProperties());

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void updateFillRefreshesExistingUpdateTime() {
        AuditEntity entity = new AuditEntity();
        entity.setUpdateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        FrameworkMetaObjectHandler handler = new FrameworkMetaObjectHandler(new DatasourceProperties());

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdateTime()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void auditCanBeDisabled() {
        DatasourceProperties properties = new DatasourceProperties();
        properties.getAudit().setEnabled(false);
        AuditEntity entity = new AuditEntity();
        FrameworkMetaObjectHandler handler = new FrameworkMetaObjectHandler(properties);

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateTime()).isNull();
        assertThat(entity.getUpdateTime()).isNull();
    }

    @Test
    void auditFieldNamesAreTrimmedBeforeFilling() {
        DatasourceProperties properties = new DatasourceProperties();
        properties.getAudit().setCreateTimeField(" createTime ");
        properties.getAudit().setUpdateTimeField(" updateTime ");
        properties.afterPropertiesSet();
        AuditEntity entity = new AuditEntity();
        FrameworkMetaObjectHandler handler = new FrameworkMetaObjectHandler(properties);

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(properties.getAudit().getCreateTimeField()).isEqualTo("createTime");
        assertThat(properties.getAudit().getUpdateTimeField()).isEqualTo("updateTime");
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    public static class AuditEntity {
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }
    }
}
