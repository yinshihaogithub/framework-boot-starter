package com.framework.log.mapper;

import com.framework.log.entity.OperationLogEntity;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

/**
 * 操作日志 Mapper
 * 使用注解 SQL，无需 XML 文件
 */
@Mapper
public interface OperationLogMapper {

    /**
     * 创建日志表（应用启动时调用）
     */
    @Update("""
            CREATE TABLE IF NOT EXISTS sys_operation_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                log_type VARCHAR(32) NOT NULL DEFAULT 'OPERATION',
                module VARCHAR(64),
                action VARCHAR(128),
                operation_type VARCHAR(32),
                uri VARCHAR(256),
                http_method VARCHAR(16),
                method VARCHAR(256),
                params TEXT,
                result TEXT,
                success TINYINT(1) DEFAULT 1,
                error_message TEXT,
                elapsed_ms BIGINT,
                operator_id BIGINT,
                operator_name VARCHAR(64),
                client_ip VARCHAR(64),
                trace_id VARCHAR(64),
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_create_time (create_time),
                INDEX idx_operator (operator_id),
                INDEX idx_trace (trace_id),
                INDEX idx_module (module)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表'
            """)
    void createTableIfNotExists();

    /**
     * 插入日志
     */
    @Insert("""
            INSERT INTO sys_operation_log
            (log_type, module, action, operation_type, uri, http_method, method,
             params, result, success, error_message, elapsed_ms,
             operator_id, operator_name, client_ip, trace_id, create_time)
            VALUES
            (#{logType}, #{module}, #{action}, #{operationType}, #{uri}, #{httpMethod}, #{method},
             #{params}, #{result}, #{success}, #{errorMessage}, #{elapsedMs},
             #{operatorId}, #{operatorName}, #{clientIp}, #{traceId}, #{createTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(OperationLogEntity entity);

    /**
     * 查询日志列表
     */
    @Select("""
            <script>
            SELECT * FROM sys_operation_log
            <where>
                <if test="module != null and module != ''">AND module = #{module}</if>
                <if test="logType != null and logType != ''">AND log_type = #{logType}</if>
                <if test="operatorId != null">AND operator_id = #{operatorId}</if>
                <if test="success != null">AND success = #{success}</if>
                <if test="traceId != null and traceId != ''">AND trace_id = #{traceId}</if>
            </where>
            ORDER BY create_time DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<OperationLogEntity> selectList(
            @Param("module") String module,
            @Param("logType") String logType,
            @Param("operatorId") Long operatorId,
            @Param("success") Boolean success,
            @Param("traceId") String traceId,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize);

    /**
     * 查询总数
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM sys_operation_log
            <where>
                <if test="module != null and module != ''">AND module = #{module}</if>
                <if test="logType != null and logType != ''">AND log_type = #{logType}</if>
                <if test="operatorId != null">AND operator_id = #{operatorId}</if>
                <if test="success != null">AND success = #{success}</if>
                <if test="traceId != null and traceId != ''">AND trace_id = #{traceId}</if>
            </where>
            </script>
            """)
    long count(
            @Param("module") String module,
            @Param("logType") String logType,
            @Param("operatorId") Long operatorId,
            @Param("success") Boolean success,
            @Param("traceId") String traceId);

    /**
     * 清理指定天数前的日志
     */
    @Delete("DELETE FROM sys_operation_log WHERE create_time &lt; #{beforeDate}")
    int deleteBefore(@Param("beforeDate") Date beforeDate);
}
