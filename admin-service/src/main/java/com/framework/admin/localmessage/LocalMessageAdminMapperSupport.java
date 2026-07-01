package com.framework.admin.localmessage;

import com.framework.admin.support.AdminTextSupport;
import com.framework.localmessage.config.LocalMessageProperties;
import com.framework.localmessage.mapper.LocalMessageMapper;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LocalMessageAdminMapperSupport {

    private LocalMessageAdminMapperSupport() {
    }

    public static String tableName(LocalMessageProperties properties) {
        if (properties == null || !AdminTextSupport.hasText(properties.getTableName())
                || !properties.getTableName().matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("framework.local-message.table-name must match [A-Za-z0-9_]+");
        }
        return properties.getTableName();
    }

    public static Map<String, Long> stats(LocalMessageMapper mapper, String tableName) {
        Map<String, Long> stats = zeroStats();
        for (LocalMessageStatus status : LocalMessageStatus.values()) {
            stats.put(status.name(), mapper.countByStatus(tableName, status));
        }
        stats.put("TOTAL", mapper.countAll(tableName));
        return stats;
    }

    public static Map<String, Long> zeroStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (LocalMessageStatus status : LocalMessageStatus.values()) {
            stats.put(status.name(), 0L);
        }
        stats.put("TOTAL", 0L);
        return stats;
    }

    public static List<LocalMessage> list(LocalMessageMapper mapper,
                                          String tableName,
                                          String topic,
                                          LocalMessageStatus status,
                                          String traceId,
                                          String businessKey,
                                          int pageNum,
                                          int pageSize) {
        return mapper.list(tableName, text(topic), status, like(traceId), like(businessKey),
                offset(pageNum, pageSize), pageSize);
    }

    public static long count(LocalMessageMapper mapper,
                             String tableName,
                             String topic,
                             LocalMessageStatus status,
                             String traceId,
                             String businessKey) {
        return mapper.count(tableName, text(topic), status, like(traceId), like(businessKey));
    }

    public static List<LocalMessage> listByTraceId(LocalMessageMapper mapper,
                                                   String tableName,
                                                   String traceId,
                                                   int pageNum,
                                                   int pageSize) {
        String safeTraceId = text(traceId);
        if (safeTraceId == null) {
            return List.of();
        }
        return mapper.list(tableName, null, null, safeTraceId, null, offset(pageNum, pageSize), pageSize);
    }

    public static long countByTraceId(LocalMessageMapper mapper,
                                      String tableName,
                                      String traceId,
                                      LocalMessageStatus status) {
        String safeTraceId = text(traceId);
        if (safeTraceId == null) {
            return 0L;
        }
        return mapper.count(tableName, null, status, safeTraceId, null);
    }

    public static Optional<LocalMessage> findById(LocalMessageMapper mapper, String tableName, Long id) {
        return Optional.ofNullable(mapper.findById(tableName, id));
    }

    public static boolean update(LocalMessageMapper mapper, String tableName, LocalMessage message) {
        touch(message);
        return mapper.update(tableName, message) > 0;
    }

    public static boolean delete(LocalMessageMapper mapper, String tableName, Long id) {
        return mapper.delete(tableName, id) > 0;
    }

    private static void touch(LocalMessage message) {
        LocalDateTime now = LocalDateTime.now();
        if (message.getCreateTime() == null) {
            message.setCreateTime(now);
        }
        message.setUpdateTime(now);
    }

    private static int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * pageSize;
    }

    private static String like(String value) {
        String text = text(value);
        return text == null ? null : "%" + text + "%";
    }

    private static String text(String value) {
        return AdminTextSupport.trimToNull(value);
    }
}
