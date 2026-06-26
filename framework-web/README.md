# framework-web

> Web 层基础设施：全局异常处理、链路追踪 traceId、CORS 跨域、ObjectMapper 配置。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-web</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 自动引入 `framework-core`。通过 Spring Boot 自动装配生效，无需额外配置。

## 核心类

| 类 | 说明 |
|---|---|
| `GlobalExceptionHandler` | 全局异常处理器，`@RestControllerAdvice` 捕获所有异常转为 `Result` |
| `TraceIdFilter` | 链路追踪过滤器，注入 traceId 到 MDC 和响应头 |
| `CorsConfig` | CORS 跨域配置 |
| `XssFilter` | 转义请求参数中的 HTML 特殊字符，不改写认证和链路 Header |
| `SqlInjectionInterceptor` | MyBatis SQL 注入特征拦截，避免误伤正常参数化 INSERT/UPDATE/DELETE |
| `WebAutoConfiguration` | 基于 Spring Jackson builder 创建 ObjectMapper Bean，支持 JavaTime 等常用模块 |

## 功能说明

### 1. 全局异常处理

自动捕获以下异常并转为统一响应体：

| 异常类型 | HTTP 状态 | 业务码 |
|---|---|---|
| `BusinessException` | 200 | 自定义 |
| `MethodArgumentNotValidException` | 400 | 1001 |
| `ConstraintViolationException` | 400 | 1001 |
| `BindException` | 400 | 1001 |
| `MissingServletRequestParameterException` | 400 | 1001 |
| `HttpMessageNotReadableException` | 400 | 400 |
| `HttpRequestMethodNotSupportedException` | 405 | 405 |
| `NoHandlerFoundException` | 404 | 404 |
| `AuthException` | 401 | 2001/2002 |
| `PermissionException` | 403 | 2005 |
| `Exception`（兜底） | 500 | 500 |

业务层只需抛异常，无需手写 try-catch：

```java
@GetMapping("/users/{id}")
public Result<User> getUser(@PathVariable Long id) {
    User user = userService.getById(id);
    if (user == null) {
        throw new BusinessException("用户不存在"); // 自动转为 {code:1002, message:"用户不存在"}
    }
    return Result.success(user);
}
```

### 2. 链路追踪 TraceId

- 请求进入时从 Header `X-Trace-Id` 获取，不存在则自动生成 UUID（去横线）
- 写入 MDC（日志可按 `%X{traceId}` 输出）
- 响应头返回 `X-Trace-Id`
- 请求结束后恢复进入过滤器前的 MDC 上下文，避免复用线程或嵌套调用时污染调用方 trace

日志配置示例（logback-spring.xml）：

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss} [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
```

### 3. CORS 跨域

默认配置：
- 允许所有来源（`*`）
- 允许凭证（Credentials）
- 允许方法：GET/POST/PUT/DELETE/OPTIONS/PATCH
- 允许所有 Header
- 预检缓存 3600s

### 4. 参数校验

配合 JSR-303 注解使用，校验失败自动转为统一响应：

```java
@PostMapping("/users")
public Result<Void> create(@Valid @RequestBody UserDTO dto) {
    // dto 上 @NotBlank/@Email 等校验失败时自动返回 {code:1001, message:"username: 不能为空"}
    userService.create(dto);
    return Result.success();
}
```

### 5. SQL 注入防护

`SqlInjectionInterceptor` 针对 MyBatis 最终 SQL 做额外风险特征检查，覆盖注释符、`UNION SELECT`、堆叠语句、`DROP TABLE`、命令执行和时间盲注等特征。

正常参数化 `INSERT`、`UPDATE`、`DELETE` 不会因为 DML 关键字本身被拦截；业务仍应优先使用 MyBatis 参数绑定，不要拼接用户输入。
