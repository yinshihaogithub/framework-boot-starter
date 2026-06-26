package com.framework.admin.log;

import com.framework.admin.system.AdminSystemModels.LoginLog;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.core.result.PageResult;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LogAdminService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ObjectProvider<OperationLogMapper> mapperProvider;
    private final AdminSystemRepository systemRepository;

    public LogAdminService(ObjectProvider<OperationLogMapper> mapperProvider,
                           AdminSystemRepository systemRepository) {
        this.mapperProvider = mapperProvider;
        this.systemRepository = systemRepository;
    }

    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        OperationLogMapper mapper = mapperProvider.getIfAvailable();
        stats.put("total", count(mapper, null, null, null, null, null));
        stats.put("operation", count(mapper, null, "OPERATION", null, null, null));
        stats.put("api", count(mapper, null, "API", null, null, null));
        stats.put("exception", count(mapper, null, "EXCEPTION", null, false, null));
        return stats;
    }

    public PageResult<OperationLogEntity> list(String module, String logType, Long operatorId,
                                               Boolean success, String traceId, int pageNum, int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        OperationLogMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return PageResult.empty(safePageNum, safePageSize);
        }
        int offset = (safePageNum - 1) * safePageSize;
        List<OperationLogEntity> records = mapper.selectList(
                module, logType, operatorId, success, traceId, offset, safePageSize);
        long total = mapper.count(module, logType, operatorId, success, traceId);
        return PageResult.of(records, total, safePageNum, safePageSize);
    }

    public PageResult<OperationLogEntity> trace(String traceId, int pageNum, int pageSize) {
        return list(null, null, null, null, traceId, pageNum, pageSize);
    }

    public PageResult<LoginLog> loginLogs(String username, Boolean success, int pageNum, int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<LoginLog> records = systemRepository.listLoginLogs(username, success, safePageNum, safePageSize);
        long total = systemRepository.countLoginLogs(username, success);
        return PageResult.of(records, total, safePageNum, safePageSize);
    }

    private long count(OperationLogMapper mapper, String module, String logType,
                       Long operatorId, Boolean success, String traceId) {
        if (mapper == null) {
            return 0;
        }
        try {
            return mapper.count(module, logType, operatorId, success, traceId);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int safePageNum(int pageNum) {
        return pageNum > 0 ? pageNum : DEFAULT_PAGE_NUM;
    }

    private int safePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
