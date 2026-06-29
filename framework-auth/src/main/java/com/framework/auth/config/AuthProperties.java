package com.framework.auth.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Auth module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.auth")
public class AuthProperties implements InitializingBean {

    public static final String DEFAULT_JWT_SECRET = "framework-default-secret-key-must-be-at-least-32-chars";

    private boolean enabled = true;
    private Jwt jwt = new Jwt();
    private long sessionTimeout = 7200;
    private List<String> whiteList = new ArrayList<>(List.of(
            "/auth/**",
            "/public/**",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    ));
    private Login login = new Login();
    private Sms sms = new Sms();
    private Password password = new Password();
    private OAuth2 oauth2 = new OAuth2();

    @Override
    public void afterPropertiesSet() {
        validateJwt(Objects.requireNonNull(jwt, "framework.auth.jwt must not be null"));
        if (sessionTimeout <= 0) {
            throw new IllegalArgumentException("framework.auth.session-timeout must be greater than 0");
        }
        validateWhiteList();
        validateLogin(Objects.requireNonNull(login, "framework.auth.login must not be null"));
        validateSms(Objects.requireNonNull(sms, "framework.auth.sms must not be null"));
        validatePassword(Objects.requireNonNull(password, "framework.auth.password must not be null"));
        validateOAuth2(Objects.requireNonNull(oauth2, "framework.auth.oauth2 must not be null"));
    }

    private void validateJwt(Jwt jwt) {
        if (!hasText(jwt.getSecret()) || jwt.getSecret().length() < 32) {
            throw new IllegalArgumentException("framework.auth.jwt.secret must be at least 32 characters");
        }
        if (jwt.getAccessTokenExpire() <= 0) {
            throw new IllegalArgumentException("framework.auth.jwt.access-token-expire must be greater than 0");
        }
        if (jwt.getRefreshTokenExpire() <= jwt.getAccessTokenExpire()) {
            throw new IllegalArgumentException("framework.auth.jwt.refresh-token-expire must be greater than access-token-expire");
        }
    }

    private void validateWhiteList() {
        if (whiteList == null) {
            throw new IllegalArgumentException("framework.auth.white-list must not be null");
        }
        for (String path : whiteList) {
            if (!hasText(path)) {
                throw new IllegalArgumentException("framework.auth.white-list must not contain blank paths");
            }
            if (!path.trim().startsWith("/")) {
                throw new IllegalArgumentException("framework.auth.white-list paths must start with /");
            }
        }
    }

    private void validateLogin(Login login) {
        if (login.getMaxFailCount() <= 0) {
            throw new IllegalArgumentException("framework.auth.login.max-fail-count must be greater than 0");
        }
        if (login.getLockDurationMinutes() <= 0) {
            throw new IllegalArgumentException("framework.auth.login.lock-duration-minutes must be greater than 0");
        }
    }

    private void validateSms(Sms sms) {
        if (sms.getCodeExpireSeconds() <= 0) {
            throw new IllegalArgumentException("framework.auth.sms.code-expire-seconds must be greater than 0");
        }
        if (sms.getResendIntervalSeconds() <= 0) {
            throw new IllegalArgumentException("framework.auth.sms.resend-interval-seconds must be greater than 0");
        }
        if (sms.getResendIntervalSeconds() >= sms.getCodeExpireSeconds()) {
            throw new IllegalArgumentException("framework.auth.sms.resend-interval-seconds must be less than code-expire-seconds");
        }
    }

    private void validatePassword(Password password) {
        if (password.getExpireDays() < 0) {
            throw new IllegalArgumentException("framework.auth.password.expire-days must be greater than or equal to 0");
        }
    }

    private void validateOAuth2(OAuth2 oauth2) {
        if (!oauth2.isEnabled()) {
            return;
        }
        requireText(oauth2.getAuthorizationUri(), "framework.auth.oauth2.authorization-uri");
        requireText(oauth2.getTokenUri(), "framework.auth.oauth2.token-uri");
        requireText(oauth2.getUserInfoUri(), "framework.auth.oauth2.user-info-uri");
        requireText(oauth2.getClientId(), "framework.auth.oauth2.client-id");
        requireText(oauth2.getClientSecret(), "framework.auth.oauth2.client-secret");
        requireText(oauth2.getRedirectUri(), "framework.auth.oauth2.redirect-uri");
        requireText(oauth2.getScopes(), "framework.auth.oauth2.scopes");
    }

    private void requireText(String value, String propertyName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Data
    public static class Jwt {
        private String secret = DEFAULT_JWT_SECRET;
        private long accessTokenExpire = 7200;
        private long refreshTokenExpire = 604800;
    }

    @Data
    public static class Login {
        private int maxFailCount = 5;
        private long lockDurationMinutes = 30;
    }

    @Data
    public static class Sms {
        private long codeExpireSeconds = 300;
        private long resendIntervalSeconds = 60;
    }

    @Data
    public static class Password {
        private long expireDays = 0;
    }

    @Data
    public static class OAuth2 {
        private boolean enabled = false;
        private String authorizationUri = "";
        private String tokenUri = "";
        private String userInfoUri = "";
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";
        private String scopes = "read:user";
    }
}
