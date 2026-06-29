package com.framework.auth.config;

import com.framework.auth.filter.TokenAuthFilter;
import com.framework.auth.jwt.JwtUtils;
import com.framework.auth.service.SessionManager;
import com.framework.auth.service.SmsCodeService;
import com.framework.auth.service.SmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthAutoConfiguration.class));

    @Test
    void autoConfigurationStartsWithoutRedisAndRegistersJwtOnly() {
        contextRunner
                .withPropertyValues("framework.auth.jwt.secret=test-secret-key-must-be-at-least-32-chars")
                .run(context -> assertThat(context)
                        .hasSingleBean(AuthProperties.class)
                        .hasSingleBean(JwtUtils.class)
                        .doesNotHaveBean(SessionManager.class)
                        .doesNotHaveBean(SmsCodeService.class)
                        .doesNotHaveBean(TokenAuthFilter.class));
    }

    @Test
    void productionProfileRejectsDefaultJwtSecret() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void autoConfigurationRegistersDefaultSmsSender() {
        contextRunner
                .withPropertyValues("framework.auth.jwt.secret=test-secret-key-must-be-at-least-32-chars")
                .run(context -> assertThat(context)
                        .hasSingleBean(SmsSender.class));
    }

    @Test
    void autoConfigurationRejectsInvalidAuthPropertiesAtStartup() {
        assertInvalidProperty("framework.auth.session-timeout=0", "framework.auth.session-timeout");
        assertInvalidProperty("framework.auth.jwt.access-token-expire=0", "framework.auth.jwt.access-token-expire");
        assertInvalidProperty("framework.auth.jwt.refresh-token-expire=1", "framework.auth.jwt.refresh-token-expire");
        assertInvalidProperty("framework.auth.login.max-fail-count=0", "framework.auth.login.max-fail-count");
        assertInvalidProperty("framework.auth.login.lock-duration-minutes=0", "framework.auth.login.lock-duration-minutes");
        assertInvalidProperty("framework.auth.sms.code-expire-seconds=0", "framework.auth.sms.code-expire-seconds");
        assertInvalidProperty("framework.auth.sms.resend-interval-seconds=0", "framework.auth.sms.resend-interval-seconds");
        assertInvalidProperty("framework.auth.password.expire-days=-1", "framework.auth.password.expire-days");
        assertInvalidProperty("framework.auth.white-list[0]=auth/**", "framework.auth.white-list paths must start with /");
    }

    @Test
    void autoConfigurationRejectsIncompleteOAuth2PropertiesWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "framework.auth.jwt.secret=test-secret-key-must-be-at-least-32-chars",
                        "framework.auth.oauth2.enabled=true",
                        "framework.auth.oauth2.authorization-uri=https://oauth.example.com/authorize",
                        "framework.auth.oauth2.token-uri=https://oauth.example.com/token",
                        "framework.auth.oauth2.user-info-uri=https://oauth.example.com/userinfo",
                        "framework.auth.oauth2.client-id=client",
                        "framework.auth.oauth2.client-secret=secret",
                        "framework.auth.oauth2.redirect-uri= ")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining("framework.auth.oauth2.redirect-uri"));
    }

    private void assertInvalidProperty(String property, String message) {
        contextRunner
                .withPropertyValues(
                        "framework.auth.jwt.secret=test-secret-key-must-be-at-least-32-chars",
                        property)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining(message));
    }
}
