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
    void engineeringDocsExist() {
        assertThat(root.resolve("docs/ENGINEERING_STANDARD.md")).exists();
        assertThat(root.resolve("docs/MODULE_MATRIX.md")).exists();
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
