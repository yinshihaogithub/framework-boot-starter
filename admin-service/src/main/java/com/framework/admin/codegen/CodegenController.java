package com.framework.admin.codegen;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/admin/codegen")
@Tag(name = "代码生成", description = "基于 MySQL 元数据生成工程代码预览")
public class CodegenController {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final Pattern SAFE_PACKAGE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$");

    private final CodegenRepository repository;
    private final AdminAuditService auditService;

    public CodegenController(CodegenRepository repository, AdminAuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Operation(summary = "数据库表列表")
    @GetMapping("/tables")
    public Result<PageResult<CodegenModels.TableInfo>> tables(@RequestParam(required = false) String keyword,
                                                              @RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "20") int pageSize) {
        int safePageNum = safePageNum(pageNum);
        int safePageSize = safePageSize(pageSize);
        List<CodegenModels.TableInfo> records = repository.listTables(keyword, safePageNum, safePageSize);
        long total = repository.countTables(keyword);
        return Result.success(PageResult.of(records, total, safePageNum, safePageSize));
    }

    @Operation(summary = "数据库表字段")
    @GetMapping("/tables/{tableName}/columns")
    public Result<List<CodegenModels.ColumnInfo>> columns(@PathVariable String tableName) {
        if (!isSafeName(tableName)) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "表名不合法");
        }
        if (repository.findTable(tableName).isEmpty()) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "表不存在");
        }
        return Result.success(repository.listColumns(tableName));
    }

    @Operation(summary = "生成代码预览")
    @PostMapping("/preview")
    public Result<CodegenModels.PreviewResponse> preview(@RequestBody CodegenModels.PreviewRequest request,
                                                         HttpServletRequest servletRequest) {
        Result<String> validation = validate(request);
        if (validation != null) {
            return Result.fail(validation.getCode(), validation.getMessage());
        }
        String tableName = request.getTableName().trim();
        Optional<CodegenModels.TableInfo> table = repository.findTable(tableName);
        if (table.isEmpty()) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "表不存在");
        }

        List<CodegenModels.ColumnInfo> columns = repository.listColumns(tableName);
        if (columns.isEmpty()) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "表字段为空");
        }

        CodegenOptions options = options(request);
        List<CodegenModels.GeneratedFile> files = generateFiles(table.get(), columns, options);
        auditService.success(servletRequest, "代码生成", "生成代码预览", "PREVIEW",
                auditService.params("tableName", tableName, "entityName", options.entityName(),
                        "packageName", options.packageName()));
        return Result.success(new CodegenModels.PreviewResponse()
                .setTable(table.get())
                .setColumns(columns)
                .setFiles(files));
    }

    private List<CodegenModels.GeneratedFile> generateFiles(CodegenModels.TableInfo table,
                                                           List<CodegenModels.ColumnInfo> columns,
                                                           CodegenOptions options) {
        String entityName = options.entityName();
        String moduleKebab = toKebab(options.moduleName());
        List<CodegenModels.GeneratedFile> files = new ArrayList<>();
        files.add(file(entityName + ".java", "src/main/java/" + packagePath(entityPackage(options)) + "/" + entityName + ".java",
                "java", generateEntity(table, columns, options)));
        files.add(file(entityName + "Mapper.java", "src/main/java/" + packagePath(mapperPackage(options)) + "/" + entityName + "Mapper.java",
                "java", generateMapper(table, columns, options)));
        files.add(file(entityName + "Service.java", "src/main/java/" + packagePath(servicePackage(options)) + "/" + entityName + "Service.java",
                "java", generateService(columns, options)));
        files.add(file(entityName + "Controller.java", "src/main/java/" + packagePath(controllerPackage(options)) + "/" + entityName + "Controller.java",
                "java", generateController(columns, options)));
        files.add(file(entityName + "Page.vue", "frontend/admin-web/src/views/" + moduleKebab + "/" + entityName + "Page.vue",
                "vue", generateVue(table, columns, options)));
        files.add(file(moduleKebab + "_menu.sql", "sql/mysql/" + moduleKebab + "_menu.sql",
                "sql", generateMenuSql(options)));
        return files;
    }

    private String generateEntity(CodegenModels.TableInfo table, List<CodegenModels.ColumnInfo> columns,
                                  CodegenOptions options) {
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(entityPackage(options)).append(";\n\n");
        if (hasJavaType(columns, "BigDecimal")) {
            builder.append("import java.math.BigDecimal;\n");
        }
        if (hasJavaType(columns, "LocalDateTime")) {
            builder.append("import java.time.LocalDateTime;\n");
        }
        builder.append("import lombok.Data;\n");
        builder.append("import lombok.experimental.Accessors;\n\n");
        builder.append("/**\n");
        builder.append(" * ").append(defaultText(table.getTableComment(), table.getTableName())).append(".\n");
        builder.append(" */\n");
        builder.append("@Data\n@Accessors(chain = true)\n");
        builder.append("public class ").append(options.entityName()).append(" {\n\n");
        for (CodegenModels.ColumnInfo column : columns) {
            if (!isBlank(column.getColumnComment())) {
                builder.append("    /** ").append(column.getColumnComment()).append(" */\n");
            }
            builder.append("    private ").append(column.getJavaType()).append(" ").append(column.getJavaField()).append(";\n\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private String generateMapper(CodegenModels.TableInfo table, List<CodegenModels.ColumnInfo> columns,
                                  CodegenOptions options) {
        CodegenModels.ColumnInfo primaryKey = primaryKey(columns);
        List<CodegenModels.ColumnInfo> insertColumns = columns.stream()
                .filter(CodegenController::isInsertableColumn)
                .toList();
        List<CodegenModels.ColumnInfo> updateColumns = columns.stream()
                .filter(CodegenController::isEditableColumn)
                .toList();
        String entity = options.entityName();
        String tableName = table.getTableName();
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(mapperPackage(options)).append(";\n\n");
        builder.append("import ").append(entityPackage(options)).append(".").append(entity).append(";\n");
        builder.append("import org.apache.ibatis.annotations.Delete;\n");
        builder.append("import org.apache.ibatis.annotations.Insert;\n");
        builder.append("import org.apache.ibatis.annotations.Mapper;\n");
        builder.append("import org.apache.ibatis.annotations.Options;\n");
        builder.append("import org.apache.ibatis.annotations.Param;\n");
        builder.append("import org.apache.ibatis.annotations.Result;\n");
        builder.append("import org.apache.ibatis.annotations.ResultMap;\n");
        builder.append("import org.apache.ibatis.annotations.Results;\n");
        builder.append("import org.apache.ibatis.annotations.Select;\n");
        builder.append("import org.apache.ibatis.annotations.Update;\n\n");
        builder.append("import java.util.List;\n");
        builder.append("\n");
        builder.append("@Mapper\n");
        builder.append("public interface ").append(entity).append("Mapper {\n\n");
        builder.append("    @Select(\"\"\"\n");
        builder.append("            SELECT * FROM `").append(tableName).append("`\n");
        builder.append("            ORDER BY `").append(primaryKey.getColumnName()).append("` DESC\n");
        builder.append("            LIMIT #{pageSize} OFFSET #{offset}\n");
        builder.append("            \"\"\")\n");
        builder.append("    @Results(id = \"").append(entity).append("ResultMap\", value = {\n");
        appendResultMappings(builder, columns);
        builder.append("    })\n");
        builder.append("    List<").append(entity).append("> list(@Param(\"offset\") int offset, @Param(\"pageSize\") int pageSize);\n\n");
        builder.append("    @Select(\"SELECT COUNT(*) FROM `").append(tableName).append("`\")\n");
        builder.append("    long count();\n\n");
        builder.append("    @Select(\"SELECT * FROM `").append(tableName).append("` WHERE `").append(primaryKey.getColumnName()).append("` = #{id}\")\n");
        builder.append("    @ResultMap(\"").append(entity).append("ResultMap\")\n");
        builder.append("    ").append(entity).append(" findById(@Param(\"id\") ").append(primaryKey.getJavaType()).append(" id);\n\n");
        builder.append("    @Insert(\"\"\"\n");
        builder.append("            INSERT INTO `").append(tableName).append("` (").append(joinColumnNames(insertColumns)).append(")\n");
        builder.append("            VALUES (").append(joinValueReferences(insertColumns)).append(")\n");
        builder.append("            \"\"\")\n");
        if (Boolean.TRUE.equals(primaryKey.getAutoIncrement())) {
            builder.append("    @Options(useGeneratedKeys = true, keyProperty = \"").append(primaryKey.getJavaField()).append("\")\n");
        }
        builder.append("    int insert(").append(entity).append(" entity);\n\n");
        builder.append("    @Update(\"\"\"\n");
        builder.append("            UPDATE `").append(tableName).append("`\n");
        builder.append("            SET ").append(joinMapperAssignments(updateColumns)).append("\n");
        builder.append("            WHERE `").append(primaryKey.getColumnName()).append("` = #{").append(primaryKey.getJavaField()).append("}\n");
        builder.append("            \"\"\")\n");
        builder.append("    int update(").append(entity).append(" entity);\n\n");
        builder.append("    @Delete(\"DELETE FROM `").append(tableName).append("` WHERE `").append(primaryKey.getColumnName()).append("` = #{id}\")\n");
        builder.append("    int deleteById(@Param(\"id\") ").append(primaryKey.getJavaType()).append(" id);\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String generateService(List<CodegenModels.ColumnInfo> columns, CodegenOptions options) {
        String entity = options.entityName();
        String mapperName = entity + "Mapper";
        CodegenModels.ColumnInfo primaryKey = primaryKey(columns);
        return """
                package %s;

                import %s.%s;
                import %s.%s;
                import com.framework.core.result.PageResult;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.support.TransactionTemplate;

                import java.util.List;

                @Service
                public class %sService {

                    private static final int DEFAULT_PAGE_NUM = 1;
                    private static final int DEFAULT_PAGE_SIZE = 20;
                    private static final int MAX_PAGE_SIZE = 200;

                    private final %s mapper;
                    private final TransactionTemplate transactionTemplate;

                    public %sService(%s mapper, TransactionTemplate transactionTemplate) {
                        this.mapper = mapper;
                        this.transactionTemplate = transactionTemplate;
                    }

                    public PageResult<%s> page(int pageNum, int pageSize) {
                        int safePageNum = pageNum > 0 ? pageNum : DEFAULT_PAGE_NUM;
                        int safePageSize = pageSize > 0 ? Math.min(pageSize, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
                        int offset = (safePageNum - 1) * safePageSize;
                        List<%s> records = mapper.list(offset, safePageSize);
                        return PageResult.of(records, mapper.count(), safePageNum, safePageSize);
                    }

                    public %s findById(%s id) {
                        return mapper.findById(id);
                    }

                    public %s create(%s request) {
                        return transactionTemplate.execute(status -> {
                            mapper.insert(request);
                            return request.get%s();
                        });
                    }

                    public void update(%s id, %s request) {
                        transactionTemplate.executeWithoutResult(status -> {
                            request.set%s(id);
                            mapper.update(request);
                        });
                    }

                    public void delete(%s id) {
                        transactionTemplate.executeWithoutResult(status -> mapper.deleteById(id));
                    }
                }
                """.formatted(servicePackage(options), entityPackage(options), entity, mapperPackage(options), mapperName,
                entity, mapperName, entity, mapperName,
                entity, entity, entity, primaryKey.getJavaType(),
                primaryKey.getJavaType(), entity, upperFirst(primaryKey.getJavaField()),
                primaryKey.getJavaType(), entity, upperFirst(primaryKey.getJavaField()),
                primaryKey.getJavaType());
    }

    private String generateController(List<CodegenModels.ColumnInfo> columns, CodegenOptions options) {
        String entity = options.entityName();
        String serviceName = entity + "Service";
        CodegenModels.ColumnInfo primaryKey = primaryKey(columns);
        String moduleKebab = toKebab(options.moduleName());
        return """
                package %s;

                import %s.%s;
                import %s.%s;
                import com.framework.core.result.PageResult;
                import com.framework.core.result.Result;
                import io.swagger.v3.oas.annotations.Operation;
                import io.swagger.v3.oas.annotations.tags.Tag;
                import org.springframework.web.bind.annotation.DeleteMapping;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.PutMapping;
                import org.springframework.web.bind.annotation.RequestBody;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/admin/%s")
                @Tag(name = "%s管理", description = "%s基础 CRUD")
                public class %sController {

                    private final %s service;

                    public %sController(%s service) {
                        this.service = service;
                    }

                    @Operation(summary = "分页列表")
                    @GetMapping
                    public Result<PageResult<%s>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
                        return Result.success(service.page(pageNum, pageSize));
                    }

                    @Operation(summary = "新增")
                    @PostMapping
                    public Result<%s> create(@RequestBody %s request) {
                        return Result.success(service.create(request));
                    }

                    @Operation(summary = "更新")
                    @PutMapping("/{id}")
                    public Result<String> update(@PathVariable %s id, @RequestBody %s request) {
                        service.update(id, request);
                        return Result.success("已更新");
                    }

                    @Operation(summary = "删除")
                    @DeleteMapping("/{id}")
                    public Result<String> delete(@PathVariable %s id) {
                        service.delete(id);
                        return Result.success("已删除");
                    }
                }
                """.formatted(controllerPackage(options), entityPackage(options), entity, servicePackage(options), serviceName,
                moduleKebab, entity, entity, entity,
                serviceName, entity, serviceName, entity,
                primaryKey.getJavaType(), entity, primaryKey.getJavaType(), entity, primaryKey.getJavaType());
    }

    private String generateVue(CodegenModels.TableInfo table, List<CodegenModels.ColumnInfo> columns,
                               CodegenOptions options) {
        String entity = options.entityName();
        String moduleKebab = toKebab(options.moduleName());
        String variable = lowerFirst(entity);
        StringBuilder fields = new StringBuilder();
        StringBuilder form = new StringBuilder();
        StringBuilder columnsVue = new StringBuilder();
        for (CodegenModels.ColumnInfo column : columns) {
            fields.append("  ").append(column.getJavaField()).append("?: ").append(column.getTsType()).append("\n");
            if (isEditableColumn(column)) {
                form.append("  ").append(column.getJavaField()).append(": undefined,\n");
            }
            columnsVue.append("          <el-table-column prop=\"").append(column.getJavaField()).append("\" label=\"")
                    .append(defaultText(column.getColumnComment(), column.getColumnName()))
                    .append("\" min-width=\"140\" show-overflow-tooltip />\n");
        }
        return """
                <template>
                  <section class="view">
                    <el-card shadow="never">
                      <template #header>
                        <div class="section-head">
                          <span>%s</span>
                          <div class="actions">
                            <el-button type="primary" @click="openCreate">新增</el-button>
                            <el-button @click="loadPage">刷新</el-button>
                          </div>
                        </div>
                      </template>
                      <el-table :data="page.records" height="520" stripe>
                %s        <el-table-column label="操作" width="140" fixed="right">
                          <template #default="{ row }">
                            <el-button size="small" @click="openEdit(row)">编辑</el-button>
                            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
                          </template>
                        </el-table-column>
                      </el-table>
                      <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
                        class="pager" layout="total, sizes, prev, pager, next" :total="page.total" @change="loadPage" />
                    </el-card>

                    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑' : '新增'" width="560px">
                      <el-form label-width="96px">
                        <el-form-item v-for="field in editableFields" :key="field" :label="field">
                          <el-input v-model="form[field]" />
                        </el-form-item>
                      </el-form>
                      <template #footer>
                        <el-button @click="dialogVisible = false">取消</el-button>
                        <el-button type="primary" @click="save">保存</el-button>
                      </template>
                    </el-dialog>
                  </section>
                </template>

                <script setup lang="ts">
                import { onMounted, reactive, ref } from 'vue'
                import axios from 'axios'

                interface %s {
                %s}

                const page = reactive({ records: [] as %s[], total: 0, pageNum: 1, pageSize: 20 })
                const dialogVisible = ref(false)
                const editingId = ref<number>()
                const editableFields = %s
                const form = reactive<Record<string, unknown>>({
                %s})

                onMounted(loadPage)

                async function loadPage() {
                  const { data } = await axios.get('/admin/%s', { params: { pageNum: page.pageNum, pageSize: page.pageSize } })
                  Object.assign(page, data.data)
                }

                function openCreate() {
                  editingId.value = undefined
                  editableFields.forEach((field) => (form[field] = undefined))
                  dialogVisible.value = true
                }

                function openEdit(row: %s) {
                  editingId.value = Number(row.id)
                  editableFields.forEach((field) => (form[field] = row[field as keyof %s]))
                  dialogVisible.value = true
                }

                async function save() {
                  if (editingId.value) {
                    await axios.put(`/admin/%s/${editingId.value}`, form)
                  } else {
                    await axios.post('/admin/%s', form)
                  }
                  dialogVisible.value = false
                  await loadPage()
                }

                async function remove(row: %s) {
                  await axios.delete(`/admin/%s/${row.id}`)
                  await loadPage()
                }
                </script>
                """.formatted(defaultText(table.getTableComment(), table.getTableName()), columnsVue,
                entity, fields, entity, editableFieldNames(columns), form, moduleKebab,
                entity, entity, moduleKebab, moduleKebab, entity, moduleKebab);
    }

    private String generateMenuSql(CodegenOptions options) {
        String moduleKebab = toKebab(options.moduleName());
        String permissionPrefix = moduleKebab;
        return """
                INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_path, component, permission, icon, sort_order, visible)
                VALUES
                    (0, 'MENU', '%s管理', '%s', '%sPage', '%s:view', 'Document', 100, 1),
                    ((SELECT id FROM sys_menu WHERE permission = '%s:view'), 'BUTTON', '新增%s', NULL, NULL, '%s:create', NULL, 1, 0),
                    ((SELECT id FROM sys_menu WHERE permission = '%s:view'), 'BUTTON', '编辑%s', NULL, NULL, '%s:update', NULL, 2, 0),
                    ((SELECT id FROM sys_menu WHERE permission = '%s:view'), 'BUTTON', '删除%s', NULL, NULL, '%s:delete', NULL, 3, 0)
                ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), route_path = VALUES(route_path),
                    component = VALUES(component), icon = VALUES(icon), sort_order = VALUES(sort_order), visible = VALUES(visible);
                """.formatted(options.entityName(), moduleKebab, options.entityName(), permissionPrefix,
                permissionPrefix, options.entityName(), permissionPrefix, permissionPrefix, options.entityName(), permissionPrefix,
                permissionPrefix, options.entityName(), permissionPrefix);
    }

    private CodegenModels.GeneratedFile file(String fileName, String filePath, String language, String content) {
        return new CodegenModels.GeneratedFile()
                .setFileName(fileName)
                .setFilePath(filePath)
                .setLanguage(language)
                .setContent(content);
    }

    private static String entityPackage(CodegenOptions options) {
        return options.modulePackage() + ".entity";
    }

    private static String mapperPackage(CodegenOptions options) {
        return options.modulePackage() + ".mapper";
    }

    private static String servicePackage(CodegenOptions options) {
        return options.modulePackage() + ".service";
    }

    private static String controllerPackage(CodegenOptions options) {
        return options.modulePackage() + ".controller";
    }

    private static String packagePath(String packageName) {
        return packageName.replace('.', '/');
    }

    private CodegenOptions options(CodegenModels.PreviewRequest request) {
        String tableName = request.getTableName().trim();
        String entityName = isBlank(request.getEntityName())
                ? CodegenRepository.toCamel(removeTablePrefix(tableName), true)
                : normalizeClassName(request.getEntityName().trim());
        String moduleName = isBlank(request.getModuleName()) ? lowerFirst(entityName) : request.getModuleName().trim();
        String packageName = isBlank(request.getPackageName()) ? "com.framework.admin.generated" : request.getPackageName().trim();
        String author = isBlank(request.getAuthor()) ? "framework" : request.getAuthor().trim();
        return new CodegenOptions(packageName, moduleName, entityName, author);
    }

    private Result<String> validate(CodegenModels.PreviewRequest request) {
        if (request == null || isBlank(request.getTableName())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "表名不能为空");
        }
        if (!isSafeName(request.getTableName().trim())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "表名不合法");
        }
        if (!isBlank(request.getPackageName()) && !SAFE_PACKAGE.matcher(request.getPackageName().trim()).matches()) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "包名不合法");
        }
        if (!isBlank(request.getModuleName()) && !isSafeName(request.getModuleName().trim())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "模块名不合法");
        }
        if (!isBlank(request.getEntityName()) && !isSafeName(request.getEntityName().trim())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "实体名不合法");
        }
        return null;
    }

    private int safePageNum(int pageNum) {
        return pageNum > 0 ? pageNum : DEFAULT_PAGE_NUM;
    }

    private int safePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static boolean isSafeName(String value) {
        return value != null && SAFE_NAME.matcher(value).matches();
    }

    private static boolean hasJavaType(List<CodegenModels.ColumnInfo> columns, String javaType) {
        return columns.stream().anyMatch(column -> javaType.equals(column.getJavaType()));
    }

    private static CodegenModels.ColumnInfo primaryKey(List<CodegenModels.ColumnInfo> columns) {
        return columns.stream()
                .filter(column -> Boolean.TRUE.equals(column.getPrimaryKey()))
                .findFirst()
                .orElse(columns.get(0));
    }

    private static String resultGetter(CodegenModels.ColumnInfo column) {
        String name = column.getColumnName();
        return switch (column.getJavaType()) {
            case "Long" -> "rs.getObject(\"" + name + "\", Long.class)";
            case "Integer" -> "rs.getObject(\"" + name + "\", Integer.class)";
            case "BigDecimal" -> "rs.getBigDecimal(\"" + name + "\")";
            case "Double" -> "rs.getObject(\"" + name + "\", Double.class)";
            case "Float" -> "rs.getObject(\"" + name + "\", Float.class)";
            case "Boolean" -> "rs.getObject(\"" + name + "\", Boolean.class)";
            case "LocalDateTime" -> "rs.getTimestamp(\"" + name + "\") == null ? null : rs.getTimestamp(\"" + name + "\").toLocalDateTime()";
            default -> "rs.getString(\"" + name + "\")";
        };
    }

    private static String joinColumnNames(List<CodegenModels.ColumnInfo> columns) {
        return columns.stream()
                .map(column -> "`" + column.getColumnName() + "`")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static String joinAssignments(List<CodegenModels.ColumnInfo> columns) {
        return columns.stream()
                .map(column -> "`" + column.getColumnName() + "` = ?")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static void appendResultMappings(StringBuilder builder, List<CodegenModels.ColumnInfo> columns) {
        for (int i = 0; i < columns.size(); i++) {
            CodegenModels.ColumnInfo column = columns.get(i);
            builder.append("            @Result(column = \"").append(column.getColumnName())
                    .append("\", property = \"").append(column.getJavaField()).append("\"");
            if (Boolean.TRUE.equals(column.getPrimaryKey())) {
                builder.append(", id = true");
            }
            builder.append(")");
            builder.append(i == columns.size() - 1 ? "\n" : ",\n");
        }
    }

    private static String joinValueReferences(List<CodegenModels.ColumnInfo> columns) {
        return columns.stream()
                .map(column -> "#{" + column.getJavaField() + "}")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static String joinMapperAssignments(List<CodegenModels.ColumnInfo> columns) {
        return columns.stream()
                .map(column -> "`" + column.getColumnName() + "` = #{" + column.getJavaField() + "}")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static String placeholders(int count) {
        if (count <= 0) {
            return "";
        }
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static void appendPreparedSetters(StringBuilder builder, List<CodegenModels.ColumnInfo> columns,
                                              String variable, int indent) {
        String spaces = " ".repeat(indent);
        for (int i = 0; i < columns.size(); i++) {
            CodegenModels.ColumnInfo column = columns.get(i);
            builder.append(spaces).append("ps.setObject(").append(i + 1).append(", ")
                    .append(variable).append(".get").append(upperFirst(column.getJavaField())).append("());\n");
        }
    }

    private static void appendUpdateArgs(StringBuilder builder, List<CodegenModels.ColumnInfo> updateColumns,
                                         CodegenModels.ColumnInfo primaryKey) {
        for (CodegenModels.ColumnInfo column : updateColumns) {
            builder.append(",\n                entity.get").append(upperFirst(column.getJavaField())).append("()");
        }
        builder.append(",\n                id");
    }

    private static String editableFieldNames(List<CodegenModels.ColumnInfo> columns) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (CodegenModels.ColumnInfo column : columns) {
            if (!isEditableColumn(column)) {
                continue;
            }
            if (!first) {
                builder.append(", ");
            }
            builder.append("'").append(column.getJavaField()).append("'");
            first = false;
        }
        builder.append("]");
        return builder.toString();
    }

    private static boolean isInsertableColumn(CodegenModels.ColumnInfo column) {
        return !Boolean.TRUE.equals(column.getAutoIncrement()) && !isDatabaseManagedColumn(column);
    }

    private static boolean isEditableColumn(CodegenModels.ColumnInfo column) {
        return !Boolean.TRUE.equals(column.getPrimaryKey())
                && !Boolean.TRUE.equals(column.getAutoIncrement())
                && !isDatabaseManagedColumn(column);
    }

    private static boolean isDatabaseManagedColumn(CodegenModels.ColumnInfo column) {
        String name = column.getColumnName();
        return "create_time".equals(name) || "update_time".equals(name);
    }

    private static String removeTablePrefix(String tableName) {
        String[] prefixes = {"sys_", "framework_", "t_"};
        for (String prefix : prefixes) {
            if (tableName.startsWith(prefix)) {
                return tableName.substring(prefix.length());
            }
        }
        return tableName;
    }

    private static String normalizeClassName(String value) {
        if (value.contains("_") || value.contains("-") || value.contains(" ")) {
            return CodegenRepository.toCamel(value, true);
        }
        return upperFirst(value);
    }

    private static String toKebab(String value) {
        if (value == null || value.isBlank()) {
            return "module";
        }
        return value.replace('_', '-').replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private static String upperFirst(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String lowerFirst(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CodegenOptions(String packageName, String moduleName, String entityName, String author) {
        private String modulePackage() {
            return packageName + "." + moduleName.toLowerCase(Locale.ROOT);
        }
    }
}
