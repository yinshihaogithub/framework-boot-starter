# framework-redis

> Redis 模块：统一 Key 规范、StringRedisTemplate 常用封装、轻量锁工具。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-redis</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置

配置前缀：`framework.redis`。

```yaml
framework:
  redis:
    enabled: true
    key-prefix: framework
    default-ttl: 1h
    lock-ttl: 30s
```

## 核心能力

| 类 | 说明 |
|---|---|
| `RedisKeyBuilder` | 统一生成 `{prefix}:{namespace}:{parts}` 格式的业务 Key |
| `RedisService` | 封装 set/get/delete 和轻量锁 |
| `RedisAutoConfiguration` | 有 `StringRedisTemplate` 时注册完整 Redis 服务 |

## 工程约束

- `key-prefix` 不能为空；`default-ttl` 和 `lock-ttl` 必须大于 0，配置错误会在启动期快速失败。
- `RedisKeyBuilder` 会拒绝空 `key-prefix`、空 `namespace` 和空 key 片段，并在拼接前去除首尾空格，避免生成不可治理或带隐形空格的 Redis key。
- `RedisService` 会在访问 Redis 前校验并归一化 key 和解锁 token 的首尾空格；TTL 必须大于 0。
- 轻量锁 `tryLock` 返回随机 token，`unlock` 必须传入同一个 token；释放锁使用 Lua compare-and-delete，避免 `get` 后 `delete` 的竞态。

## 使用示例

```java
@Autowired
private RedisKeyBuilder redisKeyBuilder;

@Autowired
private RedisService redisService;

String key = redisKeyBuilder.build("order", orderNo);
redisService.set(key, json);
String token = redisService.tryLock(redisKeyBuilder.build("lock", orderNo));
if (token != null) {
    try {
        // do work
    } finally {
        redisService.unlock(redisKeyBuilder.build("lock", orderNo), token);
    }
}
```

## 装配行为

| 条件 | 行为 |
|---|---|
| 有 `StringRedisTemplate` | 注册 `RedisKeyBuilder` 和 `RedisService` |
| 没有 `StringRedisTemplate` | 只注册 `RedisKeyBuilder`，不会强制连接 Redis |
