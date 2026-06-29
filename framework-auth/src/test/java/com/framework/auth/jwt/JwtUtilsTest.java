package com.framework.auth.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-chars";

    @Test
    void constructorRejectsWeakSecretAndInvalidExpiry() {
        assertThatThrownBy(() -> new JwtUtils("short", 3600, 86400))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("jwt secret must be at least 32 characters");
        assertThatThrownBy(() -> new JwtUtils(SECRET, 0, 86400))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("access token expire seconds must be greater than 0");
        assertThatThrownBy(() -> new JwtUtils(SECRET, 3600, 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refresh token expire seconds must be greater than access token expire seconds");
    }

    @Test
    void accessTokenCarriesExpectedClaims() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, 3600, 86400);

        String token = jwtUtils.generateAccessToken(1L, " alice ", " tenant-a ", " web ");

        assertThat(jwtUtils.validateToken(token)).isTrue();
        assertThat(jwtUtils.getUserId(token)).isEqualTo(1L);
        assertThat(jwtUtils.getUsername(token)).isEqualTo("alice");
        assertThat(jwtUtils.getTenantId(token)).isEqualTo("tenant-a");
        assertThat(jwtUtils.getDeviceId(token)).isEqualTo("web");
        assertThat(jwtUtils.getTokenType(token)).isEqualTo("access");
    }

    @Test
    void refreshTokenCarriesExpectedClaims() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, 3600, 86400);

        String token = jwtUtils.generateRefreshToken(1L, " web ");

        assertThat(jwtUtils.validateToken(token)).isTrue();
        assertThat(jwtUtils.getUserId(token)).isEqualTo(1L);
        assertThat(jwtUtils.getDeviceId(token)).isEqualTo("web");
        assertThat(jwtUtils.getTokenType(token)).isEqualTo("refresh");
    }

    @Test
    void tokenGenerationRejectsInvalidClaims() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, 3600, 86400);

        assertThatThrownBy(() -> jwtUtils.generateAccessToken(0L, "alice", "tenant-a", "web"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be greater than 0");
        assertThatThrownBy(() -> jwtUtils.generateAccessToken(1L, " ", "tenant-a", "web"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username must not be blank");
        assertThatThrownBy(() -> jwtUtils.generateAccessToken(1L, "alice", " ", "web"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId must not be blank");
        assertThatThrownBy(() -> jwtUtils.generateRefreshToken(1L, "d".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("deviceId length must not exceed 128 characters");
    }

    @Test
    void validateTokenRejectsBlankOrMalformedToken() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, 3600, 86400);

        assertThat(jwtUtils.validateToken(null)).isFalse();
        assertThat(jwtUtils.validateToken(" ")).isFalse();
        assertThat(jwtUtils.validateToken("not-a-jwt")).isFalse();
    }
}
