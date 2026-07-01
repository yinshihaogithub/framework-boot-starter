package com.framework.admin.auth;

import com.framework.admin.audit.AdminAuditService;
import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemModels.Menu;
import com.framework.admin.system.AdminSystemMapperSupport;
import com.framework.admin.support.AdminTextSupport;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.service.LoginSecurityService;
import com.framework.auth.service.SessionManager;
import com.framework.auth.util.PasswordValidator;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.exception.BusinessException;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.crypto.util.PasswordUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 后台认证服务。
 */
@Slf4j
@Service
public class AdminAuthService {

    private static final int USERNAME_MAX_LENGTH = 64;
    private static final int DEVICE_ID_MAX_LENGTH = 64;
    private static final int CLIENT_IP_MAX_LENGTH = 64;
    private static final String DEFAULT_DEVICE_ID = "admin-web";
    private static final String DEFAULT_PASSWORD_CHANGED_CONFIG_KEY = "admin.default.password.changed";

    private final AdminSystemMapperSupport systemMapperSupport;
    private final SessionManager sessionManager;
    private final ObjectProvider<LoginSecurityService> loginSecurityServiceProvider;
    private final AdminAuditService auditService;

    public AdminAuthService(AdminSystemMapperSupport systemMapperSupport,
                            SessionManager sessionManager,
                            ObjectProvider<LoginSecurityService> loginSecurityServiceProvider) {
        this(systemMapperSupport, sessionManager, loginSecurityServiceProvider, null);
    }

    @Autowired
    public AdminAuthService(AdminSystemMapperSupport systemMapperSupport,
                            SessionManager sessionManager,
                            ObjectProvider<LoginSecurityService> loginSecurityServiceProvider,
                            AdminAuditService auditService) {
        this.systemMapperSupport = systemMapperSupport;
        this.sessionManager = sessionManager;
        this.loginSecurityServiceProvider = loginSecurityServiceProvider;
        this.auditService = auditService;
    }

