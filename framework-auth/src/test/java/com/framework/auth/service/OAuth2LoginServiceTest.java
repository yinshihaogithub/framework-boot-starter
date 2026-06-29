package com.framework.auth.service;

import com.framework.auth.config.AuthProperties;
import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2LoginServiceTest {

    @Test
    void authorizationUrlStoresStateBeforeReturningUrl() {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        OAuth2LoginService service = new OAuth2LoginService(redis, properties());

        String url = service.getAuthorizationUrl();
        Map<String, String> query = queryParams(url);

        assertThat(url).startsWith("https://oauth.example/authorize?");
        assertThat(query).containsEntry("response_type", "code")
                .containsEntry("client_id", "client-id")
                .containsEntry("redirect_uri", "https://app.example/callback")
                .containsEntry("scope", "read:user");
        assertThat(redis.values).containsEntry("framework:oauth:state:" + query.get("state"), "1");
    }

    @Test
    void redisFailuresDuringAuthorizationUrlFailClosed() {
        OAuth2LoginService service = new OAuth2LoginService(
                new ThrowingRedisTemplate(true, true, true), properties());

        assertThatThrownBy(service::getAuthorizationUrl)
                .isInstanceOf(BusinessException.class)
                .hasMessage("OAuth2登录状态服务暂不可用，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.SERVICE_ERROR.getCode());
    }

    @Test
    void invalidStateIsRejectedBeforeTokenExchange() {
        OAuth2LoginService service = new OAuth2LoginService(new RecordingRedisTemplate(), properties());

        assertThatThrownBy(() -> service.handleCallback("code", "missing-state"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无效的state参数，请重新授权")
                .extracting("code")
                .isEqualTo(ResultCode.BUSINESS_ERROR.getCode());
    }

    @Test
    void redisFailuresDuringStateCheckFailClosedBeforeTokenExchange() {
        OAuth2LoginService service = new OAuth2LoginService(
                new ThrowingRedisTemplate(false, true, false), properties());

        assertThatThrownBy(() -> service.handleCallback("code", "state-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("OAuth2登录状态服务暂不可用，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.SERVICE_ERROR.getCode());
    }

    @Test
    void redisFailuresDuringStateDeleteFailClosedBeforeTokenExchange() {
        OAuth2LoginService service = new OAuth2LoginService(
                new ThrowingRedisTemplate(false, false, true), properties());

        assertThatThrownBy(() -> service.handleCallback("code", "state-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("OAuth2登录状态服务暂不可用，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.SERVICE_ERROR.getCode());
    }

    private static AuthProperties.OAuth2 properties() {
        AuthProperties.OAuth2 properties = new AuthProperties.OAuth2();
        properties.setAuthorizationUri("https://oauth.example/authorize");
        properties.setTokenUri("https://oauth.example/token");
        properties.setUserInfoUri("https://oauth.example/user");
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("https://app.example/callback");
        properties.setScopes("read:user");
        return properties;
    }

    private static Map<String, String> queryParams(String url) {
        String query = URI.create(url).getRawQuery();
        return Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> decode(pair[0]),
                        pair -> pair.length > 1 ? decode(pair[1]) : ""));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static final class RecordingRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values = new ConcurrentHashMap<>();

        @Override
        public Boolean hasKey(String key) {
            return values.containsKey(key);
        }

        @Override
        public Boolean delete(String key) {
            return values.remove(key) != null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("set".equals(method.getName())) {
                            values.put((String) args[0], (String) args[1]);
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static final class ThrowingRedisTemplate extends StringRedisTemplate {

        private final boolean throwOnSet;
        private final boolean throwOnHasKey;
        private final boolean throwOnDelete;

        private ThrowingRedisTemplate(boolean throwOnSet, boolean throwOnHasKey, boolean throwOnDelete) {
            this.throwOnSet = throwOnSet;
            this.throwOnHasKey = throwOnHasKey;
            this.throwOnDelete = throwOnDelete;
        }

        @Override
        public Boolean hasKey(String key) {
            if (throwOnHasKey) {
                throw redisUnavailable();
            }
            return true;
        }

        @Override
        public Boolean delete(String key) {
            if (throwOnDelete) {
                throw redisUnavailable();
            }
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if (throwOnSet && "set".equals(method.getName())) {
                            throw redisUnavailable();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private static IllegalStateException redisUnavailable() {
            return new IllegalStateException("redis unavailable");
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
