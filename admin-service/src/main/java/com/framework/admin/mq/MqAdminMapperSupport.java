package com.framework.admin.mq;

import com.framework.admin.support.AdminTextSupport;
import com.framework.mq.config.MqProperties;
import com.framework.mq.deadletter.MqAdminDTO;
import com.framework.mq.deadletter.MqFailedMessage;
import com.framework.mq.deadletter.MybatisMqFailedMessageRepository;
import com.framework.mq.mapper.MqFailedMessageMapper;

import java.util.List;
import java.util.Optional;

public final class MqAdminMapperSupport {

    private MqAdminMapperSupport() {
    }

    public static String tableName(MqProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("framework.mq.failed-message-table-name must match [A-Za-z0-9_]+");
        }
        return MybatisMqFailedMessageRepository.validateTableName(properties.getFailedMessageTableName());
    }

    public static MqAdminDTO.MqStats stats(MqFailedMessageMapper mapper, String tableName) {
        return new MqAdminDTO.MqStats()
                .setPendingCount(mapper.countByStatus(tableName, MqFailedMessage.STATUS_PENDING))
                .setRetryingCount(mapper.countByStatus(tableName, MqFailedMessage.STATUS_RETRYING))
                .setSuccessCount(mapper.countByStatus(tableName, MqFailedMessage.STATUS_SUCCESS)
                        + mapper.countByStatus(tableName, MqFailedMessage.STATUS_MANUAL))
                .setExhaustedCount(mapper.countByStatus(tableName, MqFailedMessage.STATUS_EXHAUSTED))
                .setTotalCount(mapper.countAll(tableName));
    }

    public static List<MqFailedMessage> list(MqFailedMessageMapper mapper,
                                             String tableName,
                                             String queueName,
                                             String status,
                                             String traceId,
                                             String businessKey,
                                             String messageType,
                                             int pageNum,
                                             int pageSize) {
        return mapper.list(tableName, text(queueName), text(status), like(traceId), like(businessKey),
                text(messageType), offset(pageNum, pageSize), pageSize);
    }

    public static long count(MqFailedMessageMapper mapper,
                             String tableName,
                             String queueName,
                             String status,
                             String traceId,
                             String businessKey,
                             String messageType) {
        return mapper.count(tableName, text(queueName), text(status), like(traceId), like(businessKey),
                text(messageType));
    }

    public static List<MqFailedMessage> listByTraceId(MqFailedMessageMapper mapper,
                                                      String tableName,
                                                      String traceId,
                                                      int pageNum,
                                                      int pageSize) {
        String safeTraceId = text(traceId);
        if (safeTraceId == null) {
            return List.of();
        }
        return mapper.list(tableName, null, null, safeTraceId, null, null, offset(pageNum, pageSize), pageSize);
    }

    public static long countByTraceId(MqFailedMessageMapper mapper,
                                      String tableName,
                                      String traceId,
                                      String status) {
        String safeTraceId = text(traceId);
        if (safeTraceId == null) {
            return 0L;
        }
        return mapper.count(tableName, null, text(status), safeTraceId, null, null);
    }

    public static Optional<MqFailedMessage> findById(MqFailedMessageMapper mapper, String tableName, Long id) {
        return Optional.ofNullable(mapper.findById(tableName, id));
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
