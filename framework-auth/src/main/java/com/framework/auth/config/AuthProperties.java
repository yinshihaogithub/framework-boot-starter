package com.framework.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Auth module configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "framework.auth")
public class AuthProperties {

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
