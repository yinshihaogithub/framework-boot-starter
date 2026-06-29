package com.framework.auth.service;

import com.framework.auth.config.AuthProperties;
import com.framework.auth.context.LoginUser;
import com.framework.core.exception.BusinessException;
import com.framework.core.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2 第三方登录服务
 * 支持通用 OAuth2 授权码模式（GitHub / Gitee / 自建 OAuth2 等）
 *
 * 流程：
 * 1. 前端跳转到授权页 → 用户授权 → 回调带回 code
 * 2. 后端用 code 换 accessToken
 * 3. 用 accessToken 获取用户信息
 * 4. 首次登录自动注册（业务层实现），后续直接登录
 */
@Slf4j
public class OAuth2LoginService {

    private static final String STATE_PREFIX = "framework:oauth:state:";
    private static final String OAUTH2_STATE_UNAVAILABLE_MESSAGE = "OAuth2登录状态服务暂不可用，请稍后重试";

    private final StringRedisTemplate redis;
    private final RestClient restClient;
    private final AuthProperties.OAuth2 properties;

    public OAuth2LoginService(StringRedisTemplate redis) {
        this(redis, new AuthProperties.OAuth2());
    }

    public OAuth2LoginService(StringRedisTemplate redis, AuthProperties.OAuth2 properties) {
        this.redis = redis;
        this.restClient = RestClient.create();
        this.properties = properties;
    }

    /**
     * 生成授权 URL（含 state 防 CSRF）
     *
     * @return 授权 URL
     */
    public String getAuthorizationUrl() {
        String state = UUID.randomUUID().toString().replace("-", "");
        // state 存 Redis，10 分钟有效
        try {
            redis.opsForValue().set(STATE_PREFIX + state, "1", 10, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            throw oauth2StateUnavailable("保存state", state, e);
        }

        return String.format("%s?response_type=code&client_id=%s&redirect_uri=%s&scope=%s&state=%s",
                properties.getAuthorizationUri(), properties.getClientId(),
                properties.getRedirectUri(), properties.getScopes(), state);
    }

    /**
     * 回调处理：code → accessToken → 用户信息
     *
     * @param code 授权码
     * @param state 状态码（防 CSRF）
     * @return 第三方用户信息（provider/userId/username/avatar/email）
     */
    public Map<String, Object> handleCallback(String code, String state) {
        // 1. 校验 state
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(STATE_PREFIX + state))) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "无效的state参数，请重新授权");
            }
            redis.delete(STATE_PREFIX + state);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw oauth2StateUnavailable("校验state", state, e);
        }

        // 2. code 换 accessToken
        String accessToken = exchangeToken(code);

        // 3. 获取用户信息
        Map<String, Object> userInfo = fetchUserInfo(accessToken);

        log.info("[OAuth2登录] 获取用户信息成功: {}", userInfo.get("login"));
        return userInfo;
    }

    private BusinessException oauth2StateUnavailable(String action, String state, RuntimeException e) {
        log.error("[OAuth2登录] {}失败 state={} error={}", action, state, e.getMessage());
        return new BusinessException(ResultCode.SERVICE_ERROR, OAUTH2_STATE_UNAVAILABLE_MESSAGE);
    }

    /**
     * 授权码换 AccessToken
     */
    @SuppressWarnings("unchecked")
    private String exchangeToken(String code) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(properties.getTokenUri())
                    .header("Accept", "application/json")
                    .body(Map.of(
                            "grant_type", "authorization_code",
                            "code", code,
                            "client_id", properties.getClientId(),
                            "client_secret", properties.getClientSecret(),
                            "redirect_uri", properties.getRedirectUri()
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("access_token")) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "获取AccessToken失败");
            }
            return (String) response.get("access_token");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OAuth2登录] 换取AccessToken失败", e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "OAuth2授权失败: " + e.getMessage());
        }
    }

    /**
     * 获取第三方用户信息
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(String accessToken) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(properties.getUserInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "获取用户信息失败");
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OAuth2登录] 获取用户信息失败", e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "获取用户信息失败: " + e.getMessage());
        }
    }

    /**
     * 创建第三方登录会话
     * 业务层调用：根据第三方用户信息查找/创建本地用户后调用
     */
    public LoginUser createOAuth2Session(SessionManager sessionManager,
                                          Long userId, String username,
                                          String tenantId, String deviceId,
                                          String[] roles, String[] permissions) {
        return sessionManager.createSession(userId, username, tenantId, deviceId, roles, permissions);
    }
}
