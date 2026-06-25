# framework-rate-limiter

> 分布式限流模块：基于 Redisson RRateLimiter（滑动窗口），支持全局/IP/用户多维度。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-rate-limiter</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 需要配置 `RedissonClient`。如果应用未提供 `RedissonClient`，自动配置会跳过 `RateLimitAspect`，不会影响应用启动。

## 注解参数

```java
@RateLimit(
    key = "",                        // 限流 key（可选，支持 SpEL 模板）
    limit = 100,                     // 时间窗口内允许的请求数，必须 > 0
    window = 60,                     // 时间窗口大小，必须 > 0
    unit = TimeUnit.SECONDS,         // 时间单位，默认 SECONDS
    limitType = LimitType.GLOBAL,    // 限流维度，默认 GLOBAL
    message = "请求过于频繁，请稍后再试"  // 限流提示
)
```

### LimitType 维度

| 维度 | key 构造 | 说明 |
|---|---|---|
| `GLOBAL` | `framework:rate:global:{Class}:{method}` | 全局限流，所有用户共享 |
| `IP` | `framework:rate:ip:{ip}:{Class}:{method}` | 按 IP 限流 |
| `USER` | `framework:rate:user:{userId}:{Class}:{method}` | 按用户限流 |
| `DEFAULT` | `framework:rate:default:{Class}:{method}` | 默认（等同 GLOBAL） |

配置 `key` 后，`{Class}:{method}` 会替换为自定义 key，例如 `api:user:#{#userId}` 会解析为 `api:user:42`。SpEL 上下文支持参数名、`#p0` / `#a0` 索引参数和 `#args` 数组。

## 使用示例

### 全局限流

```java
// 全局每秒最多 1000 个请求
@RateLimit(limit = 1000, window = 1)
@GetMapping("/data")
public Result data() {
    return Result.success();
}
```

### IP 限流

```java
// 每个 IP 每分钟最多 10 次请求
@RateLimit(limit = 10, window = 60, limitType = RateLimit.LimitType.IP)
@GetMapping("/rate-limit")
public Result rateLimited() {
    return Result.success();
}
```

### 用户限流

```java
// 每个用户每小时最多 100 次请求
@RateLimit(limit = 100, window = 1, unit = TimeUnit.HOURS,
           limitType = RateLimit.LimitType.USER)
@GetMapping("/profile")
public Result profile() {
    return Result.success();
}
```

### 自定义 key

```java
// 按指定参数限流
@RateLimit(key = "api:user:#{#userId}", limit = 50, window = 60,
           limitType = RateLimit.LimitType.DEFAULT)
@GetMapping("/users/{userId}")
public Result getUser(@PathVariable Long userId) {
    return Result.success();
}
```

## 实现说明

- 基于 Redisson `RRateLimiter`，底层是 Redis + Lua 脚本的滑动窗口算法
- `RateType.OVERALL`：集群总速率（所有实例共享限流计数）
- `trySetRate()` 幂等设置，重复调用不会覆盖
- `tryAcquire()` 非阻塞获取令牌，获取不到立即返回
- `limit` 和 `window` 非法时会快速抛出配置异常，不访问 Redis

## Redis Key

| Key | 类型 | 用途 |
|---|---|---|
| `framework:rate:{type}:{key}` | Redisson RateLimiter | 限流计数 |

## 异常处理

限流时抛出 `BusinessException(3001, "请求过于频繁，请稍后再试")`，返回：
```json
{"code": 3001, "message": "请求过于频繁，请稍后再试", "data": null}
```
