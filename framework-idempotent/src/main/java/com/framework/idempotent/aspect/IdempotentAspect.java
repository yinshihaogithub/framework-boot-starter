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
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
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
    private static final ParserContext TEMPLATE_PARSER_CONTEXT = new TemplateParserContext();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;

    public IdempotentAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(com.framework.idempotent.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        Idempotent annotation = method.getAnnotation(Idempotent.class);
        validate(annotation, method);

        String idempotentKey = buildKey(annotation, signature, method, joinPoint.getArgs());
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

    private void validate(Idempotent annotation, Method method) {
        if (annotation.expire() <= 0) {
            throw new IllegalArgumentException("@Idempotent expire must be greater than 0: " + method);
        }
        if (annotation.strategy() == Idempotent.IdempotentStrategy.BUSINESS_KEY
                && !StringUtils.hasText(annotation.key())) {
            throw new IllegalArgumentException("@Idempotent BUSINESS_KEY strategy requires a key: " + method);
        }
    }

    private String buildKey(Idempotent annotation, MethodSignature signature, Method method, Object[] args) {
        String keyExpr = annotation.key();

        // BUSINESS_KEY 策略：解析 SpEL
        if (annotation.strategy() == Idempotent.IdempotentStrategy.BUSINESS_KEY) {
            return resolveKey(keyExpr, signature, method, args);
        }

        // TOKEN 策略：从 Header 取 token
        if (annotation.strategy() == Idempotent.IdempotentStrategy.TOKEN) {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String token = request.getHeader("X-Idempotent-Token");
                if (StringUtils.hasText(token)) {
                    return "token:" + normalizeResolvedKey(token, method);
                }
            }
            throw new BusinessException(ResultCode.IDEMPOTENT_FAIL, "缺少幂等Token");
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

    private String resolveKey(String expr, MethodSignature signature, Method method, Object[] args) {
        if (!expr.contains("#")) {
            return normalizeResolvedKey(expr, method);
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("args", args);
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }
        registerParameterNames(context, signature, method, args);
        try {
            Object value = expr.contains("#{")
                    ? PARSER.parseExpression(expr, TEMPLATE_PARSER_CONTEXT).getValue(context)
                    : PARSER.parseExpression(expr).getValue(context);
            String resolved = value != null ? value.toString() : null;
            return normalizeResolvedKey(resolved, method);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("@Idempotent key SpEL parse failed: " + expr, e);
        }
    }

    private String normalizeResolvedKey(String key, Method method) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("@Idempotent key must not resolve to blank: " + method);
        }
        return key.trim();
    }

    private void registerParameterNames(StandardEvaluationContext context, MethodSignature signature,
                                        Method method, Object[] args) {
        String[] paramNames = signature.getParameterNames();
        if (paramNames == null || paramNames.length == 0) {
            paramNames = NAME_DISCOVERER.getParameterNames(method);
        }
        if (paramNames == null) {
            return;
        }
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
    }

    private String hashArgs(Object[] args) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(args);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return String.valueOf(args.hashCode());
        }
    }

    private HttpServletRequest getCurrentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
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
