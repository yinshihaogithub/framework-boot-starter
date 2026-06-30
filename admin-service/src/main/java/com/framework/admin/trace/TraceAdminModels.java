package com.framework.admin.trace;

import com.framework.admin.localmessage.LocalMessageVO;
import com.framework.log.entity.OperationLogEntity;
import com.framework.mq.deadletter.MqFailedMessage;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class TraceAdminModels {

    @Data
    @Accessors(chain = true)
    public static class TraceDetail {
        private String traceId;
        private Map<String, Long> summary;
        private Map<String, Long> displayed;
        private Map<String, Boolean> truncated;
        private int limit;
        private List<String> warnings;
        private List<TraceEvent> timeline;
        private List<OperationLogEntity> logs;
        private List<MqFailedMessage> mqMessages;
        private List<LocalMessageVO> localMessages;
    }

    @Data
    @Accessors(chain = true)
    public static class TraceEvent {
        private String source;
        private String title;
        private String status;
        private String message;
        private String businessKey;
        private Date time;
    }
}
