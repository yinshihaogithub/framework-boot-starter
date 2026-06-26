package com.framework.admin.trace;

import com.framework.admin.localmessage.LocalMessageVO;
import com.framework.admin.trace.TraceAdminModels.TraceDetail;
import com.framework.admin.trace.TraceAdminModels.TraceEvent;
import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.service.LocalMessageService;
import com.framework.log.entity.OperationLogEntity;
import com.framework.log.mapper.OperationLogMapper;
import com.framework.mq.deadletter.DeadLetterHandler;
import com.framework.mq.deadletter.MqFailedMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TraceAdminService {

    private static final int TRACE_LIMIT = 200;

    private final ObjectProvider<OperationLogMapper> operationLogMapperProvider;
    private final ObjectProvider<DeadLetterHandler> deadLetterHandlerProvider;
    private final ObjectProvider<LocalMessageService> localMessageServiceProvider;

    public TraceAdminService(ObjectProvider<OperationLogMapper> operationLogMapperProvider,
                             ObjectProvider<DeadLetterHandler> deadLetterHandlerProvider,
                             ObjectProvider<LocalMessageService> localMessageServiceProvider) {
        this.operationLogMapperProvider = operationLogMapperProvider;
        this.deadLetterHandlerProvider = deadLetterHandlerProvider;
        this.localMessageServiceProvider = localMessageServiceProvider;
    }

    public TraceDetail detail(String traceId) {
        List<OperationLogEntity> logs = findLogs(traceId);
        List<MqFailedMessage> mqMessages = findMqMessages(traceId);
        List<LocalMessage> localMessages = findLocalMessages(traceId);
        return new TraceDetail()
                .setTraceId(traceId)
                .setSummary(summary(logs, mqMessages, localMessages))
                .setTimeline(buildTimeline(logs, mqMessages, localMessages))
                .setLogs(logs)
                .setMqMessages(mqMessages)
                .setLocalMessages(localMessages.stream().map(LocalMessageVO::from).toList());
    }

    private List<OperationLogEntity> findLogs(String traceId) {
        OperationLogMapper mapper = operationLogMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        try {
            return mapper.selectList(null, null, null, null, traceId, 0, TRACE_LIMIT);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<MqFailedMessage> findMqMessages(String traceId) {
        DeadLetterHandler handler = deadLetterHandlerProvider.getIfAvailable();
        if (handler == null) {
            return List.of();
        }
        return handler.getFailedMessageStore().values().stream()
                .filter(message -> traceId.equals(message.getTraceId()))
                .sorted(Comparator.comparing(MqFailedMessage::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(TRACE_LIMIT)
                .toList();
    }

    private List<LocalMessage> findLocalMessages(String traceId) {
        LocalMessageService service = localMessageServiceProvider.getIfAvailable();
        if (service == null) {
            return List.of();
        }
        return service.findAll().stream()
                .filter(message -> traceId.equals(message.getTraceId()))
                .sorted(Comparator.comparing(LocalMessage::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(TRACE_LIMIT)
                .toList();
    }

    private Map<String, Long> summary(List<OperationLogEntity> logs,
                                      List<MqFailedMessage> mqMessages,
                                      List<LocalMessage> localMessages) {
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("logs", (long) logs.size());
        summary.put("mqMessages", (long) mqMessages.size());
        summary.put("localMessages", (long) localMessages.size());
        summary.put("failed", logs.stream().filter(log -> Boolean.FALSE.equals(log.getSuccess())).count()
                + mqMessages.stream().filter(message -> MqFailedMessage.STATUS_EXHAUSTED.equals(message.getStatus())).count()
                + localMessages.stream().filter(message -> "FAILED".equals(String.valueOf(message.getStatus()))).count());
        return summary;
    }

    private List<TraceEvent> buildTimeline(List<OperationLogEntity> logs,
                                           List<MqFailedMessage> mqMessages,
                                           List<LocalMessage> localMessages) {
        List<TraceEvent> events = new ArrayList<>();
        logs.forEach(log -> events.add(new TraceEvent()
                .setSource("LOG")
                .setTitle(firstNonBlank(log.getAction(), log.getUri(), log.getMethod()))
                .setStatus(Boolean.FALSE.equals(log.getSuccess()) ? "FAILED" : "SUCCESS")
                .setMessage(log.getErrorMessage())
                .setTime(log.getCreateTime())));
        mqMessages.forEach(message -> events.add(new TraceEvent()
                .setSource("MQ")
                .setTitle(firstNonBlank(message.getMessageType(), message.getQueueName(), message.getMessageId()))
                .setStatus(message.getStatus())
                .setMessage(message.getErrorMessage())
                .setBusinessKey(message.getBusinessKey())
                .setTime(message.getCreateTime())));
        localMessages.forEach(message -> events.add(new TraceEvent()
                .setSource("LOCAL_MESSAGE")
                .setTitle(firstNonBlank(message.getTopic(), message.getMessageId()))
                .setStatus(String.valueOf(message.getStatus()))
                .setMessage(message.getErrorMessage())
                .setBusinessKey(message.getBusinessKey())
                .setTime(toDate(message.getCreateTime()))));
        events.sort(Comparator.comparing(TraceEvent::getTime,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return events;
    }

    private Date toDate(LocalDateTime time) {
        return time == null ? null : Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
