package com.framework.admin.excel;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Excel 后台管理 Mapper，使用注解 SQL。
 */
@Mapper
public interface ExcelAdminMapper {

    @Select("""
            <script>
            SELECT
                id,
                task_name,
                task_type,
                biz_type,
                status,
                filename,
                total_rows,
                success_rows,
                failure_rows,
                operator_name,
                error_message,
                DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time
            FROM framework_excel_task
            <where>
                <if test="taskType != null">AND task_type = #{taskType}</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            ORDER BY id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    @Results(id = "excelTaskMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "task_name", property = "taskName"),
            @Result(column = "task_type", property = "taskType"),
            @Result(column = "biz_type", property = "bizType"),
            @Result(column = "status", property = "status"),
            @Result(column = "filename", property = "filename"),
            @Result(column = "total_rows", property = "totalRows"),
            @Result(column = "success_rows", property = "successRows"),
            @Result(column = "failure_rows", property = "failureRows"),
            @Result(column = "operator_name", property = "operatorName"),
            @Result(column = "error_message", property = "errorMessage"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    List<ExcelAdminModels.Task> listTasks(@Param("taskType") String taskType,
                                          @Param("status") String status,
                                          @Param("offset") int offset,
                                          @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM framework_excel_task
            <where>
                <if test="taskType != null">AND task_type = #{taskType}</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countTasks(@Param("taskType") String taskType, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM framework_excel_task")
    long countAllTasks();

    @Select("SELECT COUNT(*) FROM framework_excel_task WHERE status = #{status}")
    long countTasksByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM framework_excel_task WHERE task_type = #{taskType}")
    long countTasksByType(@Param("taskType") String taskType);

    @Insert("""
            INSERT INTO framework_excel_task
            (task_name, task_type, biz_type, status, filename, total_rows, success_rows,
             failure_rows, operator_name, error_message)
            VALUES
            (#{taskName}, #{taskType}, #{bizType}, #{status}, #{filename},
             COALESCE(#{totalRows}, 0), COALESCE(#{successRows}, 0), COALESCE(#{failureRows}, 0),
             #{operatorName}, #{errorMessage})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTask(ExcelAdminModels.Task task);

    @Insert("""
            INSERT INTO framework_excel_error (task_id, row_index, error_message, raw_data)
            VALUES (#{taskId}, #{rowIndex}, #{errorMessage}, #{rawData})
            """)
    int insertError(@Param("taskId") Long taskId,
                    @Param("rowIndex") int rowIndex,
                    @Param("errorMessage") String errorMessage,
                    @Param("rawData") String rawData);

    @Select("""
            SELECT
                id,
                task_id,
                row_index,
                error_message,
                raw_data,
                DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time
            FROM framework_excel_error
            WHERE task_id = #{taskId}
            ORDER BY row_index ASC, id ASC
            LIMIT #{pageSize} OFFSET #{offset}
            """)
    @Results(id = "excelErrorMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "task_id", property = "taskId"),
            @Result(column = "row_index", property = "rowIndex"),
            @Result(column = "error_message", property = "errorMessage"),
            @Result(column = "raw_data", property = "rawData"),
            @Result(column = "create_time", property = "createTime")
    })
    List<ExcelAdminModels.ErrorRecord> listErrors(@Param("taskId") Long taskId,
                                                  @Param("offset") int offset,
                                                  @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM framework_excel_error WHERE task_id = #{taskId}")
    long countErrors(@Param("taskId") Long taskId);
}
