package com.framework.admin.system;

import com.framework.admin.system.AdminSystemModels.ConfigItem;
import com.framework.admin.system.AdminSystemModels.ConfigRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminSystemRepositoryTest {

    private final RecordingMapper mapper = new RecordingMapper();
    private final AdminSystemRepository repository = new AdminSystemRepository(mapper.proxy());

    @Test
    void listConfigsMasksSensitiveValues() {
        mapper.configs = List.of(
                new ConfigItem()
                        .setId(1L)
                        .setConfigKey("app.name")
                        .setConfigValue("Framework")
                        .setSensitive(false),
                new ConfigItem()
                        .setId(2L)
                        .setConfigKey("oauth.client-secret")
                        .setConfigValue("real-secret")
                        .setSensitive(true));

        List<ConfigItem> configs = repository.listConfigs();

        assertThat(configs.get(0).getConfigValue()).isEqualTo("Framework");
        assertThat(configs.get(1).getConfigValue()).isEqualTo("******");
    }

    @Test
    void updateConfigPreservesExistingSensitiveValueWhenMaskedPlaceholderIsSubmitted() {
        ConfigRequest request = new ConfigRequest();
        request.setConfigKey("oauth.client-secret");
        request.setConfigName("OAuth Secret");
        request.setConfigValue("******");
        request.setSensitive(true);

        repository.updateConfig(9L, request);

        assertThat(mapper.updatedConfig.getId()).isEqualTo(9L);
        assertThat(mapper.updatedConfig.getConfigKey()).isEqualTo("oauth.client-secret");
        assertThat(mapper.preserveValue).isTrue();
    }

    @Test
    void updateConfigPreservesExistingSensitiveValueWhenBlankValueIsSubmitted() {
        ConfigRequest request = new ConfigRequest();
        request.setConfigKey("oauth.client-secret");
        request.setConfigName("OAuth Secret");
        request.setConfigValue(" ");
        request.setSensitive(true);

        repository.updateConfig(9L, request);

        assertThat(mapper.updatedConfig.getId()).isEqualTo(9L);
        assertThat(mapper.preserveValue).isTrue();
    }

    @Test
    void updateConfigStoresNewSensitiveValueWhenValueChanges() {
        ConfigRequest request = new ConfigRequest();
        request.setConfigKey("oauth.client-secret");
        request.setConfigName("OAuth Secret");
        request.setConfigValue("new-secret");
        request.setSensitive(true);

        repository.updateConfig(9L, request);

        assertThat(mapper.updatedConfig.getConfigValue()).isEqualTo("new-secret");
        assertThat(mapper.preserveValue).isFalse();
    }

    @Test
    void updateConfigValueReportsAffectedRows() {
        assertThat(repository.updateConfigValue("admin.default.password.changed", "true")).isTrue();

        mapper.updateConfigValueResult = 0;

        assertThat(repository.updateConfigValue("missing.key", "true")).isFalse();
    }

    @Test
    void resetPasswordAndUpdateConfigValueUpdatesBothInOrder() {
        boolean updated = repository.resetPasswordAndUpdateConfigValue(
                7L, "password-hash", "admin.default.password.changed", "true");

        assertThat(updated).isTrue();
        assertThat(mapper.operations).containsExactly(
                "resetPassword:7",
                "updateConfigValue:admin.default.password.changed=true");
    }

    @Test
    void resetPasswordAndUpdateConfigValueSkipsConfigWhenPasswordRowIsMissing() {
        mapper.resetPasswordResult = 0;

        boolean updated = repository.resetPasswordAndUpdateConfigValue(
                7L, "password-hash", "admin.default.password.changed", "true");

        assertThat(updated).isFalse();
        assertThat(mapper.operations).containsExactly("resetPassword:7");
    }

    @Test
    void resetPasswordAndUpdateConfigValueThrowsWhenConfigRowIsMissing() {
        mapper.updateConfigValueResult = 0;

        assertThatThrownBy(() -> repository.resetPasswordAndUpdateConfigValue(
                7L, "password-hash", "admin.default.password.changed", "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin.default.password.changed");
        assertThat(mapper.operations).containsExactly(
                "resetPassword:7",
                "updateConfigValue:admin.default.password.changed=true");
    }

    @Test
    void deleteTenantSkipsDeptCleanupWhenTenantDoesNotExist() {
        mapper.deleteTenantResult = 0;

        boolean deleted = repository.deleteTenant(9L);

        assertThat(deleted).isFalse();
        assertThat(mapper.operations).containsExactly("deleteTenant:9");
    }

    @Test
    void deleteUserDeletesUserBeforeRoleBindingsAndReportsAffectedRows() {
        boolean deleted = repository.deleteUser(7L);

        assertThat(deleted).isTrue();
        assertThat(mapper.operations).containsExactly("deleteUser:7", "deleteUserRoles:7");
    }

    private static class RecordingMapper {
        private List<ConfigItem> configs = List.of();
        private ConfigItem updatedConfig;
        private boolean preserveValue;
        private int deleteTenantResult = 1;
        private int deleteUserResult = 1;
        private int resetPasswordResult = 1;
        private int updateConfigValueResult = 1;
        private final List<String> operations = new ArrayList<>();

        private AdminSystemMapper proxy() {
            return (AdminSystemMapper) Proxy.newProxyInstance(
                    AdminSystemMapper.class.getClassLoader(),
                    new Class<?>[]{AdminSystemMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "listConfigs" -> configs;
                        case "updateConfig" -> {
                            updatedConfig = (ConfigItem) args[0];
                            preserveValue = (Boolean) args[1];
                            yield 1;
                        }
                        case "deleteTenant" -> {
                            operations.add("deleteTenant:" + args[0]);
                            yield deleteTenantResult;
                        }
                        case "deleteDeptsByTenantId" -> {
                            operations.add("deleteDeptsByTenantId:" + args[0]);
                            yield 1;
                        }
                        case "deleteUser" -> {
                            operations.add("deleteUser:" + args[0]);
                            yield deleteUserResult;
                        }
                        case "deleteUserRoles" -> {
                            operations.add("deleteUserRoles:" + args[0]);
                            yield 1;
                        }
                        case "resetPassword" -> {
                            operations.add("resetPassword:" + args[0]);
                            yield resetPasswordResult;
                        }
                        case "updateConfigValue" -> {
                            operations.add("updateConfigValue:" + args[0] + "=" + args[1]);
                            yield updateConfigValueResult;
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
