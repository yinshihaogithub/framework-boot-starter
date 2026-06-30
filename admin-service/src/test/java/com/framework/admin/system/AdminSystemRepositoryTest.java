package com.framework.admin.system;

import com.framework.admin.system.AdminSystemModels.ConfigItem;
import com.framework.admin.system.AdminSystemModels.ConfigRequest;
import com.framework.admin.system.AdminSystemModels.Dept;
import com.framework.admin.system.AdminSystemModels.DeptRequest;
import com.framework.admin.system.AdminSystemModels.DictItem;
import com.framework.admin.system.AdminSystemModels.DictItemRequest;
import com.framework.admin.system.AdminSystemModels.DictType;
import com.framework.admin.system.AdminSystemModels.DictTypeRequest;
import com.framework.admin.system.AdminSystemModels.Menu;
import com.framework.admin.system.AdminSystemModels.MenuRequest;
import com.framework.admin.system.AdminSystemModels.Role;
import com.framework.admin.system.AdminSystemModels.RoleRequest;
import com.framework.admin.system.AdminSystemModels.Tenant;
import com.framework.admin.system.AdminSystemModels.TenantRequest;
import com.framework.admin.system.AdminSystemModels.UserCreateRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
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
    void createResourcesReturnGeneratedIdsAndValidateRelationInserts() {
        List<Long> ids = List.of(
                repository.createUser(userRequest(List.of(2L, 3L)), "password-hash"),
                repository.createTenant(tenantRequest()),
                repository.createDept(deptRequest()),
                repository.createRole(roleRequest()),
                repository.createMenu(menuRequest()),
                repository.createDictType(dictTypeRequest()),
                repository.createDictItem(dictItemRequest()),
                repository.createConfig(configRequest()));

        assertThat(ids).containsExactly(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L);
        assertThat(mapper.operations).containsExactly(
                "insertUser",
                "deleteUserRoles:100",
                "insertUserRole:100=2",
                "insertUserRole:100=3",
                "insertTenant",
                "insertDept",
                "insertRole",
                "insertMenu",
                "insertDictType",
                "insertDictItem",
                "insertConfig");
    }

    @Test
    void createResourceThrowsWhenInsertAffectsNoRows() {
        mapper.insertTenantResult = 0;

        assertThatThrownBy(() -> repository.createTenant(tenantRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system tenant insert failed");
        assertThat(mapper.operations).containsExactly("insertTenant");
    }

    @Test
    void createResourceThrowsWhenGeneratedIdIsMissing() {
        mapper.assignGeneratedIds = false;

        assertThatThrownBy(() -> repository.createMenu(menuRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system menu generated id missing");
        assertThat(mapper.operations).containsExactly("insertMenu");
    }

    @Test
    void createUserSkipsRoleBindingsWhenUserInsertFails() {
        mapper.insertUserResult = 0;

        assertThatThrownBy(() -> repository.createUser(userRequest(List.of(2L)), "password-hash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system user insert failed");
        assertThat(mapper.operations).containsExactly("insertUser");
    }

    @Test
    void createUserThrowsWhenDefaultRoleBindingInsertFails() {
        mapper.insertUserRoleResult = 0;

        assertThatThrownBy(() -> repository.createUser(userRequest(null), "password-hash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system user role insert failed");
        assertThat(mapper.operations).containsExactly(
                "insertUser",
                "deleteUserRoles:100",
                "insertUserRole:100=1");
    }

    @Test
    void replaceRoleMenusThrowsWhenRelationInsertFails() {
        mapper.insertRoleMenuResult = 0;

        assertThatThrownBy(() -> repository.replaceRoleMenus(9L, List.of(11L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system role menu insert failed");
        assertThat(mapper.operations).containsExactly(
                "countRoleById:9",
                "deleteRoleMenus:9",
                "insertRoleMenu:9=11");
    }

    @Test
    void replaceRoleMenusReturnsFalseWhenRoleDoesNotExist() {
        mapper.roleCountById = 0;

        boolean replaced = repository.replaceRoleMenus(9L, List.of(11L));

        assertThat(replaced).isFalse();
        assertThat(mapper.operations).containsExactly("countRoleById:9");
    }

    @Test
    void replaceRoleMenusReturnsTrueWhenClearingExistingRoleMenus() {
        boolean replaced = repository.replaceRoleMenus(9L, null);

        assertThat(replaced).isTrue();
        assertThat(mapper.operations).containsExactly(
                "countRoleById:9",
                "deleteRoleMenus:9");
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

    @Test
    void deleteDeptDeletesSubtreeAndValidatesMainDeleteRows() {
        mapper.allDepts = List.of(dept(7L, 0L), dept(8L, 7L), dept(9L, 0L));

        boolean deleted = repository.deleteDept(7L);

        assertThat(deleted).isTrue();
        assertThat(mapper.operations).containsExactly(
                "listAllDepts",
                "clearUserDeptIds:[7, 8]",
                "deleteDeptIds:[7, 8]");
    }

    @Test
    void deleteDeptReturnsFalseWhenSubtreeIsMissing() {
        mapper.allDepts = List.of(dept(9L, 0L));

        boolean deleted = repository.deleteDept(7L);

        assertThat(deleted).isFalse();
        assertThat(mapper.operations).containsExactly("listAllDepts");
    }

    @Test
    void deleteDeptThrowsWhenMainDeleteAffectsTooFewRows() {
        mapper.allDepts = List.of(dept(7L, 0L), dept(8L, 7L));
        mapper.deleteDeptIdsResult = 1;

        assertThatThrownBy(() -> repository.deleteDept(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system dept delete failed");
        assertThat(mapper.operations).containsExactly(
                "listAllDepts",
                "clearUserDeptIds:[7, 8]",
                "deleteDeptIds:[7, 8]");
    }

    @Test
    void deleteMenuDeletesSubtreeAndValidatesMainDeleteRows() {
        mapper.allMenus = List.of(menu(11L, 0L), menu(12L, 11L), menu(13L, 0L));

        boolean deleted = repository.deleteMenu(11L);

        assertThat(deleted).isTrue();
        assertThat(mapper.operations).containsExactly(
                "listAllMenus",
                "deleteRoleMenusByMenuIds:[11, 12]",
                "deleteMenuIds:[11, 12]");
    }

    @Test
    void deleteMenuReturnsFalseWhenSubtreeIsMissing() {
        mapper.allMenus = List.of(menu(13L, 0L));

        boolean deleted = repository.deleteMenu(11L);

        assertThat(deleted).isFalse();
        assertThat(mapper.operations).containsExactly("listAllMenus");
    }

    @Test
    void deleteMenuThrowsWhenMainDeleteAffectsTooFewRows() {
        mapper.allMenus = List.of(menu(11L, 0L), menu(12L, 11L));
        mapper.deleteMenuIdsResult = 1;

        assertThatThrownBy(() -> repository.deleteMenu(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system menu delete failed");
        assertThat(mapper.operations).containsExactly(
                "listAllMenus",
                "deleteRoleMenusByMenuIds:[11, 12]",
                "deleteMenuIds:[11, 12]");
    }

    @Test
    void deleteDictTypeDeletesItemsThenTypeAndValidatesTypeDelete() {
        boolean deleted = repository.deleteDictType(5L);

        assertThat(deleted).isTrue();
        assertThat(mapper.operations).containsExactly(
                "findDictCodeById:5",
                "deleteDictItemsByCode:sys_status",
                "deleteDictType:5");
    }

    @Test
    void deleteDictTypeReturnsFalseWhenTypeIsMissing() {
        mapper.dictCodeById = null;

        boolean deleted = repository.deleteDictType(5L);

        assertThat(deleted).isFalse();
        assertThat(mapper.operations).containsExactly("findDictCodeById:5");
    }

    @Test
    void deleteDictTypeThrowsWhenTypeDeleteAffectsNoRows() {
        mapper.deleteDictTypeResult = 0;

        assertThatThrownBy(() -> repository.deleteDictType(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system dict type delete failed");
        assertThat(mapper.operations).containsExactly(
                "findDictCodeById:5",
                "deleteDictItemsByCode:sys_status",
                "deleteDictType:5");
    }

    private static class RecordingMapper {
        private List<ConfigItem> configs = List.of();
        private List<Dept> allDepts = List.of();
        private List<Menu> allMenus = List.of();
        private ConfigItem updatedConfig;
        private boolean preserveValue;
        private boolean assignGeneratedIds = true;
        private long nextGeneratedId = 100L;
        private int insertUserResult = 1;
        private int insertTenantResult = 1;
        private int insertDeptResult = 1;
        private int insertRoleResult = 1;
        private int insertMenuResult = 1;
        private int insertDictTypeResult = 1;
        private int insertDictItemResult = 1;
        private int insertConfigResult = 1;
        private int insertUserRoleResult = 1;
        private int insertRoleMenuResult = 1;
        private long roleCountById = 1L;
        private Integer deleteDeptIdsResult;
        private Integer deleteMenuIdsResult;
        private String dictCodeById = "sys_status";
        private int deleteDictTypeResult = 1;
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
                        case "listAllDepts" -> {
                            operations.add("listAllDepts");
                            yield allDepts;
                        }
                        case "listAllMenus" -> {
                            operations.add("listAllMenus");
                            yield allMenus;
                        }
                        case "updateConfig" -> {
                            updatedConfig = (ConfigItem) args[0];
                            preserveValue = (Boolean) args[1];
                            yield 1;
                        }
                        case "insertUser" -> {
                            operations.add("insertUser");
                            assignGeneratedId(args[0]);
                            yield insertUserResult;
                        }
                        case "insertTenant" -> {
                            operations.add("insertTenant");
                            assignGeneratedId(args[0]);
                            yield insertTenantResult;
                        }
                        case "insertDept" -> {
                            operations.add("insertDept");
                            assignGeneratedId(args[0]);
                            yield insertDeptResult;
                        }
                        case "insertRole" -> {
                            operations.add("insertRole");
                            assignGeneratedId(args[0]);
                            yield insertRoleResult;
                        }
                        case "insertMenu" -> {
                            operations.add("insertMenu");
                            assignGeneratedId(args[0]);
                            yield insertMenuResult;
                        }
                        case "insertDictType" -> {
                            operations.add("insertDictType");
                            assignGeneratedId(args[0]);
                            yield insertDictTypeResult;
                        }
                        case "insertDictItem" -> {
                            operations.add("insertDictItem");
                            assignGeneratedId(args[0]);
                            yield insertDictItemResult;
                        }
                        case "insertConfig" -> {
                            operations.add("insertConfig");
                            assignGeneratedId(args[0]);
                            yield insertConfigResult;
                        }
                        case "insertUserRole" -> {
                            operations.add("insertUserRole:" + args[0] + "=" + args[1]);
                            yield insertUserRoleResult;
                        }
                        case "insertRoleMenu" -> {
                            operations.add("insertRoleMenu:" + args[0] + "=" + args[1]);
                            yield insertRoleMenuResult;
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
                        case "deleteRoleMenus" -> {
                            operations.add("deleteRoleMenus:" + args[0]);
                            yield 1;
                        }
                        case "countRoleById" -> {
                            operations.add("countRoleById:" + args[0]);
                            yield roleCountById;
                        }
                        case "clearUserDeptIds" -> {
                            operations.add("clearUserDeptIds:" + args[0]);
                            yield 0;
                        }
                        case "deleteDeptIds" -> {
                            operations.add("deleteDeptIds:" + args[0]);
                            yield deleteDeptIdsResult == null ? ((List<?>) args[0]).size() : deleteDeptIdsResult;
                        }
                        case "deleteRoleMenusByMenuIds" -> {
                            operations.add("deleteRoleMenusByMenuIds:" + args[0]);
                            yield 0;
                        }
                        case "deleteMenuIds" -> {
                            operations.add("deleteMenuIds:" + args[0]);
                            yield deleteMenuIdsResult == null ? ((List<?>) args[0]).size() : deleteMenuIdsResult;
                        }
                        case "findDictCodeById" -> {
                            operations.add("findDictCodeById:" + args[0]);
                            yield dictCodeById;
                        }
                        case "deleteDictItemsByCode" -> {
                            operations.add("deleteDictItemsByCode:" + args[0]);
                            yield 0;
                        }
                        case "deleteDictType" -> {
                            operations.add("deleteDictType:" + args[0]);
                            yield deleteDictTypeResult;
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

        private void assignGeneratedId(Object value) {
            if (!assignGeneratedIds) {
                return;
            }
            long id = nextGeneratedId++;
            if (value instanceof AdminSystemModels.AdminUser user) {
                user.setId(id);
            } else if (value instanceof Tenant tenant) {
                tenant.setId(id);
            } else if (value instanceof Dept dept) {
                dept.setId(id);
            } else if (value instanceof Role role) {
                role.setId(id);
            } else if (value instanceof Menu menu) {
                menu.setId(id);
            } else if (value instanceof DictType dictType) {
                dictType.setId(id);
            } else if (value instanceof DictItem dictItem) {
                dictItem.setId(id);
            } else if (value instanceof ConfigItem config) {
                config.setId(id);
            }
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

    private static UserCreateRequest userRequest(List<Long> roleIds) {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("alice");
        request.setPassword("Admin@123");
        request.setNickname("Alice");
        request.setRoleIds(roleIds);
        return request;
    }

    private static TenantRequest tenantRequest() {
        TenantRequest request = new TenantRequest();
        request.setTenantCode("tenant-a");
        request.setTenantName("Tenant A");
        request.setStatus("ENABLED");
        return request;
    }

    private static DeptRequest deptRequest() {
        DeptRequest request = new DeptRequest();
        request.setTenantId(1L);
        request.setDeptName("研发部");
        request.setStatus("ENABLED");
        return request;
    }

    private static RoleRequest roleRequest() {
        RoleRequest request = new RoleRequest();
        request.setRoleCode("OPS");
        request.setRoleName("运维");
        request.setStatus("ENABLED");
        return request;
    }

    private static MenuRequest menuRequest() {
        MenuRequest request = new MenuRequest();
        request.setParentId(0L);
        request.setMenuType("MENU");
        request.setMenuName("系统管理");
        request.setRoutePath("/system");
        request.setVisible(true);
        return request;
    }

    private static DictTypeRequest dictTypeRequest() {
        DictTypeRequest request = new DictTypeRequest();
        request.setDictCode("sys_status");
        request.setDictName("系统状态");
        request.setStatus("ENABLED");
        return request;
    }

    private static DictItemRequest dictItemRequest() {
        DictItemRequest request = new DictItemRequest();
        request.setDictCode("sys_status");
        request.setItemLabel("启用");
        request.setItemValue("ENABLED");
        request.setStatus("ENABLED");
        return request;
    }

    private static ConfigRequest configRequest() {
        ConfigRequest request = new ConfigRequest();
        request.setConfigKey("app.name");
        request.setConfigName("应用名称");
        request.setConfigValue("Framework");
        request.setSensitive(false);
        return request;
    }

    private static Dept dept(Long id, Long parentId) {
        return new Dept()
                .setId(id)
                .setParentId(parentId)
                .setDeptName("dept-" + id);
    }

    private static Menu menu(Long id, Long parentId) {
        return new Menu()
                .setId(id)
                .setParentId(parentId)
                .setMenuType("MENU")
                .setMenuName("menu-" + id)
                .setVisible(true);
    }
}
