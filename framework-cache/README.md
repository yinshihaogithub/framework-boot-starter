# framework-cache

> 多级缓存模块：Redis 封装、Caffeine 本地缓存、本地兜底、多级缓存、防穿透/击穿/雪崩。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 核心能力

| 功能 | 说明 |
|---|---|
| `CacheService` | 统一缓存接口 |
| `LocalCacheService` | Caffeine 本地缓存 |
| `RedisCacheService` | Redis 缓存实现 |
| `MultiLevelCacheService` | L1(Caffeine) + L2(Redis) 多级缓存 |
| 本地兜底 | 未提供 `StringRedisTemplate` 时自动注册本地 `CacheService` |
| 防缓存穿透 | Redis 层空值缓存 |
| 防缓存击穿 | Redis 层互斥锁 singleflight |
| 防缓存雪崩 | Redis TTL 随机抖动 |

## 配置

配置前缀：`framework.cache`。

```yaml
framework:
  cache:
    enabled: true
    multi-level: true
    local:
      max-size: 10000
      expire-after-write: 300
    remote:
      default-ttl: 3600
```

## 使用示例

```java
@Autowired
private CacheService cacheService;

public UserDTO getUser(Long id) {
    return cacheService.get("user:" + id, UserDTO.class,
            () -> userService.getById(id), 10, TimeUnit.MINUTES);
}
```

## 装配行为

| 场景 | 注册的 `CacheService` |
|---|---|
| 有 Redis 且 `multi-level=true` | `MultiLevelCacheService` |
| 有 Redis 且 `multi-level=false` | `RedisCacheService` |
| 没有 `StringRedisTemplate` | 本地 Caffeine 兜底实现 |
