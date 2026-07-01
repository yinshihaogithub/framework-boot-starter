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

class AdminSystemMapperSupportTest {

    private final RecordingMapper mapper = new RecordingMapper();
    private final AdminSystemMapperSupport mapperSupport = new AdminSystemMapperSupport(mapper.proxy());

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

        List<ConfigItem> configs = mapperSupport.listConfigs(null, 1, 20);

        assertThat(configs.get(0).getConfigValue()).isEqualTo("Framework");
        assertThat(configs.get(1).getConfigValue()).isEqualTo("******");
    }

    @Test
    void listConfigsUsesPagingAndKeywordThenCountsMatches() {
        mapper.configs = List.of(new ConfigItem()
                .setId(1L)
                .setConfigKey("app.name")
                .setConfigValue("Framework")
                .setSensitive(false));
        mapper.configCount = 8L;

        List<ConfigItem> configs = mapperSupport.listConfigs(" app ", 2, 10);
        long count = mapperSupport.countConfigs(" app ");

        assertThat(configs).hasSize(1);
        assertThat(count).isEqualTo(8L);
        assertThat(mapper.operations).containsExactly(
                "listConfigs:%app%:10:10",
                "countConfigs:%app%");
    }

    @Test
    void findConfigByKeyReturnsMaskedValue() {
        mapper.configByKey = new ConfigItem()
                .setId(9L)
                .setConfigKey("oauth.secret")
                .setConfigValue("real-secret")
                .setSensitive(true);

        assertThat(mapperSupport.findConfigByKey("\u00A0oauth.secret\u3000"))
                .get()
                .extracting(ConfigItem::getConfigValue)
                .isEqualTo("******");
        assertThat(mapper.operations).containsExactly("findConfigByKey:oauth.secret");
    }

    @Test
    void updateConfigPreservesExistingSensitiveValueWhenMaskedPlaceholderIsSubmitted() {
        ConfigRequest request = new ConfigRequest();
        request.setConfigKey("oauth.client-secret");
        request.setConfigName("OAuth Secret");
        request.setConfigValue("\u00A0******\u3000");
        request.setSensitive(true);

        mapperSupport.updateConfig(9L, request);

        assertThat(mapper.updatedConfig.getId()).isEqualTo(9L);
        assertThat(mapper.updatedConfig.getConfigKey()).isEqualTo("oauth.client-secret");
        assertThat(mapper.preserveValue).isTrue();
    }

    @Test
    void updateConfigPreservesExistingSensitiveValueWhenBlankValueIsSubmitted() {
        ConfigRequest request = new ConfigRequest();
        request.setConfigKey("oauth.client-secret");
        request.setConfigName("OAuth Secret");
        request.setConfigValue("\u00A0\u3000");
        request.setSensitive(true);

        mapperSupport.updateConfig(9L, request);

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

        mapperSupport.updateConfig(9L, request);

        assertThat(mapper.updatedConfig.getConfigValue()).isEqualTo("new-secret");
        assertThat(mapper.preserveValue).isFalse();
    }

    @Test
    void updateConfigValueReportsAffectedRows() {
        assertThat(mapperSupport.updateConfigValue("admin.default.password.changed", "true")).isTrue();

        mapper.updateConfigValueResult = 0;

        assertThat(mapperSupport.updateConfigValue("missing.key", "true")).isFalse();
    }

    @Test
    void createResourcesReturnGeneratedIdsAndValidateRelationInserts() {
        List<Long> ids = List.of(
                mapperSupport.createUser(userRequest(List.of(2L, 3L)), "password-hash"),
                mapperSupport.createTenant(tenantRequest()),
                mapperSupport.createDept(deptRequest()),
                mapperSupport.createRole(roleRequest()),
                mapperSupport.createMenu(menuRequest()),
                mapperSupport.createDictType(dictTypeRequest()),
                mapperSupport.createDictItem(dictItemRequest()),
                mapperSupport.createConfig(configRequest()));

        assertThat(ids).containsExactly(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L);
        assertThat(mapper.operations).containsExactly(
                "countRolesByIds:[2, 3]",
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
    void createResourcesNormalizeBoundaryTextBeforeMapperInsert() {
        UserCreateRequest user = userRequest(List.of(2L));
        user.setUsername("\u00A0alice\u3000");
        user.setNickname("\u3000Alice\u00A0");
        user.setMobile("\u00A013800000000\u3000");
        user.setEmail("\u3000alice@example.com\u00A0");
        TenantRequest tenant = tenantRequest();
        tenant.setTenantCode("\u00A0tenant-a\u3000");
        tenant.setTenantName("\u3000Tenant A\u00A0");
        tenant.setStatus("\u00A0enabled\u3000");
        DeptRequest dept = deptRequest();
        dept.setDeptName("\u3000研发部\u00A0");
        dept.setStatus("\u00A0enabled\u3000");
        RoleRequest role = roleRequest();
        role.setRoleCode("\u00A0ops\u3000");
        role.setRoleName("\u3000运维\u00A0");
        role.setStatus("\u00A0enabled\u3000");
        MenuRequest menu = menuRequest();
        menu.setMenuType("\u3000menu\u00A0");
        menu.setMenuName("\u00A0系统管理\u3000");
        menu.setRoutePath("\u3000/system\u00A0");
        menu.setComponent("\u00A0System\u3000");
        menu.setPermission("\u00A0system:view\u3000");
        menu.setIcon("\u3000Setting\u00A0");
        DictTypeRequest dictType = dictTypeRequest();
        dictType.setDictCode("\u00A0sys_status\u3000");
        dictType.setDictName("\u3000系统状态\u00A0");
        dictType.setStatus("\u00A0enabled\u3000");
        DictItemRequest dictItem = dictItemRequest();
        dictItem.setDictCode("\u3000sys_status\u00A0");
        dictItem.setItemLabel("\u00A0启用\u3000");
        dictItem.setItemValue("\u3000ENABLED\u00A0");
        dictItem.setStatus("\u00A0enabled\u3000");
        ConfigRequest config = configRequest();
        config.setConfigKey("\u00A0app.name\u3000");
        config.setConfigName("\u3000应用名称\u00A0");
        config.setConfigValue("\u00A0Framework\u3000");
        config.setRemark("\u3000公开显示名称\u00A0");

        mapperSupport.createUser(user, "password-hash");
        mapperSupport.createTenant(tenant);
        mapperSupport.createDept(dept);
        mapperSupport.createRole(role);
        mapperSupport.createMenu(menu);
        mapperSupport.createDictType(dictType);
        mapperSupport.createDictItem(dictItem);
        mapperSupport.createConfig(config);

        assertThat(mapper.insertedUser.getUsername()).isEqualTo("alice");
        assertThat(mapper.insertedUser.getNickname()).isEqualTo("Alice");
        assertThat(mapper.insertedUser.getMobile()).isEqualTo("13800000000");
        assertThat(mapper.insertedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(mapper.insertedUser.getStatus()).isEqualTo("ENABLED");
        assertThat(mapper.insertedTenant.getTenantCode()).isEqualTo("tenant-a");
        assertThat(mapper.insertedTenant.getTenantName()).isEqualTo("Tenant A");
        assertThat(mapper.insertedTenant.getStatus()).isEqualTo("ENABLED");
        assertThat(mapper.insertedDept.getDeptName()).isEqualTo("研发部");
        assertThat(mapper.insertedDept.getStatus()).isEqualTo("ENABLED");
        assertThat(mapper.insertedRole.getRoleCode()).isEqualTo("ops");
        assertThat(mapper.insertedRole.getRoleName()).isEqualTo("运维");
        assertThat(mapper.insertedRole.getStatus()).isEqualTo("ENABLED");
        assertThat(mapper.insertedMenu.getMenuType()).isEqualTo("MENU");
        assertThat(mapper.insertedMenu.getMenuName()).isEqualTo("系统管理");
        assertThat(mapper.insertedMenu.getRoutePath()).isEqualTo("/system");
        assertThat(mapper.insertedMenu.getComponent()).isEqualTo("System");
        assertThat(mapper.insertedMenu.getPermission()).isEqualTo("system:view");
        assertThat(mapper.insertedMenu.getIcon()).isEqualTo("Setting");
        assertThat(mapper.insertedDictType.getDictCode()).isEqualTo("sys_status");
        assertThat(mapper.insertedDictType.getDictName()).isEqualTo("系统状态");
        assertThat(mapper.insertedDictType.getStatus()).isEqualTo("ENABLED");
        assertThat(mapper.insertedDictItem.getDictCode()).isEqualTo("sys_status");
        assertThat(mapper.insertedDictItem.getItemLabel()).isEqualTo("启用");
        assertThat(mapper.insertedDictItem.getItemValue()).isEqualTo("ENABLED");
        assertThat(mapper.insertedDictItem.getStatus()).isEqualTo("ENABLED");
        assertThat(mapper.insertedConfig.getConfigKey()).isEqualTo("app.name");
        assertThat(mapper.insertedConfig.getConfigName()).isEqualTo("应用名称");
        assertThat(mapper.insertedConfig.getConfigValue()).isEqualTo("\u00A0Framework\u3000");
        assertThat(mapper.insertedConfig.getRemark()).isEqualTo("公开显示名称");
    }

    @Test
    void createResourceThrowsWhenInsertAffectsNoRows() {
        mapper.insertTenantResult = 0;

        assertThatThrownBy(() -> mapperSupport.createTenant(tenantRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system tenant insert failed");
        assertThat(mapper.operations).containsExactly("insertTenant");
    }

    @Test
    void createResourceThrowsWhenGeneratedIdIsMissing() {
        mapper.assignGeneratedIds = false;

        assertThatThrownBy(() -> mapperSupport.createMenu(menuRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system menu generated id missing");
        assertThat(mapper.operations).containsExactly("insertMenu");
    }

    @Test
    void createUserSkipsRoleBindingsWhenUserInsertFails() {
        mapper.insertUserResult = 0;

        assertThatThrownBy(() -> mapperSupport.createUser(userRequest(List.of(2L)), "password-hash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system user insert failed");
        assertThat(mapper.operations).containsExactly("countRolesByIds:[2]", "insertUser");
    }

    @Test
    void createUserThrowsBeforeInsertWhenRoleReferenceIsMissing() {
        mapper.countRolesByIdsResult = 1L;

        assertThatThrownBy(() -> mapperSupport.createUser(userRequest(List.of(2L, 3L)), "password-hash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system role reference missing");
        assertThat(mapper.operations).containsExactly("countRolesByIds:[2, 3]");
    }

    @Test
    void createUserThrowsWhenDefaultRoleBindingInsertFails() {
        mapper.insertUserRoleResult = 0;

        assertThatThrownBy(() -> mapperSupport.createUser(userRequest(null), "password-hash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system user role insert failed");
        assertThat(mapper.operations).containsExactly(
                "countRolesByIds:[1]",
                "insertUser",
                "deleteUserRoles:100",
                "insertUserRole:100=1");
    }

    @Test
    void createUserDeduplicatesRoleBindings() {
        Long userId = mapperSupport.createUser(userRequest(List.of(2L, 2L, 3L)), "password-hash");

        assertThat(userId).isEqualTo(100L);
        assertThat(mapper.operations).containsExactly(
                "countRolesByIds:[2, 3]",
                "insertUser",
                "deleteUserRoles:100",
                "insertUserRole:100=2",
                "insertUserRole:100=3");
    }

    @Test
    void replaceRoleMenusThrowsWhenRelationInsertFails() {
        mapper.insertRoleMenuResult = 0;

        assertThatThrownBy(() -> mapperSupport.replaceRoleMenus(9L, List.of(11L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system role menu insert failed");
        assertThat(mapper.operations).containsExactly(
                "countRoleById:9",
                "countMenusByIds:[11]",
                "deleteRoleMenus:9",
                "insertRoleMenu:9=11");
    }

    @Test
    void replaceRoleMenusReturnsFalseWhenRoleDoesNotExist() {
        mapper.roleCountById = 0;

        boolean replaced = mapperSupport.replaceRoleMenus(9L, List.of(11L));

        assertThat(replaced).isFalse();
        assertThat(mapper.operations).containsExactly("countRoleById:9");
    }

    @Test
    void replaceRoleMenusReturnsTrueWhenClearingExistingRoleMenus() {
        boolean replaced = mapperSupport.replaceRoleMenus(9L, null);

        assertThat(replaced).isTrue();
        assertThat(mapper.operations).containsExactly(
                "countRoleById:9",
                "deleteRoleMenus:9");
    }

    @Test
    void replaceRoleMenusThrowsBeforeClearingWhenMenuReferenceIsMissing() {
        mapper.countMenusByIdsResult = 1L;

        assertThatThrownBy(() -> mapperSupport.replaceRoleMenus(9L, List.of(11L, 12L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system menu reference missing");
        assertThat(mapper.operations).containsExactly(
                "countRoleById:9",
                "countMenusByIds:[11, 12]");
    }

    @Test
    void replaceRoleMenusDeduplicatesMenuBindings() {
        boolean replaced = mapperSupport.replaceRoleMenus(9L, List.of(11L, 11L, 12L));

        assertThat(replaced).isTrue();
        assertThat(mapper.operations).containsExactly(
                "countRoleById:9",
                "countMenusByIds:[11, 12]",
                "deleteRoleMenus:9",
                "insertRoleMenu:9=11",
                "insertRoleMenu:9=12");
    }

    @Test
    void resetPasswordAndUpdateConfigValueUpdatesBothInOrder() {
        boolean updated = mapperSupport.resetPasswordAndUpdateConfigValue(
                7L, "password-hash", "admin.default.password.changed", "true");

        assertThat(updated).isTrue();
        assertThat(mapper.operations).containsExactly(
                "resetPassword:7",
                "updateConfigValue:admin.default.password.changed=true");
    }

    @Test
    void resetPasswordAndUpdateConfigValueSkipsConfigWhenPasswordRowIsMissing() {
        mapper.resetPasswordResult = 0;

        boolean updated = mapperSupport.resetPasswordAndUpdateConfigValue(
                7L, "password-hash", "admin.default.password.changed", "true");

        assertThat(updated).isFalse();
        assertThat(mapper.operations).containsExactly("resetPassword:7");
    }

    @Test
    void resetPasswordAndUpdateConfigValueThrowsWhenConfigRowIsMissing() {
        mapper.updateConfigValueResult = 0;

        assertThatThrownBy(() -> mapperSupport.resetPasswordAndUpdateConfigValue(
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

        boolean deleted = mapperSupport.deleteTenant(9L);

        assertThat(deleted).isFalse();
        assertThat(mapper.operations).containsExactly("deleteTenant:9");
    }

    @Test
    void deleteUserDeletesUserBeforeRoleBindingsAndReportsAffectedRows() {
        boolean deleted = mapperSupport.deleteUser(7L);

        assertThat(deleted).isTrue();
        assertThat(mapper.operations).containsExactly("deleteUser:7", "deleteUserRoles:7");
    }

    @Test
    void deleteDeptDeletesSubtreeAndValidatesMainDeleteRows() {
        mapper.allDepts = List.of(dept(7L, 0L), dept(8L, 7L), dept(9L, 0L));

        boolean deleted = mapperSupport.deleteDept(7L);

        assertThat(deleted).isTrue();
        assertThat(mapper.operations).containsExactly(
                "listAllDepts",
                "clearUserDeptIds:[7, 8]",
                "deleteDeptIds:[7, 8]");
    }

    @Test
    void deleteDeptReturnsFalseWhenSubtreeIsMissing() {
        mapper.allDepts = List.of(dept(9L, 0L));

        boolean deleted = mapperSupport.deleteDept(7L);

        assertThat(deleted).isFalse();
        assertThat(mapper.operations).containsExactly("listAllDepts");
    }

    @Test
    void deleteDeptThrowsWhenMainDeleteAffectsTooFewRows() {
        mapper.allDepts = List.of(dept(7L, 0L), dept(8L, 7L));
        mapper.deleteDeptIdsResult = 1;

        assertThatThrownBy(() -> mapperSupport.deleteDept(7L))
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

        boolean deleted = mapperSupport.deleteMenu(11L);

        assertThat(deleted).isTrue();
        assertThat(mapper.operations).containsExactly(
                "listAllMenus",
                "deleteRoleMenusByMenuIds:[11, 12]",
                "deleteMenuIds:[11, 12]");
    }

    @Test
    void deleteMenuReturnsFalseWhenSubtreeIsMissing() {
        mapper.allMenus = List.of(menu(13L, 0L));

        boolean deleted = mapperSupport.deleteMenu(11L);

        assertThat(deleted).isFalse();
        assertThat(mapper.operations).containsExactly("listAllMenus");
    }

    @Test
    void deleteMenuThrowsWhenMainDeleteAffectsTooFewRows() {
        mapper.allMenus = List.of(menu(11L, 0L), menu(12L, 11L));
        mapper.deleteMenuIdsResult = 1;

        assertThatThrownBy(() -> mapperSupport.deleteMenu(11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system menu delete failed");
        assertThat(mapper.operations).containsExactly(
                "listAllMenus",
                "deleteRoleMenusByMenuIds:[11, 12]",
                "deleteMenuIds:[11, 12]");
    }

    @Test
    void isMenuDescendantDetectsNestedMenuSubtree() {
        mapper.allMenus = List.of(menu(11L, 0L), menu(12L, 11L), menu(13L, 12L), menu(14L, 0L));

        assertThat(mapperSupport.isMenuDescendant(11L, 13L)).isTrue();
        assertThat(mapperSupport.isMenuDescendant(11L, 14L)).isFalse();
        assertThat(mapperSupport.isMenuDescendant(11L, 11L)).isFalse();
        assertThat(mapper.operations).containsExactly(
                "listAllMenus",
                "listAllMenus");
    }

    @Test
    void deleteDictTypeDeletesItemsThenTypeAndValidatesTypeDelete() {
        boolean deleted = mapperSupport.deleteDictType(5L);

        assertThat(deleted).isTrue();
        assertThat(mapper.operations).containsExactly(
                "findDictCodeById:5",
                "deleteDictItemsByCode:sys_status",
                "deleteDictType:5");
    }

    @Test
    void deleteDictTypeReturnsFalseWhenTypeIsMissing() {
        mapper.dictCodeById = null;

        boolean deleted = mapperSupport.deleteDictType(5L);

        assertThat(deleted).isFalse();
        assertThat(mapper.operations).containsExactly("findDictCodeById:5");
    }

    @Test
    void deleteDictTypeThrowsWhenTypeDeleteAffectsNoRows() {
        mapper.deleteDictTypeResult = 0;

        assertThatThrownBy(() -> mapperSupport.deleteDictType(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("system dict type delete failed");
        assertThat(mapper.operations).containsExactly(
                "findDictCodeById:5",
                "deleteDictItemsByCode:sys_status",
                "deleteDictType:5");
    }

    private static class RecordingMapper {
        private List<ConfigItem> configs = List.of();
        private ConfigItem configByKey;
        private long configCount;
        private List<Dept> allDepts = List.of();
        private List<Menu> allMenus = List.of();
        private AdminSystemModels.AdminUser insertedUser;
        private Tenant insertedTenant;
        private Dept insertedDept;
        private Role insertedRole;
        private Menu insertedMenu;
        private DictType insertedDictType;
        private DictItem insertedDictItem;
        private ConfigItem insertedConfig;
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
        private Long countRolesByIdsResult;
        private Long countMenusByIdsResult;
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
                        case "listConfigs" -> {
                            operations.add("listConfigs:" + args[0] + ":" + args[1] + ":" + args[2]);
                            yield configs;
                        }
                        case "countConfigs" -> {
                            operations.add("countConfigs:" + args[0]);
                            yield configCount;
                        }
                        case "findConfigByKey" -> {
                            operations.add("findConfigByKey:" + args[0]);
                            yield configByKey;
                        }
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
                            insertedUser = (AdminSystemModels.AdminUser) args[0];
                            assignGeneratedId(args[0]);
                            yield insertUserResult;
                        }
                        case "insertTenant" -> {
                            operations.add("insertTenant");
                            insertedTenant = (Tenant) args[0];
                            assignGeneratedId(args[0]);
                            yield insertTenantResult;
                        }
                        case "insertDept" -> {
                            operations.add("insertDept");
                            insertedDept = (Dept) args[0];
                            assignGeneratedId(args[0]);
                            yield insertDeptResult;
                        }
                        case "insertRole" -> {
                            operations.add("insertRole");
                            insertedRole = (Role) args[0];
                            assignGeneratedId(args[0]);
                            yield insertRoleResult;
                        }
                        case "insertMenu" -> {
                            operations.add("insertMenu");
                            insertedMenu = (Menu) args[0];
                            assignGeneratedId(args[0]);
                            yield insertMenuResult;
                        }
                        case "insertDictType" -> {
                            operations.add("insertDictType");
                            insertedDictType = (DictType) args[0];
                            assignGeneratedId(args[0]);
                            yield insertDictTypeResult;
                        }
                        case "insertDictItem" -> {
                            operations.add("insertDictItem");
                            insertedDictItem = (DictItem) args[0];
                            assignGeneratedId(args[0]);
                            yield insertDictItemResult;
                        }
                        case "insertConfig" -> {
                            operations.add("insertConfig");
                            insertedConfig = (ConfigItem) args[0];
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
                        case "countRolesByIds" -> {
                            operations.add("countRolesByIds:" + args[0]);
                            yield countRolesByIdsResult == null ? (long) ((List<?>) args[0]).size() : countRolesByIdsResult;
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
                        case "countMenusByIds" -> {
                            operations.add("countMenusByIds:" + args[0]);
                            yield countMenusByIdsResult == null ? (long) ((List<?>) args[0]).size() : countMenusByIdsResult;
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
