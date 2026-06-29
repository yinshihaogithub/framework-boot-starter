package com.framework.admin.system;

import com.framework.admin.system.AdminSystemModels.ConfigItem;
import com.framework.admin.system.AdminSystemModels.ConfigRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static class RecordingMapper {
        private List<ConfigItem> configs = List.of();
        private ConfigItem updatedConfig;
        private boolean preserveValue;

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
