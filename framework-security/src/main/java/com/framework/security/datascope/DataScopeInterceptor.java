package com.framework.security.datascope;

import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.security.annotation.DataScope;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * 数据权限 MyBatis 拦截器
 * 拦截标注了 @DataScope 的查询方法，自动拼接数据权限 SQL
 *
 * 拼接规则：
 * - ALL：无限制
 * - DEPT：AND {deptAlias}.{deptField} = #{deptId}
 * - DEPT_AND_CHILD：AND {deptAlias}.{deptField} IN (...)
 * - SELF：AND {userAlias}.{userField} = #{userId}
 */
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class DataScopeInterceptor implements Interceptor {

    private static final Pattern WHERE_PATTERN = Pattern.compile("(?i)\\bwhere\\b");
    private static final Pattern TAIL_CLAUSE_PATTERN = Pattern.compile(
            "(?i)\\s+(group\\s+by|order\\s+by|having|limit|offset)\\b");

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        // 获取当前方法上的 @DataScope 注解
        DataScope dataScope = getDataScopeAnnotation(ms);
        if (dataScope == null) {
            return invocation.proceed();
        }

        // 获取当前登录用户
        LoginUser user = UserContextHolder.get();
        if (user == null) {
            return invocation.proceed();
        }

        // 构建数据权限 SQL
        String scopeSql = buildScopeSql(dataScope, user, parameter);
        if (scopeSql == null || scopeSql.isEmpty()) {
            return invocation.proceed();
        }

        // 拼接 SQL
        BoundSql boundSql = ms.getBoundSql(parameter);
        String originalSql = boundSql.getSql();
        String newSql = appendScopeSql(originalSql, scopeSql);

        // 重建 MappedStatement
        BoundSql newBoundSql = new BoundSql(ms.getConfiguration(), newSql,
                boundSql.getParameterMappings(), parameter);
        // 复制 additionalParameters
        for (Field field : boundSql.getClass().getDeclaredFields()) {
            if (field.getName().equals("additionalParameters")) {
                field.setAccessible(true);
                Map<String, Object> additionalParams = (Map<String, Object>) field.get(boundSql);
                if (additionalParams != null) {
                    for (var entry : additionalParams.entrySet()) {
                        newBoundSql.setAdditionalParameter(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        MappedStatement newMs = copyMappedStatement(ms, new BoundSqlSource(newBoundSql));
        invocation.getArgs()[0] = newMs;

        return invocation.proceed();
    }

    static String appendScopeSql(String originalSql, String scopeSql) {
        if (originalSql == null || originalSql.isBlank() || scopeSql == null || scopeSql.isBlank()) {
            return originalSql;
        }
        String sql = originalSql.trim();
        var matcher = TAIL_CLAUSE_PATTERN.matcher(sql);
        int insertIndex = matcher.find() ? matcher.start() : sql.length();
        String head = sql.substring(0, insertIndex);
        String tail = sql.substring(insertIndex);
        String connector = WHERE_PATTERN.matcher(head).find() ? " AND " : " WHERE ";
        return head + connector + "(" + scopeSql.trim() + ")" + tail;
    }

    /**
     * 构建 数据权限 SQL
     */
    private String buildScopeSql(DataScope dataScope, LoginUser user, Object parameter) {
        DataScope.DataScopeType scopeType = dataScope.scopeType();

        // 如果是 DEFAULT，从参数对象中读取 scopeType
        if (scopeType == DataScope.DataScopeType.DEFAULT) {
            scopeType = resolveScopeType(parameter);
        }

        String deptAlias = dataScope.deptAlias();
        String userAlias = dataScope.userAlias();
        String deptField = dataScope.deptField();
        String userField = dataScope.userField();

        // 获取用户信息
        Long userId = user.getUserId();
        Long deptId = getDeptId(user);
        String roleKey = getRoleKey(user);

        // 超级管理员不限数据
        if ("admin".equalsIgnoreCase(roleKey) || "super_admin".equalsIgnoreCase(roleKey)) {
            return null;
        }

        return switch (scopeType) {
            case ALL -> null; // 无限制
            case DEPT -> {
                String prefix = deptAlias.isEmpty() ? "" : deptAlias + ".";
                yield deptId != null ? prefix + deptField + " = " + deptId : null;
            }
            case DEPT_AND_CHILD -> {
                String prefix = deptAlias.isEmpty() ? "" : deptAlias + ".";
                // 简化：实际应查子部门列表
                yield deptId != null ? prefix + deptField + " = " + deptId : null;
            }
            case SELF -> {
                String prefix = userAlias.isEmpty() ? "" : userAlias + ".";
                yield userId != null ? prefix + userField + " = " + userId : null;
            }
            default -> null;
        };
    }

    /**
     * 从参数对象中读取 scopeType
     */
    private DataScope.DataScopeType resolveScopeType(Object parameter) {
        if (parameter == null) {
            return DataScope.DataScopeType.ALL;
        }
        try {
            // 参数可能是 Map 或实体
            Object target = parameter;
            if (parameter instanceof Map map) {
                // 取第一个非 param 参数
                for (var value : map.values()) {
                    if (value != null && !value.getClass().getName().startsWith("java.")) {
                        target = value;
                        break;
                    }
                }
            }
            Field scopeField = findField(target.getClass(), "dataScope");
            if (scopeField != null) {
                scopeField.setAccessible(true);
                Object value = scopeField.get(target);
                if (value instanceof String strValue) {
                    return DataScope.DataScopeType.valueOf(strValue.toUpperCase());
                }
                if (value instanceof DataScope.DataScopeType typeValue) {
                    return typeValue;
                }
            }
        } catch (Exception e) {
            log.debug("[数据权限] 解析 scopeType 失败: {}", e.getMessage());
        }
        return DataScope.DataScopeType.ALL;
    }

    private Long getDeptId(LoginUser user) {
        try {
            Field deptField = findField(user.getClass(), "deptId");
            if (deptField != null) {
                deptField.setAccessible(true);
                Object deptId = deptField.get(user);
                if (deptId instanceof Long l) {
                    return l;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getRoleKey(LoginUser user) {
        if (user.getRoles() == null || user.getRoles().length == 0) {
            return null;
        }
        return user.getRoles()[0];
    }

    private Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取 Mapper 方法上的 @DataScope 注解
     */
    private DataScope getDataScopeAnnotation(MappedStatement ms) {
        String id = ms.getId();
        try {
            String className = id.substring(0, id.lastIndexOf("."));
            String methodName = id.substring(id.lastIndexOf(".") + 1);
            Class<?> clazz = Class.forName(className);
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName)) {
                    DataScope annotation = method.getAnnotation(DataScope.class);
                    if (annotation != null) {
                        return annotation;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[数据权限] 获取注解失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 复制 MappedStatement
     */
    private MappedStatement copyMappedStatement(MappedStatement ms, SqlSource newSqlSource) {
        MappedStatement.Builder builder = new MappedStatement.Builder(
                ms.getConfiguration(), ms.getId(), newSqlSource, ms.getSqlCommandType());
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length > 0) {
            builder.keyProperty(String.join(",", ms.getKeyProperties()));
        }
        builder.timeout(ms.getTimeout());
        builder.parameterMap(ms.getParameterMap());
        builder.resultMaps(ms.getResultMaps());
        builder.cache(ms.getCache());
        builder.useCache(ms.isUseCache());
        return builder.build();
    }

    /**
     * 包装 BoundSql 为 SqlSource
     */
    record BoundSqlSource(BoundSql boundSql) implements SqlSource {
        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
