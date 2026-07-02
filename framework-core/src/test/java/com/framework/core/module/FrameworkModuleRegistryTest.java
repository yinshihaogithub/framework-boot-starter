package com.framework.core.module;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkModuleRegistryTest {

    @Test
    void defaultModulesCoverRuntimeAndOptionalFrameworkModulesInStableOrder() {
        List<FrameworkModuleRegistry.ModuleMarker> modules = FrameworkModuleRegistry.defaultModules();

        assertThat(modules)
                .extracting(FrameworkModuleRegistry.ModuleMarker::name)
                .containsExactly(
                        "framework-core",
                        "framework-web",
                        "framework-auth",
                        "framework-security",
                        "framework-cache",
                        "framework-lock",
                        "framework-idempotent",
                        "framework-crypto",
                        "framework-log",
                        "framework-rate-limiter",
                        "framework-mq",
                        "framework-retry",
                        "framework-tools",
                        "framework-notify",
                        "framework-local-message",
                        "framework-excel",
                        "framework-datasource",
                        "framework-redis",
                        "framework-feign",
                        "framework-monitor",
                        "framework-job",
                        "framework-file"
                )
                .doesNotHaveDuplicates();
        assertThat(modules)
                .extracting(FrameworkModuleRegistry.ModuleMarker::markerClass)
                .allSatisfy(markerClass -> assertThat(markerClass).startsWith("com.framework."))
                .doesNotHaveDuplicates();
    }

    @Test
    void availableModuleNamesDetectsClasspathModules() {
        assertThat(FrameworkModuleRegistry.availableModuleNames(getClass().getClassLoader()))
                .contains("framework-core")
                .doesNotContain("framework-job");
    }
}
