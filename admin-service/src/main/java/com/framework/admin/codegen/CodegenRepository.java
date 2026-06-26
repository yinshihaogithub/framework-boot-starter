package com.framework.admin.codegen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class CodegenRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CodegenModels.TableInfo> tableMapper = (rs, rowNum) -> new CodegenModels.TableInfo()
            .setTableName(rs.getString("table_name"))
            .setTableComment(rs.getString("table_comment"))
            .setTableRows(rs.getLong("table_rows"))
            .setEngine(rs.getString("engine"))
            .setCreateTime(format(rs.getTimestamp("create_time")))
            .setUpdateTime(format(rs.getTimestamp("update_time")));

    private final RowMapper<CodegenModels.ColumnInfo> columnMapper = (rs, rowNum) -> {
        String columnName = rs.getString("column_name");
        String dataType = rs.getString("data_type");
        String extra = rs.getString("extra");
        return new CodegenModels.ColumnInfo()
                .setColumnName(columnName)
                .setColumnType(rs.getString("column_type"))
                .setDataType(dataType)
                .setColumnComment(rs.getString("column_comment"))
                .setNullable("YES".equalsIgnoreCase(rs.getString("is_nullable")))
                .setPrimaryKey("PRI".equalsIgnoreCase(rs.getString("column_key")))
                .setAutoIncrement(extra != null && extra.toLowerCase(Locale.ROOT).contains("auto_increment"))
                .setColumnDefault(rs.getString("column_default"))
                .setOrdinalPosition(rs.getInt("ordinal_position"))
                .setJavaType(javaType(dataType))
                .setJavaField(toCamel(columnName, false))
                .setTsType(tsType(dataType));
    };

    public CodegenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CodegenModels.TableInfo> listTables(String keyword, int pageNum, int pageSize) {
        String like = like(keyword);
        return jdbcTemplate.query("""
                SELECT table_name, table_comment, table_rows, engine, create_time, update_time
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND (? IS NULL OR table_name LIKE ? OR table_comment LIKE ?)
                ORDER BY table_name ASC
                LIMIT ? OFFSET ?
                """, tableMapper, like, like, like, pageSize, offset(pageNum, pageSize));
    }

    public long countTables(String keyword) {
        String like = like(keyword);
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND (? IS NULL OR table_name LIKE ? OR table_comment LIKE ?)
                """, Long.class, like, like, like);
        return count == null ? 0 : count;
    }

    public Optional<CodegenModels.TableInfo> findTable(String tableName) {
        List<CodegenModels.TableInfo> tables = jdbcTemplate.query("""
                SELECT table_name, table_comment, table_rows, engine, create_time, update_time
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name = ?
                """, tableMapper, tableName);
        return tables.stream().findFirst();
    }

    public List<CodegenModels.ColumnInfo> listColumns(String tableName) {
        return jdbcTemplate.query("""
                SELECT column_name, column_type, data_type, column_comment, is_nullable,
                       column_key, extra, column_default, ordinal_position
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                ORDER BY ordinal_position ASC
                """, columnMapper, tableName);
    }

    public static String toCamel(String value, boolean upperFirst) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean upper = upperFirst;
        for (char ch : value.toCharArray()) {
            if (ch == '_' || ch == '-' || ch == ' ') {
                upper = true;
                continue;
            }
            builder.append(upper ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
            upper = false;
        }
        return builder.toString();
    }

    private static String javaType(String dataType) {
        if (dataType == null) {
            return "String";
        }
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint" -> "Long";
            case "int", "integer", "smallint", "tinyint", "mediumint" -> "Integer";
            case "decimal", "numeric" -> "BigDecimal";
            case "double" -> "Double";
            case "float" -> "Float";
            case "bit", "boolean" -> "Boolean";
            case "date", "datetime", "timestamp", "time" -> "LocalDateTime";
            default -> "String";
        };
    }

    private static String tsType(String dataType) {
        if (dataType == null) {
            return "string";
        }
        return switch (dataType.toLowerCase(Locale.ROOT)) {
            case "bigint", "int", "integer", "smallint", "tinyint", "mediumint", "decimal", "numeric", "double", "float" -> "number";
            case "bit", "boolean" -> "boolean";
            default -> "string";
        };
    }

    private static String like(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "%" + value.trim() + "%";
    }

    private static int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * pageSize;
    }

    private static String format(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime time = timestamp.toLocalDateTime();
        return time.toString().replace('T', ' ');
    }
}
