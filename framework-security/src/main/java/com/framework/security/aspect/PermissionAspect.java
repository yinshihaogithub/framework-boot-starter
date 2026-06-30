package com.framework.security.aspect;

import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.support.AuthTextSupport;
import com.framework.core.exception.AuthException;
import com.framework.core.exception.PermissionException;
import com.framework.security.annotation.IgnoreToken;
import com.framework.security.annotation.RequireLogin;
import com.framework.security.annotation.RequirePermission;
import com.framework.security.annotation.RequireRole;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限校验切面
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Around("@annotation(com.framework.security.annotation.RequireLogin) " +
            "|| @annotation(com.framework.security.annotation.RequireRole) " +
            "|| @annotation(com.framework.security.annotation.RequirePermission) " +
            "|| @within(com.framework.security.annotation.RequireLogin) " +
            "|| @within(com.framework.security.annotation.RequireRole) " +
            "|| @within(com.framework.security.annotation.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        var methodSignature = (org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = joinPoint.getTarget() != null
                ? joinPoint.getTarget().getClass()
                : methodSignature.getMethod().getDeclaringClass();
        Method method = AopUtils.getMostSpecificMethod(methodSignature.getMethod(), targetClass);

        if (findAnnotation(method, targetClass, IgnoreToken.class) != null) {
            return joinPoint.proceed();
        }

        // 必须登录
        LoginUser user = UserContextHolder.get();
        if (user == null) {
            throw new AuthException("请先登录");
        }

        // 角色校验
        RequireRole requireRole = findAnnotation(method, targetClass, RequireRole.class);
        if (requireRole != null) {
            checkRoles(user, requireRole);
        }

        // 权限校验
        RequirePermission requirePermission = findAnnotation(method, targetClass, RequirePermission.class);
        if (requirePermission != null) {
            checkPermissions(user, requirePermission);
        }

        return joinPoint.proceed();
    }

    private void checkRoles(LoginUser user, RequireRole annotation) {
        List<String> required = normalizeItems(annotation.value());
        if (required.isEmpty()) {
            throw new PermissionException("角色配置不能为空");
        }
        Set<String> userRoles = normalizeItems(user.getRoles()).stream().collect(Collectors.toSet());

        if (annotation.logicalAnd()) {
            // 需要全部满足
            for (String role : required) {
                if (!userRoles.contains(role)) {
                    throw new PermissionException("缺少角色: " + role);
                }
            }
        } else {
            // 满足任一即可
            boolean hasAny = required.stream().anyMatch(userRoles::contains);
            if (!hasAny) {
                throw new PermissionException("缺少必要角色: " + required);
            }
        }
    }

    private void checkPermissions(LoginUser user, RequirePermission annotation) {
        List<String> required = normalizeItems(annotation.value());
        if (required.isEmpty()) {
            throw new PermissionException("权限配置不能为空");
        }
        Set<String> userPermissions = normalizeItems(user.getPermissions()).stream().collect(Collectors.toSet());

        if (annotation.logicalAnd()) {
            for (String perm : required) {
                if (!userPermissions.contains(perm)) {
                    throw new PermissionException("缺少权限: " + perm);
                }
            }
        } else {
            boolean hasAny = required.stream().anyMatch(userPermissions::contains);
            if (!hasAny) {
                throw new PermissionException("缺少必要权限: " + required);
            }
        }
    }

    private List<String> normalizeItems(String[] values) {
        return Arrays.stream(values != null ? values : new String[0])
                .map(AuthTextSupport::trimToNull)
                .filter(value -> value != null)
                .toList();
    }

    private <A extends Annotation> A findAnnotation(Method method, Class<?> targetClass, Class<A> annotationType) {
        A methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, annotationType);
    }
}
