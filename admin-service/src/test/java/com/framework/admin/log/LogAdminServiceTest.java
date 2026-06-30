package com.framework.admin.log;

import com.framework.admin.system.AdminSystemModels.LoginLog;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.admin.support.AdminPageSupport;
import com.framework.core.result.PageResult;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LogAdminServiceTest {

    @Test
    void returnsZeroStatsWhenMapperIsMissing() {
        LogAdminService service = new LogAdminService(provider(null), systemRepository(List.of()));

        Map<String, Long> stats = service.stats();

        assertThat(stats)
                .containsEntry("total", 0L)
                .containsEntry("operation", 0L)
                .containsEntry("api", 0L)
                .containsEntry("exception", 0L);
    }

    @Test
    void returnsZeroStatsAndEmptyPageWhenMapperProviderFails() {
        LogAdminService service = new LogAdminService(failingProvider(), systemRepository(List.of()));

        Map<String, Long> stats = service.stats();
        PageResult<OperationLogEntity> page = service.list(null, null, null, null, null, 0, 0);

        assertThat(stats)
                .containsEntry("total", 0L)
                .containsEntry("operation", 0L)
                .containsEntry("api", 0L)
                .containsEntry("exception", 0L);
        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void listsOperationLogsWithSafePaging() {
        LogAdminService service = new LogAdminService(provider(mapper(List.of(
                operationLog(1L, "system", "OPERATION", true, "trace-a"),
                operationLog(2L, "system", "API", false, "trace-b")))), systemRepository(List.of()));

        PageResult<OperationLogEntity> page = service.list("system", null, null, null, null, -1, 500);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(OperationLogEntity::getId).containsExactly(1L, 2L);
    }

    @Test
    void normalizesOperationLogFiltersBeforeQuerying() {
        LogAdminService service = new LogAdminService(provider(mapper(List.of(
                operationLog(1L, "system", "OPERATION", true, "trace-a"),
                operationLog(2L, "mq", "API", false, "trace-b")))), systemRepository(List.of()));

        PageResult<OperationLogEntity> page = service.list(
                "\u00A0system\u3000", "\u3000operation\u00A0", null, true, "\u00A0trace-a\u3000", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(OperationLogEntity::getId).containsExactly(1L);
    }

    @Test
    void returnsEmptyPageForUnsupportedLogTypeFilter() {
        LogAdminService service = new LogAdminService(provider(failingMapper()), systemRepository(List.of()));

        PageResult<OperationLogEntity> page = service.list(null, "AUDIT", null, null, null, 1, 20);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void returnsEmptyPageForUnsafeTraceIdFilter() {
        LogAdminService service = new LogAdminService(provider(failingMapper()), systemRepository(List.of()));

        PageResult<OperationLogEntity> page = service.list(null, null, null, null, "bad\ntrace", 1, 20);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void clampsHugePageNumberBeforeQueryingOperationLogs() {
        LogAdminService service = new LogAdminService(provider(mapper(List.of(
                operationLog(1L, "system", "OPERATION", true, "trace-a")))), systemRepository(List.of()));

        PageResult<OperationLogEntity> page = service.list("system", null, null, null, null,
                Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertThat(page.getPageNum()).isEqualTo(AdminPageSupport.MAX_PAGE_NUM);
        assertThat(page.getPageSize()).isEqualTo(AdminPageSupport.MAX_PAGE_SIZE);
    }

    @Test
    void returnsEmptyPageWhenOperationLogQueryFails() {
        LogAdminService service = new LogAdminService(provider(failingMapper()), systemRepository(List.of()));

        PageResult<OperationLogEntity> page = service.list(null, null, null, null, null, -1, 500);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(200);
        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void traceFiltersOperationLogsByTraceId() {
        LogAdminService service = new LogAdminService(provider(mapper(List.of(
                operationLog(1L, "system", "OPERATION", true, "trace-a"),
                operationLog(2L, "mq", "API", false, "trace-b")))), systemRepository(List.of()));

        PageResult<OperationLogEntity> page = service.trace("trace-b", 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(OperationLogEntity::getTraceId).containsExactly("trace-b");
    }

    @Test
    void loginLogsUseSystemRepositoryWithSafePaging() {
        LogAdminService service = new LogAdminService(provider(null), systemRepository(List.of(
                new LoginLog().setId(1L).setUsername("admin").setSuccess(true))));

        PageResult<LoginLog> page = service.loginLogs("admin", true, 0, 0);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(LoginLog::getUsername).containsExactly("admin");
    }

    @Test
    void loginLogsTrimUsernameBeforeQuerying() {
        LogAdminService service = new LogAdminService(provider(null), systemRepository(List.of(
                new LoginLog().setId(1L).setUsername("admin").setSuccess(true),
                new LoginLog().setId(2L).setUsername("ops").setSuccess(true))));

        PageResult<LoginLog> page = service.loginLogs("\u00A0admin\u3000", true, 1, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(LoginLog::getUsername).containsExactly("admin");
    }

    @Test
    void loginLogsReturnEmptyPageWhenSystemRepositoryFails() {
        LogAdminService service = new LogAdminService(provider(null), failingSystemRepository());

        PageResult<LoginLog> page = service.loginLogs("admin", true, 0, 0);

        assertThat(page.getPageNum()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
    }

    private static OperationLogEntity operationLog(Long id, String module, String logType, boolean success, String traceId) {
        OperationLogEntity log = new OperationLogEntity();
        log.setId(id);
        log.setModule(module);
        log.setLogType(logType);
        log.setSuccess(success);
        log.setTraceId(traceId);
        log.setCreateTime(new Date());
        return log;
    }

    private static OperationLogMapper mapper(List<OperationLogEntity> logs) {
        return new OperationLogMapper() {
            @Override
            public void createTableIfNotExists() {
            }

            @Override
            public void insert(OperationLogEntity entity) {
            }

            @Override
            public List<OperationLogEntity> selectList(String module, String logType, Long operatorId,
                                                       Boolean success, String traceId, int offset, int pageSize) {
                return logs.stream()
                        .filter(log -> module == null || module.equals(log.getModule()))
                        .filter(log -> logType == null || logType.equals(log.getLogType()))
                        .filter(log -> success == null || success.equals(log.getSuccess()))
                        .filter(log -> traceId == null || traceId.equals(log.getTraceId()))
                        .toList();
            }

            @Override
            public long count(String module, String logType, Long operatorId, Boolean success, String traceId) {
                return selectList(module, logType, operatorId, success, traceId, 0, Integer.MAX_VALUE).size();
            }

            @Override
            public int deleteBefore(Date beforeDate) {
                return 0;
            }
        };
    }

    private static OperationLogMapper failingMapper() {
        return new OperationLogMapper() {
            @Override
            public void createTableIfNotExists() {
            }

            @Override
            public void insert(OperationLogEntity entity) {
            }

            @Override
            public List<OperationLogEntity> selectList(String module, String logType, Long operatorId,
                                                       Boolean success, String traceId, int offset, int pageSize) {
                throw new IllegalStateException("operation log table unavailable");
            }

            @Override
            public long count(String module, String logType, Long operatorId, Boolean success, String traceId) {
                throw new IllegalStateException("operation log table unavailable");
            }

            @Override
            public int deleteBefore(Date beforeDate) {
                return 0;
            }
        };
    }

    private static AdminSystemRepository systemRepository(List<LoginLog> loginLogs) {
        return new AdminSystemRepository(null) {
            @Override
            public List<LoginLog> listLoginLogs(String username, Boolean success, int pageNum, int pageSize) {
                return loginLogs.stream()
                        .filter(log -> username == null || username.equals(log.getUsername()))
                        .filter(log -> success == null || success.equals(log.getSuccess()))
                        .toList();
            }

            @Override
            public long countLoginLogs(String username, Boolean success) {
                return listLoginLogs(username, success, 1, Integer.MAX_VALUE).size();
            }
        };
    }

    private static AdminSystemRepository failingSystemRepository() {
        return new AdminSystemRepository(null) {
            @Override
            public List<LoginLog> listLoginLogs(String username, Boolean success, int pageNum, int pageSize) {
                throw new IllegalStateException("login log table unavailable");
            }
        };
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }
        };
    }

    private static <T> ObjectProvider<T> failingProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public T getIfAvailable() {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public T getIfUnique() {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public T getObject() {
                throw new IllegalStateException("provider failed");
            }

            @Override
            public Stream<T> stream() {
                throw new IllegalStateException("provider failed");
            }
        };
    }
}