    public Result<AdminAuthController.LoginResponse> login(AdminAuthController.LoginRequest request, String clientIp) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户名和密码不能为空");
        }
        String username = AdminTextSupport.trimBoundarySpace(request.getUsername());
        if (username.length() > USERNAME_MAX_LENGTH) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户名长度不能超过64个字符");
        }
        String deviceId = normalizeDeviceId(request.getDeviceId());
        if (deviceId == null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "设备标识长度不能超过64个字符");
        }
        String safeClientIp = truncate(clientIp, CLIENT_IP_MAX_LENGTH);
        LoginSecurityService loginSecurity = loginSecurityService();
        try {
            if (loginSecurity != null) {
                loginSecurity.checkAccountLocked(username);
            }
            AdminUser user = systemMapperSupport.findUserByUsername(username).orElse(null);
            if (user == null || !"ENABLED".equals(user.getStatus())
                    || !PasswordUtils.verify(request.getPassword(), user.getPasswordHash())) {
                recordLoginFailure(loginSecurity, username);
                insertLoginLog(username, user == null ? null : user.getId(),
                        safeClientIp, false, "账号或密码错误");
                return Result.fail(ResultCode.LOGIN_FAIL);
            }
            clearLoginFailure(loginSecurity, username);
            List<Menu> menus = safeList(systemMapperSupport.listMenusByUserId(user.getId()));
            LoginUser loginUser = sessionManager.createSession(
                    user.getId(),
                    user.getUsername(),
                    String.valueOf(user.getTenantId()),
                    deviceId,
                    safeList(user.getRoles()).toArray(String[]::new),
                    safeList(user.getPermissions()).toArray(String[]::new));
            updateLastLogin(user.getId());
            insertLoginLog(username, user.getId(), safeClientIp, true, "登录成功");
            return Result.success(new AdminAuthController.LoginResponse()
                    .setAccessToken(loginUser.getAccessToken())
                    .setUser(toCurrentUser(user))
                    .setMenus(menus));
        } catch (BusinessException e) {
            insertLoginLog(username, null, safeClientIp, false, e.getMessage());
            return Result.fail(e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            insertLoginLog(username, null, safeClientIp, false, "登录服务暂不可用");
            return serviceError("登录", "登录服务暂不可用", e);
        }
    }

    public Result<AdminAuthController.CurrentUser> me() {
        LoginUser loginUser = UserContextHolder.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        try {
            AdminUser user = systemMapperSupport.findUserById(loginUser.getUserId()).orElse(null);
            if (user == null) {
                return Result.fail(ResultCode.UNAUTHORIZED);
            }
            return Result.success(toCurrentUser(user).setMenus(systemMapperSupport.listMenusByUserId(user.getId())));
        } catch (RuntimeException e) {
            return serviceError("查询当前用户", "当前用户查询失败", e);
        }
    }

    public Result<String> logout(String authorization) {
        try {
            if (authorization != null && authorization.startsWith(FrameworkConstants.TOKEN_PREFIX)) {
                sessionManager.logout(AdminTextSupport.trimBoundarySpace(
                        authorization.substring(FrameworkConstants.TOKEN_PREFIX.length())));
            }
            return Result.success("已退出");
        } catch (RuntimeException e) {
            return serviceError("退出登录", "退出登录失败", e);
        }
    }

    public Result<String> changePassword(AdminAuthController.ChangePasswordRequest request,
                                         HttpServletRequest servletRequest) {
        LoginUser loginUser = UserContextHolder.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        if (request == null || isBlank(request.getOldPassword()) || isBlank(request.getNewPassword())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "原密码和新密码不能为空");
        }
        String passwordError = PasswordValidator.validateStrong(request.getNewPassword());
        if (passwordError != null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), passwordError);
        }
        try {
            AdminUser user = systemMapperSupport.findUserById(loginUser.getUserId()).orElse(null);
            if (user == null || !"ENABLED".equals(user.getStatus())) {
                return Result.fail(ResultCode.UNAUTHORIZED);
            }
            if (!PasswordUtils.verify(request.getOldPassword(), user.getPasswordHash())) {
                return Result.fail(ResultCode.LOGIN_FAIL.getCode(), "原密码不正确");
            }
            if (PasswordUtils.verify(request.getNewPassword(), user.getPasswordHash())) {
                return Result.fail(ResultCode.PARAM_ERROR.getCode(), "新密码不能与原密码相同");
            }
            if (!systemMapperSupport.resetPasswordAndUpdateConfigValue(user.getId(),
                    PasswordUtils.hash(request.getNewPassword()), DEFAULT_PASSWORD_CHANGED_CONFIG_KEY, "true")) {
                return Result.fail(ResultCode.UNAUTHORIZED);
            }
            forceLogoutAll(user.getId());
            auditChangePassword(servletRequest, user);
            return Result.success("密码已修改，请重新登录");
        } catch (RuntimeException e) {
            return serviceError("修改密码", "密码修改失败", e);
        }
    }

    private LoginSecurityService loginSecurityService() {
        if (loginSecurityServiceProvider == null) {
            return null;
        }
        try {
            return loginSecurityServiceProvider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("[后台认证] 登录安全服务不可用 error={}", e.getMessage());
            return null;
        }
    }

    private void recordLoginFailure(LoginSecurityService loginSecurity, String username) {
        if (loginSecurity == null) {
            return;
        }
        try {
            loginSecurity.recordLoginFailure(username);
        } catch (RuntimeException e) {
            log.warn("[后台认证] 登录失败次数记录失败 username={}, error={}", username, e.getMessage());
        }
    }

    private void clearLoginFailure(LoginSecurityService loginSecurity, String username) {
        if (loginSecurity == null) {
            return;
        }
        try {
            loginSecurity.clearLoginFailure(username);
        } catch (RuntimeException e) {
            log.warn("[后台认证] 登录失败次数清理失败 username={}, error={}", username, e.getMessage());
        }
    }

    private void updateLastLogin(Long userId) {
        try {
            systemMapperSupport.updateLastLogin(userId);
        } catch (RuntimeException e) {
            log.warn("[后台认证] 最近登录时间更新失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    private void insertLoginLog(String username, Long userId, String clientIp, boolean success, String message) {
        try {
            systemMapperSupport.insertLoginLog(username, userId, clientIp, success, message);
        } catch (RuntimeException e) {
            log.warn("[后台认证] 登录日志写入失败 username={}, success={}, error={}",
                    username, success, e.getMessage());
        }
    }

    private void forceLogoutAll(Long userId) {
        try {
            sessionManager.forceLogoutAll(userId);
        } catch (RuntimeException e) {
            log.warn("[后台认证] 修改密码后强制下线失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    private void auditChangePassword(HttpServletRequest servletRequest, AdminUser user) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.success(servletRequest, "账号安全", "修改密码", "UPDATE",
                    auditService.params("userId", user.getId(), "username", user.getUsername(),
                            "operator", currentOperatorName(user)));
        } catch (RuntimeException e) {
            log.warn("[后台认证] 修改密码审计日志写入失败 userId={}, error={}", user.getId(), e.getMessage());
        }
    }

    private String currentOperatorName(AdminUser user) {
        String username = AdminTextSupport.trimToNull(UserContextHolder.getUsername());
        if (username != null) {
            return username;
        }
        String userUsername = user == null ? null : AdminTextSupport.trimToNull(user.getUsername());
        return userUsername == null ? "admin" : userUsername;
    }

    private <T> Result<T> serviceError(String action, String message, RuntimeException exception) {
        log.warn("[后台认证] {}失败 error={}", action, exception.getMessage());
        return Result.fail(ResultCode.SERVICE_ERROR.getCode(), message);
    }

    private AdminAuthController.CurrentUser toCurrentUser(AdminUser user) {
        return new AdminAuthController.CurrentUser()
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setNickname(user.getNickname())
                .setTenantId(user.getTenantId())
                .setRoles(safeList(user.getRoles()))
                .setPermissions(safeList(user.getPermissions()));
    }

    private boolean isBlank(String value) {
        return !AdminTextSupport.hasText(value);
    }

    private String normalizeDeviceId(String deviceId) {
        String normalized = AdminTextSupport.trimToNull(deviceId);
        if (normalized == null) {
            return DEFAULT_DEVICE_ID;
        }
        return normalized.length() > DEVICE_ID_MAX_LENGTH ? null : normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
