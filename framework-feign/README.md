# framework-feign

> Feign 模块：透传 `Authorization`、`X-Trace-Id`、`X-Tenant-Id` 和网关注入的 `X-User-*` 请求头。

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
      - X-User-Id
      - X-User-Name
      - X-User-Roles
      - X-User-Permissions
```

`relay-headers` 为空、未配置或归一化后只剩空白项时默认只透传 `X-Trace-Id`。配置的 Header 名会 trim，空白项会移除；非空 Header 名必须符合 HTTP token 规则，不能包含空格、冒号、换行等非法字符；配置错误会在启动期快速抛出 `IllegalArgumentException`，避免把协议问题推迟到下游调用。`X-Trace-Id` 的请求头值会按框架 traceId 规则净化，不合法时回退到 MDC 或生成新的 traceId。

## 核心能力

| 类 | 说明 |
|---|---|
| `FrameworkFeignRequestInterceptor` | Feign 请求头透传，包含 traceId fallback |
| `FeignAutoConfiguration` | 有 Feign 依赖时自动注册拦截器 |

## 装配行为

缺少 Feign 依赖时不会注册拦截器；业务方可提供同类型 Bean 覆盖默认实现。
