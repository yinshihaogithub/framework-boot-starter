package com.framework.auth.config;

import com.framework.auth.filter.TokenAuthFilter;
import com.framework.auth.jwt.JwtUtils;
import com.framework.auth.service.LoginSecurityService;
import com.framework.auth.service.LoggingSmsSender;
import com.framework.auth.service.OAuth2LoginService;
import com.framework.auth.service.PasswordExpireService;
import com.framework.auth.service.SessionManager;
import com.framework.auth.service.SmsCodeService;
import com.framework.auth.service.SmsSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 鉴权模块自动配置
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@ConditionalOnProperty(prefix = "framework.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtUtils jwtUtils(AuthProperties properties, Environment environment) {
        validateJwtSecret(properties.getJwt(), environment);
        return new JwtUtils(properties.getJwt().getSecret(),
                properties.getJwt().getAccessTokenExpire(),
                properties.getJwt().getRefreshTokenExpire());
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public SessionManager sessionManager(StringRedisTemplate redisTemplate, JwtUtils jwtUtils,
                                         AuthProperties properties) {
        return new SessionManager(redisTemplate, jwtUtils, properties.getSessionTimeout());
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public LoginSecurityService loginSecurityService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        return new LoginSecurityService(redisTemplate,
                properties.getLogin().getMaxFailCount(),
                properties.getLogin().getLockDurationMinutes());
    }

    @Bean
    @ConditionalOnMissingBean
    public SmsSender smsSender() {
        return new LoggingSmsSender();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public SmsCodeService smsCodeService(StringRedisTemplate redisTemplate,
                                         AuthProperties properties,
                                         SmsSender smsSender) {
        return new SmsCodeService(redisTemplate,
                properties.getSms().getCodeExpireSeconds(),
                properties.getSms().getResendIntervalSeconds(),
                smsSender);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public PasswordExpireService passwordExpireService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        return new PasswordExpireService(redisTemplate, properties.getPassword().getExpireDays());
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "framework.auth.oauth2", name = "enabled", havingValue = "true")
    public OAuth2LoginService oAuth2LoginService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        return new OAuth2LoginService(redisTemplate, properties.getOauth2());
    }

    @Bean
    @ConditionalOnBean(SessionManager.class)
    @ConditionalOnMissingBean(name = "tokenAuthFilterRegistration")
    public FilterRegistrationBean<TokenAuthFilter> tokenAuthFilterRegistration(
            JwtUtils jwtUtils, SessionManager sessionManager, AuthProperties properties) {
        Set<String> whiteList = new HashSet<>(properties.getWhiteList());
        TokenAuthFilter filter = new TokenAuthFilter(jwtUtils, sessionManager, whiteList);
        FilterRegistrationBean<TokenAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    private void validateJwtSecret(AuthProperties.Jwt jwt, Environment environment) {
        String secret = jwt.getSecret();
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            throw new IllegalStateException("framework.auth.jwt.secret must be at least 32 characters");
        }
        boolean productionProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));
        if (productionProfile && AuthProperties.DEFAULT_JWT_SECRET.equals(secret)) {
            throw new IllegalStateException("framework.auth.jwt.secret must be configured in production");
        }
    }
}
