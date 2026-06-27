package com.framework.admin.system;

import com.framework.admin.system.AdminSystemMapper.RoleRow;
import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemModels.ConfigItem;
import com.framework.admin.system.AdminSystemModels.ConfigRequest;
import com.framework.admin.system.AdminSystemModels.DictItem;
import com.framework.admin.system.AdminSystemModels.DictItemRequest;
import com.framework.admin.system.AdminSystemModels.DictType;
import com.framework.admin.system.AdminSystemModels.DictTypeRequest;
import com.framework.admin.system.AdminSystemModels.Dept;
import com.framework.admin.system.AdminSystemModels.DeptRequest;
import com.framework.admin.system.AdminSystemModels.LoginLog;
import com.framework.admin.system.AdminSystemModels.Menu;
import com.framework.admin.system.AdminSystemModels.MenuRequest;
import com.framework.admin.system.AdminSystemModels.Role;
import com.framework.admin.system.AdminSystemModels.RoleRequest;
import com.framework.admin.system.AdminSystemModels.Tenant;
import com.framework.admin.system.AdminSystemModels.TenantRequest;
import com.framework.admin.system.AdminSystemModels.UserUpdateRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AdminSystemRepository {

    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final Long DEFAULT_PARENT_ID = 0L;
    private static final Long DEFAULT_ROLE_ID = 1L;

    private final AdminSystemMapper mapper;

    public AdminSystemRepository(AdminSystemMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<AdminUser> findUserByUsername(String username) {
        AdminUser user = mapper.findUserByUsername(username);
        enrichUser(user);
        return Optional.ofNullable(user);
    }

    public Optional<AdminUser> findUserById(Long id) {
        AdminUser user = mapper.findUserById(id);
        enrichUser(user);
        return Optional.ofNullable(user);
    }

    public List<AdminUser> listUsers(String keyword, String status, int pageNum, int pageSize) {
        List<AdminUser> users = mapper.listUsers(like(keyword), text(status), offset(pageNum, pageSize), pageSize);
        users.forEach(this::enrichUser);
        return users;
    }

    public long countUsers(String keyword, String status) {
        return mapper.countUsers(like(keyword), text(status));
    }

    @Transactional
    public Long createUser(AdminSystemModels.UserCreateRequest request, String passwordHash) {
        AdminUser user = new AdminUser()
                .setTenantId(DEFAULT_TENANT_ID)
                .setDeptId(request.getDeptId())
                .setUsername(request.getUsername())
                .setNickname(request.getNickname())
                .setMobile(request.getMobile())
                .setEmail(request.getEmail())
                .setPasswordHash(passwordHash)
                .setStatus("ENABLED");
        mapper.insertUser(user);
        replaceUserRoles(user.getId(), request.getRoleIds());
        return user.getId();
    }

    @Transactional
    public void updateUser(Long userId, UserUpdateRequest request) {
        mapper.updateUser(new AdminUser()
                .setId(userId)
                .setDeptId(request.getDeptId())
                .setNickname(request.getNickname())
                .setMobile(request.getMobile())
                .setEmail(request.getEmail())
                .setStatus(enabledStatus(request.getStatus())));
        replaceUserRoles(userId, request.getRoleIds());
    }

    public void updateUserStatus(Long userId, String status) {
        mapper.updateUserStatus(userId, status);
    }

    public void resetPassword(Long userId, String passwordHash) {
        mapper.resetPassword(userId, passwordHash);
    }

    @Transactional
    public void deleteUser(Long userId) {
        mapper.deleteUserRoles(userId);
        mapper.deleteUser(userId);
    }

    public void updateLastLogin(Long userId) {
        mapper.updateLastLogin(userId);
    }

    public void insertLoginLog(String username, Long userId, String clientIp, boolean success, String message) {
        mapper.insertLoginLog(new LoginLog()
                .setUsername(username)
                .setUserId(userId)
                .setClientIp(clientIp)
                .setSuccess(success)
                .setMessage(message));
    }

    public List<LoginLog> listLoginLogs(String username, Boolean success, int pageNum, int pageSize) {
        return mapper.listLoginLogs(like(username), success, offset(pageNum, pageSize), pageSize);
    }

    public long countLoginLogs(String username, Boolean success) {
        return mapper.countLoginLogs(like(username), success);
    }

    public List<Tenant> listTenants() {
        return mapper.listTenants();
    }

    public Long createTenant(TenantRequest request) {
        Tenant tenant = new Tenant()
                .setTenantCode(request.getTenantCode())
                .setTenantName(request.getTenantName())
                .setStatus(enabledStatus(request.getStatus()));
        mapper.insertTenant(tenant);
        return tenant.getId();
    }

    public void updateTenant(Long id, TenantRequest request) {
        mapper.updateTenant(new Tenant()
                .setId(id)
                .setTenantCode(request.getTenantCode())
                .setTenantName(request.getTenantName())
                .setStatus(enabledStatus(request.getStatus())));
    }

    public long countUsersByTenant(Long tenantId) {
        return mapper.countUsersByTenant(tenantId);
    }

    @Transactional
    public void deleteTenant(Long id) {
        mapper.deleteDeptsByTenantId(id);
        mapper.deleteTenant(id);
    }

    public List<Dept> listDeptTree(Long tenantId) {
        Long safeTenantId = tenantId == null ? DEFAULT_TENANT_ID : tenantId;
        return buildDeptTree(mapper.listDeptsByTenantId(safeTenantId));
    }

    public Long createDept(DeptRequest request) {
        Dept dept = new Dept()
                .setTenantId(request.getTenantId() == null ? DEFAULT_TENANT_ID : request.getTenantId())
                .setParentId(defaultLong(request.getParentId()))
                .setDeptName(request.getDeptName())
                .setSortOrder(defaultInt(request.getSortOrder()))
                .setStatus(enabledStatus(request.getStatus()));
        mapper.insertDept(dept);
        return dept.getId();
    }

    public void updateDept(Long id, DeptRequest request) {
        mapper.updateDept(new Dept()
                .setId(id)
                .setTenantId(request.getTenantId() == null ? DEFAULT_TENANT_ID : request.getTenantId())
                .setParentId(defaultLong(request.getParentId()))
                .setDeptName(request.getDeptName())
                .setSortOrder(defaultInt(request.getSortOrder()))
                .setStatus(enabledStatus(request.getStatus())));
    }

    @Transactional
    public void deleteDept(Long id) {
        List<Long> ids = collectDeptSubtreeIds(id);
        if (ids.isEmpty()) {
            return;
        }
        mapper.clearUserDeptIds(ids);
        mapper.deleteDeptIds(ids);
    }

    public List<Role> listRoles() {
        return mapper.listRoles();
    }

    public Long createRole(RoleRequest request) {
        RoleRow role = new RoleRow()
                .setTenantId(DEFAULT_TENANT_ID);
        role.setRoleCode(request.getRoleCode())
                .setRoleName(request.getRoleName())
                .setSortOrder(defaultInt(request.getSortOrder()))
                .setStatus(enabledStatus(request.getStatus()));
        mapper.insertRole(role);
        return role.getId();
    }

    public void updateRole(Long roleId, RoleRequest request) {
        mapper.updateRole(new Role()
                .setId(roleId)
                .setRoleCode(request.getRoleCode())
                .setRoleName(request.getRoleName())
                .setSortOrder(defaultInt(request.getSortOrder()))
                .setStatus(enabledStatus(request.getStatus())));
    }

    @Transactional
    public void deleteRole(Long roleId) {
        mapper.deleteRoleMenus(roleId);
        mapper.deleteUserRolesByRoleId(roleId);
        mapper.deleteRole(roleId);
    }

    public List<Long> listMenuIdsByRoleId(Long roleId) {
        return mapper.listMenuIdsByRoleId(roleId);
    }

    @Transactional
    public void replaceRoleMenus(Long roleId, List<Long> menuIds) {
        mapper.deleteRoleMenus(roleId);
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : menuIds) {
            mapper.insertRoleMenu(roleId, menuId);
        }
    }

    public List<Menu> listMenuTree() {
        return buildMenuTree(mapper.listAllMenus());
    }

    public Long createMenu(MenuRequest request) {
        Menu menu = toMenu(null, request);
        mapper.insertMenu(menu);
        return menu.getId();
    }

    public void updateMenu(Long menuId, MenuRequest request) {
        mapper.updateMenu(toMenu(menuId, request));
    }

    @Transactional
    public void deleteMenu(Long menuId) {
        List<Long> ids = collectMenuSubtreeIds(menuId);
        if (ids.isEmpty()) {
            return;
        }
        mapper.deleteRoleMenusByMenuIds(ids);
        mapper.deleteMenuIds(ids);
    }

    public List<Menu> listMenusByUserId(Long userId) {
        return buildMenuTree(mapper.listMenusByUserId(userId));
    }

    public List<String> listRoleCodesByUserId(Long userId) {
        return mapper.listRoleCodesByUserId(userId);
    }

    public List<Long> listRoleIdsByUserId(Long userId) {
        return mapper.listRoleIdsByUserId(userId);
    }

    public List<String> listPermissionsByUserId(Long userId) {
        return mapper.listPermissionsByUserId(userId);
    }

    public List<DictType> listDictTypes() {
        return mapper.listDictTypes();
    }

    public Long createDictType(DictTypeRequest request) {
        DictType dictType = new DictType()
                .setDictCode(request.getDictCode())
                .setDictName(request.getDictName())
                .setStatus(enabledStatus(request.getStatus()));
        mapper.insertDictType(dictType);
        return dictType.getId();
    }

    public void updateDictType(Long id, DictTypeRequest request) {
        mapper.updateDictType(new DictType()
                .setId(id)
                .setDictCode(request.getDictCode())
                .setDictName(request.getDictName())
                .setStatus(enabledStatus(request.getStatus())));
    }

    @Transactional
    public void deleteDictType(Long id) {
        String dictCode = mapper.findDictCodeById(id);
        if (dictCode != null) {
            mapper.deleteDictItemsByCode(dictCode);
        }
        mapper.deleteDictType(id);
    }

    public List<DictItem> listDictItems(String dictCode) {
        return mapper.listDictItems(text(dictCode));
    }

    public Long createDictItem(DictItemRequest request) {
        DictItem item = new DictItem()
                .setDictCode(request.getDictCode())
                .setItemLabel(request.getItemLabel())
                .setItemValue(request.getItemValue())
                .setSortOrder(defaultInt(request.getSortOrder()))
                .setStatus(enabledStatus(request.getStatus()));
        mapper.insertDictItem(item);
        return item.getId();
    }

    public void updateDictItem(Long id, DictItemRequest request) {
        mapper.updateDictItem(new DictItem()
                .setId(id)
                .setDictCode(request.getDictCode())
                .setItemLabel(request.getItemLabel())
                .setItemValue(request.getItemValue())
                .setSortOrder(defaultInt(request.getSortOrder()))
                .setStatus(enabledStatus(request.getStatus())));
    }

    public void deleteDictItem(Long id) {
        mapper.deleteDictItem(id);
    }

    public List<ConfigItem> listConfigs() {
        return mapper.listConfigs().stream()
                .map(config -> config.setConfigValue(Boolean.TRUE.equals(config.getSensitive())
                        ? "******"
                        : config.getConfigValue()))
                .toList();
    }

    public Long createConfig(ConfigRequest request) {
        ConfigItem config = new ConfigItem()
                .setConfigKey(request.getConfigKey())
                .setConfigName(request.getConfigName())
                .setConfigValue(request.getConfigValue())
                .setSensitive(defaultBoolean(request.getSensitive()))
                .setRemark(request.getRemark());
        mapper.insertConfig(config);
        return config.getId();
    }

    public void updateConfig(Long id, ConfigRequest request) {
        mapper.updateConfig(new ConfigItem()
                        .setId(id)
                        .setConfigKey(request.getConfigKey())
                        .setConfigName(request.getConfigName())
                        .setConfigValue(request.getConfigValue())
                        .setSensitive(defaultBoolean(request.getSensitive()))
                        .setRemark(request.getRemark()),
                isMaskedSensitiveValue(request));
    }

    public void deleteConfig(Long id) {
        mapper.deleteConfig(id);
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        mapper.deleteUserRoles(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            mapper.insertUserRole(userId, DEFAULT_ROLE_ID);
            return;
        }
        for (Long roleId : roleIds) {
            mapper.insertUserRole(userId, roleId);
        }
    }

    private void enrichUser(AdminUser user) {
        if (user == null || user.getId() == null) {
            return;
        }
        user.setRoles(listRoleCodesByUserId(user.getId()));
        user.setRoleIds(listRoleIdsByUserId(user.getId()));
        user.setPermissions(listPermissionsByUserId(user.getId()));
    }

    private List<Long> collectMenuSubtreeIds(Long rootId) {
        List<Long> ids = new ArrayList<>();
        collectMenuSubtreeIds(rootId, mapper.listAllMenus(), ids);
        return ids;
    }

    private void collectMenuSubtreeIds(Long parentId, List<Menu> menus, List<Long> ids) {
        for (Menu menu : menus) {
            if (menu.getId().equals(parentId)) {
                ids.add(menu.getId());
            }
            if (menu.getParentId().equals(parentId)) {
                collectMenuSubtreeIds(menu.getId(), menus, ids);
            }
        }
    }

    private List<Long> collectDeptSubtreeIds(Long rootId) {
        List<Long> ids = new ArrayList<>();
        collectDeptSubtreeIds(rootId, mapper.listAllDepts(), ids);
        return ids;
    }

    private void collectDeptSubtreeIds(Long parentId, List<Dept> depts, List<Long> ids) {
        for (Dept dept : depts) {
            if (dept.getId().equals(parentId)) {
                ids.add(dept.getId());
            }
            if (dept.getParentId().equals(parentId)) {
                collectDeptSubtreeIds(dept.getId(), depts, ids);
            }
        }
    }

    private List<Menu> buildMenuTree(List<Menu> menus) {
        Map<Long, Menu> byId = new LinkedHashMap<>();
        for (Menu menu : menus) {
            menu.setChildren(new ArrayList<>());
            byId.put(menu.getId(), menu);
        }
        List<Menu> roots = new ArrayList<>();
        for (Menu menu : menus) {
            Menu parent = byId.get(menu.getParentId());
            if (parent == null || menu.getParentId().equals(DEFAULT_PARENT_ID)) {
                roots.add(menu);
            } else {
                parent.getChildren().add(menu);
            }
        }
        return roots;
    }

    private List<Dept> buildDeptTree(List<Dept> depts) {
        Map<Long, Dept> byId = new LinkedHashMap<>();
        for (Dept dept : depts) {
            dept.setChildren(new ArrayList<>());
            byId.put(dept.getId(), dept);
        }
        List<Dept> roots = new ArrayList<>();
        for (Dept dept : depts) {
            Dept parent = byId.get(dept.getParentId());
            if (parent == null || dept.getParentId().equals(DEFAULT_PARENT_ID)) {
                roots.add(dept);
            } else {
                parent.getChildren().add(dept);
            }
        }
        return roots;
    }

    private Menu toMenu(Long id, MenuRequest request) {
        return new Menu()
                .setId(id)
                .setParentId(defaultLong(request.getParentId()))
                .setMenuType(request.getMenuType())
                .setMenuName(request.getMenuName())
                .setRoutePath(request.getRoutePath())
                .setComponent(request.getComponent())
                .setPermission(text(request.getPermission()))
                .setIcon(request.getIcon())
                .setSortOrder(defaultInt(request.getSortOrder()))
                .setVisible(defaultVisible(request.getVisible()));
    }

    private static int offset(int pageNum, int pageSize) {
        return (Math.max(pageNum, 1) - 1) * pageSize;
    }

    private static String like(String value) {
        String text = text(value);
        return text == null ? null : "%" + text + "%";
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String enabledStatus(String status) {
        return status == null || status.isBlank() ? "ENABLED" : status;
    }

    private static Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static Long defaultLong(Long value) {
        return value == null ? DEFAULT_PARENT_ID : value;
    }

    private static Boolean defaultVisible(Boolean value) {
        return value == null || value;
    }

    private static Boolean defaultBoolean(Boolean value) {
        return value != null && value;
    }

    private static boolean isMaskedSensitiveValue(ConfigRequest request) {
        return Boolean.TRUE.equals(request.getSensitive()) && "******".equals(request.getConfigValue());
    }
}
