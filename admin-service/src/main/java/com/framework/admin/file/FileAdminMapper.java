package com.framework.admin.file;

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
 * 文件中心 Mapper，使用注解 SQL。
 */
@Mapper
public interface FileAdminMapper {

    @Select("""
            <script>
            SELECT
                id,
                file_key,
                original_filename,
                content_type,
                file_size,
                url,
                storage_type,
                business_type,
                business_key,
                operator_id,
                operator_name,
                deleted,
                DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time
            FROM framework_file_record
            <where>
                deleted = 0
                <if test="keyword != null">
                    AND (original_filename LIKE #{keyword}
                        OR file_key LIKE #{keyword}
                        OR business_key LIKE #{keyword})
                </if>
                <if test="businessType != null">AND business_type = #{businessType}</if>
                <if test="businessKey != null">AND business_key LIKE #{businessKey}</if>
                <if test="contentType != null">AND content_type LIKE #{contentType}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    @Results(id = "fileRecordMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "file_key", property = "fileKey"),
            @Result(column = "original_filename", property = "originalFilename"),
            @Result(column = "content_type", property = "contentType"),
            @Result(column = "file_size", property = "fileSize"),
            @Result(column = "url", property = "url"),
            @Result(column = "storage_type", property = "storageType"),
            @Result(column = "business_type", property = "businessType"),
            @Result(column = "business_key", property = "businessKey"),
            @Result(column = "operator_id", property = "operatorId"),
            @Result(column = "operator_name", property = "operatorName"),
            @Result(column = "deleted", property = "deleted"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    List<FileAdminModels.FileRecord> list(@Param("keyword") String keyword,
                                          @Param("businessType") String businessType,
                                          @Param("businessKey") String businessKey,
                                          @Param("contentType") String contentType,
                                          @Param("offset") int offset,
                                          @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM framework_file_record
            <where>
                deleted = 0
                <if test="keyword != null">
                    AND (original_filename LIKE #{keyword}
                        OR file_key LIKE #{keyword}
                        OR business_key LIKE #{keyword})
                </if>
                <if test="businessType != null">AND business_type = #{businessType}</if>
                <if test="businessKey != null">AND business_key LIKE #{businessKey}</if>
                <if test="contentType != null">AND content_type LIKE #{contentType}</if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword,
               @Param("businessType") String businessType,
               @Param("businessKey") String businessKey,
               @Param("contentType") String contentType);

    @Select("SELECT COUNT(*) FROM framework_file_record WHERE deleted = 0")
    long countActive();

    @Select("SELECT COUNT(*) FROM framework_file_record WHERE deleted = 1")
    long countDeleted();

    @Select("SELECT COALESCE(SUM(file_size), 0) FROM framework_file_record WHERE deleted = 0")
    long sumActiveSize();

    @Select("""
            SELECT
                id,
                file_key,
                original_filename,
                content_type,
                file_size,
                url,
                storage_type,
                business_type,
                business_key,
                operator_id,
                operator_name,
                deleted,
                DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time
            FROM framework_file_record
            WHERE id = #{id}
            """)
    @Results(id = "fileRecordDetailMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "file_key", property = "fileKey"),
            @Result(column = "original_filename", property = "originalFilename"),
            @Result(column = "content_type", property = "contentType"),
            @Result(column = "file_size", property = "fileSize"),
            @Result(column = "url", property = "url"),
            @Result(column = "storage_type", property = "storageType"),
            @Result(column = "business_type", property = "businessType"),
            @Result(column = "business_key", property = "businessKey"),
            @Result(column = "operator_id", property = "operatorId"),
            @Result(column = "operator_name", property = "operatorName"),
            @Result(column = "deleted", property = "deleted"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    FileAdminModels.FileRecord findById(@Param("id") Long id);

    @Insert("""
            INSERT INTO framework_file_record
            (file_key, original_filename, content_type, file_size, url, storage_type,
             business_type, business_key, operator_id, operator_name)
            VALUES
            (#{fileKey}, #{originalFilename}, #{contentType}, #{fileSize}, #{url}, #{storageType},
             #{businessType}, #{businessKey}, #{operatorId}, #{operatorName})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FileAdminModels.FileRecord record);

    @Update("""
            UPDATE framework_file_record
            SET deleted = 1
            WHERE id = #{id}
            """)
    int markDeleted(@Param("id") Long id);

}
