# framework-retry

> 重试与熔断降级模块：`@Retry` 指数退避重试 + `@CircuitBreaker` 熔断降级（基于 Resilience4j）。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-retry</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 自动装配生效。依赖 Resilience4j。

## 配置

配置前缀：`framework.circuit-breaker`。

```yaml
framework:
  circuit-breaker:
    default-config:
      failure-rate-threshold: 50
      slow-call-duration-threshold: 2000
      slow-call-rate-threshold: 0.8
      sliding-window-size: 100
      minimum-number-of-calls: 10
      wait-duration-in-open-state-seconds: 30
      permitted-number-of-calls-in-half-open-state: 10
      automatic-transition-from-open-to-half-open-enabled: true
    configs:
      payment:
        failure-rate-threshold: 25
        slow-call-duration-threshold: 1500
```

## 核心类

| 类 | 说明 |
|---|---|
| `@Retry` | 重试注解（固定/指数退避） |
| `@CircuitBreaker` | 熔断降级注解 |
| `RetryAspect` | 重试切面 |
| `CircuitBreakerAspect` | 熔断切面 |
| `CircuitBreakerConfig` | 熔断器配置 POJO |

## @Retry 使用

### 注解参数

```java
@Retry(
    maxAttempts = 3,              // 最大重试次数（不含首次），默认 3
    strategy = RetryStrategy.FIXED, // 重试策略：FIXED / EXPONENTIAL，默认 FIXED
    initialInterval = 1000,       // 初始等待间隔（ms），默认 1000
    multiplier = 2.0,             // 指数退避乘数（仅 EXPONENTIAL），默认 2.0
    maxInterval = 30000,          // 最大等待间隔（ms），默认 30000
    retryFor = {},                // 需重试的异常类型（为空则所有异常重试）
    noRetryFor = {},              // 不重试的异常类型（优先于 retryFor）
    fallback = ""                 // 重试耗尽后的回调方法名
)
```

### 固定间隔重试

```java
@Retry(maxAttempts = 3, strategy = RetryStrategy.FIXED, initialInterval = 1000)
public String callApi() {
    // 失败后等待 1s 重试，共重试 3 次
    // 1s → 1s → 1s
    return httpClient.get(url);
}
```

### 指数退避重试

```java
@Retry(maxAttempts = 4, strategy = RetryStrategy.EXPONENTIAL,
       initialInterval = 1000, multiplier = 2.0, maxInterval = 30000)
public String callApi() {
    // 1s → 2s → 4s → 8s（不超过 maxInterval）
    return httpClient.get(url);
}
```

### 指定异常重试

```java
@Retry(maxAttempts = 3,
       retryFor = {IOException.class, TimeoutException.class},
       noRetryFor = {BusinessException.class})
public String callApi() throws IOException {
    // 只有 IOException/TimeoutException 才重试
    // BusinessException 立即抛出不重试
    return httpClient.get(url);
}
```

### 重试耗尽回调

```java
@Retry(maxAttempts = 3, strategy = RetryStrategy.EXPONENTIAL,
       fallback = "callApiFallback")
public Result callApi() {
    throw new RuntimeException("调用失败");
}

// 回调方法（同类，参数与原方法一致）
public Result callApiFallback() {
    return Result.fail("服务暂不可用，请稍后重试");
}
```

## @CircuitBreaker 使用

### 注解参数

```java
@CircuitBreaker(
    name = "payment-service",       // 熔断器名称（必填）
    fallback = "payFallback",       // 降级方法名
    timeout = 2000,                 // 超时时间（ms），默认 2000
    failureRate = 0.5,              // 失败率阈值（0-1），默认 0.5
    slidingWindowSize = 100,        // 滑动窗口大小，默认 100
    waitDurationInOpenState = 30    // 熔断持续时间（秒），默认 30
)
```

### 熔断降级

```java
@CircuitBreaker(name = "payment-service", failureRate = 0.5, timeout = 2000,
                fallback = "payFallback")
@PostMapping("/pay")
public Result pay(PayRequest req) {
    return paymentClient.call(req);  // 失败率 > 50% 自动熔断
}

public Result payFallback(PayRequest req) {
    return Result.fail("支付服务暂不可用，请稍后重试");
}
```

## 熔断状态机

```
CLOSED（正常）
  │ 失败率 > 50%（滑动窗口内）
  ▼
OPEN（熔断，拒绝所有请求）
  │ 等待 30s
  ▼
HALF_OPEN（半开，放行 10 个探测请求）
  ├─ 探测成功 → CLOSED（恢复正常）
  └─ 探测失败 → OPEN（继续熔断）
```

### 熔断参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| failureRateThreshold | 50% | 失败率阈值 |
| slowCallDurationThreshold | 2000ms | 慢调用阈值 |
| slowCallRateThreshold | 80% | 慢调用比例触发 |
| slidingWindowSize | 100 | 统计窗口大小 |
| minimumNumberOfCalls | 10 | 最小请求量（低于此值不计算） |
| waitDurationInOpenState | 30s | 熔断持续时间 |
| permittedNumberOfCallsInHalfOpenState | 10 | 半开状态放行数 |

## 高可用四层防线

```
请求 → @RateLimit（限流，第一道防线）
      → @CircuitBreaker（熔断，第二道防线）
      → fallback（降级，兜底）
      → @Retry（重试，自愈）
```
