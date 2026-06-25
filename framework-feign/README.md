# framework-feign

> Feign 模块：透传 `Authorization`、`X-Trace-Id`、`X-Tenant-Id` 等请求头。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-feign</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置

配置前缀：`framework.feign`。

```yaml
framework:
  feign:
    enabled: true
    relay-headers:
      - Authorization
      - X-Trace-Id
      - X-Tenant-Id
```

## 核心能力

| 类 | 说明 |
|---|---|
| `HeaderRelayRequestInterceptor` | Feign 请求头透传，包含 traceId fallback |
| `FeignAutoConfiguration` | 有 Feign 依赖时自动注册拦截器 |

## 装配行为

缺少 Feign 依赖时不会注册拦截器；业务方可提供同类型 Bean 覆盖默认实现。
