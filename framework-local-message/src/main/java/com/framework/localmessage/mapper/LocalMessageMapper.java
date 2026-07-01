package com.framework.localmessage.mapper;

import com.framework.localmessage.model.LocalMessage;
import com.framework.localmessage.model.LocalMessageStatus;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息 Mapper，使用注解 SQL，无需 XML。
 */
@Mapper
public interface LocalMessageMapper {

    @Update("""
            <script>
            CREATE TABLE IF NOT EXISTS ${tableName} (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                message_id VARCHAR(64),
                trace_id VARCHAR(64),
                parent_message_id VARCHAR(64),
                topic VARCHAR(128) NOT NULL,
                business_key VARCHAR(256),
                tenant_id VARCHAR(64),
                operator VARCHAR(64),
                source VARCHAR(64),
                payload LONGTEXT,
                status VARCHAR(32) NOT NULL,
                retry_count INT NOT NULL DEFAULT 0,
                max_retry INT NOT NULL DEFAULT 3,
                next_retry_time DATETIME NULL,
                error_message TEXT,
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_status_next_retry (status, next_retry_time),
                INDEX idx_message_id (message_id),
                INDEX idx_trace_id (trace_id),
                INDEX idx_topic_business_key (topic, business_key),
                INDEX idx_tenant_status (tenant_id, status),
                INDEX idx_create_time (create_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表'
            </script>
            """)
    void createTableIfNotExists(@Param("tableName") String tableName);

    @Insert("""
            INSERT INTO ${tableName}
            (message_id, trace_id, parent_message_id, topic, business_key, tenant_id, operator, source,
             payload, status, retry_count, max_retry, next_retry_time, error_message, create_time, update_time)
            VALUES
            (#{message.messageId}, #{message.traceId}, #{message.parentMessageId}, #{message.topic},
             #{message.businessKey}, #{message.tenantId}, #{message.operator}, #{message.source},
             #{message.payload}, #{message.status}, #{message.retryCount}, #{message.maxRetry},
             #{message.nextRetryTime}, #{message.errorMessage}, #{message.createTime}, #{message.updateTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "message.id")
    int insert(@Param("tableName") String tableName, @Param("message") LocalMessage message);

    @Update("""
            UPDATE ${tableName} SET
                message_id = #{message.messageId},
                trace_id = #{message.traceId},
                parent_message_id = #{message.parentMessageId},
                topic = #{message.topic},
                business_key = #{message.businessKey},
                tenant_id = #{message.tenantId},
                operator = #{message.operator},
                source = #{message.source},
                payload = #{message.payload},
                status = #{message.status},
                retry_count = #{message.retryCount},
                max_retry = #{message.maxRetry},
                next_retry_time = #{message.nextRetryTime},
                error_message = #{message.errorMessage},
                create_time = #{message.createTime},
                update_time = #{message.updateTime}
            WHERE id = #{message.id}
            """)
    int update(@Param("tableName") String tableName, @Param("message") LocalMessage message);

    @Select("""
            SELECT *
            FROM ${tableName}
            WHERE id = #{id}
            """)
    @Results(id = "localMessageMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "message_id", property = "messageId"),
            @Result(column = "trace_id", property = "traceId"),
            @Result(column = "parent_message_id", property = "parentMessageId"),
            @Result(column = "topic", property = "topic"),
            @Result(column = "business_key", property = "businessKey"),
            @Result(column = "tenant_id", property = "tenantId"),
            @Result(column = "operator", property = "operator"),
            @Result(column = "source", property = "source"),
            @Result(column = "payload", property = "payload"),
            @Result(column = "status", property = "status"),
            @Result(column = "retry_count", property = "retryCount"),
            @Result(column = "max_retry", property = "maxRetry"),
            @Result(column = "next_retry_time", property = "nextRetryTime"),
            @Result(column = "error_message", property = "errorMessage"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    LocalMessage findById(@Param("tableName") String tableName, @Param("id") Long id);

    @Select("""
            SELECT *
            FROM ${tableName}
            WHERE status = #{status}
              AND (next_retry_time IS NULL OR next_retry_time <= #{now})
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    @ResultMap("localMessageMap")
    List<LocalMessage> findDueMessages(@Param("tableName") String tableName,
                                       @Param("status") LocalMessageStatus status,
                                       @Param("now") LocalDateTime now,
                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT *
            FROM ${tableName}
            <where>
                <if test="topic != null">AND topic = #{topic}</if>
                <if test="status != null">AND status = #{status}</if>
                <if test="traceIdLike != null">AND trace_id LIKE #{traceIdLike}</if>
                <if test="businessKeyLike != null">AND business_key LIKE #{businessKeyLike}</if>
            </where>
            ORDER BY create_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    @ResultMap("localMessageMap")
    List<LocalMessage> list(@Param("tableName") String tableName,
                            @Param("topic") String topic,
                            @Param("status") LocalMessageStatus status,
                            @Param("traceIdLike") String traceIdLike,
                            @Param("businessKeyLike") String businessKeyLike,
                            @Param("offset") int offset,
                            @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ${tableName}
            <where>
                <if test="topic != null">AND topic = #{topic}</if>
                <if test="status != null">AND status = #{status}</if>
                <if test="traceIdLike != null">AND trace_id LIKE #{traceIdLike}</if>
                <if test="businessKeyLike != null">AND business_key LIKE #{businessKeyLike}</if>
            </where>
            </script>
            """)
    long count(@Param("tableName") String tableName,
               @Param("topic") String topic,
               @Param("status") LocalMessageStatus status,
               @Param("traceIdLike") String traceIdLike,
               @Param("businessKeyLike") String businessKeyLike);

    @Select("SELECT COUNT(*) FROM ${tableName}")
    long countAll(@Param("tableName") String tableName);

    @Select("SELECT COUNT(*) FROM ${tableName} WHERE status = #{status}")
    long countByStatus(@Param("tableName") String tableName,
                       @Param("status") LocalMessageStatus status);

    @Delete("DELETE FROM ${tableName} WHERE id = #{id}")
    int delete(@Param("tableName") String tableName, @Param("id") Long id);
}
