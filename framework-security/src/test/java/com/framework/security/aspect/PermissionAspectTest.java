package com.framework.security.aspect;

import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.core.exception.AuthException;
import com.framework.core.exception.PermissionException;
import com.framework.security.annotation.IgnoreToken;
import com.framework.security.annotation.RequireLogin;
import com.framework.security.annotation.RequirePermission;
import com.framework.security.annotation.RequireRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionAspectTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void ignoreTokenSkipsLoginCheckWhenCombinedWithRequireLogin() {
        IgnoreTokenService service = proxy(new IgnoreTokenService());

        assertThat(service.open()).isEqualTo("ok");
    }

    @Test
    void classLevelRequireRoleIsEnforced() {
        UserContextHolder.set(new LoginUser().setUserId(1L).setRoles(new String[]{"USER"}));
        ClassLevelRoleService service = proxy(new ClassLevelRoleService());

        assertThatThrownBy(service::adminOnly)
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    void classLevelRequirePermissionIsEnforced() {
        UserContextHolder.set(new LoginUser().setUserId(1L).setPermissions(new String[]{"user:view"}));
        ClassLevelPermissionService service = proxy(new ClassLevelPermissionService());

        assertThatThrownBy(service::edit)
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("user:edit");
    }

    @Test
    void methodLevelRequirementOverridesClassLevelRequirement() {
        UserContextHolder.set(new LoginUser().setUserId(1L).setRoles(new String[]{"AUDITOR"}));
        MethodOverrideService service = proxy(new MethodOverrideService());

        assertThat(service.audit()).isEqualTo("ok");
    }

    @Test
    void trimsRoleAndPermissionValuesBeforeChecking() {
        UserContextHolder.set(new LoginUser()
                .setUserId(1L)
                .setRoles(new String[]{" ADMIN "})
                .setPermissions(new String[]{" user:edit "}));
        TrimmedRequirementService service = proxy(new TrimmedRequirementService());

        assertThat(service.update()).isEqualTo("ok");
    }

    @Test
    void emptyRoleConfigurationFailsClosed() {
        UserContextHolder.set(new LoginUser().setUserId(1L).setRoles(new String[]{"ADMIN"}));
        EmptyRoleService service = proxy(new EmptyRoleService());

        assertThatThrownBy(service::adminOnly)
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("角色配置不能为空");
    }

    @Test
    void emptyPermissionConfigurationFailsClosed() {
        UserContextHolder.set(new LoginUser().setUserId(1L).setPermissions(new String[]{"user:edit"}));
        EmptyPermissionService service = proxy(new EmptyPermissionService());

        assertThatThrownBy(service::edit)
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("权限配置不能为空");
    }

    @Test
    void requireLoginStillFailsWhenNoUserContextExists() {
        LoginRequiredService service = proxy(new LoginRequiredService());

        assertThatThrownBy(service::profile)
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("请先登录");
    }

    private static <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new PermissionAspect());
        return factory.getProxy();
    }

    public static class IgnoreTokenService {
        @IgnoreToken
        @RequireLogin
        public String open() {
            return "ok";
        }
    }

    @RequireRole("ADMIN")
    public static class ClassLevelRoleService {
        public String adminOnly() {
            return "ok";
        }
    }

    @RequirePermission("user:edit")
    public static class ClassLevelPermissionService {
        public String edit() {
            return "ok";
        }
    }

    @RequireRole("ADMIN")
    public static class MethodOverrideService {
        @RequireRole("AUDITOR")
        public String audit() {
            return "ok";
        }
    }

    public static class LoginRequiredService {
        @RequireLogin
        public String profile() {
            return "ok";
        }
    }

    public static class TrimmedRequirementService {
        @RequireRole("ADMIN")
        @RequirePermission("user:edit")
        public String update() {
            return "ok";
        }
    }

    public static class EmptyRoleService {
        @RequireRole(value = {" ", ""}, logicalAnd = true)
        public String adminOnly() {
            return "ok";
        }
    }

    public static class EmptyPermissionService {
        @RequirePermission(value = {" ", ""}, logicalAnd = true)
        public String edit() {
            return "ok";
        }
    }
}
