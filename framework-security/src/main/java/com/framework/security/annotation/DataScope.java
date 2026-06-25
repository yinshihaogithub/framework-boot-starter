package com.framework.security.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解
 * 标注在 Mapper 方法上，配合 MyBatis 拦截器自动拼接数据权限 SQL
 *
 * 使用示例：
 * @DataScope(deptAlias = "d", userAlias = "u")
 * List<User> selectUsers(@Param("query") UserQuery query);
 *
 * 自动拼接：AND (d.id = #{deptId} OR u.create_by = #{userId})
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 部门表别名 */
    String deptAlias() default "";

    /** 用户表别名 */
    String userAlias() default "";

    /** 权限字段名（默认 dept_id） */
    String deptField() default "dept_id";

    /** 创建人字段名（默认 create_by） */
    String userField() default "create_by";

    /** 权限范围 */
    DataScopeType scopeType() default DataScopeType.DEFAULT;

    /**
     * 数据权限类型
     */
    enum DataScopeType {
        /** 全部数据 */
        ALL,
        /** 本部门 */
        DEPT,
        /** 本部门及子部门 */
        DEPT_AND_CHILD,
        /** 仅本人 */
        SELF,
        /** 自定义（从上下文获取） */
        DEFAULT
    }
}
