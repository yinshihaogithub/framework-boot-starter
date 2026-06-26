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

`application-name` 为空时默认输出 `framework-application`；非空时会在启动期校验，不能包含换行等控制字符，避免污染健康检查响应。

## 核心能力

| 类 | 说明 |
|---|---|
| `FrameworkHealthIndicator` | 输出框架模块健康状态、框架版本、Java/OS 和已引入 framework 模块列表，通过真实 marker 类识别按需引入的 `framework-job`、`framework-tools` 等模块 |
| `MonitorAutoConfiguration` | 有 Actuator health 依赖时自动注册健康检查 |

## 装配行为

未引入 Actuator 时模块不会注册健康检查 Bean；业务可自定义同名 `frameworkHealthIndicator` 覆盖默认实现。
