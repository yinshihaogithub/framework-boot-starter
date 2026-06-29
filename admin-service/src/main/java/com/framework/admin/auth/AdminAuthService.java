package com.framework.admin.auth;

import com.framework.admin.audit.AdminAuditService;
import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.service.LoginSecurityService;
import com.framework.auth.service.SessionManager;
import com.framework.auth.util.PasswordValidator;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.crypto.util.PasswordUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 后台认证服务。
 */
@Service
public class AdminAuthService {

    private final AdminSystemRepository systemRepository;
    private final SessionManager sessionManager;
    private final ObjectProvider<LoginSecurityService> loginSecurityServiceProvider;
    private final AdminAuditService auditService;

    public AdminAuthService(AdminSystemRepository systemRepository,
                            SessionManager sessionManager,
                            ObjectProvider<LoginSecurityService> loginSecurityServiceProvider) {
        this(systemRepository, sessionManager, loginSecurityServiceProvider, null);
    }

    @Autowired
    public AdminAuthService(AdminSystemRepository systemRepository,
                            SessionManager sessionManager,
                            ObjectProvider<LoginSecurityService> loginSecurityServiceProvider,
                            AdminAuditService auditService) {
        this.systemRepository = systemRepository;
        this.sessionManager = sessionManager;
        this.loginSecurityServiceProvider = loginSecurityServiceProvider;
        this.auditService = auditService;
    }

    public Result<AdminAuthController.LoginResponse> login(AdminAuthController.LoginRequest request, String clientIp) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "用户名和密码不能为空");
        }
        String username = request.getUsername().trim();
        LoginSecurityService loginSecurity = loginSecurityServiceProvider.getIfAvailable();
        try {
            if (loginSecurity != null) {
                loginSecurity.checkAccountLocked(username);
            }
            AdminUser user = systemRepository.findUserByUsername(username).orElse(null);
            if (user == null || !"ENABLED".equals(user.getStatus())
                    || !PasswordUtils.verify(request.getPassword(), user.getPasswordHash())) {
                if (loginSecurity != null) {
                    loginSecurity.recordLoginFailure(username);
                }
                systemRepository.insertLoginLog(username, user == null ? null : user.getId(),
                        clientIp, false, "账号或密码错误");
                return Result.fail(ResultCode.LOGIN_FAIL);
            }
            if (loginSecurity != null) {
                loginSecurity.clearLoginFailure(username);
            }
            String deviceId = isBlank(request.getDeviceId()) ? "admin-web" : request.getDeviceId().trim();
            LoginUser loginUser = sessionManager.createSession(
                    user.getId(),
                    user.getUsername(),
                    String.valueOf(user.getTenantId()),
                    deviceId,
                    user.getRoles().toArray(String[]::new),
                    user.getPermissions().toArray(String[]::new));
            systemRepository.updateLastLogin(user.getId());
            systemRepository.insertLoginLog(username, user.getId(), clientIp, true, "登录成功");
            return Result.success(new AdminAuthController.LoginResponse()
                    .setAccessToken(loginUser.getAccessToken())
                    .setUser(toCurrentUser(user))
                    .setMenus(systemRepository.listMenusByUserId(user.getId())));
        } catch (Exception e) {
            systemRepository.insertLoginLog(username, null, clientIp, false, e.getMessage());
            return Result.fail(ResultCode.LOGIN_FAIL.getCode(), e.getMessage());
        }
    }

    public Result<AdminAuthController.CurrentUser> me() {
        LoginUser loginUser = UserContextHolder.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        AdminUser user = systemRepository.findUserById(loginUser.getUserId()).orElse(null);
        if (user == null) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        return Result.success(toCurrentUser(user).setMenus(systemRepository.listMenusByUserId(user.getId())));
    }

    public Result<String> logout(String authorization) {
        if (authorization != null && authorization.startsWith(FrameworkConstants.TOKEN_PREFIX)) {
            sessionManager.logout(authorization.substring(FrameworkConstants.TOKEN_PREFIX.length()).trim());
        }
        return Result.success("已退出");
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
        AdminUser user = systemRepository.findUserById(loginUser.getUserId()).orElse(null);
        if (user == null || !"ENABLED".equals(user.getStatus())) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        if (!PasswordUtils.verify(request.getOldPassword(), user.getPasswordHash())) {
            return Result.fail(ResultCode.LOGIN_FAIL.getCode(), "原密码不正确");
        }
        if (PasswordUtils.verify(request.getNewPassword(), user.getPasswordHash())) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "新密码不能与原密码相同");
        }
        systemRepository.resetPassword(user.getId(), PasswordUtils.hash(request.getNewPassword()));
        systemRepository.updateConfigValue("admin.default.password.changed", "true");
        sessionManager.forceLogoutAll(user.getId());
        if (auditService != null) {
            auditService.success(servletRequest, "账号安全", "修改密码", "UPDATE",
                    auditService.params("userId", user.getId(), "username", user.getUsername()));
        }
        return Result.success("密码已修改，请重新登录");
    }

    private AdminAuthController.CurrentUser toCurrentUser(AdminUser user) {
        return new AdminAuthController.CurrentUser()
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setNickname(user.getNickname())
                .setTenantId(user.getTenantId())
                .setRoles(user.getRoles())
                .setPermissions(user.getPermissions());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
