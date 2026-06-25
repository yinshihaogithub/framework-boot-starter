package com.framework.auth.context;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 登录用户上下文
 */
@Data
@Accessors(chain = true)
public class LoginUser {

    private Long userId;
    private String username;
    private String tenantId;
    private String deviceId;
    private String accessToken;
    private String[] roles;
    private String[] permissions;
}
