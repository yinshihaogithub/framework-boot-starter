package com.framework.admin.system;

import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemModels.ConfigItem;
import com.framework.admin.system.AdminSystemModels.DictItem;
import com.framework.admin.system.AdminSystemModels.DictType;
import com.framework.admin.system.AdminSystemModels.Dept;
import com.framework.admin.system.AdminSystemModels.LoginLog;
import com.framework.admin.system.AdminSystemModels.Menu;
import com.framework.admin.system.AdminSystemModels.Role;
import com.framework.admin.system.AdminSystemModels.Tenant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 系统管理后台 Mapper，使用注解 SQL。
 */
@Mapper
public interface AdminSystemMapper {

    @Select("""
            SELECT id, tenant_id AS tenantId, dept_id AS deptId, username, nickname, mobile, email, status,
                   password_hash AS passwordHash,
                   DATE_FORMAT(last_login_time, '%Y-%m-%d %H:%i:%s') AS lastLoginTime,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM sys_user
            WHERE username = #{username}
            """)
    AdminUser findUserByUsername(@Param("username") String username);

    @Select("""
            SELECT id, tenant_id AS tenantId, dept_id AS deptId, username, nickname, mobile, email, status,
                   password_hash AS passwordHash,
                   DATE_FORMAT(last_login_time, '%Y-%m-%d %H:%i:%s') AS lastLoginTime,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM sys_user
            WHERE id = #{id}
            """)
    AdminUser findUserById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id, tenant_id AS tenantId, dept_id AS deptId, username, nickname, mobile, email, status,
                   password_hash AS passwordHash,
                   DATE_FORMAT(last_login_time, '%Y-%m-%d %H:%i:%s') AS lastLoginTime,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM sys_user
            <where>
                <if test="keywordLike != null">
                    AND (username LIKE #{keywordLike} OR nickname LIKE #{keywordLike} OR mobile LIKE #{keywordLike})
                </if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<AdminUser> listUsers(@Param("keywordLike") String keywordLike,
                              @Param("status") String status,
                              @Param("offset") int offset,
                              @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_user
            <where>
                <if test="keywordLike != null">
                    AND (username LIKE #{keywordLike} OR nickname LIKE #{keywordLike} OR mobile LIKE #{keywordLike})
                </if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countUsers(@Param("keywordLike") String keywordLike, @Param("status") String status);

    @Insert("""
            INSERT INTO sys_user (tenant_id, dept_id, username, nickname, mobile, email, password_hash, status)
            VALUES (#{tenantId}, #{deptId}, #{username}, #{nickname}, #{mobile}, #{email}, #{passwordHash}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(AdminUser user);

    @Update("""
            UPDATE sys_user
            SET dept_id = #{deptId}, nickname = #{nickname}, mobile = #{mobile}, email = #{email},
                status = #{status}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateUser(AdminUser user);

    @Update("UPDATE sys_user SET status = #{status}, update_time = CURRENT_TIMESTAMP WHERE id = #{userId}")
    int updateUserStatus(@Param("userId") Long userId, @Param("status") String status);

    @Update("UPDATE sys_user SET password_hash = #{passwordHash}, update_time = CURRENT_TIMESTAMP WHERE id = #{userId}")
    int resetPassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_role WHERE role_id = #{roleId}")
    int deleteUserRolesByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user WHERE id = #{userId}")
    int deleteUser(@Param("userId") Long userId);

    @Update("UPDATE sys_user SET last_login_time = CURRENT_TIMESTAMP WHERE id = #{userId}")
    int updateLastLogin(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Insert("""
            INSERT INTO sys_login_log (username, user_id, client_ip, success, message)
            VALUES (#{username}, #{userId}, #{clientIp}, #{success}, #{message})
            """)
    int insertLoginLog(LoginLog log);

    @Select("""
            <script>
            SELECT id, username, user_id AS userId, client_ip AS clientIp, success, message,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM sys_login_log
            <where>
                <if test="usernameLike != null">AND username LIKE #{usernameLike}</if>
                <if test="success != null">AND success = #{success}</if>
            </where>
            ORDER BY create_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<LoginLog> listLoginLogs(@Param("usernameLike") String usernameLike,
                                 @Param("success") Boolean success,
                                 @Param("offset") int offset,
                                 @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_login_log
            <where>
                <if test="usernameLike != null">AND username LIKE #{usernameLike}</if>
                <if test="success != null">AND success = #{success}</if>
            </where>
            </script>
            """)
    long countLoginLogs(@Param("usernameLike") String usernameLike, @Param("success") Boolean success);

    @Select("""
            <script>
            SELECT id, tenant_code AS tenantCode, tenant_name AS tenantName, status,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM sys_tenant
            <where>
                <if test="keywordLike != null">
                    AND (tenant_code LIKE #{keywordLike} OR tenant_name LIKE #{keywordLike})
                </if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Tenant> listTenants(@Param("keywordLike") String keywordLike,
                             @Param("status") String status,
                             @Param("offset") int offset,
                             @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_tenant
            <where>
                <if test="keywordLike != null">
                    AND (tenant_code LIKE #{keywordLike} OR tenant_name LIKE #{keywordLike})
                </if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countTenants(@Param("keywordLike") String keywordLike, @Param("status") String status);

    @Select("""
            <script>
            SELECT id, tenant_code AS tenantCode, tenant_name AS tenantName, status,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM sys_tenant
            WHERE status = 'ENABLED'
            <if test="keywordLike != null">
                AND (tenant_code LIKE #{keywordLike} OR tenant_name LIKE #{keywordLike})
            </if>
            ORDER BY id ASC
            LIMIT #{limit}
            </script>
            """)
    List<Tenant> listTenantOptions(@Param("keywordLike") String keywordLike, @Param("limit") int limit);

    @Insert("""
            INSERT INTO sys_tenant (tenant_code, tenant_name, status)
            VALUES (#{tenantCode}, #{tenantName}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTenant(Tenant tenant);

    @Update("""
            UPDATE sys_tenant
            SET tenant_code = #{tenantCode}, tenant_name = #{tenantName}, status = #{status},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateTenant(Tenant tenant);

    @Select("SELECT COUNT(*) FROM sys_user WHERE tenant_id = #{tenantId}")
    long countUsersByTenant(@Param("tenantId") Long tenantId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_role
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countRolesByIds(@Param("ids") List<Long> ids);

    @Delete("DELETE FROM sys_dept WHERE tenant_id = #{tenantId}")
    int deleteDeptsByTenantId(@Param("tenantId") Long tenantId);

    @Delete("DELETE FROM sys_tenant WHERE id = #{id}")
    int deleteTenant(@Param("id") Long id);

    @Select("""
            SELECT id, tenant_id AS tenantId, parent_id AS parentId, dept_name AS deptName, sort_order AS sortOrder,
                   status, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM sys_dept
            WHERE tenant_id = #{tenantId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<Dept> listDeptsByTenantId(@Param("tenantId") Long tenantId);

    @Select("""
            WITH RECURSIVE dept_tree AS (
                SELECT id
                FROM sys_dept
                WHERE id = #{rootId}
                UNION ALL
                SELECT d.id
                FROM sys_dept d
                JOIN dept_tree dt ON d.parent_id = dt.id
            )
            SELECT id
            FROM dept_tree
            ORDER BY id ASC
            """)
    List<Long> listDeptSubtreeIds(@Param("rootId") Long rootId);

    @Insert("""
            INSERT INTO sys_dept (tenant_id, parent_id, dept_name, sort_order, status)
            VALUES (#{tenantId}, #{parentId}, #{deptName}, #{sortOrder}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDept(Dept dept);

    @Update("""
            UPDATE sys_dept
            SET tenant_id = #{tenantId}, parent_id = #{parentId}, dept_name = #{deptName},
                sort_order = #{sortOrder}, status = #{status}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDept(Dept dept);

    @Update("""
            <script>
            UPDATE sys_user SET dept_id = NULL WHERE dept_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int clearUserDeptIds(@Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE FROM sys_dept WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteDeptIds(@Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT id, role_code AS roleCode, role_name AS roleName, sort_order AS sortOrder, status,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM sys_role
            <where>
                <if test="keywordLike != null">
                    AND (role_code LIKE #{keywordLike} OR role_name LIKE #{keywordLike})
                </if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            ORDER BY sort_order ASC, id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Role> listRoles(@Param("keywordLike") String keywordLike,
                         @Param("status") String status,
                         @Param("offset") int offset,
                         @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_role
            <where>
                <if test="keywordLike != null">
                    AND (role_code LIKE #{keywordLike} OR role_name LIKE #{keywordLike})
                </if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countRoles(@Param("keywordLike") String keywordLike, @Param("status") String status);

    @Select("""
            <script>
            SELECT id, role_code AS roleCode, role_name AS roleName, sort_order AS sortOrder, status,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM sys_role
            <where>
                <if test="keywordLike != null">
                    AND (role_code LIKE #{keywordLike} OR role_name LIKE #{keywordLike})
                </if>
            </where>
            ORDER BY sort_order ASC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<Role> listRoleOptions(@Param("keywordLike") String keywordLike, @Param("limit") int limit);

    @Insert("""
            INSERT INTO sys_role (tenant_id, role_code, role_name, sort_order, status)
            VALUES (#{tenantId}, #{roleCode}, #{roleName}, #{sortOrder}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRole(RoleRow role);

    @Update("""
            UPDATE sys_role
            SET role_code = #{roleCode}, role_name = #{roleName}, sort_order = #{sortOrder},
                status = #{status}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateRole(Role role);

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteRoleMenus(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role WHERE id = #{roleId}")
    int deleteRole(@Param("roleId") Long roleId);

    @Select("SELECT COUNT(*) FROM sys_role WHERE id = #{roleId}")
    long countRoleById(@Param("roleId") Long roleId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_menu
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countMenusByIds(@Param("ids") List<Long> ids);

    @Select("""
            SELECT menu_id
            FROM sys_role_menu
            WHERE role_id = #{roleId}
            ORDER BY menu_id ASC
            """)
    List<Long> listMenuIdsByRoleId(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (#{roleId}, #{menuId})")
    int insertRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    @Select("""
            SELECT id, parent_id AS parentId, menu_type AS menuType, menu_name AS menuName, route_path AS routePath,
                   component, permission, icon, sort_order AS sortOrder, visible
            FROM sys_menu
            ORDER BY sort_order ASC, id ASC
            """)
    List<Menu> listAllMenus();

    @Select("""
            WITH RECURSIVE menu_tree AS (
                SELECT id
                FROM sys_menu
                WHERE id = #{rootId}
                UNION ALL
                SELECT m.id
                FROM sys_menu m
                JOIN menu_tree mt ON m.parent_id = mt.id
            )
            SELECT id
            FROM menu_tree
            ORDER BY id ASC
            """)
    List<Long> listMenuSubtreeIds(@Param("rootId") Long rootId);

    @Select("""
            SELECT DISTINCT m.id, m.parent_id AS parentId, m.menu_type AS menuType, m.menu_name AS menuName,
                   m.route_path AS routePath, m.component, m.permission, m.icon, m.sort_order AS sortOrder, m.visible
            FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.status = 'ENABLED' AND m.visible = 1 AND m.menu_type <> 'BUTTON'
            ORDER BY m.sort_order ASC, m.id ASC
            """)
    List<Menu> listMenusByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO sys_menu (parent_id, menu_type, menu_name, route_path, component, permission, icon, sort_order, visible)
            VALUES (#{parentId}, #{menuType}, #{menuName}, #{routePath}, #{component}, #{permission}, #{icon}, #{sortOrder}, #{visible})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMenu(Menu menu);

    @Update("""
            UPDATE sys_menu
            SET parent_id = #{parentId}, menu_type = #{menuType}, menu_name = #{menuName}, route_path = #{routePath},
                component = #{component}, permission = #{permission}, icon = #{icon}, sort_order = #{sortOrder},
                visible = #{visible}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateMenu(Menu menu);

    @Delete("""
            <script>
            DELETE FROM sys_role_menu WHERE menu_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteRoleMenusByMenuIds(@Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE FROM sys_menu WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteMenuIds(@Param("ids") List<Long> ids);

    @Select("""
            SELECT r.role_code
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.status = 'ENABLED'
            ORDER BY r.sort_order ASC, r.id ASC
            """)
    List<String> listRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT role_id
            FROM sys_user_role
            WHERE user_id = #{userId}
            ORDER BY role_id ASC
            """)
    List<Long> listRoleIdsByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT user_id
            FROM sys_user_role
            WHERE role_id = #{roleId}
            ORDER BY user_id ASC
            """)
    List<Long> listUserIdsByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT DISTINCT m.permission
            FROM sys_menu m
            JOIN sys_role_menu rm ON rm.menu_id = m.id
            JOIN sys_user_role ur ON ur.role_id = rm.role_id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.status = 'ENABLED'
              AND m.permission IS NOT NULL AND m.permission <> ''
            ORDER BY m.permission ASC
            """)
    List<String> listPermissionsByUserId(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT id, dict_code AS dictCode, dict_name AS dictName, status
            FROM sys_dict_type
            <where>
                <if test="keywordLike != null">
                    AND (dict_code LIKE #{keywordLike} OR dict_name LIKE #{keywordLike})
                </if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<DictType> listDictTypes(@Param("keywordLike") String keywordLike,
                                 @Param("status") String status,
                                 @Param("offset") int offset,
                                 @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_dict_type
            <where>
                <if test="keywordLike != null">
                    AND (dict_code LIKE #{keywordLike} OR dict_name LIKE #{keywordLike})
                </if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countDictTypes(@Param("keywordLike") String keywordLike, @Param("status") String status);

    @Select("""
            <script>
            SELECT id, dict_code AS dictCode, dict_name AS dictName, status
            FROM sys_dict_type
            <where>
                <if test="keywordLike != null">
                    AND (dict_code LIKE #{keywordLike} OR dict_name LIKE #{keywordLike})
                </if>
            </where>
            ORDER BY id ASC
            LIMIT #{limit}
            </script>
            """)
    List<DictType> listDictTypeOptions(@Param("keywordLike") String keywordLike, @Param("limit") int limit);

    @Insert("""
            INSERT INTO sys_dict_type (dict_code, dict_name, status)
            VALUES (#{dictCode}, #{dictName}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDictType(DictType dictType);

    @Update("""
            UPDATE sys_dict_type
            SET dict_code = #{dictCode}, dict_name = #{dictName}, status = #{status}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDictType(DictType dictType);

    @Select("SELECT dict_code FROM sys_dict_type WHERE id = #{id}")
    String findDictCodeById(@Param("id") Long id);

    @Delete("DELETE FROM sys_dict_item WHERE dict_code = #{dictCode}")
    int deleteDictItemsByCode(@Param("dictCode") String dictCode);

    @Delete("DELETE FROM sys_dict_type WHERE id = #{id}")
    int deleteDictType(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id, dict_code AS dictCode, item_label AS itemLabel, item_value AS itemValue,
                   sort_order AS sortOrder, status
            FROM sys_dict_item
            <where>
                <if test="dictCode != null">AND dict_code = #{dictCode}</if>
                <if test="keywordLike != null">
                    AND (item_label LIKE #{keywordLike} OR item_value LIKE #{keywordLike})
                </if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            ORDER BY dict_code ASC, sort_order ASC, id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<DictItem> listDictItems(@Param("dictCode") String dictCode,
                                 @Param("keywordLike") String keywordLike,
                                 @Param("status") String status,
                                 @Param("offset") int offset,
                                 @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_dict_item
            <where>
                <if test="dictCode != null">AND dict_code = #{dictCode}</if>
                <if test="keywordLike != null">
                    AND (item_label LIKE #{keywordLike} OR item_value LIKE #{keywordLike})
                </if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countDictItems(@Param("dictCode") String dictCode,
                        @Param("keywordLike") String keywordLike,
                        @Param("status") String status);

    @Insert("""
            INSERT INTO sys_dict_item (dict_code, item_label, item_value, sort_order, status)
            VALUES (#{dictCode}, #{itemLabel}, #{itemValue}, #{sortOrder}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDictItem(DictItem dictItem);

    @Update("""
            UPDATE sys_dict_item
            SET dict_code = #{dictCode}, item_label = #{itemLabel}, item_value = #{itemValue},
                sort_order = #{sortOrder}, status = #{status}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDictItem(DictItem dictItem);

    @Delete("DELETE FROM sys_dict_item WHERE id = #{id}")
    int deleteDictItem(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id, config_key AS configKey, config_name AS configName, config_value AS configValue,
                   `sensitive`, remark
            FROM sys_config
            <where>
                <if test="keywordLike != null">
                    AND (config_key LIKE #{keywordLike}
                        OR config_name LIKE #{keywordLike}
                        OR remark LIKE #{keywordLike})
                </if>
            </where>
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<ConfigItem> listConfigs(@Param("keywordLike") String keywordLike,
                                 @Param("offset") int offset,
                                 @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_config
            <where>
                <if test="keywordLike != null">
                    AND (config_key LIKE #{keywordLike}
                        OR config_name LIKE #{keywordLike}
                        OR remark LIKE #{keywordLike})
                </if>
            </where>
            </script>
            """)
    long countConfigs(@Param("keywordLike") String keywordLike);

    @Select("""
            SELECT id, config_key AS configKey, config_name AS configName, config_value AS configValue,
                   `sensitive`, remark
            FROM sys_config
            WHERE config_key = #{configKey}
            """)
    ConfigItem findConfigByKey(@Param("configKey") String configKey);

    @Insert("""
            INSERT INTO sys_config (config_key, config_name, config_value, `sensitive`, remark)
            VALUES (#{configKey}, #{configName}, #{configValue}, #{sensitive}, #{remark})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertConfig(ConfigItem config);

    @Update("""
            UPDATE sys_config
            SET config_key = #{config.configKey}, config_name = #{config.configName},
                config_value = CASE WHEN #{preserveValue} = 1 THEN config_value ELSE #{config.configValue} END,
                `sensitive` = #{config.sensitive}, remark = #{config.remark}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{config.id}
            """)
    int updateConfig(@Param("config") ConfigItem config, @Param("preserveValue") boolean preserveValue);

    @Update("""
            UPDATE sys_config
            SET config_value = #{configValue}, update_time = CURRENT_TIMESTAMP
            WHERE config_key = #{configKey}
            """)
    int updateConfigValue(@Param("configKey") String configKey, @Param("configValue") String configValue);

    @Delete("DELETE FROM sys_config WHERE id = #{id}")
    int deleteConfig(@Param("id") Long id);

    class RoleRow extends Role {
        private Long tenantId;

        public Long getTenantId() {
            return tenantId;
        }

        public RoleRow setTenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }
    }
}
