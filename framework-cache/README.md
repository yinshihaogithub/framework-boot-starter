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
| `MultiLevelCacheService` | L1(Caffeine) + L2(Redis) 多级缓存，删除时使用受控 daemon scheduler 做延迟双删 |
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
      max-size: 10000              # 必须 > 0
      expire-after-write: 300      # 秒，必须 > 0
    remote:
      default-ttl: 3600            # 秒，必须 > 0，Redis 默认写入/加载 TTL
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

## API 约束

- `key` / `pattern` 不能为空白。
- 带 TTL 的写入、加载和过期设置要求 `ttl > 0` 且 `TimeUnit` 非空。
- 多级缓存和本地兜底模式会把带 TTL 写入、loader 加载和 `expire()` 同步应用到 L1，避免 Redis 已过期但本地缓存继续命中旧值。
- 本地缓存配置 `framework.cache.local.max-size`、`framework.cache.local.expire-after-write` 必须大于 0，启动期快速失败。
- 未显式传入 TTL 的 Redis 写入和 loader 加载使用 `framework.cache.remote.default-ttl`，配置必须大于 0。
- `loader` 不能为空；缓存未命中时才会调用。
- `deleteByPattern()` 使用 `*` 作为通配符，其他字符按字面值处理；本地缓存使用安全 regex，Redis 缓存使用 `SCAN MATCH` 并转义 `?`、`[]`、`\` 等 glob 元字符，不使用阻塞式 `KEYS`，例如 `user[prod]?:*` 只匹配 `user[prod]?:1` 这类 key。
- 多级缓存 `delete()` 会先删 Redis、再删本地，并通过内部单线程 daemon scheduler 延迟 500ms 再删一次 Redis；不会为每次删除创建业务不可控线程。

## 装配行为

| 场景 | 注册的 `CacheService` |
|---|---|
| 有 Redis 且 `multi-level=true` | `MultiLevelCacheService` |
| 有 Redis 且 `multi-level=false` | `RedisCacheService` |
| 没有 `StringRedisTemplate` | 本地 Caffeine 兜底实现 |
