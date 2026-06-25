# framework-auth

> 认证授权模块：JWT 签发/校验、登录会话管理、Token 刷新/黑名单、多端互踢。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-auth</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 需要配合 Redis 使用。自动引入 `framework-core`。

## 核心类

| 类 | 说明 |
|---|---|
| `JwtUtils` | JWT 工具：签发/解析/校验 accessToken 与 refreshToken |
| `SessionManager` | 会话管理：登录/登出/刷新/黑名单/多端互踢/强制下线，accessToken 绑定 Redis 会话 |
| `SmsSender` | 短信发送扩展点，默认日志实现，业务方可替换为云厂商实现 |
| `TokenAuthFilter` | Token 认证过滤器：白名单放行 + accessToken 校验 + 从会话恢复用户上下文 |
| `LoginUser` | 登录用户信息（userId/username/tenantId/deviceId/roles/permissions） |
| `UserContextHolder` | 基于 ThreadLocal 的当前登录用户上下文 |
| `AuthAutoConfiguration` | 自动配置，读取 yaml 配置初始化 Bean |

## 配置

配置前缀：`framework.auth`。

```yaml
framework:
  auth:
    enabled: true                              # 是否启用（默认 true）
    jwt:
      secret: your-secret-at-least-32-chars     # JWT 密钥（至少32字符）
      access-token-expire: 7200                 # accessToken 有效期（秒），默认2小时
      refresh-token-expire: 604800              # refreshToken 有效期（秒），默认7天
    session-timeout: 7200                       # 会话超时（秒）
    white-list:                                 # 白名单路径（Ant 风格，逗号分隔）
      - /auth/**
      - /public/**
      - /actuator/**
      - /swagger-ui/**
      - /v3/api-docs/**
    login:
      max-fail-count: 5
      lock-duration-minutes: 30
    sms:
      code-expire-seconds: 300
      resend-interval-seconds: 60
```

`prod` / `production` profile 下必须显式配置 `framework.auth.jwt.secret`，不能使用默认密钥。
如果没有 `StringRedisTemplate`，模块仍会注册 `JwtUtils` 和 `SmsSender`，Redis 相关的会话、短信验证码、过滤器 Bean 会自动跳过。

## 使用示例

### 登录

```java
@Autowired
private SessionManager sessionManager;

@PostMapping("/auth/login")
public Result<LoginUser> login(@RequestBody LoginDTO dto) {
    // 1. 校验用户名密码
    User user = userService.verify(dto.getUsername(), dto.getPassword());

    // 2. 创建会话（含多端互踢）
    LoginUser loginUser = sessionManager.createSession(
            user.getId(),
            user.getUsername(),
            user.getTenantId(),
            dto.getDeviceId(),
            user.getRoles(),
            user.getPermissions()
    );
    return Result.success(loginUser);
}
```

### 获取当前登录用户

```java
// 在 Controller / Service 中任意位置
Long userId = UserContextHolder.getUserId();
String username = UserContextHolder.getUsername();
String tenantId = UserContextHolder.getTenantId();
LoginUser fullUser = UserContextHolder.get();
```

### 刷新 Token

```java
@PostMapping("/auth/refresh")
public Result<String> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
    String newAccessToken = sessionManager.refreshToken(refreshToken);
    return Result.success(newAccessToken);
}
```

### 登出

```java
@PostMapping("/auth/logout")
public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
    String token = authHeader.substring(7); // 去掉 "Bearer "
    sessionManager.logout(token);
    return Result.success();
}
```

### 强制下线

```java
sessionManager.forceLogout(userId, deviceId);
```

### 替换短信发送

```java
@Bean
public SmsSender smsSender() {
    return (phone, code, expireSeconds) -> aliyunSmsClient.send(phone, code);
}
```

## Token 规范

| 属性 | accessToken | refreshToken |
|---|---|---|
| 用途 | 接口鉴权 | 刷新 accessToken |
| 默认有效期 | 2 小时 | 7 天 |
| 载荷 | userId/username/tenantId/deviceId/type | userId/deviceId/type |
| 服务端校验 | 必须是 `type=access`，且 Redis 会话存在，且不在黑名单 | 必须是 `type=refresh`，且与 Redis 会话中保存的 refreshToken 一致 |
| 吊销 | Redis 删除会话或加入黑名单后立即失效 | Redis 删除会话后失效 |

## Redis Key 设计

| Key | 类型 | TTL | 用途 |
|---|---|---|---|
| `framework:session:{userId}:{deviceId}` | Hash | 2h | 用户会话，保存 refreshToken、roles、permissions 等上下文 |
| `framework:token:blacklist:{token}` | String | Token剩余有效期 | Token 黑名单 |

## 工作流程

```
请求 → TokenAuthFilter
  ├─ 白名单匹配 → 放行
  └─ 提取 Authorization Header
       ├─ 无 Token → 401
       └─ 校验 Token（含黑名单检查）
            ├─ 无效/过期 → 401
            ├─ 非 accessToken / 会话不存在 → 401
            └─ 有效 → 从 Redis 会话恢复 LoginUser → 注入 UserContextHolder → 执行业务 → 清理 ThreadLocal
```
