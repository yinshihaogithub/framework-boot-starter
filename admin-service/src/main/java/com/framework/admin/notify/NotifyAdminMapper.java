package com.framework.admin.notify;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 通知中心后台管理 Mapper，使用注解 SQL。
 */
@Mapper
public interface NotifyAdminMapper {

    @Select("""
            <script>
            SELECT
                id,
                template_code,
                template_name,
                channel,
                title,
                content,
                receivers,
                webhook_url,
                status,
                DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time
            FROM framework_notify_template
            <where>
                <if test="keywordLike != null">
                    AND (template_code LIKE #{keywordLike}
                         OR template_name LIKE #{keywordLike}
                         OR title LIKE #{keywordLike})
                </if>
                <if test="channel != null">AND channel = #{channel}</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    @Results(id = "notifyTemplateRowMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "template_code", property = "templateCode"),
            @Result(column = "template_name", property = "templateName"),
            @Result(column = "channel", property = "channel"),
            @Result(column = "title", property = "title"),
            @Result(column = "content", property = "content"),
            @Result(column = "receivers", property = "receivers"),
            @Result(column = "webhook_url", property = "webhookUrl"),
            @Result(column = "status", property = "status"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    List<TemplateRow> listTemplates(@Param("keywordLike") String keywordLike,
                                    @Param("channel") String channel,
                                    @Param("status") String status,
                                    @Param("offset") int offset,
                                    @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM framework_notify_template
            <where>
                <if test="keywordLike != null">
                    AND (template_code LIKE #{keywordLike}
                         OR template_name LIKE #{keywordLike}
                         OR title LIKE #{keywordLike})
                </if>
                <if test="channel != null">AND channel = #{channel}</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countTemplates(@Param("keywordLike") String keywordLike,
                        @Param("channel") String channel,
                        @Param("status") String status);

    @Select("""
            SELECT
                id,
                template_code,
                template_name,
                channel,
                title,
                content,
                receivers,
                webhook_url,
                status,
                DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time
            FROM framework_notify_template
            WHERE id = #{id}
            """)
    @Results(id = "notifyTemplateRowMapById", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "template_code", property = "templateCode"),
            @Result(column = "template_name", property = "templateName"),
            @Result(column = "channel", property = "channel"),
            @Result(column = "title", property = "title"),
            @Result(column = "content", property = "content"),
            @Result(column = "receivers", property = "receivers"),
            @Result(column = "webhook_url", property = "webhookUrl"),
            @Result(column = "status", property = "status"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    TemplateRow findTemplate(@Param("id") Long id);

    @Insert("""
            INSERT INTO framework_notify_template
            (template_code, template_name, channel, title, content, receivers, webhook_url, status)
            VALUES
            (#{templateCode}, #{templateName}, #{channel}, #{title}, #{content}, #{receivers}, #{webhookUrl}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTemplate(TemplateRow row);

    @Update("""
            UPDATE framework_notify_template SET
                template_code = #{templateCode},
                template_name = #{templateName},
                channel = #{channel},
                title = #{title},
                content = #{content},
                receivers = #{receivers},
                webhook_url = #{webhookUrl},
                status = #{status}
            WHERE id = #{id}
            """)
    int updateTemplate(TemplateRow row);

    @Delete("DELETE FROM framework_notify_template WHERE id = #{id}")
    int deleteTemplate(@Param("id") Long id);

    @Insert("""
            INSERT INTO framework_notify_record
            (template_code, channel, title, content, receivers, webhook_url,
             success, result_message, trace_id, operator_name)
            VALUES
            (#{templateCode}, #{channel}, #{title}, #{content}, #{receivers}, #{webhookUrl},
             #{success}, #{resultMessage}, #{traceId}, #{operatorName})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRecord(RecordRow row);

    @Select("""
            <script>
            SELECT
                id,
                template_code,
                channel,
                title,
                content,
                receivers,
                webhook_url,
                success,
                result_message,
                trace_id,
                operator_name,
                DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time
            FROM framework_notify_record
            <where>
                <if test="channel != null">AND channel = #{channel}</if>
                <if test="success != null">AND success = #{success}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    @Results(id = "notifyRecordRowMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "template_code", property = "templateCode"),
            @Result(column = "channel", property = "channel"),
            @Result(column = "title", property = "title"),
            @Result(column = "content", property = "content"),
            @Result(column = "receivers", property = "receivers"),
            @Result(column = "webhook_url", property = "webhookUrl"),
            @Result(column = "success", property = "success"),
            @Result(column = "result_message", property = "resultMessage"),
            @Result(column = "trace_id", property = "traceId"),
            @Result(column = "operator_name", property = "operatorName"),
            @Result(column = "create_time", property = "createTime")
    })
    List<RecordRow> listRecords(@Param("channel") String channel,
                                @Param("success") Boolean success,
                                @Param("offset") int offset,
                                @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM framework_notify_record
            <where>
                <if test="channel != null">AND channel = #{channel}</if>
                <if test="success != null">AND success = #{success}</if>
            </where>
            </script>
            """)
    long countRecords(@Param("channel") String channel, @Param("success") Boolean success);

    @Select("SELECT COUNT(*) FROM framework_notify_record WHERE success = #{success}")
    long countRecordsBySuccess(@Param("success") boolean success);

    @Select("SELECT COUNT(*) FROM framework_notify_template WHERE status = #{status}")
    long countTemplatesByStatus(@Param("status") String status);

    @Data
    @Accessors(chain = true)
    class TemplateRow {
        private Long id;
        private String templateCode;
        private String templateName;
        private String channel;
        private String title;
        private String content;
        private String receivers;
        private String webhookUrl;
        private String status;
        private String createTime;
        private String updateTime;
    }

    @Data
    @Accessors(chain = true)
    class RecordRow {
        private Long id;
        private String templateCode;
        private String channel;
        private String title;
        private String content;
        private String receivers;
        private String webhookUrl;
        private Boolean success;
        private String resultMessage;
        private String traceId;
        private String operatorName;
        private String createTime;
    }
}
