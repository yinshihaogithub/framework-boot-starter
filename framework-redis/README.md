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

## 使用示例

```java
@Autowired
private RedisKeyBuilder redisKeyBuilder;

@Autowired
private RedisService redisService;

String key = redisKeyBuilder.build("order", orderNo);
redisService.set(key, json);
String token = redisService.tryLock(redisKeyBuilder.build("lock", orderNo));
```

## 装配行为

| 条件 | 行为 |
|---|---|
| 有 `StringRedisTemplate` | 注册 `RedisKeyBuilder` 和 `RedisService` |
| 没有 `StringRedisTemplate` | 只注册 `RedisKeyBuilder`，不会强制连接 Redis |
