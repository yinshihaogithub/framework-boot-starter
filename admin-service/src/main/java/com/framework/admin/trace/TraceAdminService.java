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
    private static final String LOGS_KEY = "logs";
    private static final String MQ_MESSAGES_KEY = "mqMessages";
    private static final String LOCAL_MESSAGES_KEY = "localMessages";
    private static final String LOG_WARNING = "操作日志数据不可用";
    private static final String LOG_COUNT_WARNING = "操作日志统计不可用";
    private static final String MQ_WARNING = "MQ失败消息数据不可用";
    private static final String LOCAL_MESSAGE_WARNING = "本地消息数据不可用";

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
        List<String> warnings = new ArrayList<>();
        TraceSourceData<OperationLogEntity> logs = findLogs(traceId, warnings);
        TraceSourceData<MqFailedMessage> mqMessages = findMqMessages(traceId, warnings);
        TraceSourceData<LocalMessage> localMessages = findLocalMessages(traceId, warnings);
        addTruncationWarning("操作日志", logs, warnings);
        addTruncationWarning("MQ失败消息", mqMessages, warnings);
        addTruncationWarning("本地消息", localMessages, warnings);
        return new TraceDetail()
                .setTraceId(traceId)
                .setSummary(summary(logs, mqMessages, localMessages))
                .setDisplayed(displayed(logs, mqMessages, localMessages))
                .setTruncated(truncated(logs, mqMessages, localMessages))
                .setLimit(TRACE_LIMIT)
                .setWarnings(warnings)
                .setTimeline(buildTimeline(logs.items(), mqMessages.items(), localMessages.items()))
                .setLogs(logs.items())
                .setMqMessages(mqMessages.items())
                .setLocalMessages(localMessages.items().stream().map(LocalMessageVO::from).toList());
    }

    private TraceSourceData<OperationLogEntity> findLogs(String traceId, List<String> warnings) {
        OperationLogMapper mapper = available(operationLogMapperProvider, LOG_WARNING, warnings);
        if (mapper == null) {
            return TraceSourceData.empty(LOGS_KEY);
        }
        try {
            List<OperationLogEntity> logs = limit(mapper.selectList(null, null, null, null, traceId, 0, TRACE_LIMIT));
            long total = countLogs(mapper, traceId, null, logs.size(), warnings);
            long failed = countLogs(mapper, traceId, Boolean.FALSE,
                    logs.stream().filter(log -> Boolean.FALSE.equals(log.getSuccess())).count(), warnings);
            return new TraceSourceData<>(LOGS_KEY, logs, total, failed);
        } catch (Exception e) {
            warnings.add(warning(LOG_WARNING, e));
            return TraceSourceData.empty(LOGS_KEY);
        }
    }

    private TraceSourceData<MqFailedMessage> findMqMessages(String traceId, List<String> warnings) {
        DeadLetterHandler handler = available(deadLetterHandlerProvider, MQ_WARNING, warnings);
        if (handler == null) {
            return TraceSourceData.empty(MQ_MESSAGES_KEY);
        }
        try {
            List<MqFailedMessage> matched = handler.getFailedMessageStore().values().stream()
                    .filter(message -> traceId.equals(message.getTraceId()))
                    .sorted(Comparator.comparing(MqFailedMessage::getCreateTime, newestFirst()))
                    .toList();
            List<MqFailedMessage> displayed = matched.stream().limit(TRACE_LIMIT).toList();
            long failed = matched.stream()
                    .filter(message -> MqFailedMessage.STATUS_EXHAUSTED.equals(message.getStatus()))
                    .count();
            return new TraceSourceData<>(MQ_MESSAGES_KEY, displayed, matched.size(), failed);
        } catch (Exception e) {
            warnings.add(warning(MQ_WARNING, e));
            return TraceSourceData.empty(MQ_MESSAGES_KEY);
        }
    }

    private TraceSourceData<LocalMessage> findLocalMessages(String traceId, List<String> warnings) {
        LocalMessageService service = available(localMessageServiceProvider, LOCAL_MESSAGE_WARNING, warnings);
        if (service == null) {
            return TraceSourceData.empty(LOCAL_MESSAGES_KEY);
        }
        try {
            List<LocalMessage> matched = service.findAll().stream()
                    .filter(message -> traceId.equals(message.getTraceId()))
                    .sorted(Comparator.comparing(LocalMessage::getCreateTime, newestFirst()))
                    .toList();
            List<LocalMessage> displayed = matched.stream().limit(TRACE_LIMIT).toList();
            long failed = matched.stream()
                    .filter(message -> "FAILED".equals(String.valueOf(message.getStatus())))
                    .count();
            return new TraceSourceData<>(LOCAL_MESSAGES_KEY, displayed, matched.size(), failed);
        } catch (Exception e) {
            warnings.add(warning(LOCAL_MESSAGE_WARNING, e));
            return TraceSourceData.empty(LOCAL_MESSAGES_KEY);
        }
    }

    private List<OperationLogEntity> limit(List<OperationLogEntity> logs) {
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }
        return logs.stream().limit(TRACE_LIMIT).toList();
    }

    private long countLogs(OperationLogMapper mapper, String traceId, Boolean success, long fallback,
                           List<String> warnings) {
        try {
            return Math.max(mapper.count(null, null, null, success, traceId), fallback);
        } catch (Exception e) {
            warnings.add(warning(LOG_COUNT_WARNING, e));
            return fallback;
        }
    }

    private <T> T available(ObjectProvider<T> provider, String source, List<String> warnings) {
        if (provider == null) {
            return null;
        }
        try {
            return provider.getIfAvailable();
        } catch (RuntimeException e) {
            warnings.add(warning(source, e));
            return null;
        }
    }

    private String warning(String source, Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return source + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private void addTruncationWarning(String label, TraceSourceData<?> data, List<String> warnings) {
        if (data.truncated()) {
            warnings.add(label + "匹配 " + data.total() + " 条，仅展示最新 " + data.items().size() + " 条");
        }
    }

    private Map<String, Long> summary(TraceSourceData<OperationLogEntity> logs,
                                      TraceSourceData<MqFailedMessage> mqMessages,
                                      TraceSourceData<LocalMessage> localMessages) {
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put(LOGS_KEY, logs.total());
        summary.put(MQ_MESSAGES_KEY, mqMessages.total());
        summary.put(LOCAL_MESSAGES_KEY, localMessages.total());
        summary.put("failed", logs.failed() + mqMessages.failed() + localMessages.failed());
        return summary;
    }

    private Map<String, Long> displayed(TraceSourceData<OperationLogEntity> logs,
                                        TraceSourceData<MqFailedMessage> mqMessages,
                                        TraceSourceData<LocalMessage> localMessages) {
        Map<String, Long> displayed = new LinkedHashMap<>();
        displayed.put(LOGS_KEY, (long) logs.items().size());
        displayed.put(MQ_MESSAGES_KEY, (long) mqMessages.items().size());
        displayed.put(LOCAL_MESSAGES_KEY, (long) localMessages.items().size());
        displayed.put("timeline", (long) logs.items().size() + mqMessages.items().size() + localMessages.items().size());
        return displayed;
    }

    private Map<String, Boolean> truncated(TraceSourceData<OperationLogEntity> logs,
                                           TraceSourceData<MqFailedMessage> mqMessages,
                                           TraceSourceData<LocalMessage> localMessages) {
        Map<String, Boolean> truncated = new LinkedHashMap<>();
        truncated.put(LOGS_KEY, logs.truncated());
        truncated.put(MQ_MESSAGES_KEY, mqMessages.truncated());
        truncated.put(LOCAL_MESSAGES_KEY, localMessages.truncated());
        truncated.put("timeline", logs.truncated() || mqMessages.truncated() || localMessages.truncated());
        return truncated;
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
        events.sort(Comparator.comparing(TraceEvent::getTime, newestFirst()));
        return events;
    }

    private <T extends Comparable<? super T>> Comparator<T> newestFirst() {
        return Comparator.nullsLast(Comparator.reverseOrder());
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

    private record TraceSourceData<T>(String key, List<T> items, long total, long failed) {

        private static <T> TraceSourceData<T> empty(String key) {
            return new TraceSourceData<>(key, List.of(), 0L, 0L);
        }

        private boolean truncated() {
            return total > items.size();
        }
    }
}
