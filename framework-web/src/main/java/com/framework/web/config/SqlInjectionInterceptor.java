package com.framework.web.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Properties;
import java.util.regex.Pattern;

/**
 * SQL 注入防护拦截器
 * 拦截 MyBatis 查询，检查 SQL 中是否包含危险关键字
 *
 * 注意：MyBatis 参数化查询本身已防注入，此拦截器作为额外防护层
 * 主要拦截：注释符、UNION注入、堆叠注入、时间盲注等
 */
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SqlInjectionInterceptor implements Interceptor {

    /** 危险关键字正则（忽略大小写） */
    private static final Pattern DANGER_PATTERN = Pattern.compile(
            "(?i)(--|/\\*|\\*/|;\\s*\\w|\\bunion\\s+select\\b|\\bdrop\\s+table\\b|" +
            "\\bexec\\s*\\(|\\bexecute\\s*\\(|\\bxp_cmdshell\\b|\\bsleep\\s*\\(|\\bbenchmark\\s*\\(|" +
            "\\bload_file\\b|\\binto\\s+outfile\\b|\\binformation_schema\\b)"
    );

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql.getSql();

        if (containsSqlInjection(sql)) {
            log.error("[SQL注入拦截] 检测到危险SQL, msId={}, sql={}", ms.getId(), sql);
            throw new RuntimeException("检测到SQL注入风险，请求已被拦截");
        }

        return invocation.proceed();
    }

    /**
     * 检查 SQL 是否包含注入特征
     */
    private boolean containsSqlInjection(String sql) {
        if (sql == null || sql.isEmpty()) {
            return false;
        }
        return DANGER_PATTERN.matcher(sql).find();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
