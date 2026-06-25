# framework-monitor

> 监控模块：基于 Actuator 注册框架健康指示器。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-monitor</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置

配置前缀：`framework.monitor`。

```yaml
framework:
  monitor:
    enabled: true
    application-name: demo
```

## 核心能力

| 类 | 说明 |
|---|---|
| `FrameworkHealthIndicator` | 输出框架模块健康状态 |
| `MonitorAutoConfiguration` | 有 Actuator health 依赖时自动注册健康检查 |

## 装配行为

未引入 Actuator 时模块不会注册健康检查 Bean；业务可自定义同名 `frameworkHealthIndicator` 覆盖默认实现。
