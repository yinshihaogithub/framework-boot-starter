package com.framework.admin.system;

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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AdminSystemRepository {

    private static final Long DEFAULT_TENANT_ID = 1L;

    private final JdbcTemplate jdbcTemplate;

    public AdminSystemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AdminUser> findUserByUsername(String username) {
        try {
            AdminUser user = jdbcTemplate.queryForObject("""
                    SELECT id, tenant_id, dept_id, username, nickname, mobile, email, status,
                           password_hash, last_login_time, create_time
                    FROM sys_user
                    WHERE username = ?
                    """, this::mapUser, username);
            enrichUser(user);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<AdminUser> findUserById(Long id) {
        try {
            AdminUser user = jdbcTemplate.queryForObject("""
                    SELECT id, tenant_id, dept_id, username, nickname, mobile, email, status,
                           password_hash, last_login_time, create_time
                    FROM sys_user
                    WHERE id = ?
                    """, this::mapUser, id);
            enrichUser(user);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<AdminUser> listUsers(String keyword, String status, int pageNum, int pageSize) {
        List<Object> args = new ArrayList<>();
        String where = buildUserWhere(keyword, status, args);
        args.add((pageNum - 1) * pageSize);
        args.add(pageSize);
        List<AdminUser> users = jdbcTemplate.query("""
                SELECT id, tenant_id, dept_id, username, nickname, mobile, email, status,
                       password_hash, last_login_time, create_time
                FROM sys_user
                """ + where + " ORDER BY id ASC LIMIT ?, ?", this::mapUser, args.toArray());
        users.forEach(this::enrichUser);
        return users;
    }

    public long countUsers(String keyword, String status) {
        List<Object> args = new ArrayList<>();
        String where = buildUserWhere(keyword, status, args);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user " + where, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    @Transactional
    public Long createUser(AdminSystemModels.UserCreateRequest request, String passwordHash) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (tenant_id, dept_id, username, nickname, mobile, email, password_hash, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ENABLED')
                """, DEFAULT_TENANT_ID, request.getDeptId(), request.getUsername(), request.getNickname(),
                request.getMobile(), request.getEmail(), passwordHash);
        Long userId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        replaceUserRoles(userId, request.getRoleIds());
        return userId;
    }

    @Transactional
    public void updateUser(Long userId, UserUpdateRequest request) {
        jdbcTemplate.update("""
                UPDATE sys_user
                SET dept_id = ?, nickname = ?, mobile = ?, email = ?, status = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.getDeptId(), request.getNickname(), request.getMobile(), request.getEmail(),
                enabledStatus(request.getStatus()), userId);
        replaceUserRoles(userId, request.getRoleIds());
    }

    public void updateUserStatus(Long userId, String status) {
        jdbcTemplate.update("UPDATE sys_user SET status = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?", status, userId);
    }

    public void resetPassword(Long userId, String passwordHash) {
        jdbcTemplate.update("UPDATE sys_user SET password_hash = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?", passwordHash, userId);
    }

    @Transactional
    public void deleteUser(Long userId) {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", userId);
    }

    public void updateLastLogin(Long userId) {
        jdbcTemplate.update("UPDATE sys_user SET last_login_time = CURRENT_TIMESTAMP WHERE id = ?", userId);
    }

    public void insertLoginLog(String username, Long userId, String clientIp, boolean success, String message) {
        jdbcTemplate.update("""
                INSERT INTO sys_login_log (username, user_id, client_ip, success, message)
                VALUES (?, ?, ?, ?, ?)
                """, username, userId, clientIp, success, message);
    }

    public List<LoginLog> listLoginLogs(String username, Boolean success, int pageNum, int pageSize) {
        List<Object> args = new ArrayList<>();
        String where = buildLoginLogWhere(username, success, args);
        args.add((pageNum - 1) * pageSize);
        args.add(pageSize);
        return jdbcTemplate.query("""
                SELECT id, username, user_id, client_ip, success, message, create_time
                FROM sys_login_log
                """ + where + " ORDER BY create_time DESC, id DESC LIMIT ?, ?", this::mapLoginLog, args.toArray());
    }

    public long countLoginLogs(String username, Boolean success) {
        List<Object> args = new ArrayList<>();
        String where = buildLoginLogWhere(username, success, args);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_login_log " + where, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public List<Tenant> listTenants() {
        return jdbcTemplate.query("""
                SELECT id, tenant_code, tenant_name, status, create_time
                FROM sys_tenant
                ORDER BY id ASC
                """, this::mapTenant);
    }

    public Long createTenant(TenantRequest request) {
        jdbcTemplate.update("""
                INSERT INTO sys_tenant (tenant_code, tenant_name, status)
                VALUES (?, ?, ?)
                """, request.getTenantCode(), request.getTenantName(), enabledStatus(request.getStatus()));
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateTenant(Long id, TenantRequest request) {
        jdbcTemplate.update("""
                UPDATE sys_tenant
                SET tenant_code = ?, tenant_name = ?, status = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.getTenantCode(), request.getTenantName(), enabledStatus(request.getStatus()), id);
    }

    public long countUsersByTenant(Long tenantId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE tenant_id = ?", Long.class, tenantId);
        return count == null ? 0 : count;
    }

    @Transactional
    public void deleteTenant(Long id) {
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ?", id);
        jdbcTemplate.update("DELETE FROM sys_tenant WHERE id = ?", id);
    }

    public List<Dept> listDeptTree(Long tenantId) {
        Long safeTenantId = tenantId == null ? DEFAULT_TENANT_ID : tenantId;
        List<Dept> depts = jdbcTemplate.query("""
                SELECT id, tenant_id, parent_id, dept_name, sort_order, status, create_time
                FROM sys_dept
                WHERE tenant_id = ?
                ORDER BY sort_order ASC, id ASC
                """, this::mapDept, safeTenantId);
        return buildDeptTree(depts);
    }

    public Long createDept(DeptRequest request) {
        jdbcTemplate.update("""
                INSERT INTO sys_dept (tenant_id, parent_id, dept_name, sort_order, status)
                VALUES (?, ?, ?, ?, ?)
                """, request.getTenantId() == null ? DEFAULT_TENANT_ID : request.getTenantId(),
                defaultLong(request.getParentId()), request.getDeptName(), defaultInt(request.getSortOrder()),
                enabledStatus(request.getStatus()));
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateDept(Long id, DeptRequest request) {
        jdbcTemplate.update("""
                UPDATE sys_dept
                SET tenant_id = ?, parent_id = ?, dept_name = ?, sort_order = ?, status = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.getTenantId() == null ? DEFAULT_TENANT_ID : request.getTenantId(),
                defaultLong(request.getParentId()), request.getDeptName(), defaultInt(request.getSortOrder()),
                enabledStatus(request.getStatus()), id);
    }

    @Transactional
    public void deleteDept(Long id) {
        List<Long> ids = collectDeptSubtreeIds(id);
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(item -> "?").toList());
        jdbcTemplate.update("UPDATE sys_user SET dept_id = NULL WHERE dept_id IN (" + placeholders + ")", ids.toArray());
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id IN (" + placeholders + ")", ids.toArray());
    }

    public List<Role> listRoles() {
        return jdbcTemplate.query("""
                SELECT id, role_code, role_name, sort_order, status, create_time
                FROM sys_role
                ORDER BY sort_order ASC, id ASC
                """, this::mapRole);
    }

    public Long createRole(RoleRequest request) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (tenant_id, role_code, role_name, sort_order, status)
                VALUES (?, ?, ?, ?, ?)
                """, DEFAULT_TENANT_ID, request.getRoleCode(), request.getRoleName(),
                defaultInt(request.getSortOrder()), enabledStatus(request.getStatus()));
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateRole(Long roleId, RoleRequest request) {
        jdbcTemplate.update("""
                UPDATE sys_role
                SET role_code = ?, role_name = ?, sort_order = ?, status = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.getRoleCode(), request.getRoleName(), defaultInt(request.getSortOrder()),
                enabledStatus(request.getStatus()), roleId);
    }

    @Transactional
    public void deleteRole(Long roleId) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", roleId);
    }

    public List<Long> listMenuIdsByRoleId(Long roleId) {
        return jdbcTemplate.queryForList("""
                SELECT menu_id
                FROM sys_role_menu
                WHERE role_id = ?
                ORDER BY menu_id ASC
                """, Long.class, roleId);
    }

    @Transactional
    public void replaceRoleMenus(Long roleId, List<Long> menuIds) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : menuIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", roleId, menuId);
        }
    }

    public List<Menu> listMenuTree() {
        return buildMenuTree(listMenusBySql("""
                SELECT id, parent_id, menu_type, menu_name, route_path, component, permission,
                       icon, sort_order, visible
                FROM sys_menu
                ORDER BY sort_order ASC, id ASC
                """));
    }

    public Long createMenu(MenuRequest request) {
        jdbcTemplate.update("""
                INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_path, component, permission, icon, sort_order, visible)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, defaultLong(request.getParentId()), request.getMenuType(), request.getMenuName(),
                request.getRoutePath(), request.getComponent(), blankToNull(request.getPermission()), request.getIcon(),
                defaultInt(request.getSortOrder()), defaultVisible(request.getVisible()));
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateMenu(Long menuId, MenuRequest request) {
        jdbcTemplate.update("""
                UPDATE sys_menu
                SET parent_id = ?, menu_type = ?, menu_name = ?, route_path = ?, component = ?,
                    permission = ?, icon = ?, sort_order = ?, visible = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, defaultLong(request.getParentId()), request.getMenuType(), request.getMenuName(),
                request.getRoutePath(), request.getComponent(), blankToNull(request.getPermission()), request.getIcon(),
                defaultInt(request.getSortOrder()), defaultVisible(request.getVisible()), menuId);
    }

    @Transactional
    public void deleteMenu(Long menuId) {
        List<Long> ids = collectMenuSubtreeIds(menuId);
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id IN (" + placeholders + ")", ids.toArray());
        jdbcTemplate.update("DELETE FROM sys_menu WHERE id IN (" + placeholders + ")", ids.toArray());
    }

    public List<Menu> listMenusByUserId(Long userId) {
        return buildMenuTree(listMenusBySql("""
                SELECT DISTINCT m.id, m.parent_id, m.menu_type, m.menu_name, m.route_path, m.component,
                       m.permission, m.icon, m.sort_order, m.visible
                FROM sys_menu m
                JOIN sys_role_menu rm ON rm.menu_id = m.id
                JOIN sys_user_role ur ON ur.role_id = rm.role_id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND r.status = 'ENABLED' AND m.visible = 1 AND m.menu_type <> 'BUTTON'
                ORDER BY m.sort_order ASC, m.id ASC
                """, userId));
    }

    public List<String> listRoleCodesByUserId(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT r.role_code
                FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND r.status = 'ENABLED'
                ORDER BY r.sort_order ASC, r.id ASC
                """, String.class, userId);
    }

    public List<Long> listRoleIdsByUserId(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT role_id
                FROM sys_user_role
                WHERE user_id = ?
                ORDER BY role_id ASC
                """, Long.class, userId);
    }

    public List<String> listPermissionsByUserId(Long userId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT m.permission
                FROM sys_menu m
                JOIN sys_role_menu rm ON rm.menu_id = m.id
                JOIN sys_user_role ur ON ur.role_id = rm.role_id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND r.status = 'ENABLED'
                  AND m.permission IS NOT NULL AND m.permission <> ''
                ORDER BY m.permission ASC
                """, String.class, userId);
    }

    public List<DictType> listDictTypes() {
        return jdbcTemplate.query("""
                SELECT id, dict_code, dict_name, status
                FROM sys_dict_type
                ORDER BY id ASC
                """, (rs, rowNum) -> new DictType()
                .setId(rs.getLong("id"))
                .setDictCode(rs.getString("dict_code"))
                .setDictName(rs.getString("dict_name"))
                .setStatus(rs.getString("status")));
    }

    public Long createDictType(DictTypeRequest request) {
        jdbcTemplate.update("""
                INSERT INTO sys_dict_type (dict_code, dict_name, status)
                VALUES (?, ?, ?)
                """, request.getDictCode(), request.getDictName(), enabledStatus(request.getStatus()));
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateDictType(Long id, DictTypeRequest request) {
        jdbcTemplate.update("""
                UPDATE sys_dict_type
                SET dict_code = ?, dict_name = ?, status = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.getDictCode(), request.getDictName(), enabledStatus(request.getStatus()), id);
    }

    @Transactional
    public void deleteDictType(Long id) {
        String dictCode = jdbcTemplate.queryForObject("SELECT dict_code FROM sys_dict_type WHERE id = ?", String.class, id);
        jdbcTemplate.update("DELETE FROM sys_dict_item WHERE dict_code = ?", dictCode);
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE id = ?", id);
    }

    public List<DictItem> listDictItems(String dictCode) {
        return jdbcTemplate.query("""
                SELECT id, dict_code, item_label, item_value, sort_order, status
                FROM sys_dict_item
                WHERE (? IS NULL OR dict_code = ?)
                ORDER BY dict_code ASC, sort_order ASC, id ASC
                """, (rs, rowNum) -> new DictItem()
                .setId(rs.getLong("id"))
                .setDictCode(rs.getString("dict_code"))
                .setItemLabel(rs.getString("item_label"))
                .setItemValue(rs.getString("item_value"))
                .setSortOrder(rs.getInt("sort_order"))
                .setStatus(rs.getString("status")), dictCode, dictCode);
    }

    public Long createDictItem(DictItemRequest request) {
        jdbcTemplate.update("""
                INSERT INTO sys_dict_item (dict_code, item_label, item_value, sort_order, status)
                VALUES (?, ?, ?, ?, ?)
                """, request.getDictCode(), request.getItemLabel(), request.getItemValue(),
                defaultInt(request.getSortOrder()), enabledStatus(request.getStatus()));
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateDictItem(Long id, DictItemRequest request) {
        jdbcTemplate.update("""
                UPDATE sys_dict_item
                SET dict_code = ?, item_label = ?, item_value = ?, sort_order = ?, status = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.getDictCode(), request.getItemLabel(), request.getItemValue(),
                defaultInt(request.getSortOrder()), enabledStatus(request.getStatus()), id);
    }

    public void deleteDictItem(Long id) {
        jdbcTemplate.update("DELETE FROM sys_dict_item WHERE id = ?", id);
    }

    public List<ConfigItem> listConfigs() {
        return jdbcTemplate.query("""
                SELECT id, config_key, config_name, config_value, `sensitive`, remark
                FROM sys_config
                ORDER BY id ASC
                """, (rs, rowNum) -> {
            boolean sensitive = rs.getBoolean("sensitive");
            return new ConfigItem()
                    .setId(rs.getLong("id"))
                    .setConfigKey(rs.getString("config_key"))
                    .setConfigName(rs.getString("config_name"))
                    .setConfigValue(sensitive ? "******" : rs.getString("config_value"))
                    .setSensitive(sensitive)
                    .setRemark(rs.getString("remark"));
        });
    }

    public Long createConfig(ConfigRequest request) {
        jdbcTemplate.update("""
                INSERT INTO sys_config (config_key, config_name, config_value, `sensitive`, remark)
                VALUES (?, ?, ?, ?, ?)
                """, request.getConfigKey(), request.getConfigName(), request.getConfigValue(),
                defaultBoolean(request.getSensitive()), request.getRemark());
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateConfig(Long id, ConfigRequest request) {
        jdbcTemplate.update("""
                UPDATE sys_config
                SET config_key = ?, config_name = ?,
                    config_value = CASE WHEN ? = 1 THEN config_value ELSE ? END,
                    `sensitive` = ?, remark = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, request.getConfigKey(), request.getConfigName(),
                isMaskedSensitiveValue(request) ? 1 : 0, request.getConfigValue(),
                defaultBoolean(request.getSensitive()), request.getRemark(), id);
    }

    public void deleteConfig(Long id) {
        jdbcTemplate.update("DELETE FROM sys_config WHERE id = ?", id);
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        if (roleIds == null || roleIds.isEmpty()) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, 1)", userId);
            return;
        }
        for (Long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
    }

    private String buildUserWhere(String keyword, String status, List<Object> args) {
        List<String> conditions = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(username LIKE ? OR nickname LIKE ? OR mobile LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value);
            args.add(value);
            args.add(value);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            args.add(status.trim());
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private String buildLoginLogWhere(String username, Boolean success, List<Object> args) {
        List<String> conditions = new ArrayList<>();
        if (username != null && !username.isBlank()) {
            conditions.add("username LIKE ?");
            args.add("%" + username.trim() + "%");
        }
        if (success != null) {
            conditions.add("success = ?");
            args.add(success);
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
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
        List<Menu> allMenus = listMenusBySql("""
                SELECT id, parent_id, menu_type, menu_name, route_path, component, permission,
                       icon, sort_order, visible
                FROM sys_menu
                ORDER BY sort_order ASC, id ASC
                """);
        List<Long> ids = new ArrayList<>();
        collectMenuSubtreeIds(rootId, allMenus, ids);
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
        List<Dept> allDepts = jdbcTemplate.query("""
                SELECT id, tenant_id, parent_id, dept_name, sort_order, status, create_time
                FROM sys_dept
                ORDER BY sort_order ASC, id ASC
                """, this::mapDept);
        List<Long> ids = new ArrayList<>();
        collectDeptSubtreeIds(rootId, allDepts, ids);
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

    private List<Menu> listMenusBySql(String sql, Object... args) {
        return jdbcTemplate.query(sql, this::mapMenu, args);
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
            if (parent == null || menu.getParentId() == 0) {
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
            if (parent == null || dept.getParentId() == 0) {
                roots.add(dept);
            } else {
                parent.getChildren().add(dept);
            }
        }
        return roots;
    }

    private AdminUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new AdminUser()
                .setId(rs.getLong("id"))
                .setTenantId(rs.getLong("tenant_id"))
                .setDeptId(nullableLong(rs, "dept_id"))
                .setUsername(rs.getString("username"))
                .setNickname(rs.getString("nickname"))
                .setMobile(rs.getString("mobile"))
                .setEmail(rs.getString("email"))
                .setStatus(rs.getString("status"))
                .setPasswordHash(rs.getString("password_hash"))
                .setLastLoginTime(dateTimeString(rs, "last_login_time"))
                .setCreateTime(dateTimeString(rs, "create_time"));
    }

    private Tenant mapTenant(ResultSet rs, int rowNum) throws SQLException {
        return new Tenant()
                .setId(rs.getLong("id"))
                .setTenantCode(rs.getString("tenant_code"))
                .setTenantName(rs.getString("tenant_name"))
                .setStatus(rs.getString("status"))
                .setCreateTime(dateTimeString(rs, "create_time"));
    }

    private Dept mapDept(ResultSet rs, int rowNum) throws SQLException {
        return new Dept()
                .setId(rs.getLong("id"))
                .setTenantId(rs.getLong("tenant_id"))
                .setParentId(rs.getLong("parent_id"))
                .setDeptName(rs.getString("dept_name"))
                .setSortOrder(rs.getInt("sort_order"))
                .setStatus(rs.getString("status"))
                .setCreateTime(dateTimeString(rs, "create_time"));
    }

    private Role mapRole(ResultSet rs, int rowNum) throws SQLException {
        return new Role()
                .setId(rs.getLong("id"))
                .setRoleCode(rs.getString("role_code"))
                .setRoleName(rs.getString("role_name"))
                .setSortOrder(rs.getInt("sort_order"))
                .setStatus(rs.getString("status"))
                .setCreateTime(dateTimeString(rs, "create_time"));
    }

    private Menu mapMenu(ResultSet rs, int rowNum) throws SQLException {
        return new Menu()
                .setId(rs.getLong("id"))
                .setParentId(rs.getLong("parent_id"))
                .setMenuType(rs.getString("menu_type"))
                .setMenuName(rs.getString("menu_name"))
                .setRoutePath(rs.getString("route_path"))
                .setComponent(rs.getString("component"))
                .setPermission(rs.getString("permission"))
                .setIcon(rs.getString("icon"))
                .setSortOrder(rs.getInt("sort_order"))
                .setVisible(rs.getBoolean("visible"));
    }

    private LoginLog mapLoginLog(ResultSet rs, int rowNum) throws SQLException {
        return new LoginLog()
                .setId(rs.getLong("id"))
                .setUsername(rs.getString("username"))
                .setUserId(nullableLong(rs, "user_id"))
                .setClientIp(rs.getString("client_ip"))
                .setSuccess(rs.getBoolean("success"))
                .setMessage(rs.getString("message"))
                .setCreateTime(dateTimeString(rs, "create_time"));
    }

    private String dateTimeString(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime().toString().replace('T', ' ');
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String enabledStatus(String status) {
        return status == null || status.isBlank() ? "ENABLED" : status;
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Long defaultLong(Long value) {
        return value == null ? 0 : value;
    }

    private Boolean defaultVisible(Boolean value) {
        return value == null || value;
    }

    private Boolean defaultBoolean(Boolean value) {
        return value != null && value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean isMaskedSensitiveValue(ConfigRequest request) {
        return Boolean.TRUE.equals(request.getSensitive()) && "******".equals(request.getConfigValue());
    }
}
