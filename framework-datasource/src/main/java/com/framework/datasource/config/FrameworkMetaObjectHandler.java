package com.framework.datasource.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus audit field filler.
 */
public class FrameworkMetaObjectHandler implements MetaObjectHandler {

    private final DatasourceProperties properties;

    public FrameworkMetaObjectHandler(DatasourceProperties properties) {
        this.properties = properties;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        if (!properties.getAudit().isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        fillIfSetterExists(metaObject, properties.getAudit().getCreateTimeField(), now);
        fillIfSetterExists(metaObject, properties.getAudit().getUpdateTimeField(), now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (!properties.getAudit().isEnabled()) {
            return;
        }
        setIfSetterExists(metaObject, properties.getAudit().getUpdateTimeField(), LocalDateTime.now());
    }

    private void fillIfSetterExists(MetaObject metaObject, String fieldName, Object value) {
        if (metaObject.hasSetter(fieldName) && metaObject.getValue(fieldName) == null) {
            metaObject.setValue(fieldName, value);
        }
    }

    private void setIfSetterExists(MetaObject metaObject, String fieldName, Object value) {
        if (metaObject.hasSetter(fieldName)) {
            metaObject.setValue(fieldName, value);
        }
    }
}
