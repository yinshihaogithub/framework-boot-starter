package com.framework.log.service;

import com.framework.log.config.LogProperties;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 操作日志存储服务
 * - 异步写入 DB
 * - API 日志采样
 * - 定时清理过期日志
 */
@Slf4j
@Service
public class OperationLogStorageService {

    @Autowired(required = false)
    private OperationLogMapper operationLogMapper;

    private final LogProperties properties;

    public OperationLogStorageService(LogProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * 启动时创建表
     */
    @PostConstruct
    public void init() {
        if (properties.getDbStorage().isEnabled() && operationLogMapper != null) {
            try {
                operationLogMapper.createTableIfNotExists();
                log.info("[操作日志] DB表已初始化");
            } catch (Exception e) {
                log.warn("[操作日志] DB表初始化失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 异步保存操作日志
     */
    @Async("logAsyncExecutor")
    public void saveAsync(OperationLogEntity entity) {
        if (!properties.getDbStorage().isEnabled() || operationLogMapper == null) {
            return;
        }
        try {
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("[操作日志] DB存储失败", e);
        }
    }

    /**
     * API 日志采样判断
     *
     * @return true 表示当前请求应该记录日志
     */
    public boolean shouldLogApi() {
        int apiSampleRate = properties.getApiSampleRate();
        if (apiSampleRate <= 0) {
            return false;
        }
        if (apiSampleRate >= 100) {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(100) < apiSampleRate;
    }

    /**
     * 定时清理过期日志（每天凌晨 2 点执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredLogs() {
        if (!properties.getDbStorage().isEnabled() || operationLogMapper == null) {
            return;
        }
        try {
            long beforeMillis = System.currentTimeMillis() - (long) properties.getRetentionDays() * 24 * 60 * 60 * 1000;
            Date beforeDate = new Date(beforeMillis);
            int deleted = operationLogMapper.deleteBefore(beforeDate);
            log.info("[操作日志] 清理过期日志完成，删除 {} 条，保留 {} 天", deleted, properties.getRetentionDays());
        } catch (Exception e) {
            log.error("[操作日志] 清理过期日志失败", e);
        }
    }
}
