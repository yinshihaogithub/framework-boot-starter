package com.framework.idempotent.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import com.framework.idempotent.annotation.Idempotent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性切面
 */
@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;

    public IdempotentAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(com.framework.idempotent.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent annotation = method.getAnnotation(Idempotent.class);

        String idempotentKey = buildKey(annotation, method, joinPoint.getArgs());
        String redisKey = FrameworkConstants.IDEMPOTENT_PREFIX + idempotentKey;

        // SETNX 抢占
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                redisKey, "1", annotation.expire(), TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[幂等拦截] key={}, 重复请求", idempotentKey);
            throw new BusinessException(ResultCode.IDEMPOTENT_FAIL, annotation.message());
        }

        try {
            Object result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            // 业务异常时释放幂等锁，允许重试
            redisTemplate.delete(redisKey);
            throw e;
        }
    }

    private String buildKey(Idempotent annotation, Method method, Object[] args) {
        String keyExpr = annotation.key();

        // BUSINESS_KEY 策略：解析 SpEL
        if (annotation.strategy() == Idempotent.IdempotentStrategy.BUSINESS_KEY
                && keyExpr != null && !keyExpr.isEmpty() && keyExpr.contains("#")) {
            return resolveSpel(keyExpr, method, args);
        }

        // TOKEN 策略：从 Header 取 token
        if (annotation.strategy() == Idempotent.IdempotentStrategy.TOKEN) {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String token = request.getHeader("X-Idempotent-Token");
                if (token != null && !token.isEmpty()) {
                    return "token:" + token;
                }
            }
        }

        // REQUEST_HASH 策略（默认）：URI + 请求体 hash
        String uri = "";
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            uri = request.getRequestURI();
        }
        String argsHash = hashArgs(args);
        String userId = getCurrentUserId();
        return "hash:" + uri + ":" + userId + ":" + argsHash;
    }

    private String resolveSpel(String expr, Method method, Object[] args) {
        try {
            String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
            if (paramNames == null) return expr;
            EvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            String spel = expr.replaceAll("#\\{(.+?)}", "$1");
            Expression expression = PARSER.parseExpression(spel);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : "null";
        } catch (Exception e) {
            return expr;
        }
    }

    private String hashArgs(Object[] args) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(args);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(json.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return String.valueOf(args.hashCode());
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getCurrentUserId() {
        try {
            Class<?> clazz = Class.forName("com.framework.auth.context.UserContextHolder");
            Method getUserId = clazz.getMethod("getUserId");
            Object userId = getUserId.invoke(null);
            return userId != null ? userId.toString() : "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
