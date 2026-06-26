package com.framework.admin.codegen;

import com.framework.admin.audit.AdminAuditService;
import com.framework.core.result.PageResult;
import com.framework.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CodegenControllerTest {

    @Test
    void tablesUsesSafePaginationDefaults() {
        CodegenController controller = controller(repository());

        Result<PageResult<CodegenModels.TableInfo>> result = controller.tables(null, -1, 0);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getPageNum()).isEqualTo(1);
        assertThat(result.getData().getPageSize()).isEqualTo(20);
        assertThat(result.getData().getRecords()).hasSize(1);
    }

    @Test
    void columnsRejectsUnsafeTableName() {
        CodegenController controller = controller(repository());

        Result<List<CodegenModels.ColumnInfo>> result = controller.columns("sys_user;drop");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("表名不合法");
    }

    @Test
    void previewGeneratesExpectedFilesAndExcludesManagedFields() {
        CodegenModels.PreviewRequest request = new CodegenModels.PreviewRequest();
        request.setTableName("sys_user");
        request.setPackageName("com.framework.admin.generated");
        request.setModuleName("userDemo");
        request.setEntityName("UserDemo");
        CodegenController controller = controller(repository());

        Result<CodegenModels.PreviewResponse> result = controller.preview(request, null);

        assertThat(result.isSuccess()).isTrue();
        List<CodegenModels.GeneratedFile> files = result.getData().getFiles();
        assertThat(files).extracting(CodegenModels.GeneratedFile::getFileName)
                .containsExactly("UserDemo.java", "UserDemoMapper.java", "UserDemoService.java",
                        "UserDemoController.java", "UserDemoPage.vue", "user-demo_menu.sql");
        assertThat(files).extracting(CodegenModels.GeneratedFile::getFilePath)
                .containsExactly(
                        "src/main/java/com/framework/admin/generated/userdemo/entity/UserDemo.java",
                        "src/main/java/com/framework/admin/generated/userdemo/mapper/UserDemoMapper.java",
                        "src/main/java/com/framework/admin/generated/userdemo/service/UserDemoService.java",
                        "src/main/java/com/framework/admin/generated/userdemo/controller/UserDemoController.java",
                        "frontend/admin-web/src/views/user-demo/UserDemoPage.vue",
                        "sql/mysql/user-demo_menu.sql");
        assertThat(file(files, "UserDemo.java").getContent())
                .contains("package com.framework.admin.generated.userdemo.entity;")
                .contains("public class UserDemo");
        assertThat(file(files, "UserDemoController.java").getContent())
                .contains("package com.framework.admin.generated.userdemo.controller;")
                .contains("import com.framework.admin.generated.userdemo.entity.UserDemo;")
                .contains("import com.framework.admin.generated.userdemo.service.UserDemoService;")
                .contains("@RequestMapping(\"/admin/user-demo\")")
                .contains("private final UserDemoService service")
                .contains("return Result.success(service.page(pageNum, pageSize))");
        assertThat(file(files, "user-demo_menu.sql").getContent()).contains("'user-demo:view'");

        String mapperContent = file(files, "UserDemoMapper.java").getContent();
        assertThat(mapperContent)
                .contains("package com.framework.admin.generated.userdemo.mapper;")
                .contains("import com.framework.admin.generated.userdemo.entity.UserDemo;")
                .contains("@Mapper")
                .contains("@Select(\"\"\"")
                .contains("@Insert(\"\"\"")
                .contains("@Update(\"\"\"")
                .contains("INSERT INTO `sys_user` (`tenant_id`, `username`, `status`)")
                .contains("VALUES (#{tenantId}, #{username}, #{status})");
        assertThat(mapperContent).doesNotContain("JdbcTemplate");
        assertThat(mapperContent).doesNotContain("`create_time` = #{createTime}");
        assertThat(mapperContent).doesNotContain("`update_time` = #{updateTime}");

        String serviceContent = file(files, "UserDemoService.java").getContent();
        assertThat(serviceContent)
                .contains("package com.framework.admin.generated.userdemo.service;")
                .contains("import com.framework.admin.generated.userdemo.entity.UserDemo;")
                .contains("import com.framework.admin.generated.userdemo.mapper.UserDemoMapper;")
                .contains("private final TransactionTemplate transactionTemplate")
                .contains("transactionTemplate.execute(status ->")
                .contains("transactionTemplate.executeWithoutResult(status ->")
                .contains("mapper.insert(request)")
                .contains("mapper.update(request)");
        assertThat(serviceContent).doesNotContain("@Transactional");

        String vueContent = file(files, "UserDemoPage.vue").getContent();
        assertThat(vueContent).contains("interface UserDemo");
        assertThat(vueContent).contains("const editableFields = ['tenantId', 'username', 'status']");
        assertThat(vueContent).doesNotContain("createTime: undefined");
        assertThat(vueContent).doesNotContain("updateTime: undefined");
    }

    @Test
    void previewRejectsInvalidPackageName() {
        CodegenModels.PreviewRequest request = new CodegenModels.PreviewRequest();
        request.setTableName("sys_user");
        request.setPackageName("com.framework.admin..bad");
        CodegenController controller = controller(repository());

        Result<CodegenModels.PreviewResponse> result = controller.preview(request, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("包名不合法");
    }

    private static CodegenModels.GeneratedFile file(List<CodegenModels.GeneratedFile> files, String fileName) {
        return files.stream()
                .filter(file -> fileName.equals(file.getFileName()))
                .findFirst()
                .orElseThrow();
    }

    private static CodegenController controller(CodegenRepository repository) {
        return new CodegenController(repository, auditService());
    }

    private static CodegenRepository repository() {
        return new CodegenRepository(null) {
            @Override
            public List<CodegenModels.TableInfo> listTables(String keyword, int pageNum, int pageSize) {
                return List.of(table());
            }

            @Override
            public long countTables(String keyword) {
                return 1;
            }

            @Override
            public Optional<CodegenModels.TableInfo> findTable(String tableName) {
                return "sys_user".equals(tableName) ? Optional.of(table()) : Optional.empty();
            }

            @Override
            public List<CodegenModels.ColumnInfo> listColumns(String tableName) {
                return List.of(
                        column("id", "bigint", "Long", "id", true, true),
                        column("tenant_id", "bigint", "Long", "tenantId", false, false),
                        column("username", "varchar(64)", "String", "username", false, false),
                        column("status", "varchar(32)", "String", "status", false, false),
                        column("create_time", "datetime", "LocalDateTime", "createTime", false, false),
                        column("update_time", "datetime", "LocalDateTime", "updateTime", false, false));
            }
        };
    }

    private static CodegenModels.TableInfo table() {
        return new CodegenModels.TableInfo()
                .setTableName("sys_user")
                .setTableComment("后台用户表")
                .setTableRows(1L)
                .setEngine("InnoDB");
    }

    private static CodegenModels.ColumnInfo column(String columnName, String columnType, String javaType,
                                                  String javaField, boolean primaryKey, boolean autoIncrement) {
        return new CodegenModels.ColumnInfo()
                .setColumnName(columnName)
                .setColumnType(columnType)
                .setDataType(columnType.split("\\(")[0])
                .setJavaType(javaType)
                .setJavaField(javaField)
                .setTsType("Long".equals(javaType) ? "number" : "string")
                .setPrimaryKey(primaryKey)
                .setAutoIncrement(autoIncrement)
                .setNullable(false)
                .setOrdinalPosition(1);
    }

    private static AdminAuditService auditService() {
        return new AdminAuditService(null, null) {
            @Override
            public void success(HttpServletRequest request, String module, String action, String operationType, Object params) {
            }

            @Override
            public void failure(HttpServletRequest request, String module, String action, String operationType,
                                Object params, Exception exception) {
            }
        };
    }
}
