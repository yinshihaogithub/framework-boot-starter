package com.framework.auth.service;

import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsCodeServiceTest {

    @Test
    void sendCodePersistsCodeBeforeSendingSms() {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        RecordingSmsSender sender = new RecordingSmsSender();
        SmsCodeService service = new SmsCodeService(redis, 300, 60, sender, () -> 42);

        String code = service.sendCode("13800000000");

        assertThat(code).isEqualTo("000042");
        assertThat(redis.values).containsEntry("framework:sms:code:13800000000", code);
        assertThat(redis.values).containsEntry("framework:sms:limit:13800000000", "1");
        assertThat(sender.sentPhone).isEqualTo("13800000000");
        assertThat(sender.sentCode).isEqualTo(code);
        assertThat(sender.sentExpireSeconds).isEqualTo(300);
    }

    @Test
    void verifyCodeDeletesCodeAfterSuccess() {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        SmsCodeService service = new SmsCodeService(redis, 300, 60, new RecordingSmsSender());
        redis.values.put("framework:sms:code:13800000000", "123456");

        assertThat(service.verifyCode("13800000000", "123456")).isTrue();

        assertThat(redis.values).doesNotContainKey("framework:sms:code:13800000000");
    }

    @Test
    void redisFailuresDuringSendCodeFailClosedAndDoNotSendSms() {
        RecordingSmsSender sender = new RecordingSmsSender();
        SmsCodeService service = new SmsCodeService(new ThrowingRedisTemplate(), 300, 60, sender);

        assertThatThrownBy(() -> service.sendCode("13800000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("短信验证码服务暂不可用，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.SERVICE_ERROR.getCode());
        assertThat(sender.sentPhone).isNull();
    }

    @Test
    void redisFailuresDuringVerifyCodeFailClosed() {
        SmsCodeService service = new SmsCodeService(new ThrowingRedisTemplate(), 300, 60, new RecordingSmsSender());

        assertThatThrownBy(() -> service.verifyCode("13800000000", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("短信验证码服务暂不可用，请稍后重试")
                .extracting("code")
                .isEqualTo(ResultCode.SERVICE_ERROR.getCode());
    }

    @Test
    void redisFailuresDuringCodeSentQueryFallbackToFalse() {
        SmsCodeService service = new SmsCodeService(new ThrowingRedisTemplate(), 300, 60, new RecordingSmsSender());

        assertThat(service.isCodeSent("13800000000")).isFalse();
    }

    private static final class RecordingSmsSender implements SmsSender {

        private String sentPhone;
        private String sentCode;
        private long sentExpireSeconds;

        @Override
        public void send(String phone, String code, long expireSeconds) {
            this.sentPhone = phone;
            this.sentCode = code;
            this.sentExpireSeconds = expireSeconds;
        }
    }

    private static final class RecordingRedisTemplate extends StringRedisTemplate {

        private final Map<String, String> values = new ConcurrentHashMap<>();

        @Override
        public Boolean hasKey(String key) {
            return values.containsKey(key);
        }

        @Override
        public Long getExpire(String key, TimeUnit timeUnit) {
            return values.containsKey(key) ? 30L : -2L;
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
                        if ("get".equals(method.getName())) {
                            return values.get((String) args[0]);
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static final class ThrowingRedisTemplate extends StringRedisTemplate {

        @Override
        public Boolean hasKey(String key) {
            throw redisUnavailable();
        }

        @Override
        public Long getExpire(String key, TimeUnit timeUnit) {
            throw redisUnavailable();
        }

        @Override
        public Boolean delete(String key) {
            throw redisUnavailable();
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        throw redisUnavailable();
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
