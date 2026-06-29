package com.framework.auth.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 */
@Slf4j
public class JwtUtils {

    private final SecretKey key;
    private final long accessTokenExpire;
    private final long refreshTokenExpire;

    public JwtUtils(String secret, long accessTokenExpireSeconds, long refreshTokenExpireSeconds) {
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalArgumentException("jwt secret must be at least 32 characters");
        }
        if (accessTokenExpireSeconds <= 0) {
            throw new IllegalArgumentException("access token expire seconds must be greater than 0");
        }
        if (refreshTokenExpireSeconds <= accessTokenExpireSeconds) {
            throw new IllegalArgumentException("refresh token expire seconds must be greater than access token expire seconds");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpire = accessTokenExpireSeconds * 1000;
        this.refreshTokenExpire = refreshTokenExpireSeconds * 1000;
    }

    /**
     * 生成 access token
     */
    public String generateAccessToken(Long userId, String username, String tenantId, String deviceId) {
        return buildToken(userId, username, tenantId, deviceId, accessTokenExpire, "access");
    }

    /**
     * 生成 refresh token
     */
    public String generateRefreshToken(Long userId, String deviceId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("deviceId", deviceId);
        claims.put("type", "refresh");
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpire))
                .signWith(key)
                .compact();
    }

    private String buildToken(Long userId, String username, String tenantId,
                              String deviceId, long expireMs, String type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("tenantId", tenantId);
        claims.put("deviceId", deviceId);
        claims.put("type", type);
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireMs))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            throw new io.jsonwebtoken.JwtException("Token已过期");
        } catch (Exception e) {
            log.warn("Token解析失败: {}", e.getMessage());
            throw new io.jsonwebtoken.JwtException("Token无效");
        }
    }

    /**
     * 校验 token
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 token 提取 userId
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return (Long) userId;
    }

    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    public String getTenantId(String token) {
        return parseToken(token).get("tenantId", String.class);
    }

    public String getDeviceId(String token) {
        return parseToken(token).get("deviceId", String.class);
    }

    public String getTokenType(String token) {
        return parseToken(token).get("type", String.class);
    }
}
