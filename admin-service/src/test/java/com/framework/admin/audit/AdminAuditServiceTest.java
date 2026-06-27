package com.framework.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.trace.TraceContext;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AdminAuditServiceTest {

    private final RecordingOperationLogMapper mapper = new RecordingOperationLogMapper();
    private final AdminAuditService service = new AdminAuditService(mapper, new ObjectMapper());

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        TraceContext.clear();
    }

    @Test
    void successWritesOperationLogThroughMapper() {
        UserContextHolder.set(new LoginUser().setUserId(7L).setUsername("alice"));
        TraceContext.putTraceId("trace-admin");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/system/users");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");

        service.success(request, "系统管理", "创建用户", "INSERT", Map.of("username", "bob"));

        assertThat(mapper.inserted).isNotNull();
        assertThat(mapper.inserted.getLogType()).isEqualTo("OPERATION");
        assertThat(mapper.inserted.getModule()).isEqualTo("系统管理");
        assertThat(mapper.inserted.getAction()).isEqualTo("创建用户");
        assertThat(mapper.inserted.getOperationType()).isEqualTo("INSERT");
        assertThat(mapper.inserted.getUri()).isEqualTo("/admin/system/users");
        assertThat(mapper.inserted.getHttpMethod()).isEqualTo("POST");
        assertThat(mapper.inserted.getMethod()).isEqualTo("admin-service");
        assertThat(mapper.inserted.getParams()).contains("\"username\":\"bob\"");
        assertThat(mapper.inserted.getResult()).isEqualTo("{\"message\":\"success\"}");
        assertThat(mapper.inserted.getSuccess()).isTrue();
        assertThat(mapper.inserted.getElapsedMs()).isZero();
        assertThat(mapper.inserted.getOperatorId()).isEqualTo(7L);
        assertThat(mapper.inserted.getOperatorName()).isEqualTo("alice");
        assertThat(mapper.inserted.getClientIp()).isEqualTo("10.0.0.1");
        assertThat(mapper.inserted.getTraceId()).isEqualTo("trace-admin");
        assertThat(mapper.inserted.getCreateTime()).isNotNull();
    }

    @Test
    void failureWritesErrorMessage() {
        service.failure(null, "消息中心", "人工补偿", "UPDATE", null,
                new IllegalStateException("send failed"));

        assertThat(mapper.inserted.getSuccess()).isFalse();
        assertThat(mapper.inserted.getResult()).isNull();
        assertThat(mapper.inserted.getErrorMessage()).isEqualTo("send failed");
        assertThat(mapper.inserted.getTraceId()).isNotBlank();
    }

    @Test
    void mapperFailureDoesNotBlockAdminFlow() {
        mapper.throwOnInsert = true;

        assertThatCode(() -> service.success(null, "系统管理", "删除角色", "DELETE", null))
                .doesNotThrowAnyException();
    }

    private static class RecordingOperationLogMapper implements OperationLogMapper {
        private OperationLogEntity inserted;
        private boolean throwOnInsert;

        @Override
        public void createTableIfNotExists() {
        }

        @Override
        public void insert(OperationLogEntity entity) {
            if (throwOnInsert) {
                throw new IllegalStateException("db unavailable");
            }
            this.inserted = entity;
        }

        @Override
        public List<OperationLogEntity> selectList(String module, String logType, Long operatorId, Boolean success,
                                                   String traceId, int offset, int pageSize) {
            return List.of();
        }

        @Override
        public long count(String module, String logType, Long operatorId, Boolean success, String traceId) {
            return 0;
        }

        @Override
        public int deleteBefore(Date beforeDate) {
            return 0;
        }
    }
}
