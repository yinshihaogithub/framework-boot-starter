package com.framework.core.quality;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryEngineeringGuardTest {

    private static final Pattern MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");
    private static final Pattern ARTIFACT_PATTERN = Pattern.compile("<artifactId>([^<]+)</artifactId>");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w.]+);");
    private static final Pattern MQ_PROVIDER_ENUM_PATTERN = Pattern.compile("enum\\s+Provider\\s*\\{([^}]+)}",
            Pattern.DOTALL);
    private static final Pattern CONFIGURATION_PROPERTIES_PREFIX_PATTERN = Pattern.compile(
            "@ConfigurationProperties\\s*\\(\\s*prefix\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern DEFAULT_STRING_DECODING_PATTERN = Pattern.compile(
            "new\\s+String\\s*\\([^,\\n]*\\)(?!\\s*,)");
    private static final Pattern DEFAULT_GET_BYTES_PATTERN = Pattern.compile(
            "\\.getBytes\\s*\\(\\s*\\)");
    private static final Pattern REQUIRE_PERMISSION_PATTERN = Pattern.compile(
            "@RequirePermission\\s*\\(\\s*(?:\\{\\s*)?([^)]*?)(?:\\s*}\\s*)?\\)", Pattern.DOTALL);
    private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern WRITE_MAPPING_WITH_VIEW_PERMISSION_PATTERN = Pattern.compile(
            "@(?:Post|Put|Delete)Mapping[^\\n]*(?:\\R\\s*@[A-Za-z][^\\n]*)*\\R\\s*@RequirePermission\\(\"[^\"]*:view\"\\)");

    private final Path root = repositoryRoot();

    @Test
    void everyModuleHasPomAndReadme() throws Exception {
        for (String module : modules()) {
            assertThat(root.resolve(module).resolve("pom.xml"))
                    .as(module + " must have a Maven descriptor")
                    .exists();
            assertThat(root.resolve(module).resolve("README.md"))
                    .as(module + " must document usage, dependencies and configuration")
                    .exists();
        }
    }

    @Test
    void frameworkModuleReadmesDocumentMavenCoordinates() throws Exception {
        for (String module : modules()) {
            if (!module.startsWith("framework-")) {
                continue;
            }
            String readme = read(root.resolve(module).resolve("README.md"));
            assertThat(readme)
                    .as(module + " README must document Maven coordinates")
                    .contains("<groupId>com.framework</groupId>")
                    .contains("<artifactId>" + module + "</artifactId>");
        }
    }

    @Test
    void dependencyManagementCoversEveryPublishedModule() throws Exception {
        String rootPom = read(root.resolve("pom.xml"));
        List<String> managedArtifacts = artifactsInDependencyManagement(rootPom);

        for (String module : modules()) {
            if ("demo".equals(module)) {
                continue;
            }
            assertThat(managedArtifacts)
                    .as(module + " must be managed by the root dependencyManagement")
                    .contains(module);
        }
    }

    @Test
    void starterAggregatesDefaultRuntimeFrameworkModulesWithoutJob() throws Exception {
        String starterPom = read(root.resolve("framework-starter/pom.xml"));
        List<String> starterDependencies = artifactIds(starterPom);

        for (String module : modules()) {
            if (!module.startsWith("framework-")
                    || "framework-starter".equals(module)
                    || "framework-job".equals(module)) {
                continue;
            }
            assertThat(starterDependencies)
                    .as("framework-starter should aggregate " + module)
                    .contains(module);
        }
        assertThat(starterDependencies)
                .as("framework-starter must not pull in scheduled job infrastructure by default")
                .doesNotContain("framework-job");
    }

    @Test
    void jobModuleUsesXxlJobAndRemainsOptionalFromStarter() throws Exception {
        String rootPom = read(root.resolve("pom.xml"));
        String starterPom = read(root.resolve("framework-starter/pom.xml"));
        String jobPom = read(root.resolve("framework-job/pom.xml"));
        String jobAutoConfiguration = read(root.resolve(
                "framework-job/src/main/java/com/framework/job/config/JobAutoConfiguration.java"));
        List<String> rootModules = modules();
        List<String> managedArtifacts = artifactsInDependencyManagement(rootPom);
        List<String> starterDependencies = artifactIds(starterPom);

        assertThat(rootModules)
                .as("root reactor should include the XXL-JOB integration module")
                .contains("framework-job");
        assertThat(managedArtifacts)
                .as("root dependencyManagement should manage the XXL-JOB integration module")
                .contains("framework-job");
        assertThat(starterDependencies)
                .as("framework-starter should keep XXL-JOB optional instead of pulling external scheduler runtime")
                .doesNotContain("framework-job");
        assertThat(jobPom)
                .as("framework-job must be backed by XXL-JOB")
                .contains("xxl-job-core")
                .doesNotContain("<artifactId>spring-context</artifactId>");
        assertThat(jobAutoConfiguration)
                .as("framework-job must configure XXL-JOB executor instead of Spring Scheduler")
                .contains("XxlJobSpringExecutor")
                .doesNotContain("ThreadPoolTaskScheduler")
                .doesNotContain("@EnableScheduling");
    }

    @Test
    void everyAutoConfigurationIsRegisteredInSpringImports() throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> autoConfigurations = files
                    .filter(path -> path.toString().endsWith("AutoConfiguration.java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .toList();

            assertThat(autoConfigurations).isNotEmpty();
            for (Path autoConfiguration : autoConfigurations) {
                Path moduleRoot = moduleRoot(autoConfiguration);
                Path imports = moduleRoot.resolve("src/main/resources/META-INF/spring/"
                        + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");
                String fqcn = fullyQualifiedClassName(autoConfiguration);

                assertThat(imports)
                        .as(fqcn + " must have an AutoConfiguration.imports file")
                        .exists();
                assertThat(read(imports))
                        .as(fqcn + " must be registered for Spring Boot 3 auto-configuration")
                        .contains(fqcn);
            }
        }
    }

    @Test
    void everyAutoConfigurationHasContextRunnerCoverage() throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> autoConfigurations = files
                    .filter(path -> path.toString().endsWith("AutoConfiguration.java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .toList();

            for (Path autoConfiguration : autoConfigurations) {
                Path moduleRoot = moduleRoot(autoConfiguration);
                String className = autoConfiguration.getFileName().toString().replace(".java", "");
                try (Stream<Path> tests = Files.walk(moduleRoot.resolve("src/test/java"))) {
                    List<String> testNames = tests
                            .filter(path -> path.toString().endsWith(".java"))
                            .map(path -> path.getFileName().toString())
                            .toList();
                    assertThat(testNames)
                            .as(className + " must have an auto-configuration context runner test")
                            .anySatisfy(name -> assertThat(name)
                                    .matches(className + "(Imports)?Test\\.java"));
                }
            }
        }
    }

    @Test
    void configurationPropertiesUseFrameworkNamespaceAndAreDocumented() throws Exception {
        Map<String, Set<String>> prefixesByModule = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> javaFiles = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .toList();

            for (Path javaFile : javaFiles) {
                Matcher matcher = CONFIGURATION_PROPERTIES_PREFIX_PATTERN.matcher(read(javaFile));
                while (matcher.find()) {
                    String prefix = matcher.group(1);
                    assertThat(prefix)
                            .as(javaFile + " configuration prefix must live under framework.*")
                            .startsWith("framework.");
                    String module = root.relativize(moduleRoot(javaFile)).toString();
                    prefixesByModule.computeIfAbsent(module, ignored -> new LinkedHashSet<>()).add(prefix);
                }
            }
        }

        assertThat(prefixesByModule).isNotEmpty();
        for (Map.Entry<String, Set<String>> entry : prefixesByModule.entrySet()) {
            String readme = read(root.resolve(entry.getKey()).resolve("README.md"));
            for (String prefix : entry.getValue()) {
                assertThat(readme)
                        .as(entry.getKey() + " README must document " + prefix)
                        .contains(prefix);
            }
        }
    }

    @Test
    void rootBuildGeneratesSpringConfigurationMetadata() throws Exception {
        String rootPom = read(root.resolve("pom.xml"));

        assertThat(rootPom)
                .contains("<artifactId>spring-boot-configuration-processor</artifactId>")
                .contains("<annotationProcessorPaths>");
    }

    @Test
    void mysqlBackedModulesShipInitializationScripts() {
        Map<String, String> scripts = new LinkedHashMap<>();
        scripts.put("framework-log", "src/main/resources/db/mysql/sys_operation_log.sql");
        scripts.put("framework-local-message", "src/main/resources/db/mysql/framework_local_message.sql");
        scripts.put("framework-mq", "src/main/resources/db/mysql/framework_mq.sql");
        scripts.put("admin-service", "src/main/resources/db/mysql/admin_service.sql");

        for (Map.Entry<String, String> entry : scripts.entrySet()) {
            Path script = root.resolve(entry.getKey()).resolve(entry.getValue());
            assertThat(script)
                    .as(entry.getKey() + " must ship a MySQL initialization script")
                    .exists();
            assertThat(readUnchecked(script))
                    .contains("CREATE TABLE IF NOT EXISTS")
                    .contains("ENGINE=InnoDB")
                    .contains("DEFAULT CHARSET=utf8mb4");
        }

        assertThat(root.resolve("sql/mysql/framework_boot_starter_init.sql"))
                .as("root project must provide an aggregate MySQL initialization script")
                .exists();
    }

    @Test
    void jobModuleShipsOptionalXxlJobAdminMysqlScript() throws Exception {
        Path script = root.resolve("framework-job/src/main/resources/db/mysql/xxl_job_admin.sql");

        assertThat(script)
                .as("framework-job must ship the optional XXL-JOB admin MySQL script")
                .exists();
        assertThat(read(script))
                .contains("CREATE TABLE IF NOT EXISTS xxl_job_info")
                .contains("CREATE TABLE IF NOT EXISTS xxl_job_log")
                .contains("CREATE TABLE IF NOT EXISTS xxl_job_group")
                .contains("CREATE TABLE IF NOT EXISTS xxl_job_user")
                .contains("ENGINE=InnoDB")
                .contains("DEFAULT CHARSET=utf8mb4");
    }

    @Test
    void aggregateMysqlScriptIncludesAllFrameworkTables() throws Exception {
        String aggregateScript = read(root.resolve("sql/mysql/framework_boot_starter_init.sql"));

        assertThat(aggregateScript)
                .contains("CREATE TABLE IF NOT EXISTS sys_operation_log")
                .contains("CREATE TABLE IF NOT EXISTS framework_local_message")
                .contains("CREATE TABLE IF NOT EXISTS framework_mq_failed_message");
    }

    @Test
    void aggregateMysqlScriptIncludesAdminServiceTables() throws Exception {
        String aggregateScript = read(root.resolve("sql/mysql/framework_boot_starter_init.sql"));
        String adminServiceScript = read(root.resolve("admin-service/src/main/resources/db/mysql/admin_service.sql"));

        assertThat(aggregateScript)
                .contains("CREATE TABLE IF NOT EXISTS sys_tenant")
                .contains("CREATE TABLE IF NOT EXISTS sys_dept")
                .contains("CREATE TABLE IF NOT EXISTS sys_user")
                .contains("CREATE TABLE IF NOT EXISTS sys_role")
                .contains("CREATE TABLE IF NOT EXISTS sys_menu")
                .contains("CREATE TABLE IF NOT EXISTS sys_dict_type")
                .contains("CREATE TABLE IF NOT EXISTS sys_dict_item")
                .contains("CREATE TABLE IF NOT EXISTS sys_config")
                .contains("CREATE TABLE IF NOT EXISTS sys_login_log")
                .contains("CREATE TABLE IF NOT EXISTS framework_notify_template")
                .contains("CREATE TABLE IF NOT EXISTS framework_notify_record")
                .contains("CREATE TABLE IF NOT EXISTS framework_excel_task")
                .contains("CREATE TABLE IF NOT EXISTS framework_excel_error");
        assertThat(adminServiceScript)
                .as("admin-service must ship its own package-level MySQL initialization script")
                .contains("CREATE TABLE IF NOT EXISTS sys_tenant")
                .contains("CREATE TABLE IF NOT EXISTS sys_user")
                .contains("CREATE TABLE IF NOT EXISTS framework_notify_template")
                .contains("CREATE TABLE IF NOT EXISTS framework_excel_task")
                .contains("INSERT INTO sys_user")
                .contains("INSERT INTO sys_menu");
    }

    @Test
    void adminServiceLoadsPackageLevelMysqlScriptOnStartup() throws Exception {
        String applicationYml = read(root.resolve("admin-service/src/main/resources/application.yml"));

        assertThat(applicationYml)
                .contains("optional:classpath:db/mysql/admin_service.sql")
                .contains("optional:file:./sql/mysql/framework_boot_starter_init.sql");
    }

    @Test
    void adminServiceMainCodeUsesMapperStyleInsteadOfJdbcTemplate() throws Exception {
        Path adminMain = root.resolve("admin-service/src/main/java");
        try (Stream<Path> files = Files.walk(adminMain)) {
            List<Path> javaFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            assertThat(javaFiles).isNotEmpty();
            for (Path javaFile : javaFiles) {
                String content = read(javaFile);
                assertThat(content)
                        .as(javaFile + " must keep admin-service on annotation Mapper repositories")
                        .doesNotContain("JdbcTemplate")
                        .doesNotContain("NamedParameterJdbcTemplate")
                        .doesNotContain("GeneratedKeyHolder");
            }
        }
    }

    @Test
    void adminServiceUsesManualLocalTransactionsOnly() throws Exception {
        Path adminMain = root.resolve("admin-service/src/main/java");
        try (Stream<Path> files = Files.walk(adminMain)) {
            List<Path> javaFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            assertThat(javaFiles).isNotEmpty();
            for (Path javaFile : javaFiles) {
                String content = read(javaFile);
                assertThat(content)
                        .as(javaFile + " must use manual local transactions instead of declarative transactions")
                        .doesNotContain("@Transactional")
                        .doesNotContain("org.springframework.transaction.annotation.Transactional");
            }
        }

        assertThat(read(root.resolve("admin-service/src/main/java/com/framework/admin/system/AdminSystemRepository.java")))
                .as("multi-table admin writes must keep an explicit local transaction boundary")
                .contains("TransactionTemplate")
                .contains("inTransaction(");
    }

    @Test
    void mqProvidersAreLimitedToRabbitKafkaAndRocket() throws Exception {
        String source = read(root.resolve("framework-mq/src/main/java/com/framework/mq/config/MqProperties.java"));
        Matcher matcher = MQ_PROVIDER_ENUM_PATTERN.matcher(source);

        assertThat(matcher.find()).as("MqProperties.Provider enum must exist").isTrue();
        List<String> providers = Stream.of(matcher.group(1).split(","))
                .map(String::trim)
                .map(value -> value.replaceAll("//.*", "").trim())
                .filter(value -> !value.isBlank())
                .toList();

        assertThat(providers).containsExactly("RABBIT", "KAFKA", "ROCKET");
    }

    @Test
    void codegenModuleIsRemovedFromTheScaffold() throws Exception {
        assertThat(modules())
                .as("codegen is intentionally not part of this scaffold")
                .noneMatch(module -> module.toLowerCase().contains("codegen"));

        try (Stream<Path> files = Files.walk(root)) {
            List<Path> codegenPaths = files
                    .filter(path -> !isIgnoredRepositoryPath(path))
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> path.getFileName().toString().toLowerCase().contains("codegen"))
                    .toList();

            assertThat(codegenPaths)
                    .as("codegen directories/files should stay removed")
                    .isEmpty();
        }
    }

    @Test
    void adminExcelManagementDoesNotExposeDemoRoutes() throws Exception {
        List<Path> guardedFiles = List.of(
                root.resolve("admin-service/src/main/java/com/framework/admin/excel/ExcelAdminController.java"),
                root.resolve("admin-service/src/main/java/com/framework/admin/excel/ExcelAdminService.java"),
                root.resolve("frontend/admin-web/src/api/client.ts"),
                root.resolve("frontend/admin-web/src/App.vue"),
                root.resolve("admin-service/README.md"));
        List<String> forbiddenTerms = List.of(
                "demo-export",
                "demo-failure",
                "demoExport",
                "demoFailure",
                "createDemoExport",
                "createDemoFailure",
                "DemoExcel");

        for (Path file : guardedFiles) {
            String content = read(file);
            for (String forbiddenTerm : forbiddenTerms) {
                assertThat(content)
                        .as(file + " must expose engineering-grade Excel management routes")
                        .doesNotContain(forbiddenTerm);
            }
        }
    }

    @Test
    void adminControllersUseExplicitFailureCodes() throws Exception {
        try (Stream<Path> files = Files.walk(root.resolve("admin-service/src/main/java"))) {
            List<Path> controllerFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .toList();

            assertThat(controllerFiles).isNotEmpty();
            for (Path file : controllerFiles) {
                assertThat(read(file))
                        .as(file + " must not use Result.fail(String), because it defaults to 500")
                        .doesNotContain("Result.fail(\"");
            }
        }
    }

    @Test
    void frontendSourceDoesNotHardcodeDefaultAdminPassword() throws Exception {
        Path frontendSource = root.resolve("frontend/admin-web/src");
        try (Stream<Path> files = Files.walk(frontendSource)) {
            List<Path> sourceFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".vue") || path.toString().endsWith(".ts"))
                    .toList();

            assertThat(sourceFiles).isNotEmpty();
            for (Path file : sourceFiles) {
                assertThat(read(file))
                        .as(file + " must not embed the seeded default admin password")
                        .doesNotContain("Admin@123");
            }
        }
    }

    @Test
    void adminManagementControllersDeclareBackendPermissions() throws Exception {
        Path adminMain = root.resolve("admin-service/src/main/java/com/framework/admin");
        try (Stream<Path> files = Files.walk(adminMain)) {
            List<Path> controllerFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .filter(path -> !path.toString().contains("/auth/"))
                    .toList();

            assertThat(controllerFiles).isNotEmpty();
            for (Path file : controllerFiles) {
                assertThat(read(file))
                        .as(file + " must enforce backend permissions instead of relying on frontend menus")
                        .contains("RequirePermission");
            }
        }
    }

    @Test
    void adminBackendPermissionsAreSeededInMysqlScripts() throws Exception {
        Set<String> permissions = adminBackendPermissions();
        String adminServiceScript = read(root.resolve("admin-service/src/main/resources/db/mysql/admin_service.sql"));
        String aggregateScript = read(root.resolve("sql/mysql/framework_boot_starter_init.sql"));

        assertThat(permissions).isNotEmpty();
        for (String permission : permissions) {
            assertThat(adminServiceScript)
                    .as("admin-service SQL must seed backend permission " + permission)
                    .contains("'" + permission + "'");
            assertThat(aggregateScript)
                    .as("aggregate SQL must seed backend permission " + permission)
                    .contains("'" + permission + "'");
        }
    }

    @Test
    void adminOnlineSessionManagementIsExposedEndToEnd() throws Exception {
        String sessionManager = read(root.resolve(
                "framework-auth/src/main/java/com/framework/auth/service/SessionManager.java"));
        String controller = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/session/AdminSessionController.java"));
        String service = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/session/AdminSessionService.java"));
        String client = read(root.resolve("frontend/admin-web/src/api/client.ts"));
        String app = read(root.resolve("frontend/admin-web/src/App.vue"));
        String adminServiceScript = read(root.resolve("admin-service/src/main/resources/db/mysql/admin_service.sql"));
        String aggregateScript = read(root.resolve("sql/mysql/framework_boot_starter_init.sql"));

        assertThat(sessionManager)
                .contains("public List<OnlineSession> listOnlineSessions()")
                .contains("redis.getExpire(sessionKey, TimeUnit.SECONDS)")
                .contains("public record OnlineSession");
        assertThat(controller)
                .contains("@RequestMapping(\"/admin/sessions\")")
                .contains("@RequirePermission(\"session:view\")")
                .contains("@RequirePermission(\"session:kick\")")
                .contains("@DeleteMapping(\"/{userId}/{deviceId}\")");
        assertThat(service)
                .contains("sessionManager.forceLogout(userId, deviceId)")
                .contains("auditService.success")
                .contains("不能强制下线当前会话");
        assertThat(client)
                .contains("export interface OnlineSession")
                .contains("sessions: () => getData<OnlineSession[]>('/admin/sessions')")
                .contains("kickSession:");
        assertThat(app)
                .contains("activeView === 'sessions'")
                .contains("Sessions: 'sessions'")
                .contains("session:kick")
                .contains("loadSessions");
        assertThat(adminServiceScript)
                .contains("'在线会话'")
                .contains("'session:view'")
                .contains("'session:kick'");
        assertThat(aggregateScript)
                .contains("'在线会话'")
                .contains("'session:view'")
                .contains("'session:kick'");
    }

    @Test
    void adminUserLoginLockManagementIsExposedEndToEnd() throws Exception {
        String loginSecurityService = read(root.resolve(
                "framework-auth/src/main/java/com/framework/auth/service/LoginSecurityService.java"));
        String models = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/system/AdminSystemModels.java"));
        String controller = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/system/AdminSystemController.java"));
        String service = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/system/AdminSystemService.java"));
        String client = read(root.resolve("frontend/admin-web/src/api/client.ts"));
        String app = read(root.resolve("frontend/admin-web/src/App.vue"));
        String adminServiceScript = read(root.resolve("admin-service/src/main/resources/db/mysql/admin_service.sql"));
        String aggregateScript = read(root.resolve("sql/mysql/framework_boot_starter_init.sql"));

        assertThat(loginSecurityService)
                .contains("public LoginSecurityStatus getStatus(String username)")
                .contains("public record LoginSecurityStatus");
        assertThat(models)
                .contains("private Long loginFailCount")
                .contains("private Boolean loginLocked")
                .contains("private Long loginLockTtlMinutes");
        assertThat(controller)
                .contains("@PutMapping(\"/users/{id}/unlock\")")
                .contains("@RequirePermission(\"system:user:unlock\")");
        assertThat(service)
                .contains("ObjectProvider<LoginSecurityService>")
                .contains("enrichLoginSecurity")
                .contains("unlockUser")
                .contains("loginSecurityService.unlock(user.getUsername())")
                .contains("setLoginLocked");
        assertThat(client)
                .contains("loginFailCount?: number")
                .contains("unlockUser:");
        assertThat(app)
                .contains("登录安全")
                .contains("system:user:unlock")
                .contains("api.unlockUser(row.id)");
        assertThat(adminServiceScript)
                .contains("'解锁用户'")
                .contains("'system:user:unlock'");
        assertThat(aggregateScript)
                .contains("'解锁用户'")
                .contains("'system:user:unlock'");
    }

    @Test
    void adminFileManagementIsExposedEndToEnd() throws Exception {
        String controller = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/file/FileAdminController.java"));
        String service = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/file/FileAdminService.java"));
        String mapper = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/file/FileAdminMapper.java"));
        String client = read(root.resolve("frontend/admin-web/src/api/client.ts"));
        String app = read(root.resolve("frontend/admin-web/src/App.vue"));
        String adminServiceScript = read(root.resolve("admin-service/src/main/resources/db/mysql/admin_service.sql"));
        String aggregateScript = read(root.resolve("sql/mysql/framework_boot_starter_init.sql"));

        assertThat(controller)
                .contains("@RequestMapping(\"/admin/files\")")
                .contains("@RequirePermission(\"file:view\")")
                .contains("@RequirePermission(\"file:upload\")")
                .contains("@RequirePermission(\"file:delete\")")
                .contains("@GetMapping(\"/{id}/download\")");
        assertThat(service)
                .contains("ObjectProvider<FileStorageService>")
                .contains("storageService.store")
                .contains("storageService.load")
                .contains("storageService.delete")
                .contains("auditService.success");
        assertThat(mapper)
                .contains("@Mapper")
                .contains("framework_file_record")
                .contains("INSERT INTO framework_file_record")
                .contains("UPDATE framework_file_record");
        assertThat(client)
                .contains("export interface FileRecord")
                .contains("uploadFile:")
                .contains("downloadFile:")
                .contains("deleteFile:");
        assertThat(app)
                .contains("activeView === 'files'")
                .contains("Files: 'files'")
                .contains("file:upload")
                .contains("file:delete")
                .contains("api.uploadFile")
                .contains("api.downloadFile");
        assertThat(adminServiceScript)
                .contains("CREATE TABLE IF NOT EXISTS framework_file_record")
                .contains("'文件中心'")
                .contains("'file:view'")
                .contains("'file:upload'")
                .contains("'file:delete'");
        assertThat(aggregateScript)
                .contains("CREATE TABLE IF NOT EXISTS framework_file_record")
                .contains("'文件中心'")
                .contains("'file:view'")
                .contains("'file:upload'")
                .contains("'file:delete'");
    }

    @Test
    void adminCurrentUserCanChangeOwnPasswordEndToEnd() throws Exception {
        String controller = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/auth/AdminAuthController.java"));
        String service = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/auth/AdminAuthService.java"));
        String repository = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/system/AdminSystemRepository.java"));
        String mapper = read(root.resolve(
                "admin-service/src/main/java/com/framework/admin/system/AdminSystemMapper.java"));
        String client = read(root.resolve("frontend/admin-web/src/api/client.ts"));
        String app = read(root.resolve("frontend/admin-web/src/App.vue"));
        String adminServiceScript = read(root.resolve("admin-service/src/main/resources/db/mysql/admin_service.sql"));

        assertThat(controller)
                .contains("@PutMapping(\"/password\")")
                .contains("ChangePasswordRequest")
                .contains("oldPassword")
                .contains("newPassword");
        assertThat(service)
                .contains("PasswordValidator.validateStrong(request.getNewPassword())")
                .contains("PasswordUtils.verify(request.getOldPassword(), user.getPasswordHash())")
                .contains("新密码不能与原密码相同")
                .contains("updateConfigValue(\"admin.default.password.changed\", \"true\")")
                .contains("sessionManager.forceLogoutAll(user.getId())")
                .contains("密码已修改，请重新登录");
        assertThat(repository)
                .contains("public void updateConfigValue(String configKey, String configValue)");
        assertThat(mapper)
                .contains("UPDATE sys_config")
                .contains("WHERE config_key = #{configKey}");
        assertThat(client)
                .contains("changePassword:")
                .contains("putData<string>('/admin/auth/password'");
        assertThat(app)
                .contains("changePasswordVisible")
                .contains("openChangePassword")
                .contains("changeOwnPassword")
                .contains("两次输入的新密码不一致")
                .contains("clearToken()");
        assertThat(adminServiceScript)
                .contains("'admin.default.password.changed'")
                .contains("'生产环境应强制修改默认管理员密码'");
    }

    @Test
    void adminWriteEndpointsUseActionPermissionsInsteadOfViewPermissions() throws Exception {
        Path adminMain = root.resolve("admin-service/src/main/java/com/framework/admin");
        try (Stream<Path> files = Files.walk(adminMain)) {
            List<Path> controllerFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .filter(path -> !path.toString().contains("/auth/"))
                    .toList();

            assertThat(controllerFiles).isNotEmpty();
            for (Path file : controllerFiles) {
                assertThat(WRITE_MAPPING_WITH_VIEW_PERMISSION_PATTERN.matcher(read(file)).find())
                        .as(file + " write endpoints must use create/update/delete/action permissions")
                        .isFalse();
            }
        }
    }

    @Test
    void adminPermissionChangingOperationsInvalidatePermissionCache() throws Exception {
        String service = read(root.resolve("admin-service/src/main/java/com/framework/admin/system/AdminSystemService.java"));
        String repository = read(root.resolve("admin-service/src/main/java/com/framework/admin/system/AdminSystemRepository.java"));
        String mapper = read(root.resolve("admin-service/src/main/java/com/framework/admin/system/AdminSystemMapper.java"));
        String sessionManager = read(root.resolve("framework-auth/src/main/java/com/framework/auth/service/SessionManager.java"));

        assertThat(service)
                .as("system management writes must invalidate Redis-backed permission cache")
                .contains("PermissionCacheService")
                .contains("SessionManager")
                .contains("refreshPermissionCache(userId)")
                .contains("refreshPermissionCache(id)")
                .contains("repository.listUserIdsByRoleId(id)")
                .contains("refreshPermissionCache(affectedUserIds)")
                .contains("clearPermissionCache()")
                .contains("cacheService.refreshBatch")
                .contains("cacheService.clearAll")
                .contains("forceLogoutUser(id)")
                .contains("forceLogoutUsers(affectedUserIds)")
                .contains("forceLogoutAllUsers()");
        assertThat(repository)
                .as("role-level permission changes need the affected users")
                .contains("listUserIdsByRoleId");
        assertThat(mapper)
                .as("role-level permission changes need an annotation mapper query for affected users")
                .contains("SELECT user_id")
                .contains("FROM sys_user_role")
                .contains("WHERE role_id = #{roleId}");
        assertThat(sessionManager)
                .as("session permissions are restored from Redis and must be invalidated after permission model changes")
                .contains("public void forceLogoutAll(Long userId)")
                .contains("public void forceLogoutAll()")
                .contains("ScanOptions.scanOptions()");
    }

    @Test
    void adminSessionsAreRevalidatedAgainstCurrentAccountStatus() throws Exception {
        String tokenFilter = read(root.resolve("framework-auth/src/main/java/com/framework/auth/filter/TokenAuthFilter.java"));
        String authAutoConfiguration = read(root.resolve("framework-auth/src/main/java/com/framework/auth/config/AuthAutoConfiguration.java"));
        String adminValidator = read(root.resolve("admin-service/src/main/java/com/framework/admin/auth/AdminLoginUserValidator.java"));

        assertThat(tokenFilter)
                .as("restored Redis sessions must be revalidated before injecting UserContextHolder")
                .contains("LoginUserValidator")
                .contains("isLoginUserValid(user)")
                .contains("sessionManager.forceLogoutAll(user.getUserId())");
        assertThat(authAutoConfiguration)
                .as("auth auto-configuration must pass all LoginUserValidator beans into TokenAuthFilter")
                .contains("ObjectProvider<LoginUserValidator>")
                .contains("loginUserValidators.orderedStream().toList()");
        assertThat(adminValidator)
                .as("admin-service must reject disabled or deleted accounts even when Redis sessions still exist")
                .contains("implements LoginUserValidator")
                .contains("\"ENABLED\".equals(adminUser.getStatus())");
    }

    @Test
    void sourceConfigurationDoesNotUseH2AsDefaultDatabase() throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> sourceFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> path.toString().endsWith(".xml")
                            || path.toString().endsWith(".yml")
                            || path.toString().endsWith(".yaml")
                            || path.toString().endsWith(".md"))
                    .toList();

            for (Path file : sourceFiles) {
                String content = read(file);
                assertThat(content)
                        .as(file + " must not use H2 as the default database")
                        .doesNotContain("jdbc:h2")
                        .doesNotContain("com.h2database")
                        .doesNotContain("org.h2.Driver")
                        .doesNotContain("db-type: H2");
            }
        }
    }

    @Test
    void productionSourceSpecifiesCharsetWhenEncodingOrDecodingBytes() throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> sourceFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            for (Path file : sourceFiles) {
                String content = read(file);
                assertThat(DEFAULT_STRING_DECODING_PATTERN.matcher(content).find())
                        .as(file + " must decode bytes with an explicit charset")
                        .isFalse();
                assertThat(DEFAULT_GET_BYTES_PATTERN.matcher(content).find())
                        .as(file + " must encode strings with an explicit charset")
                        .isFalse();
            }
        }
    }

    @Test
    void engineeringDocsExistAndUseManualLocalTransactionLanguage() throws Exception {
        assertThat(root.resolve("docs/ENGINEERING_STANDARD.md")).exists();
        assertThat(root.resolve("docs/MODULE_MATRIX.md")).exists();

        Path consistencyDesign = root.resolve("docs/ORDER_FINAL_CONSISTENCY_DESIGN.md");
        assertThat(consistencyDesign)
                .as("distributed workflow design must document local-transaction consistency")
                .exists();
        assertThat(read(consistencyDesign))
                .as("engineering docs must match the scaffold's manual local transaction stance")
                .contains("TransactionTemplate")
                .doesNotContain("@Transactional")
                .doesNotContain("org.springframework.transaction.annotation.Transactional");
    }

    private List<String> modules() throws IOException {
        Matcher matcher = MODULE_PATTERN.matcher(read(root.resolve("pom.xml")));
        List<String> modules = new ArrayList<>();
        while (matcher.find()) {
            modules.add(matcher.group(1));
        }
        return modules;
    }

    private List<String> artifactsInDependencyManagement(String pom) {
        int start = pom.indexOf("<dependencyManagement>");
        int end = pom.indexOf("</dependencyManagement>");
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return artifactIds(pom.substring(start, end));
    }

    private List<String> artifactIds(String pom) {
        Matcher matcher = ARTIFACT_PATTERN.matcher(pom);
        List<String> artifacts = new ArrayList<>();
        while (matcher.find()) {
            artifacts.add(matcher.group(1));
        }
        return artifacts;
    }

    private Set<String> adminBackendPermissions() throws IOException {
        Set<String> permissions = new LinkedHashSet<>();
        Path adminMain = root.resolve("admin-service/src/main/java/com/framework/admin");
        try (Stream<Path> files = Files.walk(adminMain)) {
            List<Path> javaFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            for (Path javaFile : javaFiles) {
                Matcher annotationMatcher = REQUIRE_PERMISSION_PATTERN.matcher(read(javaFile));
                while (annotationMatcher.find()) {
                    Matcher permissionMatcher = QUOTED_STRING_PATTERN.matcher(annotationMatcher.group(1));
                    while (permissionMatcher.find()) {
                        permissions.add(permissionMatcher.group(1));
                    }
                }
            }
        }
        return permissions;
    }

    private String fullyQualifiedClassName(Path javaFile) throws IOException {
        String source = read(javaFile);
        Matcher matcher = PACKAGE_PATTERN.matcher(source);
        assertThat(matcher.find()).as(javaFile + " must declare a package").isTrue();
        String className = javaFile.getFileName().toString().replace(".java", "");
        return matcher.group(1) + "." + className;
    }

    private Path moduleRoot(Path path) {
        Path relative = root.relativize(path);
        return root.resolve(relative.getName(0));
    }

    private boolean isIgnoredRepositoryPath(Path path) {
        String value = path.toString();
        return value.contains("/.git/")
                || value.contains("/target/")
                || value.contains("/node_modules/")
                || value.contains("/dist/");
    }

    private Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath();
        if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("framework-core"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.exists(parent.resolve("pom.xml")) && Files.exists(parent.resolve("framework-core"))) {
            return parent;
        }
        throw new IllegalStateException("Cannot locate repository root from " + current);
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String readUnchecked(Path path) {
        try {
            return read(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
