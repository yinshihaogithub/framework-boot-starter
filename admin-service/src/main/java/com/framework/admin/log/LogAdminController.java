package com.framework.admin.log;

import com.framework.admin.system.AdminSystemModels.LoginLog;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志审计后台接口。
 */
@RestController
@RequestMapping("/admin/logs")
@Tag(name = "日志审计", description = "操作日志、API日志和 trace 查询")
public class LogAdminController {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ObjectProvider<OperationLogMapper> mapperProvider;
    private final AdminSystemRepository systemRepository;

    public LogAdminController(ObjectProvider<OperationLogMapper> mapperProvider,
                              AdminSystemRepository systemRepository) {
        this.mapperProvider = mapperProvider;
        this.systemRepository = systemRepository;
    }

    @Operation(summary = "日志统计")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        OperationLogMapper mapper = mapperProvider.getIfAvailable();
        stats.put("total", count(mapper, null, null, null, null, null));
        stats.put("operation", count(mapper, null, "OPERATION", null, null, null));
        stats.put("api", count(mapper, null, "API", null, null, null));
        stats.put("exception", count(mapper, null, "EXCEPTION", null, false, null));
        return Result.success(stats);
    }

    @Operation(summary = "日志列表")
    @GetMapping
    public Result<PageResult<OperationLogEntity>> list(@RequestParam(required = false) String module,
                                                       @RequestParam(required = false) String logType,
                                                       @RequestParam(required = false) Long operatorId,
                                                       @RequestParam(required = false) Boolean success,
                                                       @RequestParam(required = false) String traceId,
                                                       @RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        OperationLogMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return Result.success(PageResult.empty(safePageNum, safePageSize));
        }
        int offset = (safePageNum - 1) * safePageSize;
        List<OperationLogEntity> records = mapper.selectList(
                module, logType, operatorId, success, traceId, offset, safePageSize);
        long total = mapper.count(module, logType, operatorId, success, traceId);
        return Result.success(PageResult.of(records, total, safePageNum, safePageSize));
    }

    @Operation(summary = "按 traceId 查询链路日志")
    @GetMapping("/traces/{traceId}")
    public Result<PageResult<OperationLogEntity>> trace(@PathVariable String traceId,
                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "50") int pageSize) {
        return list(null, null, null, null, traceId, pageNum, pageSize);
    }

    @Operation(summary = "登录日志列表")
    @GetMapping("/login")
    public Result<PageResult<LoginLog>> loginLogs(@RequestParam(required = false) String username,
                                                  @RequestParam(required = false) Boolean success,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<LoginLog> records = systemRepository.listLoginLogs(username, success, safePageNum, safePageSize);
        long total = systemRepository.countLoginLogs(username, success);
        return Result.success(PageResult.of(records, total, safePageNum, safePageSize));
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
