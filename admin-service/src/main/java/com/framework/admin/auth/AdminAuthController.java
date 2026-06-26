package com.framework.admin.auth;

import com.framework.admin.system.AdminSystemModels.AdminUser;
import com.framework.admin.system.AdminSystemModels.Menu;
import com.framework.admin.system.AdminSystemRepository;
import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.service.LoginSecurityService;
import com.framework.auth.service.SessionManager;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.framework.crypto.util.PasswordUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/auth")
@Tag(name = "后台认证", description = "登录、退出和当前用户")
public class AdminAuthController {

    private final AdminSystemRepository systemRepository;
    private final SessionManager sessionManager;
    private final ObjectProvider<LoginSecurityService> loginSecurityServiceProvider;

    public AdminAuthController(AdminSystemRepository systemRepository,
                               SessionManager sessionManager,
                               ObjectProvider<LoginSecurityService> loginSecurityServiceProvider) {
        this.systemRepository = systemRepository;
        this.sessionManager = sessionManager;
        this.loginSecurityServiceProvider = loginSecurityServiceProvider;
    }

    @Operation(summary = "后台登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
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
                        clientIp(servletRequest), false, "账号或密码错误");
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
            systemRepository.insertLoginLog(username, user.getId(), clientIp(servletRequest), true, "登录成功");
            return Result.success(new LoginResponse()
                    .setAccessToken(loginUser.getAccessToken())
                    .setUser(toCurrentUser(user))
                    .setMenus(systemRepository.listMenusByUserId(user.getId())));
        } catch (Exception e) {
            systemRepository.insertLoginLog(username, null, clientIp(servletRequest), false, e.getMessage());
            return Result.fail(ResultCode.LOGIN_FAIL.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public Result<CurrentUser> me() {
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

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = FrameworkConstants.AUTH_HEADER, required = false) String authorization) {
        if (authorization != null && authorization.startsWith(FrameworkConstants.TOKEN_PREFIX)) {
            sessionManager.logout(authorization.substring(FrameworkConstants.TOKEN_PREFIX.length()).trim());
        }
        return Result.success("已退出");
    }

    private CurrentUser toCurrentUser(AdminUser user) {
        return new CurrentUser()
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setNickname(user.getNickname())
                .setTenantId(user.getTenantId())
                .setRoles(user.getRoles())
                .setPermissions(user.getPermissions());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!isBlank(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
        private String deviceId;
    }

    @Data
    public static class LoginResponse {
        private String accessToken;
        private CurrentUser user;
        private List<Menu> menus;

        public LoginResponse setAccessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public LoginResponse setUser(CurrentUser user) {
            this.user = user;
            return this;
        }

        public LoginResponse setMenus(List<Menu> menus) {
            this.menus = menus;
            return this;
        }
    }

    @Data
    public static class CurrentUser {
        private Long userId;
        private String username;
        private String nickname;
        private Long tenantId;
        private List<String> roles;
        private List<String> permissions;
        private List<Menu> menus;

        public CurrentUser setUserId(Long userId) {
            this.userId = userId;
            return this;
        }

        public CurrentUser setUsername(String username) {
            this.username = username;
            return this;
        }

        public CurrentUser setNickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public CurrentUser setTenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public CurrentUser setRoles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        public CurrentUser setPermissions(List<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public CurrentUser setMenus(List<Menu> menus) {
            this.menus = menus;
            return this;
        }
    }
}
