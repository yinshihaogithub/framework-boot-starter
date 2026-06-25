# framework-idempotent

> 接口幂等性模块：防止重复提交，支持 Token / RequestHash / BusinessKey 三种策略。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-idempotent</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 需要配置 Redis（基于 SETNX 实现）。自动装配生效。

## 注解参数

```java
@Idempotent(
    key = "order:#{#request.orderNo}",   // 幂等 key，支持 SpEL（为空时按策略自动生成）
    expire = 10,                           // 幂等窗口（秒），默认 10
    strategy = IdempotentStrategy.REQUEST_HASH,  // 幂等策略，默认 REQUEST_HASH
    message = "请勿重复提交"                // 拦截提示
)
```

## 三种策略

| 策略 | 原理 | 适用场景 |
|---|---|---|
| `REQUEST_HASH` | URI + 请求体 MD5 hash，窗口内相同 hash 拦截 | API 防重（默认） |
| `TOKEN` | 前端先获取 token，提交时校验并删除 | 表单提交 |
| `BUSINESS_KEY` | 业务唯一键（如订单号），SpEL 指定 | 支付/下单 |

## 使用示例

### RequestHash 策略（默认）

```java
// 自动对 URI + 请求体 + userId 做 hash，窗口内相同请求拦截
@Idempotent(expire = 5)
@PostMapping("/data")
public Result save(@RequestBody DataDTO dto) {
    return Result.success(service.save(dto));
}
```

### BusinessKey 策略

```java
@Idempotent(
    key = "pay:#{#request.orderNo}",
    expire = 10,
    strategy = IdempotentStrategy.BUSINESS_KEY
)
@PostMapping("/pay")
public Result pay(@RequestBody PayRequest request) {
    // 10秒内相同 orderNo 的请求只执行一次
    return Result.success(paymentService.process(request));
}
```

### Token 策略

```java
// 前端先调用获取 token，提交时放入 X-Idempotent-Token Header
@Idempotent(strategy = IdempotentStrategy.TOKEN, expire = 60)
@PostMapping("/submit")
public Result submit(@RequestBody FormDTO dto) {
    return Result.success(service.submit(dto));
}
```

## 实现原理

```
请求进入 → IdempotentAspect
  → 构建幂等 key（SpEL / Token / Hash）
  → Redis SETNX 抢占（TTL = expire）
    → 成功 → 执行业务
      → 成功 → 返回结果
      → 异常 → 删除 Redis key（允许重试）
    → 失败 → 抛出 BusinessException(3003, "重复请求已被拦截")
```

**关键特性**：业务异常时会释放幂等锁，允许用户重试。只有业务成功才保持幂等标记到窗口过期。

## Redis Key

| Key | 类型 | TTL | 用途 |
|---|---|---|---|
| `framework:idempotent:{key}` | String | expire（默认10s） | 幂等防重标记 |

## 异常处理

重复请求时抛出 `BusinessException(3003, "请勿重复提交")`，返回：
```json
{"code": 3003, "message": "请勿重复提交", "data": null}
```
