package com.framework.web.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlInjectionInterceptorTest {

    private final SqlInjectionInterceptor interceptor = new SqlInjectionInterceptor();

    @Test
    void allowsNormalParameterizedWriteStatements() throws Throwable {
        assertThat(interceptUpdate("INSERT INTO sys_user (name, age) VALUES (?, ?)")).isEqualTo(1);
        assertThat(interceptUpdate("UPDATE sys_user SET name = ? WHERE id = ?")).isEqualTo(1);
        assertThat(interceptUpdate("DELETE FROM sys_user WHERE id = ?")).isEqualTo(1);
    }

    @Test
    void blocksSqlInjectionSignatures() {
        assertThatThrownBy(() -> interceptQuery("SELECT * FROM sys_user WHERE name = 'a' UNION SELECT password FROM sys_user"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SQL注入风险");

        assertThatThrownBy(() -> interceptQuery("SELECT * FROM sys_user WHERE id = 1; DROP TABLE sys_user"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SQL注入风险");

        assertThatCode(() -> interceptQuery("SELECT * FROM sys_user WHERE id = ?"))
                .doesNotThrowAnyException();
    }

    private Object interceptUpdate(String sql) throws Throwable {
        Method update = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        return interceptor.intercept(new Invocation(executor(), update, new Object[]{
                mappedStatement("test.update", sql, SqlCommandType.UPDATE),
                null
        }));
    }

    private Object interceptQuery(String sql) throws Throwable {
        Method query = Executor.class.getMethod("query", MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        return interceptor.intercept(new Invocation(executor(), query, new Object[]{
                mappedStatement("test.query", sql, SqlCommandType.SELECT),
                null,
                RowBounds.DEFAULT,
                null
        }));
    }

    private static MappedStatement mappedStatement(String id, String sql, SqlCommandType commandType) {
        Configuration configuration = new Configuration();
        RawSqlSource sqlSource = new RawSqlSource(configuration, sql, Object.class);
        return new MappedStatement.Builder(configuration, id, sqlSource, commandType).build();
    }

    private static Executor executor() {
        return (Executor) Proxy.newProxyInstance(
                Executor.class.getClassLoader(),
                new Class<?>[]{Executor.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> returnType) throws SQLException {
        if (returnType == int.class) {
            return 1;
        }
        if (returnType == List.class) {
            return List.of();
        }
        if (returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        return null;
    }
}
