# framework-security

> RBAC 权限控制：注解式鉴权，基于 AOP 拦截。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-security</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 依赖 `framework-auth`（需配合 `TokenAuthFilter` 注入 `UserContextHolder`）。自动装配生效。

## 注解一览

| 注解 | 作用 | 示例 |
|---|---|---|
| `@IgnoreToken` | 跳过鉴权（公开接口） | 登录/验证码接口 |
| `@RequireLogin` | 必须登录 | 所有需登录的接口 |
| `@RequireRole("ADMIN")` | 必须指定角色 | 管理后台接口 |
| `@RequirePermission("user:add")` | 必须指定权限 | 增删改操作 |

`@RequireLogin`、`@RequireRole`、`@RequirePermission` 支持方法级和类级声明；方法级声明优先于类级声明。`@IgnoreToken` 可与上述注解组合使用，用于明确跳过当前方法或类的权限切面。

## 使用示例

### 公开接口

```java
@IgnoreToken
@GetMapping("/public/news")
public Result<List<News>> news() {
    return Result.success(newsService.list());
}
```

### 需要登录

```java
@RequireLogin
@GetMapping("/profile")
public Result<User> profile() {
    Long userId = UserContextHolder.getUserId();
    return Result.success(userService.getById(userId));
}
```

### 角色校验（满足任一即可）

```java
@RequireRole("ADMIN")
@GetMapping("/admin/dashboard")
public Result dashboard() {
    return Result.success();
}

// 多角色，满足任一
@RequireRole({"ADMIN", "OPERATOR"})
@GetMapping("/admin/users")
public Result users() {
    return Result.success();
}

// 多角色，必须全部满足
@RequireRole(value = {"ADMIN", "AUDITOR"}, logicalAnd = true)
@GetMapping("/admin/audit")
public Result audit() {
    return Result.success();
}
```

### 权限校验

```java
@RequirePermission("user:add")
@PostMapping("/users")
public Result create(@RequestBody UserDTO dto) {
    return Result.success();
}

// 多权限，必须全部满足
@RequirePermission(value = {"user:add", "user:edit"}, logicalAnd = true)
@PutMapping("/users")
public Result update(@RequestBody UserDTO dto) {
    return Result.success();
}
```

## 校验逻辑

| 模式 | 逻辑 | 配置 |
|---|---|---|
| OR（默认） | 满足任一角色/权限即可 | `logicalAnd = false`（默认） |
| AND | 必须全部满足 | `logicalAnd = true` |

## 权限数据来源

角色和权限数据来自 `LoginUser`。登录时由 `SessionManager.createSession()` 写入 Redis 会话，请求进入时由 `TokenAuthFilter` 根据 accessToken 从会话恢复并注入 `UserContextHolder`：

```java
LoginUser loginUser = sessionManager.createSession(
    userId, username, tenantId, deviceId,
    new String[]{"ADMIN", "OPERATOR"},           // 角色
    new String[]{"user:add", "user:edit", ...}    // 权限
);
```

`PermissionAspect` 从 `UserContextHolder.get()` 读取当前用户的角色和权限进行校验；请求结束后 `TokenAuthFilter` 会清理 ThreadLocal，避免线程复用造成用户串号。

## 数据权限

`DataScopeInterceptor` 只在 Mapper 方法标注 `@DataScope` 时生效。拼接权限条件时会根据原 SQL 是否已有 `WHERE` 自动选择 `WHERE` 或 `AND`，并把条件插入到 `GROUP BY`、`ORDER BY`、`HAVING`、`LIMIT`、`OFFSET` 等尾部子句之前，避免无 `WHERE` 查询或分页/排序查询被拼成非法 SQL。

## 异常处理

| 场景 | 异常 | 业务码 |
|---|---|---|
| 未登录 | `AuthException` | 2001 |
| 角色不足 | `PermissionException` | 2005 |
| 权限不足 | `PermissionException` | 2005 |

异常由 `GlobalExceptionHandler` 统一处理，返回：
```json
{"code": 2005, "message": "缺少必要权限: [user:add]", "data": null}
```
