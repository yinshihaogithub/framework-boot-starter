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
}
