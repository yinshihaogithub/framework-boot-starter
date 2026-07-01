package com.framework.mq.mapper;

import com.framework.mq.deadletter.MqFailedMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MQ 失败消息 Mapper，使用注解 SQL，无需 XML。
 */
@Mapper
public interface MqFailedMessageMapper {

    @Update("""
            <script>
            CREATE TABLE IF NOT EXISTS ${tableName} (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                message_id VARCHAR(64),
                trace_id VARCHAR(64),
                parent_message_id VARCHAR(64),
                business_key VARCHAR(256),
                message_type VARCHAR(128),
                exchange_name VARCHAR(128),
                routing_key VARCHAR(128),
                queue_name VARCHAR(128),
                payload LONGTEXT,
                error_message TEXT,
                error_stack LONGTEXT,
                retry_count INT NOT NULL DEFAULT 0,
                max_retry INT NOT NULL DEFAULT 3,
                status VARCHAR(32) NOT NULL,
                next_retry_time DATETIME NULL,
                source VARCHAR(32),
                tenant_id VARCHAR(64),
                operator VARCHAR(64),
                compensate_remark VARCHAR(512),
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_status_next_retry (status, next_retry_time),
                INDEX idx_message_id (message_id),
                INDEX idx_trace_id (trace_id),
                INDEX idx_business_key (business_key),
                INDEX idx_queue_status (queue_name, status),
                INDEX idx_create_time (create_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ失败消息与人工补偿表'
            </script>
            """)
    void createTableIfNotExists(@Param("tableName") String tableName);

    @Insert("""
            INSERT INTO ${tableName}
            (message_id, trace_id, parent_message_id, business_key, message_type,
             exchange_name, routing_key, queue_name, payload, error_message, error_stack,
             retry_count, max_retry, status, next_retry_time, source, tenant_id, operator,
             compensate_remark, create_time, update_time)
            VALUES
            (#{message.messageId}, #{message.traceId}, #{message.parentMessageId}, #{message.businessKey},
             #{message.messageType}, #{message.exchange}, #{message.routingKey}, #{message.queueName},
             #{message.payload}, #{message.errorMessage}, #{message.errorStack}, #{message.retryCount},
             #{message.maxRetry}, #{message.status}, #{message.nextRetryTime}, #{message.source},
             #{message.tenantId}, #{message.operator}, #{message.compensateRemark},
             #{message.createTime}, #{message.updateTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "message.id")
    int insert(@Param("tableName") String tableName, @Param("message") MqFailedMessage message);

    @Update("""
            UPDATE ${tableName} SET
                message_id = #{message.messageId},
                trace_id = #{message.traceId},
                parent_message_id = #{message.parentMessageId},
                business_key = #{message.businessKey},
                message_type = #{message.messageType},
                exchange_name = #{message.exchange},
                routing_key = #{message.routingKey},
                queue_name = #{message.queueName},
                payload = #{message.payload},
                error_message = #{message.errorMessage},
                error_stack = #{message.errorStack},
                retry_count = #{message.retryCount},
                max_retry = #{message.maxRetry},
                status = #{message.status},
                next_retry_time = #{message.nextRetryTime},
                source = #{message.source},
                tenant_id = #{message.tenantId},
                operator = #{message.operator},
                compensate_remark = #{message.compensateRemark},
                create_time = #{message.createTime},
                update_time = #{message.updateTime}
            WHERE id = #{message.id}
            """)
    int update(@Param("tableName") String tableName, @Param("message") MqFailedMessage message);

    @Select("""
            SELECT *
            FROM ${tableName}
            WHERE id = #{id}
            """)
    @Results(id = "mqFailedMessageMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "message_id", property = "messageId"),
            @Result(column = "trace_id", property = "traceId"),
            @Result(column = "parent_message_id", property = "parentMessageId"),
            @Result(column = "business_key", property = "businessKey"),
            @Result(column = "message_type", property = "messageType"),
            @Result(column = "exchange_name", property = "exchange"),
            @Result(column = "routing_key", property = "routingKey"),
            @Result(column = "queue_name", property = "queueName"),
            @Result(column = "payload", property = "payload"),
            @Result(column = "error_message", property = "errorMessage"),
            @Result(column = "error_stack", property = "errorStack"),
            @Result(column = "retry_count", property = "retryCount"),
            @Result(column = "max_retry", property = "maxRetry"),
            @Result(column = "status", property = "status"),
            @Result(column = "next_retry_time", property = "nextRetryTime"),
            @Result(column = "source", property = "source"),
            @Result(column = "tenant_id", property = "tenantId"),
            @Result(column = "operator", property = "operator"),
            @Result(column = "compensate_remark", property = "compensateRemark"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    MqFailedMessage findById(@Param("tableName") String tableName, @Param("id") Long id);

    @Select("""
            SELECT *
            FROM ${tableName}
            ORDER BY id ASC
            """)
    @ResultMap("mqFailedMessageMap")
    List<MqFailedMessage> findAll(@Param("tableName") String tableName);

    @Delete("DELETE FROM ${tableName} WHERE id = #{id}")
    int deleteById(@Param("tableName") String tableName, @Param("id") Long id);

    @Delete("""
            DELETE FROM ${tableName}
            WHERE status IN (#{successStatus}, #{exhaustedStatus}, #{manualStatus})
            """)
    int deleteProcessed(@Param("tableName") String tableName,
                        @Param("successStatus") String successStatus,
                        @Param("exhaustedStatus") String exhaustedStatus,
                        @Param("manualStatus") String manualStatus);
}
