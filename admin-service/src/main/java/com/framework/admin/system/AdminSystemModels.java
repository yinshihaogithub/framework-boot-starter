package com.framework.admin.system;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * DTOs used by admin system APIs.
 */
public class AdminSystemModels {

    @Data
    @Accessors(chain = true)
    public static class AdminUser {
        private Long id;
        private Long tenantId;
        private Long deptId;
        private String username;
        private String nickname;
        private String mobile;
        private String email;
        private String status;
        private String passwordHash;
        private String lastLoginTime;
        private String createTime;
        private Long loginFailCount;
        private Boolean loginLocked;
        private Long loginLockTtlMinutes;
        private List<Long> roleIds;
        private List<String> roles;
        private List<String> permissions;
    }

    @Data
    @Accessors(chain = true)
    public static class Role {
        private Long id;
        private String roleCode;
        private String roleName;
        private Integer sortOrder;
        private String status;
        private String createTime;
    }

    @Data
    @Accessors(chain = true)
    public static class Tenant {
        private Long id;
        private String tenantCode;
        private String tenantName;
        private String status;
        private String createTime;
    }

    @Data
    @Accessors(chain = true)
    public static class Dept {
        private Long id;
        private Long tenantId;
        private Long parentId;
        private String deptName;
        private Integer sortOrder;
        private String status;
        private String createTime;
        private List<Dept> children;
    }

    @Data
    @Accessors(chain = true)
    public static class Menu {
        private Long id;
        private Long parentId;
        private String menuType;
        private String menuName;
        private String routePath;
        private String component;
        private String permission;
        private String icon;
        private Integer sortOrder;
        private Boolean visible;
        private List<Menu> children;
    }

    @Data
    @Accessors(chain = true)
    public static class DictType {
        private Long id;
        private String dictCode;
        private String dictName;
        private String status;
    }

    @Data
    @Accessors(chain = true)
    public static class DictItem {
        private Long id;
        private String dictCode;
        private String itemLabel;
        private String itemValue;
        private Integer sortOrder;
        private String status;
    }

    @Data
    @Accessors(chain = true)
    public static class ConfigItem {
        private Long id;
        private String configKey;
        private String configName;
        private String configValue;
        private Boolean sensitive;
        private String remark;
    }

    @Data
    @Accessors(chain = true)
    public static class LoginLog {
        private Long id;
        private String username;
        private Long userId;
        private String clientIp;
        private Boolean success;
        private String message;
        private String createTime;
    }

    @Data
    public static class UserCreateRequest {
        private String username;
        private String password;
        private String nickname;
        private String mobile;
        private String email;
        private Long deptId;
        private List<Long> roleIds;
    }

    @Data
    public static class UserStatusRequest {
        private String status;
    }

    @Data
    public static class UserUpdateRequest {
        private Long deptId;
        private String nickname;
        private String mobile;
        private String email;
        private String status;
        private List<Long> roleIds;
    }

    @Data
    public static class ResetPasswordRequest {
        private String password;
    }

    @Data
    public static class RoleRequest {
        private String roleCode;
        private String roleName;
        private Integer sortOrder;
        private String status;
    }

    @Data
    public static class TenantRequest {
        private String tenantCode;
        private String tenantName;
        private String status;
    }

    @Data
    public static class DeptRequest {
        private Long tenantId;
        private Long parentId;
        private String deptName;
        private Integer sortOrder;
        private String status;
    }

    @Data
    public static class RoleMenuRequest {
        private List<Long> menuIds;
    }

    @Data
    public static class MenuRequest {
        private Long parentId;
        private String menuType;
        private String menuName;
        private String routePath;
        private String component;
        private String permission;
        private String icon;
        private Integer sortOrder;
        private Boolean visible;
    }

    @Data
    public static class DictTypeRequest {
        private String dictCode;
        private String dictName;
        private String status;
    }

    @Data
    public static class DictItemRequest {
        private String dictCode;
        private String itemLabel;
        private String itemValue;
        private Integer sortOrder;
        private String status;
    }

    @Data
    public static class ConfigRequest {
        private String configKey;
        private String configName;
        private String configValue;
        private Boolean sensitive;
        private String remark;
    }
}
