# framework-lock

> 分布式锁模块：基于 Redisson，注解式 + SpEL key + 看门狗自动续期 + fallback 回调。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-lock</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 需要配置 `RedissonClient`。如果应用未提供 `RedissonClient`，自动配置会跳过 `DistributedLockAspect`，不会影响应用启动。

## 注解参数

```java
@DistributedLock(
    key = "order:#{#orderId}",   // 锁 key，支持 SpEL（必填）
    waitTime = 3,                 // 等待获取锁的最大时间（秒），默认 3，必须 >= 0
    leaseTime = -1,               // 持有锁时间（秒），-1 启用看门狗自动续期，否则必须 > 0
    unit = TimeUnit.SECONDS,      // 时间单位，默认 SECONDS
    message = "操作繁忙，请稍候",   // 获取锁失败提示
    fallback = "handleLockFail"   // 获取锁失败回调方法名（可选）
)
```

`key` 不能为空。SpEL 上下文支持参数名、`#p0` / `#a0` 索引参数和 `#args` 数组；SpEL 解析失败或解析结果为空会快速抛出配置异常，避免把未解析表达式、空 key 或 `null` 写入 Redis key，写入 Redisson 前会归一化首尾空格。

`waitTime` 必须大于等于 0，`leaseTime` 只能为 `-1` 或大于 0，`unit` 不能为空；非法配置会在获取 Redisson 锁之前快速失败。

## 使用示例

### 基础用法

```java
@DistributedLock(key = "order:#{#orderId}")
public void processOrder(Long orderId) {
    // 同一 orderId 的请求会被串行化执行
    Order order = orderMapper.selectById(orderId);
    order.setStatus("PROCESSED");
    orderMapper.updateById(order);
}
```

### 看门狗自动续期（推荐）

```java
// leaseTime = -1（默认），业务执行期间锁不会过期
// 看门狗每 10s 检查并续期到 30s，业务完成后自动释放
@DistributedLock(key = "stock:#{#skuId}")
public boolean deductStock(Long skuId, int qty) {
    return stockMapper.deduct(skuId, qty);
}
```

### 固定持有时间

```java
// leaseTime = 10，10秒后锁自动释放（无论业务是否完成）
@DistributedLock(key = "task:#{#taskId}", waitTime = 5, leaseTime = 10)
public void executeTask(Long taskId) {
    // ...
}
```

### 获取锁失败回调

```java
@DistributedLock(
    key = "batch:#{#batchId}",
    waitTime = 1,
    fallback = "batchFallback"   // 回调方法名，签名需与原方法一致
)
public Result processBatch(Long batchId) {
    return Result.success("处理完成");
}

// 回调方法（同类中，参数一致）
public Result batchFallback(Long batchId) {
    return Result.fail("批次正在处理中，请稍后再试");
}
```

### SpEL 表达式

```java
// 访问参数字段
@DistributedLock(key = "order:#{#request.orderNo}")
public Result pay(PayRequest request) { ... }

// 多字段组合
@DistributedLock(key = "user:#{#userId}:#{#deviceId}")
public void bindDevice(Long userId, String deviceId) { ... }

// 静态字符串
@DistributedLock(key = "global:cleanup")
public void cleanup() { ... }

// 无参数名元数据时可使用索引参数
@DistributedLock(key = "order:#{#p0}")
public void processOrder(Long orderId) { ... }
```

## 看门狗机制

```
获取锁(leaseTime=-1)
  → 默认 30s 过期
  → 启动看门狗线程，每 10s 检查
    → 还持有锁 → 续期到 30s
    → 业务完成释放 → 停止看门狗
```

**适用场景**：业务执行时间不确定时，推荐 `leaseTime = -1`。
**不适用**：业务确定能在 X 秒内完成时，可设 `leaseTime = X` 避免看门狗开销。

## Redis Key

| Key | 类型 | TTL | 用途 |
|---|---|---|---|
| `framework:lock:{key}` | Redisson RLock | 30s（看门狗续期） | 分布式锁 |

## 异常处理

获取锁失败且未配置 fallback 时，抛出 `BusinessException(3002, "操作繁忙，请稍后再试")`。
