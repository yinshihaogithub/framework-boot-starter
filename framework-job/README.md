# framework-job

> 任务模块：Spring Scheduler 线程池配置，提供 `JobHandler` 注册和 `JobService` 手动触发。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-job</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置

配置前缀：`framework.job`。

```yaml
framework:
  job:
    enabled: true
    pool-size: 4
    thread-name-prefix: framework-job-
```

## 核心能力

| 类 | 说明 |
|---|---|
| `JobHandler` | 业务任务扩展点，暴露任务名和执行逻辑 |
| `JobService` | 按任务名查询、手动触发任务 |
| `DefaultJobService` | 默认任务注册表，自动收集所有 `JobHandler` |
| `JobAutoConfiguration` | 注册 `ThreadPoolTaskScheduler` 和 `JobService` |

## 使用示例

```java
@Bean
public JobHandler cleanJob() {
    return new JobHandler() {
        @Override
        public String name() {
            return "clean";
        }

        @Override
        public void execute() {
            cleanService.clean();
        }
    };
}
```

```java
@Autowired
private JobService jobService;

boolean triggered = jobService.run("clean");
```

## 装配行为

| 条件 | 行为 |
|---|---|
| `framework.job.enabled=true` | 启用调度线程池和任务注册表 |
| 存在业务 `JobHandler` Bean | 自动加入 `JobService` 可触发列表 |
| 业务自定义 `JobService` | 默认实现自动让位 |
