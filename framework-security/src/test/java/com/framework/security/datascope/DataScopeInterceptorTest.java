package com.framework.security.datascope;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeInterceptorTest {

    @Test
    void appendsWhereWhenSqlHasNoWhereClause() {
        String sql = DataScopeInterceptor.appendScopeSql(
                "SELECT * FROM sys_user",
                "create_by = 7"
        );

        assertThat(sql).isEqualTo("SELECT * FROM sys_user WHERE (create_by = 7)");
    }

    @Test
    void appendsConditionBeforeOrderByWhenSqlAlreadyHasWhereClause() {
        String sql = DataScopeInterceptor.appendScopeSql(
                "SELECT * FROM sys_user WHERE deleted = 0 ORDER BY id DESC",
                "create_by = 7"
        );

        assertThat(sql).isEqualTo(
                "SELECT * FROM sys_user WHERE deleted = 0 AND (create_by = 7) ORDER BY id DESC"
        );
    }
}
